# EventMesh control plane

> **Audience:** operators and contributors. What the runtime boot path
> wires from configuration, and how every state store fails when its
> backend goes down.

---

This page documents the control-plane components that the runtime boot path
wires from configuration. For each component, we list the config key, the
default value, the failure behavior when the key is missing or malformed, and
the ingress route(s) that the component gates.

## 1. DeliveryTopology (#5293, #5309)

Selects how the runtime polls / dispatches events. Two modes:

| Mode | Default? | What it does | What it requires |
| --- | --- | --- | --- |
| `LOCAL_STICKY_PULL` | **Yes** | Every runtime instance polls every partition of every subscribed topic. No Meta store dependency. Backward compatible with pre-#5301 deployments. | None |
| `PARTITION_OWNED_PULL` | No | Each instance acquires ownership of a strict subset of partitions via Meta CAS + fencing. No duplicate consumption across instances. | Meta store (Nacos today). Code is intact (see `PartitionOwnership`); the wiring follow-up is tracked in #5309. |

### 1.1 Configuration

* **Key:** `eventmesh.delivery.topology`
* **Default:** `LOCAL_STICKY_PULL` (the value is read in
  `EventMeshApplication` via `DeliveryTopology.fromConfig(System.getProperty(...))`).
* **Set via:** JVM `-D` flag (`-Deventmesh.delivery.topology=PARTITION_OWNED_PULL`)
  or the `EVENTMESH_DELIVERY_TOPOLOGY` environment variable (the runtime
  forwards env-var values into system properties at boot).
* **Documented in `eventmesh.properties`:** the property is listed with a
  commented default and an inline explanation of both modes; operators can
  uncomment + edit without leaving the config file.

### 1.2 Failure behavior

* **Missing or blank** -> resolved to `LOCAL_STICKY_PULL` (backward
  compatible). The startup log emits `delivery topology=LOCAL_STICKY_PULL`.
* **Unknown value** (e.g. `PARTITION_OWNER` typo) -> `IllegalArgumentException`
  raised by `DeliveryTopology.fromConfig`. The runtime fails fast at boot;
  the operator sees a clear error in `bin/start.sh` output. A typo must not
  silently degrade to single-instance mode (see `DeliveryTopologyTest`).
* **`PARTITION_OWNED_PULL` without a Meta store** -> `enableCluster` is
  required. The boot path throws if `metaStore` is null when the topology is
  `PARTITION_OWNED_PULL`.

### 1.3 Coverage per ingress route

The delivery topology is referenced from a single code path
(`UniRuntime.poll` -> `ownedPartitions(topic)`), which is the consumer of
`UniIngressService.deliver`. Therefore every ingress path is covered by
construction:

