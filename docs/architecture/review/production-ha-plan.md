# Architecture Review: Production HA Acceptance

> **Status (2026-09-09): COMPLETE.** Phase 0-4 executed as #5367-#5374; #5363 (Testcontainers
> E2E) deferred with a compensating-coverage note on the issue. Evidence:
> `docs/architecture/review/evidence.md` § "Production-HA acceptance". The three parent
> issues (#5352 / #5353 / #5354) close with the evidence table.

> Tracking issue: #5354 (parent)
> Sub-issues: #5352 (data-path), #5353 (control-plane)
> Baseline: `apache/eventmesh` `develop` at commit `b08e591ac59c64479caf80bced59421bf7207262` (post-#5296 closure)
> Generated: 2026-09-08
> Status: Planning — 8 tracking issues to be opened against `apache/eventmesh`; no PR work has started.

This document is the executable plan that turns the three "production HA acceptance" bug reports
(#5352, #5353, #5354) into the same shape as the closed #5296 review:

- a **topology** of tracking issues, each with a Phase 0..4 placement and a Severity,
- an explicit **dependency graph** (Depends on / Blocks),
- a **PR plan** that produces MERGEABLE PRs in the order the topology demands,
- and a final **acceptance evidence** table that maps every acceptance criterion to a concrete
  commit + test + CI run + backend version.

The plan is **intentionally conservative**: every P0 issue either makes a previously-unwired
control plane assert itself, or removes a class of silent fallback that could mask a real outage.
P1 / P2 add the cross-instance takeover / chaos / rolling-upgrade / metrics / docs work that
production operations needs.

## 1. Topology

Ten tracking issues, ordered by Phase. Phase 0 must land before any Phase 1 work starts;
within a phase, issues may run in parallel.

| # | Phase | Severity | Title | Depends on |
|---|---|---|---|---|
| #5356 | 0 | P0 | arch-guard: ban `InMemoryMetaStore` and poll-all fallback in cluster mode | - |
| #5357 | 0 | P0 | arch-guard: ban mutable `EventMeshFrame` and unbounded attribute maps | - |
| #5358 | 0 | P0 | arch-guard: ban `QuotaManager` calls without release-handle (try-with-resources) | - |
| #5359 | 1 | P0 | wire `DeliveryTopology` + `MetaStore` + `FencingToken` into `UniRuntime` boot | #5356 |
| #5360 | 1 | P0 | propagate `FencingToken` epoch through dispatch / ACK / offset / DLQ | #5359 |
| #5361 | 1 | P0 | harden `EventMeshFrame` encode/decode: bounds, overflow, immutability | #5357 |
| #5362 | 2 | P1 | quota release-handle + A2A operation classification | #5358 |
| #5363 | 2 | P1 | cross-instance takeover + crash/restart + stale-owner integration tests | #5360, #5361 |
| #5364 | 3 | P1 | chaos (broker / MetaStore / watch loss) + rolling upgrade + admin security tests | #5363 |
| #5365 | 4 | P2 | documentation sync + final acceptance evidence | #5364 |

Ten issues cover what #5296's sixteen issues covered: half the number because (a) the A2A /
storage / common subpackage work from #5296 is already MERGED, and (b) the remaining work
clusters naturally into enforcement (Phase 0) → wiring (Phase 1) → production behavior (Phase 2)
→ operational evidence (Phase 3) → docs (Phase 4).

## 2. Phase breakdown

### Phase 0 — Enforcement (ArchUnit guards)

ArchUnit `*_check` rules that fail the build when the runtime regresses. P0 because they are
cheap, can land in parallel, and stop the bleeding before any new wiring lands.

- **#5356** — `PartitionOwnership` / `MetaStore` plumbing rules: ban `new InMemoryMetaStore()`
  outside test sources; ban `ownedPartitions(...)` returning `null` (poll-all fallback) in
  `PARTITION_OWNED_PULL` mode. Forces #5359 to actually wire Meta, instead of leaving the
  current "code is intact, wiring is a follow-up" gap.
- **#5357** — `EventMeshFrame` immutability: ban setter-style methods, ban public
  `LinkedHashMap` exposure, ban attributes > 64 entries. Forces #5361 to add bounds.
- **#5358** — Quota release: ban raw `quotaManager.tryAcquire(...)` calls without
  `try (QuotaHandle h = ...)` wrapper, in non-test code. Forces #5362 to add the handle.

### Phase 1 — Wiring (P0 runtime changes)

Code changes that are net-new wiring, mostly. P0 because they close the explicit "wired in
isolated tests, not in the application" gap called out in #5353 / #5354.

- **#5359** — Boot: `EventMeshApplication` resolves `eventmesh.delivery.topology`,
  `eventmesh.meta.store`, `eventmesh.security.gate.enabled` from system properties, validates
  them **before** starting any background task, fails fast on unsupported combinations
  (`PARTITION_OWNED_PULL` without a `MetaStore`; `PARTITION_OWNED_PULL` with a backend whose
  `StorageCapabilities.partitionCountKnown=false` unless topology is explicitly downgraded).
  Log the effective configuration on a single line at INFO so operators can audit a startup.
- **#5360** — Fencing: every offset write, broker ACK, DLQ transition, and dispatch call
  carries the owner's `FencingToken`. A new `StaleOwnerException` (with a
  `fencedPartitions` metric) is raised when the in-memory token is below the value in Meta;
  the dispatcher catches it and stops processing the partition, with the
  `PartitionOwnership` reaper re-trying ownership acquisition on the next tick.
- **#5361** — Frame: add `FrameLimits` constants (max frame 16 MiB, max payload 8 MiB, max
  attrs 64, max attr-name 256 B, max attr-value 4 KiB), reject `IllegalFrameException` on
  encode/decode boundary violations, change `EventMeshFrame` to be deeply immutable
  (Collections.unmodifiableMap for attrs, defensive copies on read), add
  `withAttribute(name, value)` builder API.

### Phase 2 — Production behavior (P1)

The cross-instance, multi-tenant, quota-aware behavior that the wiring in Phase 1 enables.

- **#5362** — Quota release handle: introduce `QuotaHandle` (AutoCloseable) wrapping
  `tryAcquire/release`; convert every `quotaManager.tryAcquire(...)` call site to
  `try (QuotaHandle h = gate.acquire(ctx, op, units)) { ... }`. Add `Operation.A2A_SUBMIT`,
  `A2A_GET`, `A2A_LIST`, `A2A_STREAM`, `A2A_WAIT`, `A2A_CANCEL` to `RequestContext.Operation`
  and map them to distinct `QuotaManager.Resource` buckets (THROUGHPUT for submit, CONNECTIONS
  for stream, BACKLOG for wait). Cleanup on cancel/disconnect/timeout via the AutoCloseable
  path.
- **#5363** — Cross-instance tests: Testcontainers spin up one Nacos (MetaStore) + one
  RocketMQ 5 broker + two EventMesh Runtime instances. Tests cover (a) crash + restart with
  offset monotonicity assertion, (b) `kill -9` instance A → instance B takes over and processes
  the same partitions, (c) instance A's in-flight deliveries either complete or DLQ (never
  silently disappear), (d) stale-owner dispatch is rejected after fencing epoch bump.

