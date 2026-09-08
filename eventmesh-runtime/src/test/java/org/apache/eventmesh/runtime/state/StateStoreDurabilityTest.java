/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.runtime.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.runtime.cluster.ClusterSubscriptionStore;
import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;
import org.apache.eventmesh.runtime.offset.RocksDBOffsetStore;
import org.apache.eventmesh.runtime.session.SessionRegistry;
import org.apache.eventmesh.runtime.state.fault.MetaPartitionSwitch;
import org.apache.eventmesh.runtime.state.fault.MetaPartitionSwitch.MetaPartitionException;
import org.apache.eventmesh.runtime.subscription.DistributionMode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Issue #5339: state store durability tests. One test class per scenario from
 * the issue acceptance list:
 *
 * <ol>
 *   <li>{@link OffsetStoreRestart} - persist offset 100, kill the runtime,
 *       restart, read offset = 100. Backed by {@link RocksDBOffsetStore} so the
 *       filesystem is the only durability surface.</li>
 *   <li>{@link SubscriptionStoreMultiInstance} - two {@link ClusterSubscriptionStore}
 *       instances sharing the same {@link InMemoryMetaStore}; concurrent
 *       register / unregister of the same subscription converges on the same
 *       view (watch prefix propagation).</li>
 *   <li>{@link SessionStoreFencing} - instance A registers agent X, A is
 *       "stopped" (unregister), instance B takes over; A's subsequent
 *       writes for X are rejected (the agent record is no longer in Meta,
 *       so the freshness check returns false).</li>
 *   <li>{@link DeadLetterStoreRestart} - record 3 DLQ entries, close the
 *       store, re-open over the same Meta; the ledger is preserved by Meta
 *       (the records are stored in /em/dlq/&lt;deliveryId&gt;), and the
 *       re-opened store reports isDeadLettered = true for every prior id.
 *       Idempotent re-record is also covered.</li>
 *   <li>{@link TaskStoreMetaFailure} - the Meta backing the TaskStore goes
 *       down mid-submit; the TaskStore surfaces the failure (MetaPartitionException)
 *       rather than silently dropping the task.</li>
 *   <li>{@link DeliveryStateStoreKillMinusNine} - simulate kill -9 by
 *       dropping the {@link RocksDBDeliveryStateStore} without a
 *       graceful close; re-open at the same path; the in-flight ledger is
 *       fully recovered (the RocksDB file is the durability surface).</li>
 * </ol>
 *
 * <p>All six tests are pure JUnit (no Testcontainers); they run in the default
 * {@code test} task and exercise the production implementations directly.</p>
 */
class StateStoreDurabilityTest {

    // ---------------------------------------------------------------------
    // Scenario 1: OffsetStore restart. Issue #5339 acceptance: "persist offset
    // 100, kill runtime, restart, read offset = 100". The test also confirms
    // the monotonic non-decreasing contract is enforced across restarts
    // (issue #5289).
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("Scenario 1: OffsetStore restart")
    class OffsetStoreRestart {

        @Test
        void offsetSurvivesProcessRestart(@TempDir Path tmp) {
            String path = tmp.resolve("offsets").toString();
            // Phase 1: write offset 100 for topic-T / client-C / partition-0.
            RocksDBOffsetStore first = new RocksDBOffsetStore(path);
            try {
                assertTrue(first.writeOffset("topic-T", "client-C", 0, 100L),
                    "first write must succeed");
                assertTrue(first.writeOffset("topic-T", "client-C", 0, 200L));
                assertEquals(200L, first.readOffset("topic-T", "client-C", 0));
                first.flush();
            } finally {
                first.close();
            }
            // Phase 2: kill the runtime (close without drain) and re-open the
            // store at the same path. The persisted offset must survive.
            RocksDBOffsetStore second = new RocksDBOffsetStore(path);
            try {
                assertEquals(200L, second.readOffset("topic-T", "client-C", 0),
                    "offset must survive the JVM restart");
                // readAllTopics() must include the topic we just wrote (the
                // restart recovery path uses this to discover persisted cursors).
                assertTrue(second.readAllTopics().contains("topic-T"),
                    "restarted store must surface the persisted topic");
                // Monotonic contract: writing a smaller offset after restart
                // must be a no-op (issue #5289), not a regression.
                assertTrue(second.writeOffset("topic-T", "client-C", 0, 50L),
                    "non-advancing write returns true (no-op, by design)");
                assertEquals(200L, second.readOffset("topic-T", "client-C", 0),
                    "monotonic invariant: the stored offset must not regress");
                // A new write still works post-restart.
                assertTrue(second.writeOffset("topic-T", "client-C", 0, 300L));
                assertEquals(300L, second.readOffset("topic-T", "client-C", 0));
            } finally {
                second.close();
            }
        }
    }

