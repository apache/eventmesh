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
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Tests for the Frame-native MQ-cursor recording (Plan A replacement of develop's
 * OffsetExtensions): the storage plugin stamps emmqoffset/emmqpartition on the frame at poll time;
 * ReliableDispatcher.ack() persists the MQ PHYSICAL offset under the reserved clientId
 * MQ_CURSOR_CLIENT so UniRuntime.alignPullOffsetsToAck can rewind plugin cursors on restart.
 */
class MqCursorRecordingTest {

    private static final long ACK_TIMEOUT = 10_000L;
    private static final int MAX_ATTEMPTS = 3;

    private static EventMeshFrame frame(String id, long mqOffset, int mqPartition) {
        EventMeshFrame f = EventMeshFrame.fromCloudEvent(
            CloudEventBuilder.v1().withId(id).withSource(URI.create("test")).withType("t").build());
        if (mqOffset >= 0) {
            f.attributes().put("emmqoffset", Long.toString(mqOffset));
            f.attributes().put("emmqpartition", Integer.toString(mqPartition));
        }
        return f;
    }

    @Test
    void ackRecordsMqPhysicalCursorUnderReservedKey() {
        OffsetStore offsets = new InMemoryOffsetStore();
        RecordingChannel channel = new RecordingChannel();
        ReliableDispatcher dispatcher = new ReliableDispatcher(ACK_TIMEOUT, MAX_ATTEMPTS,
            () -> 0L, offsets, (t, e, r, a) -> java.util.concurrent.CompletableFuture.completedFuture(Boolean.TRUE), new UniMetrics());

        // MQ physical offset 4242 on partition 2 (as stamped by Kafka / RocketMQ-4.x at poll).
        dispatcher.deliver("orders", 2, 1L, frame("e-1", 4242L, 2), "client-1", channel);

        channel.lastCallback.ack();

        // Per-subscriber logical offset (1) recorded under the real clientId.
        assertEquals(1L, offsets.readOffset("orders", "client-1", 2));
        // MQ physical cursor (4242) recorded under the RESERVED key — this is what
        // alignPullOffsetsToAck reads on restart.
        assertEquals(4242L, offsets.readOffset("orders", ReliableDispatcher.MQ_CURSOR_CLIENT, 2),
            "MQ physical offset must be recorded under the reserved cursor key");
    }

    @Test
    void unstampedFrameRecordsNoCursor() {
        // RocketMQ 5.x POP stamps empopck but not emmqoffset — no cursor entry should be written.
        OffsetStore offsets = new InMemoryOffsetStore();
        RecordingChannel channel = new RecordingChannel();
        ReliableDispatcher dispatcher = new ReliableDispatcher(ACK_TIMEOUT, MAX_ATTEMPTS,
            () -> 0L, offsets, (t, e, r, a) -> java.util.concurrent.CompletableFuture.completedFuture(Boolean.TRUE), new UniMetrics());

        dispatcher.deliver("orders", 0, 1L, frame("e-1", -1, -1), "client-1", channel);
        channel.lastCallback.ack();

        assertEquals(-1L, offsets.readOffset("orders", ReliableDispatcher.MQ_CURSOR_CLIENT, 0),
            "no emmqoffset on frame → no cursor entry (5.x POP needs none)");
    }

    @Test
    void cursorMonotonicAcrossMultipleAcks() {
        OffsetStore offsets = new InMemoryOffsetStore();
        ReliableDispatcher dispatcher = new ReliableDispatcher(ACK_TIMEOUT, MAX_ATTEMPTS,
            () -> 0L, offsets, (t, e, r, a) -> java.util.concurrent.CompletableFuture.completedFuture(Boolean.TRUE), new UniMetrics());

        RecordingChannel ch1 = new RecordingChannel();
        dispatcher.deliver("t", 0, 1L, frame("e-1", 100L, 0), "c1", ch1);
        ch1.lastCallback.ack();

        RecordingChannel ch2 = new RecordingChannel();
        dispatcher.deliver("t", 0, 2L, frame("e-2", 200L, 0), "c1", ch2);
        ch2.lastCallback.ack();

        // Monotonic cursor (P4 fix): ack of e-2 after e-1 leaves cursor at 200, not regressed.
        assertEquals(200L, offsets.readOffset("t", ReliableDispatcher.MQ_CURSOR_CLIENT, 0));
    }

    private static final class RecordingChannel implements PushChannel {
        volatile AckCallback lastCallback;
        final AtomicInteger deliverCount = new AtomicInteger();

        @Override
        public void deliver(String deliveryId, EventMeshFrame event, AckCallback callback) {
            lastCallback = callback;
            deliverCount.incrementAndGet();
        }
    }
}