| Ingress route | Path through topology |
| --- | --- |
| HTTP `POST /events/publish` | `UniHttpServer` -> `UniIngressService.publish` -> `Frame` -> `Producer.send` -> `poll loop` -> topology-selected partition set |
| A2A `POST /a2a/tasks/send` | `A2AGatewayHttpHandler` -> `A2AGatewayService` -> `UniIngressService` -> same as above |
| Legacy TCP (`UniTcpServer`) | main path is being phased out (see docs/feature/protocols.md #5341); when active, the TCP server feeds the same `UniIngressService` |
| WebSocket push | passive receiver; not gated by topology (the topology decides *which* partitions to *poll*, not the push path) |
| SSE | passive receiver; not gated by topology |

Coverage is verified by `git grep` of `DeliveryTopology` in the runtime
source tree (only `UniRuntime` and `DeliveryTopology` itself should match in
`eventmesh-runtime/src/main/`).

## 2. SecurityGate (#5304)

`SecurityGate` is the per-request control-plane gate that lives in front of
`UniIngressService` and `A2AGatewayService`. It is composed of:

* **FilterChain** (auth / acl): `TokenAuthFilter`, `SignatureVerifierFilter`,
  `AclFilter`.
* **QuotaManager** (per-tenant): `TenantQuotaManager`.
* **AuditSink**: pluggable (default is `LoggingAuditSink`).

### 2.1 Configuration

* **Key:** `eventmesh.security.gate.*` (see `eventmesh.properties` section
  `eventmesh.security.gate.*` and the per-filter keys).
* **Default:** a no-op gate that allows every request. This is the
  backward-compatible default and is documented as such in
  `docs/quickstart/configuration.md`.
* **Fail-closed mode:** when `eventmesh.security.gate.failClosed=true`,
  any filter chain exception (auth failed, signature invalid, ACL denied)
  results in a 401/403 response. The default is fail-open (allowed) so a
  misconfigured gate does not silently break a deployment.

### 2.2 Failure behavior

* **Missing or blank** -> no-op gate; every request is allowed. Operators
  who need a real gate must set the per-filter keys.
* **Malformed ACL entry** -> logged at WARN, the request is denied (fail
  safe in the security context), the deny is counted in the gate's metrics
  counter.
* **Audit sink failure** -> logged at ERROR, the request is still allowed
  (audit is best-effort; a failed audit must not take down the data path).

### 2.3 Coverage per ingress route

| Ingress route | Gated by SecurityGate? | Where the gate is invoked |
| --- | --- | --- |
| HTTP `POST /events/publish` | **Yes** | `UniHttpServer` -> `RequestContext` -> `SecurityGate.check(ctx, frame)` -> `UniIngressService.publish` |
| HTTP `POST /events/subscribe` | **Yes** | same chain |
| A2A `POST /a2a/tasks/send` | **Yes** | `A2AGatewayHttpHandler` -> gate -> `A2AGatewayService` -> `UniIngressService` |
| Legacy TCP | **Yes** (gated by TCP-level auth, separate from SecurityGate) | `UniTcpServer` -> `TcpIngressBridge` (the TCP path has its own connection-level auth, see docs/feature/protocols.md) |
| Admin HTTP | **Yes** | `UniAdminServer` -> `UniAdminService` (separate gate, configured under `eventmesh.admin.security.*`) |
| WebSocket push | n/a (passive) | push does not invoke the gate; the gate ran at subscribe time |
| SSE | n/a (passive) | same as WS |
| Connector Runtime | **Yes** | `ConnectorScheduler` -> gate (configured separately) |

## 3. Configuration matrix

| Property | Default | Component | Effect of the default |
| --- | --- | --- | --- |
| `eventmesh.delivery.topology` | `LOCAL_STICKY_PULL` | DeliveryTopology | Single-instance / no Meta dependency |
| `eventmesh.security.gate.failClosed` | `false` | SecurityGate | Backward-compatible allow-all |
| `eventmesh.storage.type` | (commented out) | Storage plugin | Must be set to a supported backend |
| `eventmesh.connector.offset.store` | `memory` | Connector | Offsets not persisted (dev-only) |

## 4. References

* Parent: #5296 (Architecture Review, Q2 / 2026-09-07)
* Tracking: #5338
* DeliveryTopology decision: #5293 (closed via PR #5308, implementing `LOCAL_STICKY_PULL`)
* DeliveryTopology wiring follow-up: #5309 (`PARTITION_OWNED_PULL` wiring)
* SecurityGate: #5304
* Architecture: `docs/architecture/overview.md` (section 4, Security gate)
* Configuration: `docs/quickstart/configuration.md`


## State store failure matrix

> Absorbed from the per-backend failure analysis (issue #5339). What happens when each store's backend goes down, and how the
> restart / multi-instance / fencing scenarios are covered by `StateStoreDurabilityTest`.

## 1. Store-to-backend map

| Store | Backend | Tier | Production impl | Test impl |
| --- | --- | --- | --- | --- |
| `OffsetStore` | RocksDB (local) | L1 | `RocksDBOffsetStore` | `InMemoryOffsetStore` (degraded mode only) |
| `SubscriptionStore` | Meta + local cache | L2 | `ClusterSubscriptionStore` (watches `/em/subs/`) | `state.fault.InMemorySubscriptionStore` |
| `SessionStore` | Meta + local cache | L2 | `SessionRegistry` (agents, bindings, sessions) | direct `SessionRegistry` over `InMemoryMetaStore` |
| `DeliveryStateStore` | RocksDB (local, sub-second flush) | L1 | `RocksDBDeliveryStateStore` | `InMemoryDeliveryStateStore` |
| `DeadLetterStore` | Meta CAS | L3 | `MetaBackedDeadLetterStore` (`/em/dlq/`) | direct wrapper over `InMemoryMetaStore` |
| `TaskStore` | Meta CAS | L3 | `MetaBackedTaskStore` (`/em/tasks/`) | direct wrapper over `InMemoryMetaStore` |

## 2. Per-backend failure behavior

### 2.1 RocksDB backend (OffsetStore, DeliveryStateStore)

| Scenario | Behavior | Operator-visible signal | Recovery |
| --- | --- | --- | --- |
| Local disk full | `writeOffset` / `put` returns `false` or throws `IllegalStateException` | `RocksDBOffsetStore.offsetWriteFailures` counter; `ReliableDispatcher.pendingCount` does not drop | Operator frees disk; next write succeeds. The dispatcher keeps the delivery in flight (issue #5290). |
| Process kill -9 (no graceful close) | The RocksDB file is the durability surface; the next process open at the same path sees the last flushed state | n/a (no log emitted on the way down) | The runtime calls `UniRuntime.alignPullOffsetsToAck` + `ReliableDispatcher.recover()` on boot. `StateStoreDurabilityTest#DeliveryStateStoreKillMinusNine` exercises this path. |
| Process restart (graceful) | `flush()` is called by the shutdown hook, then `close()`. Re-open at the same path is a no-op for the persisted entries | n/a | Same as kill -9. |
| Corrupted RocksDB file | `RocksDB.open` throws `RocksDBException` -> `IllegalStateException` at the constructor. The runtime fails fast at boot. | `IllegalStateException("failed to open RocksDB offset store at <path>")` in the boot log. | Operator restores from backup or wipes the data directory (data loss is unavoidable in this case; the runtime fails fast rather than silently losing state). |

### 2.2 Meta backend (SubscriptionStore, SessionStore, DeadLetterStore, TaskStore)

All four Meta-backed stores use the same primitives:

* `MetaStore.putIfAbsent` (CAS) for first-write-wins (DLQ, TaskStore.createTask).
* `MetaStore.tryAcquire(expectedOldValue, newValue)` (CAS) for status updates (TaskStore.updateStatus).
* `MetaStore.put` (last-write-wins) for plain updates (SubscriptionStore.put).
* `MetaStore.get` / `getWithPrefix` for reads.

| Scenario | Behavior | Operator-visible signal | Recovery |
| --- | --- | --- | --- |
| Meta unreachable (network partition) | `put` / `putIfAbsent` / `tryAcquire` throw `RuntimeException` (the production `NacosMetaStore` wraps the Nacos exception) | The exception is caught at the call site; `DeliveryDispatcher` keeps the delivery in flight; `A2AGatewayService` surfaces the error to the A2A caller; `ReliableDispatcher` keeps the in-flight delivery in the ledger | Once Meta heals, the next tick succeeds. Idempotent on the retry: `DeadLetterStore.recordDeadLetter` is idempotent on already-present keys; `TaskStore.createTask` returns `null` for a duplicate taskId (the gateway generates a fresh id and retries). |
| Meta cluster split-brain (two instances write the same key) | First-write-wins (`putIfAbsent`) is correct; `tryAcquire` rejects the stale writer (returns `false`); plain `put` is last-write-wins | The losing writer observes `false` from the CAS call and re-reads | Operator doesn't need to act; the contract is self-healing. `StateStoreDurabilityTest#TaskStoreMetaFailure` exercises the partitioned-write case. |
| Meta returns stale data (clock skew) | `MetaStore.tryAcquire(expectedOldValue=...)` rejects writes whose `expectedOldValue` does not match the current Meta value; the caller re-reads | The call returns `false` | Caller re-reads via `meta.get(key)` and retries with the fresh value. The Meta-backed stores do not carry their own wall-clock dependency; staleness surfaces as a CAS mismatch, not as a time-based inconsistency. |
| Meta restarts (process restart of the Meta server) | All reads return the snapshot from the most recent successful write; writes resume once the new Meta is reachable | The production `NacosMetaStore` reconnects via the Nacos client retry loop; the runtime logs the reconnection at INFO | No operator action needed; the in-process caches (`SessionRegistry.agentCache`, `ClusterSubscriptionStore.cache`) are rebuilt from the next watch event. `StateStoreDurabilityTest#DeadLetterStoreRestart` exercises the wrapper-restart-over-shared-Meta case. |
| Local in-process cache diverges from Meta | The next read through `MetaStore.get` / `getWithPrefix` returns the Meta view; the in-process cache is refreshed on the next watch event | n/a (no log) | Convergence is automatic; the local cache is best-effort. |

## 3. Restart / multi-instance / fencing coverage

Each row maps an issue #5339 acceptance scenario to the test that exercises it.

| # | Scenario | Store | Test |
| --- | --- | --- | --- |
| 1 | Restart: persist offset 100, kill runtime, restart, read offset = 100 | `OffsetStore` (RocksDB) | `StateStoreDurabilityTest#OffsetStoreRestart.offsetSurvivesProcessRestart` |
| 2 | Multi-instance: two `ClusterSubscriptionStore` instances sharing the same Meta; concurrent register / unregister converges | `SubscriptionStore` | `StateStoreDurabilityTest#SubscriptionStoreMultiInstance.twoInstancesConvergeOnSameView` (100-iteration storm, 8 threads) + `removeIsObservedByAllInstances` |
| 3 | Fencing: stale partition owner (instance A) writes after instance B takes over; the write is rejected | `SessionStore` | `StateStoreDurabilityTest#SessionStoreFencing.staleHeartbeatAfterTakeoverIsRejected` |
| 4 | Restart: DLQ ledger survives a wrapper restart (Meta is the durability surface) | `DeadLetterStore` (Meta) | `StateStoreDurabilityTest#DeadLetterStoreRestart.ledgerSurvivesStoreWrapperRestart` + `writeSucceedsAfterMetaHeals` |
| 5 | Cross-store failure: Meta goes down mid-submit; the TaskStore surfaces the failure rather than silently dropping the task | `TaskStore` (Meta) | `StateStoreDurabilityTest#TaskStoreMetaFailure.createTaskFailsWhenMetaIsPartitioned` + `updateStatusFailsWhenMetaIsPartitioned` |
| 6 | Kill -9: in-flight ledger survives abrupt close | `DeliveryStateStore` (RocksDB) | `StateStoreDurabilityTest#DeliveryStateStoreKillMinusNine.inFlightLedgerSurvivesAbruptClose` |

## 4. Acceptance check (for #5339)

* For each of the 6 stores above, a JUnit test exists in `eventmesh-runtime`
  (`StateStoreDurabilityTest`).
* The test for each scenario above is referenced in the table in section 3.
* No production-code store is backed by an `InMemory*` implementation; the
  in-memory implementations live in `state/fault/` (test-only).
* This document exists (`docs/feature/control-plane.md`).

## 5. References

* Parent: #5296 (Architecture Review, Q3 / 2026-09-07)
* Tracking: #5339
* Tests: `eventmesh-runtime/src/test/java/org/apache/eventmesh/runtime/state/StateStoreDurabilityTest.java`
* Related: #5289 (monotonic offset), #5290 (write failure semantics), #5291 (idempotency), #5292 (DLQ durability), #5301 (state control plane)
* Sub-PRs:
  - #5301 Sub-PR A (interfaces + SPI)
  - #5301 Sub-PR B (RocksDB-backed delivery + offset stores)
  - #5301 Sub-PR C (Meta-backed DLQ + TaskStore)
  - #5301 Sub-PR D (A2A Gateway on Runtime)