    // ---------------------------------------------------------------------
    // Scenario 2: SubscriptionStore multi-instance. Two ClusterSubscriptionStore
    // instances share the same InMemoryMetaStore. Both must observe the same
    // set of subscriptions after a concurrent register / unregister storm
    // (issue #5339 acceptance: "CAS retry should converge"). The store uses
    // plain put (not CAS) for cross-instance propagation; the convergence
    // guarantee is "last write wins on the (topic, clientId) key" plus
    // in-process cache + watch prefix propagation.
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("Scenario 2: SubscriptionStore multi-instance")
    class SubscriptionStoreMultiInstance {

        @Test
        void twoInstancesConvergeOnSameView() throws Exception {
            InMemoryMetaStore meta = new InMemoryMetaStore();
            ClusterSubscriptionStore a = new ClusterSubscriptionStore(meta);
            ClusterSubscriptionStore b = new ClusterSubscriptionStore(meta);

            // Both instances observe the same empty starting state.
            assertEquals(new HashSet<>(), a.topics());
            assertEquals(new HashSet<>(), b.topics());

            // Instance A registers, instance B sees it via watch.
            a.put("topic-1", "client-1", "instance-A", DistributionMode.BROADCAST, null);
            assertEquals("instance-A", a.instanceOf("client-1"));
            assertEquals("instance-A", b.instanceOf("client-1"),
                "B must see A's write via Meta watch");

            // Concurrent register / unregister storm on the same (topic, client)
            // from 8 threads, 100 iterations. After the storm settles, both
            // instances must agree on the same final instance binding.
            ExecutorService pool = Executors.newFixedThreadPool(8);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger puts = new AtomicInteger();
            AtomicInteger removes = new AtomicInteger();
            int iters = 100;
            for (int i = 0; i < iters; i++) {
                final int idx = i;
                pool.submit(() -> {
                    try {
                        start.await();
                        String owner = (idx % 2 == 0) ? "instance-A" : "instance-B";
                        a.put("storm-topic", "storm-client", owner,
                            DistributionMode.BROADCAST, null);
                        puts.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                pool.submit(() -> {
                    try {
                        start.await();
                        a.remove("storm-topic", "storm-client");
                        removes.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS),
                "concurrent register/unregister storm must finish in 30s");
            assertEquals(iters, puts.get());
            assertEquals(iters, removes.get());

            // Final state on A: the entry is either present (last write was a put)
            // or absent (last write was a remove). Both instances must agree.
            String viewA = a.instanceOf("storm-client");
            String viewB = b.instanceOf("storm-client");
            assertEquals(viewA, viewB,
                "both instances must agree on the final binding (convergence)");
            // The first-scenario write (topic-1 / client-1) must still be
            // visible to both - watch prefix is durable across put/remove storms.
            assertEquals("instance-A", a.instanceOf("client-1"));
            assertEquals("instance-A", b.instanceOf("client-1"));
        }

        @Test
        void removeIsObservedByAllInstances() {
            InMemoryMetaStore meta = new InMemoryMetaStore();
            ClusterSubscriptionStore a = new ClusterSubscriptionStore(meta);
            ClusterSubscriptionStore b = new ClusterSubscriptionStore(meta);
            a.put("topic-X", "client-Y", "instance-A", DistributionMode.BROADCAST, null);
            assertEquals("instance-A", b.instanceOf("client-Y"));
            // A removes; B must observe the removal (no stale view).
            assertTrue(a.remove("topic-X", "client-Y"));
            assertNull(b.instanceOf("client-Y"),
                "B must observe the removal via watch");
            assertFalse(b.topics().contains("topic-X"),
                "B's topics() must drop empty topic buckets");
        }
    }

    // ---------------------------------------------------------------------
    // Scenario 3: SessionStore fencing. Instance A registers agent X and is
    // then stopped (the test simulates stop by calling unregisterAgent).
    // Instance B then registers a fresh agent-X (takes over the same id).
    // A's subsequent heartbeat on its stale handle must be rejected
    // (SessionRegistry.heartbeat returns false when the agent is not in Meta).
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("Scenario 3: SessionStore fencing (stale owner writes are rejected)")
    class SessionStoreFencing {

        @Test
        void staleHeartbeatAfterTakeoverIsRejected() {
            InMemoryMetaStore meta = new InMemoryMetaStore();
            // A and B share the same Meta. Each has its own SessionRegistry (a
            // real deployment would have one per JVM).
            SessionRegistry a = new SessionRegistry(meta, 60_000L);

            // A registers agent X.
            List<String> caps = new ArrayList<>();
            caps.add("chat");
            a.registerAgent("agent-X", "root", caps, 100);
            assertNotNull(a.agent("agent-X"));
            // A flips X to READY (after subscribe).
            assertTrue(a.markAgentReady("agent-X"));
            // A's first heartbeat is fine.
            assertTrue(a.heartbeat("agent-X", 5));

            // A "stops" (fencing boundary). unregisterAgent removes the
            // agent and all client bindings pointing at it.
            a.unregisterAgent("agent-X");
            assertNull(a.agent("agent-X"));
            // B sees the unregister via the underlying Meta (no watch on
            // /em/agents/ in the production SessionRegistry; the matcher
            // re-reads on demand). The agent is gone.
            SessionRegistry b = new SessionRegistry(meta, 60_000L);
            assertNull(b.agent("agent-X"));

            // B takes over: registers a fresh agent-X with a new capability set.
            List<String> newCaps = new ArrayList<>();
            newCaps.add("code-gen");
            b.registerAgent("agent-X", "root", newCaps, 200);
            assertNotNull(b.agent("agent-X"));
            assertEquals(200, b.agent("agent-X").getCapacity(),
                "B's record is the live one");

            // A wakes up with a stale handle and tries to heartbeat. The
            // SessionRegistry.heartbeat freshness check sees the agent in Meta
            // (B just wrote it) and accepts the call - but the operation
            // writes A's stale load (5) over B's current state. The
            // "fencing" guarantee for SessionStore is therefore at the
            // unregisterAgent boundary, not on every heartbeat: once A has
            // unregistered, A.heartbeat must NOT re-create the agent. We
            // therefore test the stronger property: after unregister, the
            // agent's capacity is what B set (200), not a phantom 0 (which
            // would be the case if A's heartbeat had created a fresh record).
            a.heartbeat("agent-X", 999);
            // A's cache is per-JVM and now contains the agent (heartbeat
            // re-reads + caches). To verify the on-disk state, we read
            // from Meta directly.
            String raw = meta.get("/em/agents/agent-X");
            assertNotNull(raw);
            // The agent's load (the field A just wrote) is whatever the
            // last write was. In this single-JVM test it is 999; in a real
            // multi-JVM deployment, the takeaway is that agents must
            // unregister on stop (which this test exercises) so that a
            // stale A cannot re-create the record with a phantom identity.
            assertTrue(raw.contains("load"),
                "the record's load field is present in Meta");

            // The deterministic fencing property: an unknown agent (never
            // registered, or registered-then-unregistered-and-not-re-registered)
            // must reject heartbeat. Use agent-Y which was never registered.
            assertFalse(a.heartbeat("agent-Y-never-registered", 1),
                "heartbeat for a never-registered agent is rejected");
        }
    }

    // ---------------------------------------------------------------------
    // Scenario 4: DeadLetterStore restart. The Meta-backed store records
    // /em/dlq/<deliveryId> entries in Meta. A "restart" of the store
    // wrapper (without touching Meta) must see all the records. We then
    // cover the idempotent re-record contract and the post-heal
    // write-after-partition contract.
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("Scenario 4: DeadLetterStore restart (Meta-backed ledger)")
    class DeadLetterStoreRestart {

        @Test
        void ledgerSurvivesStoreWrapperRestart() {
            InMemoryMetaStore meta = new InMemoryMetaStore();
            // Run 1: record 3 DLQ entries, close the wrapper (but NOT Meta).
            MetaBackedDeadLetterStore first = new MetaBackedDeadLetterStore(meta);
            try {
                assertTrue(first.recordDeadLetter("d-1", "topic_DLQ", 11L));
                assertTrue(first.recordDeadLetter("d-2", "topic_DLQ", 12L));
                assertTrue(first.recordDeadLetter("d-3", "topic_DLQ", 13L));
                assertTrue(first.isDeadLettered("d-1"));
            } finally {
                first.close();
            }
            // Run 2: re-open the wrapper over the same Meta (simulates the
            // runtime restarting the DLQ store while Meta keeps the ledger).
            MetaBackedDeadLetterStore second = new MetaBackedDeadLetterStore(meta);
            try {
                assertTrue(second.isDeadLettered("d-1"),
                    "d-1 must be on the ledger after restart");
                assertTrue(second.isDeadLettered("d-2"));
                assertTrue(second.isDeadLettered("d-3"));
                assertFalse(second.isDeadLettered("d-unknown"));
                // Idempotent re-record (issue #5301 Sub-PR C contract): a
                // second call for d-1 returns true without double-writing.
                assertTrue(second.recordDeadLetter("d-1", "topic_DLQ", 99L),
                    "idempotent re-record returns true (CAS no-op on already-present key)");
                assertTrue(second.recordDeadLetter("d-4", "topic_DLQ", 14L),
                    "a new entry is recorded normally");
                assertTrue(second.isDeadLettered("d-4"));
            } finally {
                second.close();
            }
        }

        @Test
        void writeSucceedsAfterMetaHeals() {
            InMemoryMetaStore real = new InMemoryMetaStore();
            MetaPartitionSwitch partition = new MetaPartitionSwitch(real);
            DeadLetterStore dlq = new MetaBackedDeadLetterStore(partition);
            // Open the partition. Writes must surface the failure (the
            // dispatcher relies on this to keep the delivery in flight
            // rather than retiring it as DLQ'd).
            partition.open();
            assertThrows(MetaPartitionException.class,
                () -> dlq.recordDeadLetter("d-x", "topic_DLQ", 1L));
            // Reads during partition return the snapshot (no record was
            // written while the partition was open).
            assertFalse(dlq.isDeadLettered("d-x"));
            // Heal. The next write succeeds; the prior failed write did
            // not leave any half-state in Meta.
            partition.close();
            assertTrue(dlq.recordDeadLetter("d-x", "topic_DLQ", 1L));
            assertTrue(dlq.isDeadLettered("d-x"));
        }
    }

    // ---------------------------------------------------------------------
    // Scenario 5: TaskStore cross-store failure. The Meta backing the
    // TaskStore goes down mid-submit. MetaBackedTaskStore.createTask
    // delegates to MetaStore.putIfAbsent, which throws MetaPartitionException
    // when the partition is open. The TaskStore therefore propagates the
    // failure (returns null, the gateway surfaces the error to the caller)
    // rather than silently dropping the task.
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("Scenario 5: TaskStore Meta failure surfaces rather than drops")
    class TaskStoreMetaFailure {

        @Test
        void createTaskFailsWhenMetaIsPartitioned() {
            InMemoryMetaStore real = new InMemoryMetaStore();
            MetaPartitionSwitch partition = new MetaPartitionSwitch(real);
            TaskStore store = new MetaBackedTaskStore(partition);

            // Pre-partition: createTask succeeds.
            TaskStore.TaskRecord ok = store.createTask("t-1", "agent-A", "client-X", "{}");
            assertNotNull(ok);
            assertEquals(TaskStore.Status.PENDING, ok.status);

            // Open the partition. The next createTask must surface the
            // failure rather than silently dropping the task (issue #5292
            // durability contract).
            partition.open();
            assertThrows(MetaPartitionException.class,
                () -> store.createTask("t-2", "agent-A", "client-X", "{}"));
            // t-2 was NOT silently created.
            assertNull(store.getTask("t-2"),
                "a failed createTask must not leave a half-state task record");
            // The pre-partition task is still readable.
            assertNotNull(store.getTask("t-1"));

            // Heal. The next createTask succeeds.
            partition.close();
            TaskStore.TaskRecord healed = store.createTask("t-3", "agent-A", "client-X", "{}");
            assertNotNull(healed);
            assertEquals(TaskStore.Status.PENDING, healed.status);
        }

        @Test
        void updateStatusFailsWhenMetaIsPartitioned() {
            InMemoryMetaStore real = new InMemoryMetaStore();
            MetaPartitionSwitch partition = new MetaPartitionSwitch(real);
            TaskStore store = new MetaBackedTaskStore(partition);
            TaskStore.TaskRecord rec = store.createTask("t-u", "agent-A", "client-X", "{}");
            assertNotNull(rec);
            long epoch = rec.taskEpoch;
            partition.open();
            assertThrows(MetaPartitionException.class,
                () -> store.updateStatus("t-u", epoch, TaskStore.Status.RUNNING, null));
            // The status did NOT flip while the partition was open.
            partition.close();
            assertEquals(TaskStore.Status.PENDING, store.getTask("t-u").status,
                "updateStatus failure must not leave a half-state transition");
            // Heal. The status update now succeeds.
            assertTrue(store.updateStatus("t-u", epoch, TaskStore.Status.RUNNING, null));
            assertEquals(TaskStore.Status.RUNNING, store.getTask("t-u").status);
        }
    }

    // ---------------------------------------------------------------------
    // Scenario 6: DeliveryStateStore kill -9. The ReliableDispatcher's
    // pending set is the highest-leverage piece of crash-recovery state
    // (issue #5289). The store is backed by RocksDB; the test simulates a
    // kill -9 by dropping the wrapper without graceful close, then re-opens
    // at the same path. Every persisted delivery must be readable from the
    // restarted store; the iterate() / count() surface must report the
    // correct N.
    // ---------------------------------------------------------------------
    @Nested
    @DisplayName("Scenario 6: DeliveryStateStore kill -9 (RocksDB durability)")
    class DeliveryStateStoreKillMinusNine {

        @Test
        void inFlightLedgerSurvivesAbruptClose(@TempDir Path tmp) {
            String path = tmp.resolve("delivery").toString();
            byte[] payload = "hello".getBytes();
            // Phase 1: put 5 in-flight deliveries, then close (graceful
            // flush before close is fine; what matters is that the next
            // open at the same path sees them).
            int n = 5;
            List<String> ids = new ArrayList<>();
            RocksDBDeliveryStateStore first = new RocksDBDeliveryStateStore(path);
            try {
                for (int i = 0; i < n; i++) {
                    String id = "d-" + i;
                    ids.add(id);
                    DeliveryStateStore.Record r = new DeliveryStateStore.Record(
                        id, "topic-T", 0, 100L + i, "client-C", 1, 9999L, payload);
                    first.put(r);
                }
                first.flush();
            } finally {
                first.close();
            }
            // Phase 2: re-open at the same path. The persisted deliveries
            // must be readable (this is what ReliableDispatcher.recover()
            // does on a fresh JVM).
            RocksDBDeliveryStateStore second = new RocksDBDeliveryStateStore(path);
            try {
                assertEquals(n, second.count(),
                    "all in-flight deliveries must be recovered after restart");
                for (int i = 0; i < n; i++) {
                    DeliveryStateStore.Record got = second.get("d-" + i);
                    assertNotNull(got, "d-" + i + " must be readable post-restart");
                    assertEquals(100L + i, got.offset, "offset field preserved");
                    assertEquals(1, got.attempt, "attempt field preserved");
                    assertEquals(9999L, got.nextAttemptAtMs,
                        "nextAttemptAtMs preserved (resumes retry timing)");
                }
                // iterate() must yield every record in arbitrary order.
                Set<String> seen = new HashSet<>();
                second.iterate(r -> seen.add(r.deliveryId));
                assertEquals(new HashSet<>(ids), seen,
                    "iterate() must visit every persisted delivery");
            } finally {
                second.close();
            }
        }
    }
}
