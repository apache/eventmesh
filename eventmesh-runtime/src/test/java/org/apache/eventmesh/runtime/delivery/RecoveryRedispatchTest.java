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

package org.apache.eventmesh.runtime.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.metrics.UniMetrics;
import org.apache.eventmesh.runtime.offset.OffsetStore;
import org.apache.eventmesh.runtime.state.DeliveryStateStore;
import org.apache.eventmesh.runtime.state.InMemoryDeliveryStateStore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Issue #5379 acceptance: restart recovery re-dispatches unacknowledged deliveries instead of
 * advancing the subscriber offset (which converted an unACKed delivery into acknowledged
 * progress — a skip across the crash window).
 */
class RecoveryRedispatchTest {

    private static final class RecordingOffsetStore implements OffsetStore {

        final Map<String, Long> writes = new HashMap<>();

        @Override
        public boolean writeOffset(String topic, String clientId, int partition, long offset) {
            writes.put(topic + "#" + clientId + "#" + partition, offset);
            return true;
        }

        @Override
        public long readOffset(String topic, String clientId, int partition) {
            Long v = writes.get(topic + "#" + clientId + "#" + partition);
            return v == null ? -1L : v;
        }

        @Override
        public Map<String, Long> readAllOffsets(String topic) {
            return new HashMap<>();
        }

        @Override
        public void flush() {
            // no-op
        }

        @Override
        public void close() {
            // no-op
        }
    }

    /** Channel that records re-dispatched deliveries (the crash-recovery observer). */
    private static final class RecordingChannelState implements PushChannel {

        final AtomicInteger deliveries = new AtomicInteger();

        @Override
        public void deliver(String deliveryId, EventMeshFrame event, AckCallback callback) {
            deliveries.incrementAndGet();
            // do NOT ack — the client must see it; the retry state machine owns the bound
        }
    }

    private static ReliableDispatcher crashedDispatcherWithOneInFlight(
            RecordingOffsetStore offsets, DeliveryStateStore sharedState) {
        // The "crashed" dispatcher: delivered one event, client never ACKed, state persisted.
        ReliableDispatcher crashed = new ReliableDispatcher(10_000L, 3, System::currentTimeMillis,
            offsets, null, new UniMetrics(), 0.0d, sharedState);
        EventMeshFrame frame = EventMeshFrame.event(
            new java.util.LinkedHashMap<>(Map.of("id", "e-1")), new byte[] {1});
        crashed.deliver("orders", 0, 5L, frame, "client-1",
            (deliveryId, event, cb) -> { }, null);
        return crashed;
    }

    @Test
    void recoveryRedispatchesWithoutAdvancingOffset() {
        RecordingOffsetStore offsets = new RecordingOffsetStore();
        DeliveryStateStore sharedState = new InMemoryDeliveryStateStore();
        final ReliableDispatcher crashed = crashedDispatcherWithOneInFlight(offsets, sharedState);

        // The "restarted" dispatcher shares the persisted state (RocksDB in production).
        RecordingChannelState channelObserver = new RecordingChannelState();
        ReliableDispatcher restarted = new ReliableDispatcher(10_000L, 3, System::currentTimeMillis,
            offsets, null, new UniMetrics(), 0.0d, sharedState);

        int recovered = restarted.recover();

        // The record must NOT have been retired as ACKed...
        assertEquals(1, recovered, "the in-flight record is re-dispatched");
        // ...and the subscriber offset must NOT have advanced (#5379: no skip).
        assertEquals(-1L, offsets.readOffset("orders", "client-1", 0),
            "recovery must not advance the subscriber offset (unACKed != ACKed)");
        // The delivery stays pending for the retry state machine until the client ACKs.
        assertEquals(1, restarted.pendingCount(),
            "the recovered delivery remains in flight");
        assertTrue(restarted.tick() >= 0, "tick still drives the retry bound");
        crashed.ack(crashed.pendingIdsForTest().iterator().next());
    }

    @Test
    void legacyRecordWithoutFrameRetiresWithoutOffsetAdvance() {
        RecordingOffsetStore offsets = new RecordingOffsetStore();
        DeliveryStateStore sharedState = new InMemoryDeliveryStateStore();
        // A record whose encoded event is empty (legacy/corrupt) cannot be re-delivered.
        sharedState.put(new DeliveryStateStore.Record(
            "d-legacy", "orders", 0, 9L, "client-1", 1, 0L, new byte[0]));

        ReliableDispatcher restarted = new ReliableDispatcher(10_000L, 3, System::currentTimeMillis,
            offsets, null, new UniMetrics(), 0.0d, sharedState);
        int recovered = restarted.recover();

        assertEquals(0, recovered, "an undecodable record is retired, not re-dispatched");
        assertEquals(-1L, offsets.readOffset("orders", "client-1", 0),
            "even the legacy retire path never advances the offset");
        assertEquals(0, restarted.pendingCount());
    }

    @Test
    void ackAfterRecoveryAdvancesOffsetExactlyOnce() {
        // End-to-end shape: crash -> recover (re-dispatch, no offset) -> client ACKs -> offset.
        RecordingOffsetStore offsets = new RecordingOffsetStore();
        DeliveryStateStore sharedState = new InMemoryDeliveryStateStore();
        final ReliableDispatcher crashed = crashedDispatcherWithOneInFlight(offsets, sharedState);
        String deliveryId = crashed.pendingIdsForTest().iterator().next();

        ReliableDispatcher restarted = new ReliableDispatcher(10_000L, 3, System::currentTimeMillis,
            offsets, null, new UniMetrics(), 0.0d, sharedState);
        restarted.recover();

        assertTrue(restarted.ack(deliveryId), "the client's ACK retires the recovered delivery");
        assertEquals(5L, offsets.readOffset("orders", "client-1", 0),
            "the offset advances exactly once — on the real ACK");
        assertFalse(restarted.ack(deliveryId), "double-ACK is a no-op");
    }
}