### Phase 3 — Chaos + upgrade + admin (P1)

The remaining "production operations" work. P1 not P0 because operations can be deferred
behind a deployment runbook if the P0/P1 wiring + tests pass; what matters is that the
acceptance criteria are answered with evidence, not that the work is small.

- **#5364** — Chaos: stop the MetaStore container mid-test, verify the runtime degrades to
  "fail-closed for new assignments, continue for owned partitions"; stop the broker, verify
  redelivery on broker restart; corrupt a RocksDB file in `OffsetStore`, verify the runtime
  refuses to start with a clear error. Rolling upgrade: build a v(N) image and a v(N+1) image,
  roll one instance at a time with mixed versions for 5 minutes, assert no offset regression
  and no duplicate delivery beyond the configured at-least-once bound. Admin: lock down
  `/admin/*` and `/metrics` behind a config-gated token (fail-closed by default), document
  the security posture in `docs/feature/control-plane.md`.

### Phase 4 — Documentation + evidence (P2)

The last mile.

- **#5365** — Update `docs/architecture/overview.md`, `docs/feature/control-plane.md`,
  `docs/feature/deployment.md`, `docs/feature/offset-management.md` to reflect the
  real behavior of the runtime after #5359-#5364. Add a row to
  `docs/architecture/review/evidence.md` for each of the 8 tracking issues, with the same
  7-column format as the #5296 evidence table. Final cross-link from #5354 to the updated
  docs; close #5352, #5353, #5354.

## 3. Acceptance criteria → issue mapping

Drawn directly from the bodies of #5352, #5353, #5354. Every criterion appears in exactly one
tracking issue; if a criterion needs work in two places, the second place lists the first
in its `Depends on` column.

