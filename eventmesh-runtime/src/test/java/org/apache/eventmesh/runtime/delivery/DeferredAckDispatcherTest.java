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

import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.metrics.UniMetrics;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.offset.OffsetStore;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Tests for the P2 deferred ACK mechanism: ReliableDispatcher.ack() triggers the mqAckCallback
 * (which would ACK the MQ broker for RocketMQ 5.x POP mode) only AFTER the client ACKs — restoring
 * at-least-once. Also tests DLQ path with mqAckCallback (should NOT fire MQ ACK on DLQ).
 */
class DeferredAckDispatcherTest {

    private static final long ACK_TIMEOUT = 10_000L;
    private static final int MAX_ATTEMPTS = 3;

    @Test
    void mqAckFiresOnClientAck() {
        OffsetStore offsets = new InMemoryOffsetStore();
        AtomicInteger mqAcks = new AtomicInteger(0);
        List<String> deadLetters = new ArrayList<>();
        ReliableDispatcher dispatcher = newDispatcher(offsets, mqAcks::incrementAndGet, deadLetters);

        Runnable mqAck = mqAcks::incrementAndGet;
        RecordingChannel channel = new RecordingChannel();
        dispatcher.deliver("orders", 0, 42L, EventMeshFrame.fromCloudEvent(
            CloudEventBuilder.v1().withId("e-1").withSource(URI.create("test")).withType("t").build()),
            "client-1", channel, mqAck);

        assertEquals(1, dispatcher.pendingCount());
        assertEquals(0, mqAcks.get(), "MQ ACK must NOT fire before client ACK");

        channel.lastAck().ack();

        assertEquals(0, dispatcher.pendingCount());
        assertEquals(1, mqAcks.get(), "MQ ACK must fire after client ACK");
        assertEquals(42L, offsets.readOffset("orders", "client-1", 0));
    }

    @Test
    void mqAckDoesNotFireOnTimeoutRedeliver() {
        AtomicLong clock = new AtomicLong(0L);
        AtomicInteger mqAcks = new AtomicInteger(0);
        OffsetStore offsets = new InMemoryOffsetStore();
        RecordingChannel channel = new RecordingChannel();
        ReliableDispatcher dispatcher = new ReliableDispatcher(ACK_TIMEOUT, MAX_ATTEMPTS, clock::get,
            offsets, (t, e, r, a) -> java.util.concurrent.CompletableFuture.completedFuture(Boolean.TRUE), new UniMetrics());

        Runnable mqAck = mqAcks::incrementAndGet;
        dispatcher.deliver("orders", 0, 10L, EventMeshFrame.fromCloudEvent(
            CloudEventBuilder.v1().withId("e-1").withSource(URI.create("test")).withType("t").build()),
            "client-1", channel, mqAck);

        // Timeout — no client ACK. MQ ACK must NOT fire (broker will redeliver via invisibleTime).
        clock.addAndGet(ACK_TIMEOUT);
        dispatcher.tick();

        assertEquals(2, channel.deliverCount, "should redeliver after timeout");
        assertEquals(0, mqAcks.get(), "MQ ACK must NOT fire on timeout redeliver");

        // Now client ACKs the redelivered copy.
        channel.lastAck().ack();
        assertEquals(1, mqAcks.get(), "MQ ACK fires when client finally ACKs");
    }

    @Test
    void mqAckDoesNotFireOnDLQ() {
        AtomicLong clock = new AtomicLong(0L);
        AtomicInteger mqAcks = new AtomicInteger(0);
        List<String> deadLetters = new ArrayList<>();
        OffsetStore offsets = new InMemoryOffsetStore();
        RecordingChannel channel = new RecordingChannel();
        ReliableDispatcher dispatcher = new ReliableDispatcher(ACK_TIMEOUT, MAX_ATTEMPTS, clock::get,
            offsets, (topic, event, reason, attempts) -> {
                deadLetters.add(event.attributes().get("id"));
                return java.util.concurrent.CompletableFuture.completedFuture(Boolean.TRUE);
            },
            new UniMetrics());

        Runnable mqAck = mqAcks::incrementAndGet;
        dispatcher.deliver("orders", 0, 99L, EventMeshFrame.fromCloudEvent(
            CloudEventBuilder.v1().withId("doomed").withSource(URI.create("test")).withType("t").build()),
            "client-1", channel, mqAck);

        // Exhaust all attempts via timeouts → DLQ.
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            clock.addAndGet(ACK_TIMEOUT);
            dispatcher.tick();
        }

        assertEquals(1, deadLetters.size(), "event should be DLQd");
        assertEquals("doomed", deadLetters.get(0));
        assertEquals(0, mqAcks.get(), "MQ ACK must NOT fire when event is DLQd");
    }

    @Test
    void nullMqAckCallbackWorksFine() {
        // Kafka / RocketMQ 4.x don't need MQ ACK (PULL mode). mqAckCallback = null.
        OffsetStore offsets = new InMemoryOffsetStore();
        RecordingChannel channel = new RecordingChannel();
        ReliableDispatcher dispatcher = newDispatcher(offsets, () -> {}, new ArrayList<>());

        dispatcher.deliver("orders", 0, 5L, EventMeshFrame.fromCloudEvent(
            CloudEventBuilder.v1().withId("e-1").withSource(URI.create("test")).withType("t").build()),
            "client-1", channel, null);

        channel.lastAck().ack();
        assertEquals(0, dispatcher.pendingCount());
        assertEquals(5L, offsets.readOffset("orders", "client-1", 0));
    }

    private ReliableDispatcher newDispatcher(OffsetStore offsets, Runnable noopMqAck,
                                             List<String> deadLetters) {
        return new ReliableDispatcher(ACK_TIMEOUT, MAX_ATTEMPTS, new AtomicLong(0L)::get, offsets,
            (topic, event, reason, attempts) -> {
                deadLetters.add(event.attributes().get("id"));
                return java.util.concurrent.CompletableFuture.completedFuture(Boolean.TRUE);
            },
            new UniMetrics());
    }

    private static final class RecordingChannel implements PushChannel {
        final List<AckCallback> callbacks = new ArrayList<>();
        int deliverCount = 0;

        @Override
        public void deliver(String deliveryId, EventMeshFrame event, AckCallback callback) {
            deliverCount++;
            callbacks.add(callback);
        }

        AckCallback lastAck() {
            return callbacks.get(callbacks.size() - 1);
        }
    }
}
