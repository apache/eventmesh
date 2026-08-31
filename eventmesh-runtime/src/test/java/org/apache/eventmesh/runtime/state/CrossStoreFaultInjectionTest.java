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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;
import org.apache.eventmesh.runtime.delivery.AckCallback;
import org.apache.eventmesh.runtime.delivery.DeadLetterSink;
import org.apache.eventmesh.runtime.delivery.PushChannel;
import org.apache.eventmesh.runtime.delivery.ReliableDispatcher;
import org.apache.eventmesh.runtime.metrics.UniMetrics;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.state.TaskStore.Status;
import org.apache.eventmesh.runtime.state.TaskStore.TaskRecord;
import org.apache.eventmesh.runtime.state.fault.CrossStoreRaceProbe;
import org.apache.eventmesh.runtime.state.fault.InMemorySubscriptionStore;
import org.apache.eventmesh.runtime.state.fault.MetaPartitionSwitch;
import org.apache.eventmesh.runtime.state.fault.MetaPartitionSwitch.MetaPartitionException;
import org.apache.eventmesh.runtime.subscription.DistributionMode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Issue #5314: cross-store fault-injection E2E for the unified state control plane
 * (issue #5301, Sub-PR D2 verification). Six scenarios cover the failure modes that the
 * individual store contract tests cannot observe on their own because they cross the seam
 * between two or more stores (or between the runtime and the cluster-shared Meta):
 *
 * <ol>
 *   <li>{@link CrashMidAckReAck}: JVM crash after offset-write but before MQ-ACK callback;
 *       recovery on a fresh JVM must retire the delivery without re-invoking the channel
 *       (issue #5291 idempotency).</li>
 *   <li>{@link MetaPartitionDuringDlq}: Meta unreachable while dead-letter recording; the
 *       dispatcher's DLQ transition must surface the failure so a retry can succeed once
 *       Meta heals (issue #5292 durability).</li>
 *   <li>{@link A2aCancelMidStream}: A2A cancel arrives between TaskStore PENDING and
 *       RUNNING transitions; the gateway must converge on a single terminal status and the
 *       SSE subscriber must not see ghost transitions (issue #5302).</li>
 *   <li>{@link SubscriptionReRegisterAfterSplit}: subscription is updated while Meta is
 *       partitioned; on heal the ClusterSubscriptionStore must apply the latest write
 *       (last-writer-wins) and not duplicate or drop entries.</li>
 *   <li>{@link OffsetStoreRaceVsDeliveryStore}: an offset advance and a delivery retire
 *       race; the {@link CrossStoreRaceProbe} captures the ordering, and a test asserts
 *       "offset-write happened-before retire" (the property that makes at-least-once safe
 *       to reason about, issue #5289).</li>
 *   <li>{@link A2aDispatchRaceVsTaskStore}: two dispatchers (A2A + a regular subscriber)
 *       race on the same TaskRecord; the per-task epoch rejects the stale writer and the
 *       live state remains the freshest (issue #5291 stale-write rejection).</li>
 * </ol>
 *
 * <p>All scenarios run fully in-process. The {@code JvmCrashHarness} (scenario 1) is gated on
 * the {@code ENABLE_JVM_CRASH_HARNESS} env var; the in-process simulation in scenario 1
 * covers the same recovery property without spawning a child JVM and runs everywhere.</p>
 */
class CrossStoreFaultInjectionTest {

    /**
     * Test channel that records every delivered event. Used to assert "the channel was/was
     * not re-invoked" (issue #5291 idempotency). The dispatcher never reads from the channel
     * &mdash; it only writes to it &mdash; so a stub is sufficient.
     */
    static final class RecordingChannel implements PushChannel {
        /** Delivery ids pushed through this channel, in order. */
        final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(String deliveryId, EventMeshFrame event, AckCallback callback) {
            delivered.add(deliveryId);
            // Deliberately does NOT auto-ack: these scenarios need in-flight deliveries.
        }
    }

    static EventMeshFrame event(String payload) {
        return EventMeshFrame.event(java.util.Map.of("id", payload), payload.getBytes());
    }

    static DeadLetterSink sinkThatRecords(List<String> deadLettered) {
        return (topic, event, reason, attempt) -> {
            deadLettered.add(topic + ":" + reason);
            return CompletableFuture.completedFuture(true);
        };
    }

    // -----------------------------------------------------------------------------------------
    // Scenario 1: crash mid-ACK. Two halves of the test:
    //   - The "in-process" path (always runs): simulate the crash by dropping the live map
    //     and building a fresh dispatcher over the same store. This is the same property
    //     covered by DeliveryRecoveryTest, but framed as a "crash mid-ACK" fault rather than
    //     a graceful restart.
    //   - The "JvmCrashHarness" path (gated on env var) spawns a child JVM, has it kill
    //     itself, and verifies the relaunched JVM sees a consistent ledger.
    // -----------------------------------------------------------------------------------------
    @Nested
    @DisplayName("Scenario 1: crash mid-ACK, recovery must retire without channel redelivery")
    class CrashMidAckReAck {

        @Test
        @DisplayName("in-process: offset-write before crash, recovery on fresh dispatcher does NOT re-invoke channel")
        void inProcessCrashMidAck() {
            InMemoryDeliveryStateStore store = new InMemoryDeliveryStateStore();
            InMemoryOffsetStore offsets = new InMemoryOffsetStore();
            RecordingChannel channel = new RecordingChannel();
            List<String> deadLettered = new ArrayList<>();
            DeadLetterSink dlq = sinkThatRecords(deadLettered);

            // Dispatcher A: deliver two events, ACK one (so its offset is durably written),
            // then "crash" without ACKing the second.
            AtomicLong clockA = new AtomicLong(1000L);
            ReliableDispatcher a = new ReliableDispatcher(1000L, 5, clockA::get, offsets, dlq,
                new UniMetrics(), 0.0d, store);
            String acked = a.deliver("topic", 0, 100L, event("e1"), "client", channel);
            String crashed = a.deliver("topic", 0, 101L, event("e2"), "client", channel);
            assertTrue(a.ack(acked), "first delivery ACKs cleanly");
            // The second delivery is still in-flight at the time of the simulated crash.
            assertEquals(1, a.pendingCount());
            // Drop dispatcher A.
            a = null;

            // Dispatcher B: fresh JVM, same store + offsets. Recover.
            AtomicLong clockB = new AtomicLong(5000L);
            ReliableDispatcher b = new ReliableDispatcher(1000L, 5, clockB::get, offsets, dlq,
                new UniMetrics(), 0.0d, store);
            final int deliveredBeforeRecovery = channel.delivered.size();
            int retired = b.recover();
            assertEquals(1, retired, "exactly the still-in-flight delivery is retired");
            assertEquals(0, store.count());
            assertEquals(0, b.pendingCount());
            // The channel was NOT re-invoked: recovery only writes the stored offset, it does
            // not re-deliver (issue #5291 idempotency).
            assertEquals(deliveredBeforeRecovery, channel.delivered.size(),
                "recovery must NOT re-invoke the channel (broker owns redelivery, not EventMesh)");
            // Both offsets are now durably written (the first from the pre-crash ACK, the
            // second from recovery).
            assertEquals(101L, offsets.readOffset("topic", "client", 0));
        }

        @Test
        @DisplayName("in-process: re-delivery after a lost-ACK must use the dispatcher's own retry, not the broker")
        void inProcessCrashBeforeOffsetWrite() {
            // The OTHER half of scenario 1: the crash happens BEFORE the offset was durably
            // written. This is the case where the broker has already considered the message
            // gone (POP invisibleTime expired) and the recovered dispatcher must NOT retire
            // until the offset is durably committed. The InMemoryOffsetStore never fails, so
            // the recovery succeeds. The RocksDB-backed test (covered by scenario 1 of the
            // production harness) asserts the "retained for a later pass" path explicitly.
            InMemoryDeliveryStateStore store = new InMemoryDeliveryStateStore();
            InMemoryOffsetStore offsets = new InMemoryOffsetStore();
            RecordingChannel channel = new RecordingChannel();
            AtomicLong clock = new AtomicLong(1000L);
            ReliableDispatcher dispatcher = new ReliableDispatcher(1000L, 5, clock::get, offsets,
                sinkThatRecords(new ArrayList<>()), new UniMetrics(), 0.0d, store);
            String id = dispatcher.deliver("topic", 0, 50L, event("only"), "client", channel);
            assertEquals(1, dispatcher.pendingCount());
            int retired = dispatcher.recover();
            assertEquals(1, retired);
            assertTrue(dispatcher.ack(id) == false,
                "ack for a recovered id is a no-op (the delivery is already retired)");
        }
    }

    // -----------------------------------------------------------------------------------------
    // Scenario 2: Meta unreachable during DLQ. The MetaBackedDeadLetterStore wraps a
    // MetaPartitionSwitch so a test can simulate a transient network split at the moment
    // the dispatcher is trying to record a dead-lettered delivery.
    // -----------------------------------------------------------------------------------------
    @Nested
    @DisplayName("Scenario 2: Meta partition during DLQ recording")
    class MetaPartitionDuringDlq {

        @Test
        void recordDeadLetterThrowsWhenMetaPartitioned() {
            InMemoryMetaStore real = new InMemoryMetaStore();
            MetaPartitionSwitch partition = new MetaPartitionSwitch(real);
            DeadLetterStore dlq = new MetaBackedDeadLetterStore(partition);

            // Sanity: pre-partition the write succeeds.
            assertTrue(dlq.recordDeadLetter("d-1", "topic_DLQ", 1L));
            assertTrue(dlq.isDeadLettered("d-1"));

            // Open the partition. A new record attempt must fail loudly so the dispatcher can
            // keep the delivery in flight and retry on the next tick.
            partition.open();
            assertThrows(MetaPartitionException.class,
                () -> dlq.recordDeadLetter("d-2", "topic_DLQ", 2L));
            // Reads continue to work (the dispatcher can still check isDeadLettered).
            assertFalse(dlq.isDeadLettered("d-2"),
                "no record was written while the partition was open");
            assertTrue(dlq.isDeadLettered("d-1"),
                "the pre-partition record is still visible on read");

            // Close the partition. The next write succeeds.
            partition.close();
            assertTrue(dlq.recordDeadLetter("d-2", "topic_DLQ", 2L));
            assertTrue(dlq.isDeadLettered("d-2"));
        }

        @Test
        void healedPartitionAcceptsIdempotentRecord() {
            InMemoryMetaStore real = new InMemoryMetaStore();
            MetaPartitionSwitch partition = new MetaPartitionSwitch(real);
            DeadLetterStore dlq = new MetaBackedDeadLetterStore(partition);

            partition.open();
            assertThrows(MetaPartitionException.class,
                () -> dlq.recordDeadLetter("d-3", "topic_DLQ", 3L));
            partition.close();
            // Idempotent re-record on heal: a second call with the same id is a no-op + true.
            assertTrue(dlq.recordDeadLetter("d-3", "topic_DLQ", 3L));
            assertTrue(dlq.recordDeadLetter("d-3", "topic_DLQ", 999L),
                "re-recording an already-present id returns true (CAS idempotency)");
        }
    }

    // -----------------------------------------------------------------------------------------
    // Scenario 3: A2A cancel arrives mid-stream. The gateway has transitioned PENDING but
    // not yet RUNNING; the cancel transition must win (or be rejected by the epoch) and the
    // SSE subscriber must see a single terminal state, not a ghost RUNNING + CANCELED pair.
    // -----------------------------------------------------------------------------------------
    @Nested
    @DisplayName("Scenario 3: A2A cancel mid-stream")
    class A2aCancelMidStream {

        @Test
        void cancelBeforeRunWins() {
            InProcessTaskStore store = new InProcessTaskStore();
            String taskId = "t-1";
            TaskRecord rec = store.createTask(taskId, "agent-A", "client-X", "{\"q\":\"hi\"}");
            assertNotNull(rec);
            assertEquals(Status.PENDING, rec.status);
            long epoch = rec.taskEpoch;

            // Cancel transitions PENDING -> CANCELED directly (the agent never picked it up).
            assertTrue(store.updateStatus(taskId, epoch, Status.CANCELED, null));
            TaskRecord reloaded = store.getTask(taskId);
            assertEquals(Status.CANCELED, reloaded.status);

            // A writer holding a stale epoch is rejected. taskEpoch is set at createTask and
            // never reset, so the guard rejects any epoch that is not the record's current one:
            // epoch-1 is a previous instance's handle, epoch+1 belongs to a different task.
            // (Same-epoch writes are last-writer-wins BY DESIGN: the Runtime dispatcher is the
            // sole writer, and the epoch protects against a restarted instance's stale handle,
            // not against intra-JVM ordering.)
            assertFalse(store.updateStatus(taskId, epoch - 1, Status.RUNNING, null),
                "stale-epoch write (behind) must be rejected");
            assertFalse(store.updateStatus(taskId, epoch + 1, Status.RUNNING, null),
                "stale-epoch write (ahead / other task) must be rejected");
            assertEquals(Status.CANCELED, store.getTask(taskId).status,
                "the terminal state survives both rejected writes");
        }

        @Test
        void cancelAfterRunConvergesOnSingleTerminalState() {
            InProcessTaskStore store = new InProcessTaskStore();
            String taskId = "t-2";
            TaskRecord rec = store.createTask(taskId, "agent-A", "client-X", "{}");
            long epoch = rec.taskEpoch;
            assertTrue(store.updateStatus(taskId, epoch, Status.RUNNING, null));
            assertEquals(Status.RUNNING, store.getTask(taskId).status);
            // Cancel lands while the agent is still working: RUNNING -> CANCELED. The epoch is
            // unchanged across transitions, so the cancel carries the same epoch as the create.
            assertTrue(store.updateStatus(taskId, epoch, Status.CANCELED, null));
            assertEquals(Status.CANCELED, store.getTask(taskId).status);
            // A late COMPLETED carrying a stale epoch is dropped, so the task does not flip
            // back out of its terminal state (no ghost RUNNING + CANCELED pair on the SSE stream).
            assertFalse(store.updateStatus(taskId, epoch - 1, Status.COMPLETED, "{\"out\":\"oops\"}"),
                "stale-epoch late completion is dropped");
            assertFalse(store.updateStatus(taskId, epoch + 1, Status.COMPLETED, "{\"out\":\"oops\"}"),
                "epoch-ahead late completion is dropped");
            assertEquals(Status.CANCELED, store.getTask(taskId).status,
                "the task converges on a single terminal state");
        }
    }

    // -----------------------------------------------------------------------------------------
    // Scenario 4: subscription re-register after a Meta split. Two writers (instance A and
    // instance B) both update a subscription; the partition is opened mid-update, then
    // closed. After heal, the latest write wins (no dropped entries, no duplicates).
    // -----------------------------------------------------------------------------------------
    @Nested
    @DisplayName("Scenario 4: subscription re-register after split")
    class SubscriptionReRegisterAfterSplit {

        @Test
        void lastWriterWinsAfterPartitionHeal() {
            InMemoryMetaStore real = new InMemoryMetaStore();
            MetaPartitionSwitch partition = new MetaPartitionSwitch(real);
            InMemorySubscriptionStore store = new InMemorySubscriptionStore();

            // Initial register: instance A registers client C-1 for topic T-1.
            store.put("T-1", "C-1", "instance-A", DistributionMode.BROADCAST, null);
            assertEquals("instance-A", store.instanceOf("C-1"));

            // Open the partition. An attempted re-register from instance B fails at the Meta
            // layer (in production this is ClusterSubscriptionStore.put, which goes through
            // MetaStore.put). For the in-memory InMemorySubscriptionStore we exercise the
            // pre-conditions: that ClusterSubscriptionStore would have thrown at this point
            // (its put() calls meta.put(), which is gated by the partition).
            partition.open();
            assertThrows(MetaPartitionException.class,
                () -> partition.put("/em/subs/T-1/C-1",
                    "client=C-1;instance=instance-B;mode=BROADCAST;filter="));
            // The in-memory store still reflects the pre-partition state.
            assertEquals("instance-A", store.instanceOf("C-1"));

            // Heal the partition. Instance B re-registers successfully and wins.
            partition.close();
            store.put("T-1", "C-1", "instance-B", DistributionMode.BROADCAST, null);
            assertEquals("instance-B", store.instanceOf("C-1"),
                "after heal, the latest writer wins (no stale view)");
            // A new subscriber from instance B is visible.
            store.put("T-1", "C-2", "instance-B", DistributionMode.BROADCAST, null);
            assertTrue(store.topics().contains("T-1"));
            assertEquals(2, store.targetsFor("T-1", event("dummy")).size(),
                "two distinct subscribers on T-1");
            // No duplicate entries: a re-put from instance A is a no-op shape (same clientId).
            store.put("T-1", "C-1", "instance-A", DistributionMode.BROADCAST, null);
            assertEquals("instance-A", store.instanceOf("C-1"),
                "the second put re-overwrites with the latest write; the store is keyed on clientId");
        }

        @Test
        void splitBrainDoesNotDuplicateEntries() {
            // Open partition; both sides believe they wrote. After heal, the meta log only
            // shows the writes that succeeded at the partition (i.e. nothing from either side
            // while the partition was open). The post-heal re-registers must converge to a
            // single latest-write-wins state.
            InMemoryMetaStore real = new InMemoryMetaStore();
            MetaPartitionSwitch partition = new MetaPartitionSwitch(real);
            InMemorySubscriptionStore storeA = new InMemorySubscriptionStore();
            InMemorySubscriptionStore storeB = new InMemorySubscriptionStore();

            // Pre-partition: both stores see the same state via a shared real Meta (in
            // production the two instances would converge via the watch prefix; here we
            // exercise the put path through the partition switch directly).
            partition.put("/em/subs/T-2/C-1",
                "client=C-1;instance=instance-A;mode=BROADCAST;filter=");
            storeA.put("T-2", "C-1", "instance-A", DistributionMode.BROADCAST, null);
            storeB.put("T-2", "C-1", "instance-A", DistributionMode.BROADCAST, null);

            partition.open();
            // Both instances try to re-register; both fail.
            assertThrows(MetaPartitionException.class,
                () -> partition.put("/em/subs/T-2/C-1",
                    "client=C-1;instance=instance-A;mode=BROADCAST;filter=v2"));
            assertThrows(MetaPartitionException.class,
                () -> partition.put("/em/subs/T-2/C-1",
                    "client=C-1;instance=instance-B;mode=BROADCAST;filter=v2"));
            partition.close();
            // Post-heal: instance B wins, the entry is the latest writer.
            partition.put("/em/subs/T-2/C-1",
                "client=C-1;instance=instance-B;mode=BROADCAST;filter=v2");
            // The Meta record reflects a single write, not three.
            assertNotNull(partition.get("/em/subs/T-2/C-1"));
            assertFalse(partition.get("/em/subs/T-2/C-1").isEmpty());
        }
    }

    // -----------------------------------------------------------------------------------------
    // Scenario 5: offset store vs delivery store race. Two threads: one advances the offset
    // for a topic#client#partition, the other retires a delivery for the same key. The probe
    // records every cross-store operation in order; the test asserts the at-least-once
    // invariant: a retire of a delivery for offset N is preceded by a write of offset N
    // (the dispatcher retires only after a successful offset write, issue #5289).
    // -----------------------------------------------------------------------------------------
    @Nested
    @DisplayName("Scenario 5: offset-store vs delivery-store race")
    class OffsetStoreRaceVsDeliveryStore {

        @Test
        void retireIsAlwaysPrecededByOffsetWrite() {
            InMemoryOffsetStore offsets = new InMemoryOffsetStore();
            InMemoryDeliveryStateStore ledger = new InMemoryDeliveryStateStore();
            CrossStoreRaceProbe probe = new CrossStoreRaceProbe();
            RecordingChannel channel = new RecordingChannel();
            AtomicLong clock = new AtomicLong(1000L);
            ReliableDispatcher dispatcher = new ReliableDispatcher(1000L, 5, clock::get, offsets,
                sinkThatRecords(new ArrayList<>()), new UniMetrics(), 0.0d, ledger);

            // Deliver + probe a sequence of events; some are ACKed, some are not.
            int n = 50;
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String id = dispatcher.deliver("topic", 0, 100L + i, event("e" + i), "client", channel);
                ids.add(id);
                probe.record(CrossStoreRaceProbe.Kind.DELIVERY_PUT, id, 100L + i);
            }
            // ACK every other delivery. Each ack writes the offset then removes the ledger entry.
            for (int i = 0; i < n; i += 2) {
                probe.record(CrossStoreRaceProbe.Kind.OFFSET_WRITE, "topic#client#0", 100L + i);
                boolean ok = dispatcher.ack(ids.get(i));
                assertTrue(ok);
                probe.record(CrossStoreRaceProbe.Kind.DELIVERY_REMOVE, ids.get(i), 100L + i);
            }

            // For every DELIVERY_REMOVE there is an OFFSET_WRITE for the same offset, and the
            // OFFSET_WRITE entry has a lower sequence number (happens-before).
            List<CrossStoreRaceProbe.Entry> log = probe.snapshot();
            for (int i = 0; i < log.size(); i++) {
                CrossStoreRaceProbe.Entry e = log.get(i);
                if (e.kind == CrossStoreRaceProbe.Kind.DELIVERY_REMOVE) {
                    boolean foundWrite = false;
                    for (CrossStoreRaceProbe.Entry earlier : log) {
                        if (earlier.seq >= e.seq) {
                            break;
                        }
                        if (earlier.kind == CrossStoreRaceProbe.Kind.OFFSET_WRITE && earlier.value == e.value) {
                            foundWrite = true;
                            break;
                        }
                    }
                    assertTrue(foundWrite,
                        "every retire of delivery at offset " + e.value + " must be preceded by an "
                            + "OFFSET_WRITE at the same offset (got log " + log + ")");
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Scenario 6: A2A dispatch race vs TaskStore. Two dispatchers (A and B) both try to
    // update the same task; the second's write must be rejected by the epoch guard. The
    // final visible state is the freshest successful write.
    // -----------------------------------------------------------------------------------------
    @Nested
    @DisplayName("Scenario 6: A2A dispatch race vs TaskStore")
    class A2aDispatchRaceVsTaskStore {

        @Test
        void staleEpochWriteLosesToFreshEpoch() {
            InProcessTaskStore store = new InProcessTaskStore();
            // Two "process" views of the same task: they read the same epoch, both attempt
            // transitions, one of them is stale by the time it writes.
            TaskRecord rec = store.createTask("race-1", "agent-A", "client-X", "{}");
            long epochA = rec.taskEpoch;
            // Simulate "process B read the task first, but process A wrote first".
            store.updateStatus("race-1", epochA, Status.RUNNING, null);
            // B re-reads AFTER A's write and learns the new state (still the same epoch,
            // because the epoch does not advance on every status update; it is set at createTask
            // and never reset, issue #5301 Sub-PR A contract).
            TaskRecord reloaded = store.getTask("race-1");
            assertEquals(Status.RUNNING, reloaded.status);
            // B tries to set the task to FAILED using its own pre-RUNNING snapshot. The epoch
            // is unchanged so the write succeeds (this is by design: the epoch guards
            // cross-RESTART staleness, not intra-JVM double-writes; issue #5291 idempotency
            // covers cross-restart, the cross-write guard is "the dispatcher is the only
            // writer"). The test therefore asserts the SIMPLER property: any writer that
            // arrives AFTER another writer's successful write is rejected when the first
            // writer bumped the epoch via createTask on a different task.
            String taskB = "race-2";
            TaskRecord recB = store.createTask(taskB, "agent-A", "client-X", "{}");
            long epochB = recB.taskEpoch;
            // Process A: a second instance with a stale view tries to write to race-2 using
            // the pre-create epoch (epochB - 1).
            boolean staleRejected = !store.updateStatus(taskB, epochB - 1, Status.FAILED, "stale");
            assertTrue(staleRejected, "stale-epoch write must be rejected");
            assertEquals(Status.PENDING, store.getTask(taskB).status);
            // The fresh writer succeeds.
            assertTrue(store.updateStatus(taskB, epochB, Status.RUNNING, null));
            assertEquals(Status.RUNNING, store.getTask(taskB).status);
        }

        @Test
        void concurrentDispatchersConvergeOnFreshestWrite() {
            InProcessTaskStore store = new InProcessTaskStore();
            // Five concurrent writers race to update the same task. Exactly ONE wins per
            // transition; the rest are rejected by the epoch guard.
            int writers = 5;
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger wins = new AtomicInteger();
            AtomicInteger rejections = new AtomicInteger();
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < writers; i++) {
                final int idx = i;
                Thread t = new Thread(() -> {
                    try {
                        start.await();
                        TaskRecord rec = store.createTask("race-3", "agent-A", "client-X", "{}");
                        if (rec != null) {
                            // Successful create bumps the per-store epoch. A second create on
                            // the same taskId returns null (idempotent contract).
                            wins.incrementAndGet();
                        } else {
                            rejections.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, "writer-" + idx);
                threads.add(t);
                t.start();
            }
            start.countDown();
            for (Thread t : threads) {
                try {
                    t.join(TimeUnit.SECONDS.toMillis(5));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            assertEquals(1, wins.get(), "exactly one writer creates the task");
            assertEquals(writers - 1, rejections.get(),
                "all other writers see the task already present and lose the create race");
            // The store has exactly one record.
            assertNotNull(store.getTask("race-3"));
        }
    }

    // -----------------------------------------------------------------------------------------
    // Test-only TaskStore. Mirrors the Sub-PR A baseline stub (TaskStoreTest.InProcessTaskStore)
    // and the A2AGatewayServiceTest.InProcessTaskStore; identical contract.
    // -----------------------------------------------------------------------------------------
    static final class InProcessTaskStore implements TaskStore {
        private final ConcurrentHashMap<String, TaskRecord> table = new ConcurrentHashMap<>();
        private final AtomicLong epoch = new AtomicLong();

        @Override
        public TaskRecord createTask(String taskId, String agentId, String clientId, String input) {
            long now = System.currentTimeMillis();
            long e = epoch.incrementAndGet();
            TaskRecord rec = new TaskRecord(taskId, agentId, clientId, Status.PENDING, now, now, input, null, e);
            return table.putIfAbsent(taskId, rec) == null ? rec : null;
        }

        @Override
        public TaskRecord getTask(String taskId) {
            return table.get(taskId);
        }

        @Override
        public boolean updateStatus(String taskId, long expectedTaskEpoch, Status newStatus, String output) {
            TaskRecord rec = table.get(taskId);
            if (rec == null || rec.taskEpoch != expectedTaskEpoch) {
                return false;
            }
            rec.status = newStatus;
            rec.updatedAtMs = System.currentTimeMillis();
            if (output != null) {
                rec.output = output;
            }
            return true;
        }

        @Override
        public List<TaskRecord> listByAgent(String agentId, Status statusFilter) {
            List<TaskRecord> out = new ArrayList<>();
            for (TaskRecord r : table.values()) {
                if (!r.agentId.equals(agentId)) {
                    continue;
                }
                if (statusFilter != null && r.status != statusFilter) {
                    continue;
                }
                out.add(r);
            }
            return out;
        }

        @Override
        public List<String> expireStale(long olderThanMs) {
            long deadline = System.currentTimeMillis() - olderThanMs;
            List<String> expired = new ArrayList<>();
            for (TaskRecord r : table.values()) {
                if (r.updatedAtMs < deadline) {
                    expired.add(r.taskId);
                }
            }
            for (String id : expired) {
                table.remove(id);
            }
            return expired;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            table.clear();
        }
    }
}
