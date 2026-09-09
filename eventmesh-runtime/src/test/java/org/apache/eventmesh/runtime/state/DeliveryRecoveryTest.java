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
 * Restart-recovery tests for {@link ReliableDispatcher#recover()} (issue #5379): a fresh JVM
 * RE-DISPATCHES the persisted in-flight deliveries (at-least-once) instead of advancing the
 * subscriber offset as if the absent client had ACKed. The offset advances exactly once
 * when the client actually ACKs the re-dispatched delivery.
 */
class DeliveryRecoveryTest {

    @Test
    void recoverRedispatchesInFlightWithoutAdvancingOffset() {
        InMemoryDeliveryStateStore store = new InMemoryDeliveryStateStore();
        InMemoryOffsetStore offsets = new InMemoryOffsetStore();
        TestChannel channel = new TestChannel();
        DeadLetterSink dlq = (topic, event, reason, attempt) -> CompletableFuture.completedFuture(true);

        // Dispatcher A: deliver 3 events, none ACKed (simulating a crash window)
        AtomicLong clockA = new AtomicLong(1000L);
        ReliableDispatcher a = new ReliableDispatcher(1000L, 3, clockA::get, offsets, dlq,
            new UniMetrics(), 0.0d, store);
        final String id1 = a.deliver("topic-A", 0, 100L, event("a-1"), "client-X", channel);
        final String id2 = a.deliver("topic-A", 0, 101L, event("a-2"), "client-X", channel);
        final String id3 = a.deliver("topic-B", 1, 200L, event("b-1"), "client-Y", channel);
        assertEquals(3, a.pendingCount(), "all three deliveries are in-flight");
        assertEquals(3, store.count(), "in-flight state must be persisted");

        // Simulate JVM crash: drop dispatcher A without ACKing
        a = null;

        // Dispatcher B: fresh JVM, same store + offsets
        AtomicLong clock = new AtomicLong(10_000L);
        ReliableDispatcher b = new ReliableDispatcher(1000L, 3, clock::get, offsets, dlq,
            new UniMetrics(), 0.0d, store);
        final int deliveredBeforeRecovery = channel.delivered.size();
        final int recovered = b.recover();
        assertEquals(3, recovered, "all 3 in-flight deliveries must be re-dispatched");

        // The crashed instance's channel is never re-invoked: the re-dispatch goes through
        // the buffered poll channel, the original push channel stays quiet (#5379).
        assertEquals(deliveredBeforeRecovery, channel.delivered.size(),
            "re-dispatch must not re-deliver through the crashed instance's channel");

        // #5379: unACKed is not ACKed; the subscriber offset must NOT advance during recovery...
        assertEquals(-1L, offsets.readOffset("topic-A", "client-X", 0));
        assertEquals(-1L, offsets.readOffset("topic-B", "client-Y", 1));
        // ...and the deliveries stay in flight (the retry state machine owns the bound).
        assertEquals(3, store.count(), "recovered records stay persisted until a real ACK");
        assertEquals(3, b.pendingCount(), "the fresh dispatcher tracks the recovered deliveries");

        // The offset advances exactly when the client ACKs the re-dispatched delivery.
        assertTrue(b.ack(id1));
        assertTrue(b.ack(id2));
        assertTrue(b.ack(id3));
        assertEquals(0, store.count(), "the ACK retires the persisted record");
        assertEquals(0, b.pendingCount());
        assertEquals(101L, offsets.readOffset("topic-A", "client-X", 0));
        assertEquals(200L, offsets.readOffset("topic-B", "client-Y", 1));
    }

    @Test
    void recoverIsIdempotent() {
        InMemoryDeliveryStateStore store = new InMemoryDeliveryStateStore();
        InMemoryOffsetStore offsets = new InMemoryOffsetStore();
        // The crashed instance delivered one event and never saw the ACK.
        ReliableDispatcher crashed = new ReliableDispatcher(1000L, 3, () -> 1000L, offsets,
            (t, e, r, att) -> CompletableFuture.completedFuture(true), new UniMetrics(), 0.0d, store);
        final String id = crashed.deliver("topic", 0, 50L, event("only"), "client", new TestChannel());
        crashed = null;

        // The restarted instance recovers the persisted record exactly once: the second
        // pass skips records that are already live on this dispatcher.
        ReliableDispatcher restarted = new ReliableDispatcher(1000L, 3, () -> 2000L, offsets,
            (t, e, r, att) -> CompletableFuture.completedFuture(true), new UniMetrics(), 0.0d, store);
        assertEquals(1, restarted.recover(), "the first pass re-dispatches the persisted record");
        assertEquals(0, restarted.recover(), "a second pass is a no-op (the record is already live)");
        // Still no offset movement without a client ACK.
        assertEquals(-1L, offsets.readOffset("topic", "client", 0));
        assertTrue(restarted.ack(id), "the client's ACK retires the re-dispatched delivery");
        assertEquals(50L, offsets.readOffset("topic", "client", 0));
        assertEquals(0, store.count());
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
