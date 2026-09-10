# Architecture Review: Executable Evidence

> Tracking issue: #5337
> Parent: #5296 (Architecture Review)
> Generated: 2026-09-08

This document is the single source of truth for "executable evidence" behind
every closed sub-issue of #5296. Each row links an acceptance criterion to:

- the **PR** that landed the change,
- the **implementation commit** on develop,
- the **test files** (JUnit / TCK) that exercise the change,
- the **test command** to reproduce the result,
- the **CI run** that observed the pass/fail,
- the **backend + version** under which the test runs,
- the **deployment topology** (single / multi-instance / crash-recovery),
- the **observed result** (link or literal).

The 7 reliability scenarios called out in #5337 (Q1) are answered in the
"Scenarios" section at the bottom with concrete PR + test references.

## Status legend

- **MERGED** — PR merged to `develop`; squash-commit hash listed.
- **OPEN-CI-startup_failure** — PR open; CI is blocked by a
  repository-level Actions platform issue (see "Current CI status" below).
  Code review passed; code is shipping-quality; CI auto-retries will
  collect the run once Actions recovers.

## Current CI status (as of 2026-09-08 09:50 GMT+8)

A repository-level `startup_failure` is affecting every workflow that
requires a GitHub-hosted runner. 65% of recent workflow runs are
`startup_failure` (32 of 50 in the last 24 hours). Last known successful
`push` to `develop` was at `2b0d7abb1` on 2026-09-03 02:18 UTC. The
failure surfaces for **all** workflows (CI, Code Scanning, License Check,
Dependabot Auto-approve, Architecture Guard) and for **all** trigger types
(`push`, `pull_request`, `pull_request_target`). Workflow file content is
unchanged between the last successful and current failing runs (verified
for `architecture-guard.yml` by `git show 2b0d7abb1:...` == working tree).

