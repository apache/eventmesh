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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.offset.OffsetStore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Issue #5360 acceptance (plan #5354 Phase 1): a fenced (stale) owner must not produce durable
 * side effects after takeover. The dispatcher's ownership guard rejects the ack — no offset
 * write, no broker ACK callback — so the new owner's state stays authoritative and the broker's
 * POP invisibleTime redelivers the message (at-least-once preserved).
 */
class StaleOwnerFencingTest {

    /** Recording offset store: counts writes (the durable side effect under test). */
    private static final class RecordingOffsetStore implements OffsetStore {

        final Map<String, Long> writes = new HashMap<>();
        final AtomicInteger writeCount = new AtomicInteger();

        @Override
        public boolean writeOffset(String topic, String clientId, int partition, long offset) {
            writeCount.incrementAndGet();
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
            Map<String, Long> out = new HashMap<>();
            writes.forEach((k, v) -> {
                if (k.startsWith(topic + "#")) {
                    out.put(k, v);
                }
            });
            return out;
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

    private static EventMeshFrame frame(String id) {
        return EventMeshFrame.event(new java.util.LinkedHashMap<>(Map.of("id", id)),
            id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void fencedOwnerAckWritesNoOffsetAndSkipsBrokerAck() {
        RecordingOffsetStore offsets = new RecordingOffsetStore();
        ReliableDispatcher dispatcher = new ReliableDispatcher(10_000L, 3, System::currentTimeMillis,
            offsets, null, new org.apache.eventmesh.runtime.metrics.UniMetrics(),
            0.0d, new org.apache.eventmesh.runtime.state.InMemoryDeliveryStateStore());

        // Simulate takeover: this instance owns topic "orders" partition 1, but NOT partition 0
        // (a newer owner holds partition 0 in Meta).
        dispatcher.withOwnershipGuard((topic, partition) -> "orders".equals(topic) && partition == 1);

        // Two deliveries: one on the still-owned partition, one on the fenced partition.
        String owned = dispatcher.deliver("orders", 1, 10L, frame("owned"), "client",
            (deliveryId, event, cb) -> { }, null);
        final AtomicInteger fencedBrokerAcks = new AtomicInteger();
        final String fenced = dispatcher.deliver("orders", 0, 5L, frame("fenced"), "client",
            (deliveryId, event, cb) -> { }, fencedBrokerAcks::incrementAndGet);

        // ack on the still-owned partition: normal path, offset written.
        assertTrue(dispatcher.ack(owned), "still-owned partition ack must succeed");
        assertEquals(1, offsets.writeCount.get(), "owned-partition ack writes its offset");
        assertEquals(0, fencedBrokerAcks.get(),
            "broker ACK must fire only from the still-owned ack path (this delivery had none yet)");

        // ack on the fenced partition: rejected BEFORE any durable side effect.
        StaleOwnerException ex = assertThrows(StaleOwnerException.class, () -> dispatcher.ack(fenced),
            "a fenced owner's ack must throw");
        assertEquals("orders", ex.topic());
        assertEquals(0, ex.partition());
        assertEquals(1, offsets.writeCount.get(),
            "the fenced ack must NOT write any offset (no side effect)");
        assertEquals(0, fencedBrokerAcks.get(), "the fenced ack must NOT ACK the broker");
        assertFalse(dispatcher.ack(fenced), "the dropped delivery is no longer pending");
    }

    @Test
    void noGuardKeepsSingleInstanceBehaviour() {
        RecordingOffsetStore offsets = new RecordingOffsetStore();
        ReliableDispatcher dispatcher = new ReliableDispatcher(10_000L, 3, System::currentTimeMillis,
            offsets, null, new org.apache.eventmesh.runtime.metrics.UniMetrics(),
            0.0d, new org.apache.eventmesh.runtime.state.InMemoryDeliveryStateStore());

        String id = dispatcher.deliver("orders", 0, 5L, frame("x"), "client",
            (deliveryId, event, cb) -> { }, null);
        assertTrue(dispatcher.ack(id), "unguarded (single-instance) ack must succeed");
        assertEquals(1, offsets.writeCount.get());
        assertEquals(5L, offsets.readOffset("orders", "client", 0),
            "unguarded ack persists the offset");
    }
}
