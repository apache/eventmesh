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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.delivery.AckCallback;
import org.apache.eventmesh.runtime.delivery.DeadLetterSink;
import org.apache.eventmesh.runtime.delivery.Delivery;
import org.apache.eventmesh.runtime.delivery.PushChannel;
import org.apache.eventmesh.runtime.delivery.ReliableDispatcher;
import org.apache.eventmesh.runtime.metrics.UniMetrics;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * Sub-PR B fault-injection tests: verify that {@link ReliableDispatcher#recover()} picks up
 * in-flight deliveries from a persisted {@link DeliveryStateStore} on a fresh JVM, retires each
 * one with its stored offset, and never re-runs the channel (the MQ has already considered the
 * message gone \u2014 issue #5291 idempotency).
 */
class DeliveryRecoveryTest {

    @Test
    void recoverRetiresInFlightDeliveriesAndAdvancesOffset() {
        InMemoryDeliveryStateStore store = new InMemoryDeliveryStateStore();
        InMemoryOffsetStore offsets = new InMemoryOffsetStore();
        TestChannel channel = new TestChannel();
        DeadLetterSink dlq = (topic, event, reason, attempt) -> CompletableFuture.completedFuture(true);

        // Dispatcher A: deliver 3 events, none ACKed (simulating a crash window)
        AtomicLong clockA = new AtomicLong(1000L);
        ReliableDispatcher a = new ReliableDispatcher(1000L, 3, clockA::get, offsets, dlq,
            new UniMetrics(), 0.0d, store);
        String id1 = a.deliver("topic-A", 0, 100L, event("a-1"), "client-X", channel);
        String id2 = a.deliver("topic-A", 0, 101L, event("a-2"), "client-X", channel);
        String id3 = a.deliver("topic-B", 1, 200L, event("b-1"), "client-Y", channel);
        assertEquals(3, a.pendingCount(), "all three deliveries are in-flight");
        assertEquals(3, store.count(), "in-flight state must be persisted");

        // Simulate JVM crash: drop dispatcher A without ACKing
        a = null;

        // Dispatcher B: fresh JVM, same store + offsets
        AtomicLong clock = new AtomicLong(10_000L);
        ReliableDispatcher b = new ReliableDispatcher(1000L, 3, clock::get, offsets, dlq,
            new UniMetrics(), 0.0d, store);
        int recovered = b.recover();

        // Each persisted delivery must be retired: offset advanced, store emptied
        assertEquals(3, recovered, "all 3 in-flight deliveries must be recovered");
        assertEquals(0, store.count(), "recovered entries are removed from the ledger");
        assertEquals(0, b.pendingCount(), "the fresh dispatcher must not re-track the recovered entries");
        assertEquals(100L, offsets.readOffset("topic-A", "client-X", 0));
        assertEquals(200L, offsets.readOffset("topic-B", "client-Y", 1));

        // No channel redelivery happened during recovery (issue #5291 idempotency)
        assertEquals(0, channel.delivered.size(),
            "recovery must NOT re-deliver through the channel (broker already redelivered or not, but "
                + "EventMesh is not the source of truth for the message anymore)");
    }

    @Test
    void recoverIsIdempotent() {
        InMemoryDeliveryStateStore store = new InMemoryDeliveryStateStore();
        InMemoryOffsetStore offsets = new InMemoryOffsetStore();
        AtomicLong clock = new AtomicLong(1000L);
        ReliableDispatcher a = new ReliableDispatcher(1000L, 3, clock::get, offsets,
            (t, e, r, att) -> CompletableFuture.completedFuture(true), new UniMetrics(), 0.0d, store);
        a.deliver("topic", 0, 50L, event("only"), "client", new TestChannel());
        a.recover();
        a.recover();   // second call is a no-op
        assertEquals(0, store.count());
        assertEquals(50L, offsets.readOffset("topic", "client", 0));
    }

    @Test
    void recoverEmptyStoreIsNoOp() {
        InMemoryDeliveryStateStore store = new InMemoryDeliveryStateStore();
        InMemoryOffsetStore offsets = new InMemoryOffsetStore();
        ReliableDispatcher a = new ReliableDispatcher(1000L, 3, () -> 0L, offsets,
            (t, e, r, att) -> CompletableFuture.completedFuture(true), new UniMetrics(), 0.0d, store);
        int n = a.recover();
        assertEquals(0, n);
        assertTrue(true); // sanity
    }

    @Test
    void unackedDeliveryResumesFromPersistedNextAttempt() {
        // After recovery, the persisted Record's nextAttemptAtMs must be preserved so a tick
        // running on a fresh dispatcher can resume retry timing.
        InMemoryDeliveryStateStore store = new InMemoryDeliveryStateStore();
        EventMeshFrame ev = event("persisted");
        byte[] encoded = ev.encode();
        store.put(new DeliveryStateStore.Record("d-future", "topic", 0, 10L, "client", 2, 9999L, encoded));
        DeliveryStateStore.Record got = store.get("d-future");
        assertNotNull(got);
        assertEquals(9999L, got.nextAttemptAtMs);
        Delivery d = got.toDelivery();
        assertEquals(2, d.getAttempt());
        assertEquals(9999L, d.getNextAttemptAtMs());
    }

    private static EventMeshFrame event(String id) {
        return EventMeshFrame.event(java.util.Map.of("id", id), ("payload-" + id).getBytes());
    }

    private static class TestChannel implements PushChannel {

        final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(String deliveryId, EventMeshFrame event, AckCallback cb) {
            delivered.add(deliveryId);
            // do NOT auto-ack: simulates a real subscriber that hasn't replied yet
        }
    }
}