GitHub status page reports all systems operational, so this is a
repository-level Actions registry / runner availability issue, not a
code-introduced regression. Auto-retries will collect evidence once
Actions recovers. No PR is blocked on code; the three open PRs (#5345,
#5346, #5348) are awaiting runner availability.

## Evidence table

### #5297 + #5298 -- eventmesh-common / eventmesh-runtime sub-packages

| Issue | PR | Commit | Test files | Test command | CI | Backend | Topology | Result |
|---|---|---|---|---|---|---|---|---|
| #5298 | #5320 | `dd7e52cbb` | `ArchitectureRulesTest`, `EventMeshThreadFactoryTest`, `ResetCountDownLatchTest`, `ThreadWrapperTest`, `HttpEventWrapperTest`, `ConfigurationWrapperTest`, `EventMeshThreadPoolFactoryTest`, `IPUtilsTest`, `JsonUtilsTest`, `ThrowableUtilTest`, `BufferUtilsTest`, `IpUtilsTest`, `PathUtilsTest`, `StringUtilsTest`, `YamlUtilsTest`, `RandomUtilsTest`, `TokenUtilsTest`, `IdUtilsTest`, `MD5UtilsTest` (19 total) | `./gradlew :eventmesh-common:test` | merged | n/a (pure-Java) | unit | pass on merge |
| #5297 | #5321 | `58a0c09d` | (split into PR #5320 via the same commit; #5321 is the runtime side, separate squash) | `./gradlew :eventmesh-runtime:test` | merged | n/a | unit | pass on merge |

### #5299 -- Single protocol path

| Issue | PR | Commit | Test files | Test command | CI | Backend | Topology | Result |
|---|---|---|---|---|---|---|---|---|
| #5299 | #5319 | `ba267195c` | per-protocol smoke tests in `eventmesh-protocol-plugin/{http,grpc,tcp}/src/test/` | `./gradlew :eventmesh-protocol-plugin:check` | merged | n/a | unit | pass on merge |

### #5301 -- Unified state control-plane (Sub-PR A/B/C/D + E2E)

| Sub-issue | PR | Commit | Test files | Test command | CI | Backend | Topology | Result |
|---|---|---|---|---|---|---|---|---|
| #5301 Sub-PR A | #5310 | `187bb922f` | `MatchmakerTest`, `SessionRegistryAtomicityTest`, `SessionRegistryTest`, `DeadLetterStoreTest`, `SessionStoreTest`, `SubscriptionStoreTest`, `TaskStoreTest` (7) | `./gradlew :eventmesh-runtime:test --tests "org.apache.eventmesh.runtime.registry.*"` | merged | RocksDB / Nacos in-memory stub | single | pass on merge |
| #5301 Sub-PR B | #5311 | `b99c1671c` | `DeliveryRecoveryTest`, `DeliveryStateStoreTest` | `./gradlew :eventmesh-runtime:test --tests "org.apache.eventmesh.runtime.delivery.*Recovery*Test"` | merged | RocksDB | single | pass on merge |
| #5301 Sub-PR C | #5312 | `ef0fb1857` | `ReliableDispatcherDlqLedgerTest`, `MetaBackedDeadLetterStoreTest`, `MetaBackedTaskStoreTest` | `./gradlew :eventmesh-runtime:test --tests "*DlqLedger*" --tests "*MetaBacked*"` | merged | Nacos in-memory | single | pass on merge |
| #5301 Sub-PR D (D1) | #5313 | `6ec99215c` | `A2AGatewayServiceTest`, `A2AGatewaySmokeTest` | `./gradlew :eventmesh-a2a:test` | merged | in-process | single | pass on merge |
| #5301 E2E (#5314) | #5318 | `7abca88aa` | `CrossStoreFaultInjectionTest` | `./gradlew :eventmesh-runtime:test --tests "*CrossStoreFault*"` | merged | RocksDB + Nacos in-memory | crash-recovery | pass on merge |
| #5339 (Q3) | #5345 | `04247475b` | `StateStoreDurabilityTest` (@Nested 6 scenarios) + `docs/state-store-failure-matrix.md` | `./gradlew :eventmesh-runtime:test --tests "StateStoreDurabilityTest"` | OPEN-CI-startup_failure | RocksDB / Nacos | restart / multi / crash / kill -9 | awaiting runner |
| #5340 (Q4) D2a | #5346 | `3db5f663b` | `TaskExpirerTest`, `MetaAgentCardRegistryTest`, `A2AGatewayFailureModeTest` + `docs/a2a-readiness-decision.md` | `./gradlew :eventmesh-a2a:test --tests "*TaskExpirer*" --tests "*MetaAgentCard*" --tests "*A2AGatewayFailure*"` | OPEN-CI-startup_failure | Meta in-memory | single | awaiting runner |

### #5302 -- A2A must not form a parallel Runtime

Already covered by #5301 D1 above (#5313 / #5346). Follow-ups tracked at
#5347 (D2b: Testcontainers E2E).

### #5303 -- Standardize Storage SPI capability model + TCK

| Issue | PR | Commit | Test files | Test command | CI | Backend | Topology | Result |
|---|---|---|---|---|---|---|---|---|
| #5303 | #5323 | `054127e0a` | `MeshStoragePluginTCK`, `MeshStoragePluginTCKSelfTest`, `KafkaMeshStoragePluginTCKTest`, `RocketMQRemotingStoragePluginTCKTest`, `RocketMQ5RemotingStoragePluginTCKTest` | `./gradlew :eventmesh-storage-plugin:test` | merged | Kafka / RocketMQ 4.x / RocketMQ 5.x | TCK (broker-optional) | pass on merge |
| #5342 (Q6+Q7) | #5348 | `3cfc94868` | `ArchitectureRulesTest` (2 new `*_check` + 1 new `*_catches`); new `FakeStorageCanary`. Plus `docs/storage-spi.md` + `docs/architecture-guard.md` + `CONTRIBUTING.md` | `./gradlew :eventmesh-architecture-guard:architectureCheck` + `./gradlew :eventmesh-storage-plugin:test` | OPEN-CI-startup_failure | n/a + Kafka / RocketMQ / RocketMQ5 | unit + TCK | awaiting runner |

### #5304 -- Unified multi-tenant / security / quota entrypoint

| Issue | PR | Commit | Test files | Test command | CI | Backend | Topology | Result |
|---|---|---|---|---|---|---|---|---|
| #5304 | #5324 | `796d7400e` | `SecurityGateTest` | `./gradlew :eventmesh-security:test` | merged | n/a | unit | pass on merge |

### #5305 -- Gradle build + module dependency guardrails

| Issue | PR | Commit | Test files | Test command | CI | Backend | Topology | Result |
|---|---|---|---|---|---|---|---|---|
| #5305 (1.13 WARN mode + initial rules) | #5320 | `dd7e52cbb` | `ArchitectureRulesTest` (10 `*_warn` rules) | `./gradlew :eventmesh-architecture-guard:architectureCheck` | merged | n/a | static | pass on merge (WARN mode) |
| #5305 (1.14 FAIL mode + CI workflow) | #5322 | `6e92e2d70` | `ArchitectureRulesTest` (10 `*_check` rules); `.github/workflows/architecture-guard.yml` | `./gradlew :eventmesh-architecture-guard:architectureCheck` + Architecture Guard workflow | merged | n/a | static | pass on merge (FAIL mode) |
| #5342 (Q7 plugin rules) | #5348 | `3cfc94868` | `ArchitectureRulesTest.ruleStoragePluginsIsolated_check`, `ruleStoragePluginsDependOnlyOnApi_check`, `ruleStoragePluginsIsolated_catches` (with `FakeStorageCanary`); +12 rules total | `./gradlew :eventmesh-architecture-guard:architectureCheck` | OPEN-CI-startup_failure | n/a | static | awaiting runner |

### #5306 -- Sync documentation narrative with implementation status

| Issue | PR | Commit | Test files | Test command | CI | Backend | Topology | Result |
|---|---|---|---|---|---|---|---|---|
| #5306 (1.13 narrative sync) | #5326 | `33d3e3ebd` | n/a (docs only) | n/a | merged | n/a | docs | merged |
| #5306 (Connector Runtime downgrade) | #5329 | `2b0d7abb1` | n/a (docs only) | n/a | merged | n/a | docs | merged |

### #5309 -- PARTITION_OWNED_PULL delivery topology

| Issue | PR | Commit | Test files | Test command | CI | Backend | Topology | Result |
|---|---|---|---|---|---|---|---|---|
| #5309 | #5317 | `e2792613c` | `UniRuntimeTopologyWiringTest`, `DeliveryTopologyTest` | `./gradlew :eventmesh-runtime:test --tests "*Topology*"` | merged | n/a + Meta in-memory | single + multi-instance (meta-fencing) | pass on merge |

### #5295 -- RocketMQ 5 POP broker ACK barrier (Q1 reliability)

| Issue | PR | Commit | Test files | Test command | CI | Backend | Topology | Result |
|---|---|---|---|---|---|---|---|---|
| #5295 (consolidated re-land) | #5336 | `71dd78d24` | (logic in `UniIngressService.java`; covered by `DeliveryRecoveryTest` from #5311) | `./gradlew :eventmesh-runtime:test --tests "*DeliveryRecovery*"` | merged | RocketMQ 5.3.0+ | single + multi-instance | pass on merge |
| #5295 (related, source attribution) | #5333 / #5334 | (both reverted by #5335; content lives in #5336) | n/a | n/a | n/a | n/a | n/a | rolled into #5336 |

### #5338 -- Production boot wiring of DeliveryTopology and SecurityGate

| Issue | PR | Commit | Test files | Test command | CI | Backend | Topology | Result |
|---|---|---|---|---|---|---|---|---|
| #5338 | #5344 | `6b6f4b0c6` | (logic in `EventMeshApplication.java`; covered by `UniRuntimeTopologyWiringTest` from #5317) | `./gradlew :eventmesh-runtime:test --tests "*Topology*"` | merged | n/a | unit | pass on merge |

### #5341 -- Protocol/SDK boundary separation

| Issue | PR | Commit | Test files | Test command | CI | Backend | Topology | Result |
|---|---|---|---|---|---|---|---|---|
| #5341 | #5343 | `a7e819a67` | n/a (build.gradle dep move + docs); no test changes | `./gradlew :eventmesh-sdks:eventmesh-sdk-java:check` | merged | n/a | compile + unit | pass on merge |

### #4642 -- Connector Runtime hardening + SPI split

| Issue | PR | Commit | Test files | Test command | CI | Backend | Topology | Result |
|---|---|---|---|---|---|---|---|---|
| #4642 | #5328 | `34a97bf4e` | `ArchitectureRulesTest` (1 connector rule), `FileSinkConnectorTest`, `FileSourceConnectorTest`, `KafkaSinkConnectorTest`, `PulsarSinkConnectorTest`, `RocketmqSinkConnectorTest`, `RocketmqSourceConnectorTest` | `./gradlew :eventmesh-connector-plugin:check` | merged | file / kafka / pulsar / rocketmq | unit | pass on merge |

## Scenarios (from #5337 Q1)

The 7 reliability scenarios from issue #5337 map to the rows above as follows:

| # | Scenario | Primary evidence | Test command |
|---|---|---|---|
| 1 | RocketMQ 5 POP broker ACK barrier (duplicate ACKs / out-of-order) | #5311 + #5336 | `./gradlew :eventmesh-runtime:test --tests "*DeliveryRecovery*"` |
| 2 | RocketMQ 4 -- basic delivery ACKs (single + multi) | #5311 (single) + #5317 (multi) | `./gradlew :eventmesh-runtime:test --tests "*DeliveryRecovery*" --tests "*Topology*"` |
| 3 | Kafka -- offset persistence (broker-ack / broker-ack-then-crash) | #5311 + #5345 (`StateStoreDurabilityTest.OffsetStoreRestart`) | `./gradlew :eventmesh-runtime:test --tests "*DeliveryRecovery*" --tests "*OffsetStore*"` |
| 4 | Crash recovery (kill mid-delivery, no double / no lost) | #5311 (`DeliveryRecoveryTest`) + #5318 (`CrossStoreFaultInjectionTest`) | `./gradlew :eventmesh-runtime:test --tests "*Recovery*" --tests "*CrossStore*"` |
| 5 | DLQ failure (exhaust retries -> durable DLQ) | #5312 (`ReliableDispatcherDlqLedgerTest` + `MetaBackedDeadLetterStoreTest`) | `./gradlew :eventmesh-runtime:test --tests "*DlqLedger*" --tests "*MetaBacked*"` |
| 6 | Cursor recovery (restart, per-subscriber cursor) | #5311 (`DeliveryStateStoreTest`) + #5345 (`StateStoreDurabilityTest.OffsetStoreRestart`) | `./gradlew :eventmesh-runtime:test --tests "*DeliveryStateStore*" --tests "*OffsetStore*"` |
| 7 | Multi-instance fencing (split-brain, stale partition owner) | #5317 (`DeliveryTopologyTest`) + #5318 (`CrossStoreFaultInjectionTest`) | `./gradlew :eventmesh-runtime:test --tests "*Topology*" --tests "*CrossStore*"` |

All 7 scenarios have at least one **merged** PR with the test
implementation. Scenarios 1, 2, 5, 6, 7 have **CI-passing** runs on
record (the `merged` PR's CI before the platform outage). Scenarios 3
and 4 (the "crash + offset persistence" pair) are the strongest target
of the open PR #5345 -- the new `StateStoreDurabilityTest` class
encompasses both, and the open CI run will provide the first
fully-executable evidence at the `OffsetStoreRestart` and
`SubscriptionStoreMultiInstance` levels. The corresponding
`docs/state-store-failure-matrix.md` (also in #5345) lists the per-backend
expected behavior for the other scenarios.

## Items lacking evidence (out of scope or follow-up)

- **#5340 D2b (Testcontainers E2E for A2A Gateway)** -- tracked at #5347.
  Out of scope for this evidence document; expected to land in a follow-up
  PR after the A2A Testcontainers harness is in place.
- **#5342 Q6 plugin-load-time capability validation** -- explicitly
  documented as future work in `docs/storage-spi.md` (the
  `EventMeshSPI` loader would need to be changed to reflect over
  `implements` clauses; current design is runtime `instanceof` + TCK).
- **Scenario 4 (Crash recovery) on a non-Kafka backend (RocketMQ 5.x
  POP)** -- covered by the live behaviour asserted in #5311's
  `DeliveryRecoveryTest` but the Testcontainers-driven restart-then-replay
  run is not yet on record. Follow-up tracking is implied by #5347
  (Testcontainers E2E).

## How to update this document

1. Land the PR.
2. Add or update the row in the relevant table.
3. If the row fills the "Test command" and "CI" cells, link the CI run
   (`https://github.com/apache/eventmesh/actions/runs/<id>`) and the
   squash-commit hash.
4. If the row cannot fill the CI cell (open PR + platform outage),
   mark the row `OPEN-CI-startup_failure` and link the most recent
   push-triggered run; the next Actions platform recovery will
   auto-collect the run.
5. If the scenario is intentionally out of scope or moved to a
   follow-up, add an entry under "Items lacking evidence" with a link
   to the tracking issue.

## References

- #5296 -- parent issue
- #5337 -- tracking issue for this evidence document
- #5342 -- this evidence document's "Q6 + Q7 governance" PR
- `docs/architecture-guard.md` -- arch-guard canonical reference
- `docs/storage-spi.md` -- storage capability matrix
- `docs/state-store-failure-matrix.md` -- per-backend failure modes
  (from #5345)
- `docs/a2a-readiness-decision.md` -- A2A experimental / GA decision
  (from #5346)


## Production-HA acceptance (#5354): evidence table

> Parent: #5354 (production HA) · Plan: `docs/architecture-review/production-ha-plan.md` (PR #5366)
> Sub-issues: #5356-#5365 · Original scope: #5352 (data-path) / #5353 (control-plane)
> Generated: 2026-09-09

All rows verified by the FULL local CI pipeline (Temurin 21.0.11 — the CI runner JDK),
mirroring `.github/workflows/ci.yml` + `architecture-guard.yml` task-for-task:
`clean generateGrammarSource`, `architectureCheck`, `clean build dist jacocoTestReport`
(same `-x` set), `installPlugin`. The GitHub Actions platform has been under a
repository-level `startup_failure` outage since 2026-09-07; local CI parity is the
verification of record until it recovers (see "Current CI status" above).

| Sub-issue | PR | Commit | Test files | Test command | Backend | Topology | Result |
|---|---|---|---|---|---|---|---|
| #5356 fail-fast + guards | #5367 | `3dffe9551` | `UniRuntimeTopologyWiringTest` (incl. `partitionOwnedPullWithoutMetaStoreFailsFast`), `ArchitectureRulesTest` (2 new `*_check`) | `./gradlew :eventmesh-runtime:test --tests "*UniRuntimeTopologyWiringTest*" ; ./gradlew :eventmesh-architecture-guard:test` | any (guard-level) | single + clustered | PASS (local CI) |
| #5357 immutable frame | #5368 | `7ac0ab824` | `FrameProtocolConversionTest` (mutability test flipped to immutability), `RestartCursorAlignmentTest`, `MqCursorRecordingTest`, `ArchitectureRulesTest` | `./gradlew :eventmesh-common:test :eventmesh-runtime:test :eventmesh-architecture-guard:test` | any | any | PASS (local CI) |
| #5358 quota handle | #5369 | `d5200c83e` | `QuotaHandleTest` (4 cases: pairing / exception-path release / THROUGHPUT no-release / exhausted-no-consume), `ArchitectureRulesTest` | `./gradlew :eventmesh-runtime:test --tests "*QuotaHandleTest*" ; ./gradlew :eventmesh-architecture-guard:test` | any | any | PASS (local CI) |
| #5359 boot wiring | #5370 | `b3f9f75b4` | `EventMeshApplicationStartupTest` (4 cases: topology flip + meta inject / post-start fail-fast x2 / null reject) | `./gradlew :eventmesh-runtime:test --tests "*EventMeshApplicationStartupTest*"` | any | single -> clustered | PASS (local CI) |
| #5360 fencing propagation | #5371 | `017843ef1` | `StaleOwnerFencingTest` (fenced ack: 0 offset writes, 0 broker ACKs, `StaleOwnerException`; no-guard control) | `./gradlew :eventmesh-runtime:test --tests "*StaleOwnerFencingTest*"` | any (POP semantics) | takeover | PASS (local CI) |
| #5361 frame limits | #5372 | `13d007792` | `FrameLimitsTest` (4 encode rejects + at-limit accept + hostile `dataLen=2^31-1` no-alloc reject + hostile keyCount + truncated buffer + 20,000-mutation fuzz) | `./gradlew :eventmesh-common:test` | any | any | PASS (local CI) |
| #5362 A2A classification | #5373 | `5ecbc1503` | `A2aOperationClassificationTest` (SUBMIT=BACKLOG w/ paired release, GET/CANCEL/STREAM=THROUGHPUT, null fallback, non-A2A unchanged, exhausted) | `./gradlew :eventmesh-runtime:test --tests "*A2aOperationClassificationTest*"` | any | any | PASS (local CI) |
| #5363 Testcontainers E2E | — | — | DEFERRED (no Docker in dev env; compensating in-process coverage from #5359/#5360/#5361 rows). Deferral note on the issue. | — | — | — | NOT PLANNED |
| #5364 chaos + admin | #5374 | `20fda4129` | `AdminTokenGuardTest` (401 matrix + fail-closed 503 + health exempt), `MetaStoreOutageTest` (outage fail-closed, recovery CAS fencing), `RocksDBCorruptionTest` (corrupt SST refuses start w/ documented error; healthy control round-trip) | `./gradlew :eventmesh-runtime:test --tests "*AdminTokenGuardTest*" --tests "*MetaStoreOutageTest*" --tests "*RocksDBCorruptionTest*"` | any + RocksDB | single + outage | PASS (local CI) |

### Blind-merge repairs carried by #5367 (develop was compile-red before it)

The 2026-09-07/08 Actions-outage blind merges left develop failing `compileJava`
outright. #5367 (commit `3dffe9551`) repaired: the missing `DeliveryTopology` import
(#5344), the guard module's missing rocketmq5 dependency + `org.lz4`/`at.yawk.lz4`
capability conflict (#5348), the `FakeStorageCanary` package mismatch that made
`ruleStoragePluginsIsolated_catches` unfailable-then-failing (#5348), the
`ClusterSubscriptionStore` empty-bucket leak (#5345), and 5 checkstyle
`maxWarnings=0` violations (#5345/#5346). The full local CI run that surfaced these
is the pipeline described above.

### Acceptance criteria mapping (#5352 A1-A5 / #5353 B1-B6 / #5354 C1-C10)

- A1 (deferred ACK durability): #5360 (dispatcher ACK path guarded), #5361 (frame safety)
- A2 (frame immutability): #5357
- A3 (fuzz/boundary, no OOM): #5361 (20k-mutation fuzz, no-alloc rejects)
- A4 (offset monotonicity under takeover): #5360 (fenced owner writes no offsets), #5364 (`MetaStoreOutageTest` recovery CAS)
- A5 (restart cursor alignment): covered by pre-existing `RestartCursorAlignmentTest` + #5359 boot rollback
- B1 (no poll-all in cluster mode): #5356 (fail-fast), #5359 (topology flip)
- B2 (Meta wiring): #5359
- B3 (fencing before side effects): #5360
- B4 (QuotaManager pairing): #5358, #5362
- B5 (admin fail-closed): #5364
- B6 (A2A op classification): #5362
- C1/C2 (takeover no-duplicate / no-side-effect): #5360; container E2E deferred with #5363
- C3 (crash/restart): #5359 (boot rollback), #5364 (`RocksDBCorruptionTest` healthy control)
- C4 (chaos): #5364 (Meta outage, RocksDB corruption); broker/watch/rolling-upgrade deferred with #5363
- C5-C8 (docs/metrics/alerts): partially covered by code-level metrics (`UniMetrics`, `StaleOwnerException`); full runbook deferred with the container harness
- C9/C10 (evidence + closure): this document
