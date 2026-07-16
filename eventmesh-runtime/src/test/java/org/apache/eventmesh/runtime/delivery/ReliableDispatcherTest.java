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

import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.offset.OffsetStore;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

class ReliableDispatcherTest {

    private static final long ACK_TIMEOUT = 10_000L;
    private static final int MAX_ATTEMPTS = 3;

    @Test
    void ackAdvancesOffsetAndRetiresDelivery() {
        OffsetStore offsets = new InMemoryOffsetStore();
        FakeChannel channel = new FakeChannel();
        ReliableDispatcher dispatcher = newDispatcher(offsets, channel);

        dispatcher.deliver("orders", 0, 42L, event("e-1"), "client-1", channel);

        assertEquals(1, dispatcher.pendingCount());
        channel.last().ack();

        assertEquals(0, dispatcher.pendingCount());
        assertEquals(42L, offsets.readOffset("orders", "client-1", 0));
    }

    @Test
    void nackTriggersBackoffRetryThenAckSucceeds() {
        AtomicLong clock = new AtomicLong(1_000L);
        OffsetStore offsets = new InMemoryOffsetStore();
        FakeChannel channel = new FakeChannel();
        ReliableDispatcher dispatcher = new ReliableDispatcher(ACK_TIMEOUT, MAX_ATTEMPTS, clock::get, offsets,
            deadLetters(), new org.apache.eventmesh.runtime.metrics.UniMetrics());

        dispatcher.deliver("orders", 0, 10L, event("e-1"), "client-1", channel);
        assertEquals(1, channel.deliverCount);

        // Client rejects: retry scheduled at now + backoff(1) = 1s.
        channel.last().nack(new RuntimeException("busy"));
        clock.addAndGet(1_000L);
        dispatcher.tick();

        assertEquals(2, channel.deliverCount, "nack should cause one redelivery after backoff");
        channel.last().ack();

        assertEquals(0, dispatcher.pendingCount());
        assertEquals(10L, offsets.readOffset("orders", "client-1", 0));
    }

    @Test
    void ackTimeoutRedelivers() {
        AtomicLong clock = new AtomicLong(0L);
        OffsetStore offsets = new InMemoryOffsetStore();
        FakeChannel channel = new FakeChannel();
        ReliableDispatcher dispatcher = new ReliableDispatcher(ACK_TIMEOUT, MAX_ATTEMPTS, clock::get, offsets,
            deadLetters(), new org.apache.eventmesh.runtime.metrics.UniMetrics());

        dispatcher.deliver("orders", 0, 7L, event("e-1"), "client-1", channel);
        // No ack, no nack — let the ACK window expire.
        clock.addAndGet(ACK_TIMEOUT);
        dispatcher.tick();

        assertEquals(2, channel.deliverCount, "expired delivery should be redelivered");
        channel.last().ack();
        assertEquals(7L, offsets.readOffset("orders", "client-1", 0));
    }

    @Test
    void exhaustedRetriesGoToDLQ() {
        AtomicLong clock = new AtomicLong(0L);
        OffsetStore offsets = new InMemoryOffsetStore();
        FakeChannel channel = new FakeChannel();
        List<String> deadLetters = new ArrayList<>();
        ReliableDispatcher dispatcher = new ReliableDispatcher(ACK_TIMEOUT, MAX_ATTEMPTS, clock::get, offsets,
            (topic, event, reason, attempts) -> deadLetters.add(event.getId()),
            new org.apache.eventmesh.runtime.metrics.UniMetrics());

        dispatcher.deliver("orders", 0, 99L, event("doomed"), "client-1", channel);

        // MAX_ATTEMPTS = 3: attempt 1 delivered; two timeouts redeliver (attempts 2, 3); the third
        // timeout dead-letters.
        clock.addAndGet(ACK_TIMEOUT);
        dispatcher.tick(); // attempt 1 -> 2
        assertEquals(2, channel.deliverCount);

        clock.addAndGet(ACK_TIMEOUT);
        dispatcher.tick(); // attempt 2 -> 3
        assertEquals(3, channel.deliverCount);

        clock.addAndGet(ACK_TIMEOUT);
        dispatcher.tick(); // attempt 3 exhausted -> DLQ

        assertEquals(3, channel.deliverCount, "no redelivery after DLQ");
        assertEquals(0, dispatcher.pendingCount());
        assertEquals(List.of("doomed"), deadLetters);
        assertEquals(-1L, offsets.readOffset("orders", "client-1", 0), "offset must NOT advance for DLQd event");
    }

    @Test
    void ackOfUnknownDeliveryIsFalse() {
        ReliableDispatcher dispatcher = newDispatcher(new InMemoryOffsetStore(), new FakeChannel());
        assertFalse(dispatcher.ack("nope"));
        assertFalse(dispatcher.nack("nope", new RuntimeException("x")));
    }

    @Test
    void backoffFollowsExponentialSchedule() {
        assertEquals(1_000L, ReliableDispatcher.backoffMs(1));
        assertEquals(2_000L, ReliableDispatcher.backoffMs(2));
        assertEquals(4_000L, ReliableDispatcher.backoffMs(3));
        assertEquals(8_000L, ReliableDispatcher.backoffMs(4));
        assertEquals(16_000L, ReliableDispatcher.backoffMs(5));
        assertEquals(16_000L, ReliableDispatcher.backoffMs(6), "capped at 16s");
    }

    private ReliableDispatcher newDispatcher(OffsetStore offsets, FakeChannel channel) {
        return new ReliableDispatcher(ACK_TIMEOUT, MAX_ATTEMPTS, new AtomicLong(0L)::get, offsets, deadLetters(),
            new org.apache.eventmesh.runtime.metrics.UniMetrics());
    }

    private static DeadLetterSink deadLetters() {
        return (topic, event, reason, attempts) -> {
        };
    }

    private static CloudEvent event(String id) {
        return CloudEventBuilder.v1().withId(id).withSource(URI.create("test")).withType("t").build();
    }

    /**
     * Channel that records every delivery's AckCallback without acting on it, so the test controls
     * when (and whether) each delivery is acked/nacked.
     */
    private static final class FakeChannel implements PushChannel {

        final List<AckCallback> callbacks = new ArrayList<>();
        int deliverCount = 0;

        @Override
        public void deliver(String deliveryId, CloudEvent event, AckCallback callback) {
            deliverCount++;
            callbacks.add(callback);
        }

        AckCallback last() {
            return callbacks.get(callbacks.size() - 1);
        }
    }
}
