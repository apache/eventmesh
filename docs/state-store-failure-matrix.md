# State store failure mode matrix

> **Status:** Per-backend failure behavior for the unified state control plane
> (issue #5301). Living document. Reflects the post-#5296 architecture review
> state of the `develop` branch. Tracked by #5339.

This page is the canonical answer to the architecture review question Q3
(2026-09-07): "what failure mode does each state store exhibit when its
backend (Meta or RocksDB) goes down, and is the failure visible to the
runtime / operator?" It also documents the restart / multi-instance /
fencing scenarios covered by the executable tests in
`StateStoreDurabilityTest`.

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
* This document exists (`docs/state-store-failure-matrix.md`).

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
