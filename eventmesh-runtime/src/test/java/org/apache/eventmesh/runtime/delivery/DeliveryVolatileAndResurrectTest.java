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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.metrics.UniMetrics;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.offset.OffsetStore;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Tests for P0-3 (Delivery volatile fields) and P1-6 (dispatcher tick doesn't resurrect acked
 * delivery via putIfAbsent).
 */
class DeliveryVolatileAndResurrectTest {

    private static final long ACK_TIMEOUT = 5_000L;
    private static final int MAX_ATTEMPTS = 3;

    private static EventMeshFrame frame(String id) {
        return EventMeshFrame.fromCloudEvent(
            CloudEventBuilder.v1().withId(id).withSource(URI.create("test")).withType("t").build());
    }

    @Test
    void deliveryAckRetiresCleanly() {
        // P0-3: verify that deliver→ack works cleanly (volatile fields visible across threads).
        OffsetStore offsets = new InMemoryOffsetStore();
        RecordingChannel channel = new RecordingChannel();
        ReliableDispatcher dispatcher = new ReliableDispatcher(ACK_TIMEOUT, MAX_ATTEMPTS,
            new AtomicLong(0)::get, offsets, (t, e, r, a) -> java.util.concurrent.CompletableFuture.completedFuture(Boolean.TRUE), new UniMetrics());

        dispatcher.deliver("topic", 0, 1L, frame("e-1"), "c1", channel);

        assertTrue(dispatcher.pendingCount() >= 1);
        String deliveryId = channel.lastDeliveryId;
        assertNotNull(deliveryId);

        channel.lastCallback.ack();
        assertEquals(0, dispatcher.pendingCount(), "ack must retire the delivery");
        assertEquals(1L, offsets.readOffset("topic", "c1", 0));
    }

    @Test
    void tickDoesNotResurrectAckedDelivery() {
        // P1-6: if ack() removes a delivery while tick() is iterating, tick must not re-insert it.
        AtomicLong clock = new AtomicLong(0L);
        OffsetStore offsets = new InMemoryOffsetStore();
        RecordingChannel channel = new RecordingChannel();
        ReliableDispatcher dispatcher = new ReliableDispatcher(ACK_TIMEOUT, MAX_ATTEMPTS,
            clock::get, offsets, (t, e, r, a) -> java.util.concurrent.CompletableFuture.completedFuture(Boolean.TRUE), new UniMetrics());

        dispatcher.deliver("topic", 0, 1L, frame("e-1"), "c1", channel);
        String deliveryId = channel.lastDeliveryId;

        // Advance clock past timeout — tick will try to redeliver.
        clock.addAndGet(ACK_TIMEOUT + 1);

        // But ACK the delivery BEFORE tick runs (simulating the race where ack wins).
        assertTrue(dispatcher.ack(deliveryId));
        assertEquals(0, dispatcher.pendingCount());

        // Now tick — it should NOT resurrect the acked delivery.
        dispatcher.tick();
        assertEquals(0, dispatcher.pendingCount(), "tick must not resurrect an acked delivery");
    }

    private static final class RecordingChannel implements PushChannel {
        volatile String lastDeliveryId;
        volatile AckCallback lastCallback;
        final AtomicInteger deliverCount = new AtomicInteger();

        @Override
        public void deliver(String deliveryId, EventMeshFrame event, AckCallback callback) {
            lastDeliveryId = deliveryId;
            lastCallback = callback;
            deliverCount.incrementAndGet();
        }
    }
}
