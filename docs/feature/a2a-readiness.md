# A2A readiness decision (issue #5340)

> **Status:** Decided. Keep A2A **Experimental** for the next release cycle. Promotion
> to Beta is gated on the Testcontainers E2E suite (`eventmesh-a2a-e2e`) being
> green for at least one release. Tracked by #5340.

This page documents the readiness check called out in the architecture review (issue
#5296, question Q4, 2026-09-07): are task expiry / reaping, Meta-backed AgentCard
persistence, Runtime-dispatch integration, and real Meta / Runtime failure testing
complete? Until they are, A2A remains explicitly Experimental. The current
`docs/a2a-protocol/README.md` carries the EXPERIMENTAL banner; this page is the
decision log.

## 1. Acceptance status (issue #5340)

| # | Item | Status | Reference |
| --- | --- | --- | --- |
| 1 | `TaskExpirer` reaper (configurable TTL + scan interval, opt-in to gateway) | **Done (D2a)** | `eventmesh-runtime/.../a2a/TaskExpirer.java` + `TaskExpirerTest.java` |
| 2 | `MetaAgentCardRegistry` (cluster-shared, prefix-watch) | **Done (D2a)** | `eventmesh-runtime/.../a2a/MetaAgentCardRegistry.java` + `MetaAgentCardRegistryTest.java` |
| 3 | Testcontainers E2E `eventmesh-a2a-e2e` (Nacos + 2-3 EM instances + broker) | **Open (D2b)** | follow-up issue (will be filed) |
| 4 | Failure-mode test (Meta down -> A2A surfaces error) | **Done (D2a)** | `eventmesh-runtime/.../a2a/A2AGatewayFailureModeTest.java` |
| 5 | Decision: keep A2A Experimental, or promote to Beta | **Decided: keep Experimental** | this document |

Items 1, 2, 4 land in PR #5346 (Sub-PR D2a, this PR). Item 3 (D2b) is a follow-up
Testcontainers-based E2E suite that requires live Nacos + broker containers and a
2-3 instance EM cluster; it is intentionally out of scope for D2a because the
in-process unit + integration tests in D2a already cover the gateway's failure
semantics at the API boundary. D2b will exercise the same scenarios end-to-end
against real Meta + broker + multi-instance EM.

## 2. Why keep A2A Experimental

A2A is the only protocol in EventMesh that:

* Crosses a trust boundary (a client invokes an agent that lives behind a
  gateway; the gateway must reject tasks for unregistered agents).
* Has a multi-step lifecycle (PENDING -> RUNNING -> COMPLETED/FAILED/CANCELED)
  with strong per-step consistency requirements (epoch fencing, status
  convergence on cancel-vs-complete races).
* Was redesigned end-to-end in the uni-architecture work (#5302) and has not
  yet been validated against a live Meta + broker + multi-instance EM
  deployment.

Production users who adopt A2A before the readiness checks land will get a
correct runtime (the unit + integration tests cover the contract), but the
operational story is still incomplete: there is no runbook for "the Meta
went down, here is what to do", no SLO from a sustained production
deployment, and no Testcontainers E2E that catches a future regression. Marking
the protocol Experimental is the honest signal.

## 3. Promotion criteria (Experimental -> Beta)

All of the following must be true before A2A is promoted to Beta:

1. **D2b lands and is green in CI for at least one full release cycle.** The
   Testcontainers suite must cover: 3 EM instances behind a shared Nacos
   Meta, an A2A client submitting a task to agent-A, agent-A completing the
   task, and the client receiving the response. Killing the Meta mid-task
   must surface the failure to the client (the A2A layer must not hang).
2. **At least one production-style deployment has run for 30+ days with A2A
   traffic** and the operational metrics are within the SLOs (latency p99,
   error rate, Meta outage behavior). Until then, the SLOs are aspirational.
3. **The `eventmesh-a2a-protocol.md` document is updated** with the
   post-D2b wire contract, a worked example, and the SLOs from item 2.
4. **A deprecation note for the legacy A2A path (if any)** is added to the
   changelog and to `docs/feature/protocols.md` (see #5341).

When all four are true, this page is updated, the EXPERIMENTAL banner in
`docs/a2a-protocol/README.md` is changed to **BETA**, and the tracking issue
#5340 is closed.

## 4. Test inventory (D2a)

Three new test classes, all in `eventmesh-runtime` and run in the default
`test` task (no Testcontainers, no live broker):

* `TaskExpirerTest` (6 cases): idle eviction, fresh-not-evicted, listener
  notification, idempotent start/shutdown, background scan fires on schedule,
  invalid TTL/interval throws.
* `MetaAgentCardRegistryTest` (5 cases): register/lookup/remove, wrapper
  restart, two-instance shared Meta, malformed JSON is logged + ignored,
  null args are rejected.
* `A2AGatewayFailureModeTest` (2 cases): submitTask surfaces Meta failure to
  the caller (not hung, not silently dropped); submitTask succeeds after
  Meta heals.

Plus the existing `A2AGatewayServiceTest` (in
`eventmesh-runtime/src/test/.../a2a/`) which covers the happy path.

## 5. Out of scope (D2a explicitly does NOT do)

* Testcontainers E2E (D2b follow-up).
* A2A load-testing (separate concern; not part of #5340).
* Multi-region A2A (cross-data-center agent discovery; separate concern).
* A2A auth / RBAC (the gateway currently has no auth filter; this is a
  separate concern tracked outside the #5296 review).

## 6. References

* Parent: #5296 (Architecture Review, Q4 / 2026-09-07)
* Tracking: #5340
* Sub-PRs:
  - #5302 Sub-PR D1 (PR #5313 merged) - TaskStore + InMemoryAgentCardRegistry
    on Runtime
  - #5302 Sub-PR D2a (PR #5346) - TaskExpirer + MetaAgentCardRegistry +
    failure-mode test + this decision doc
  - #5302 Sub-PR D2b (follow-up) - Testcontainers E2E
* Sibling issues: #5296 (parent), #5337 (evidence table), #5339 (state store
  durability), #5341 (protocol/SDK boundary)
* Docs:
  - `docs/a2a-protocol/README.md` (carries the EXPERIMENTAL banner)
  - `docs/architecture/overview.md` (A2A section)
  - `docs/feature/control-plane.md` (DeliveryTopology / SecurityGate)
  - `docs/feature/control-plane.md` (per-backend failure mode)