| # | Acceptance criterion (paraphrased) | Owning issue |
|---|---|---|
| A1 | POP ACK failure is observable, durable, retryable, bounded (with TTL) | #5360 + #5361 |
| A2 | Crash/restart tests pass for Kafka, RocketMQ 4.x, RocketMQ 5.x | #5363 |
| A3 | Frame fuzz and boundary tests reject malformed input without OOM | #5361 |
| A4 | Streaming overflow is not reported as a successful complete stream | #5361 + #5362 |
| A5 | Documentation distinguishes pull vs deliver/ack offsets, delivery IDs | #5365 |
| B1 | `PARTITION_OWNED_PULL` fails fast without a shared MetaStore | #5356 + #5359 |
| B2 | `PARTITION_OWNED_PULL` fails fast (or downgrades) without a partition count | #5356 + #5359 |
| B3 | Stale owners cannot dispatch / ACK / write offsets / broker-ACK / DLQ | #5360 |
| B4 | Security and quota config produces documented runtime behavior | #5362 |
| B5 | Route-level integration tests cover HTTP / A2A / TCP / SSE / WS / admin | #5363 + #5364 |
| B6 | Active / deprecated control-plane components are accurately documented | #5365 |
| C1 | `DeliveryStateStore` and `OffsetStore` are wired into `EventMeshApplication` | #5359 |
| C2 | Stale owners cannot produce side effects after takeover | #5360 + #5363 |
| C3 | POP ACK failures are durable, retryable, observable, bounded | #5360 |
| C4 | A blocked topic does not stop unrelated topics (per-partition isolation) | #5359 |
| C5 | Backup/restore and rolling-upgrade tests pass without state regression | #5364 |
| C6 | Admin / metrics endpoints meet auth/authz/audit requirements | #5364 |
| C7 | Resource limits prevent unbounded memory under tenant/topic/partition pressure | #5359 + #5362 |
| C8 | Required failure / recovery metrics are emitted and alertable | #5364 |
| C9 | Request-reply / streaming / connector / admin HA guarantees are explicit and tested | #5363 + #5364 |
| C10 | Evidence is recorded in `docs/architecture/review/evidence.md` before closing | #5365 |

## 4. PR plan (one PR per issue, plus evidence PRs)

| Issue | Sub-PR | Branch | Approx. files | Verification |
|---|---|---|---|---|
| #5356 | arch-guard ban poll-all | arch-guard/5356-ban-poll-all` | `ArchitectureRules.java`, `ArchitectureRulesTest.java` | `./gradlew :eventmesh-architecture-guard:test` |
| #5357 | arch-guard ban mutable frame | arch-guard/5357-mutable-frame` | same | same |
| #5358 | arch-guard ban raw quota | arch-guard/5358-quota-handle` | same | same |
| #5359 | wire DeliveryTopology | fix/5359-delivery-topology-wiring` | `UniRuntime.java`, `EventMeshApplication.java`, `DeliveryTopologyTest.java` | unit + boot test |
| #5360 | fencing epoch | fix/5360-fencing-epoch` | `ReliableDispatcher.java`, `FencingToken.java`, `DeliveryStateStore*` | unit + integration (single broker) |
| #5361 | frame bounds | fix/5361-frame-bounds` | `EventMeshFrame*.java`, `FrameLimits.java`, fuzz test | unit + fuzz |
| #5362 | quota handle | fix/5362-quota-handle` | `QuotaManager.java`, `SecurityGate.java`, all call sites | unit + route-level |
| #5363 | cross-instance tests | fix/5363-cross-instance-tests` | new `IntegrationTest.java` files, Testcontainers harness | Testcontainers (Nacos + RocketMQ 5) |
| #5364 | chaos + upgrade + admin | fix/5364-chaos-upgrade-admin` | new `ChaosTest.java`, `RollingUpgradeTest.java`, `UniAdminServer.java` | Testcontainers multi-host |
| #5365 | docs + evidence | docs/5365-evidence` | 4 design docs, evidence.md | docs PR (no code) |

## 5. Sequencing rationale

Phase 0 first because ArchUnit rules are cheap, fail the build locally in 30 seconds, and
prevent any new PR from regressing the runtime even if subsequent wiring lands late. Phase 1
is the actual P0 fix. Phase 2 is the P1 that proves the fix works in a multi-instance
configuration. Phase 3 is the operational reality. Phase 4 is the paperwork.

Within a phase, the three P0 issues (#5356, #5357, #5358) are independent and can run as
parallel PRs; the two Phase 1 wiring PRs (#5359 + #5360) are sequential because #5360
depends on #5359's boot wiring to exist.

## 6. Open risks

- **CI platform outage (2026-09-07 16:23 UTC → ongoing).** Affects every
  `actions/checkout@v6` workflow. Until GitHub Actions recovers, backend integration tests
  will be `startup_failure` and we will have to fall back to local test runs plus manual
  evidence collection. Mitigation: each Sub-PR's `Verification` column lists a local
  `./gradlew` command so the work is verifiable on a developer's machine.
- **Scope of #5363 / #5364.** Testcontainers E2E is the largest single piece of work
  (Nacos + RocketMQ 5 + 2 Runtime instances). If it slips, the rest of the review can still
  close on unit / boot tests, with a follow-up issue for the E2E harness.
- **`LOCAL_STICKY_PULL` semantics.** The new "multi-instance = duplicate delivery"
  documentation is a behavior change for anyone who had been running two instances. The
  `docs/feature/deployment.md` update in #5365 must call this out clearly.
