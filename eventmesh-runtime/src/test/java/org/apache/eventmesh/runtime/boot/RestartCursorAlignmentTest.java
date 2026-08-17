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

package org.apache.eventmesh.runtime.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.delivery.AckCallback;
import org.apache.eventmesh.runtime.delivery.PushChannel;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.offset.OffsetStore;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Frame-native restart-cursor alignment test (Plan A replacement of develop's
 * PullOffsetAlignmentTest): crash between poll and client ACK → restart →
 * {@code UniRuntime.alignPullOffsetsToAck} rewinds the plugin's pull cursor to the recorded MQ
 * physical offset → gap messages are re-pulled (at-least-once).
 */
class RestartCursorAlignmentTest {

    private static final long ACK_TIMEOUT = 10_000L;

    /**
     * In-memory storage that emulates broker-unmanaged pull semantics (Kafka / RocketMQ-4.x):
     * messages sit in per-partition queues; the pull cursor is self-managed ({@code pullOffsets});
     * each poll stamps the MQ physical offset onto the returned frames.
     */
    private static final class TrackingStorage implements MeshStoragePlugin {
        final ConcurrentHashMap<String, List<Long>> messages = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, ConcurrentHashMap<Integer, Long>> pullOffsets = new ConcurrentHashMap<>();
        final List<Long> pulledOffsets = new ArrayList<>();

        void put(String topic, int partition, long offset) {
            messages.computeIfAbsent(topic, k -> new ArrayList<>()).add(offset);
        }

        @Override
        public List<EventMeshFrame> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            List<EventMeshFrame> out = new ArrayList<>();
            ConcurrentHashMap<Integer, Long> offs = pullOffsets.computeIfAbsent(topic, k -> new ConcurrentHashMap<>());
            List<Long> msgs = messages.getOrDefault(topic, List.of());
            long cursor = offs.getOrDefault(0, 0L);
            for (Long o : msgs) {
                if (o >= cursor && o < cursor + maxEvents) {
                    EventMeshFrame f = EventMeshFrame.fromCloudEvent(
                        CloudEventBuilder.v1().withId("m-" + o).withSource(URI.create("t")).withType("t").build());
                    f.attributes().put("emmqoffset", Long.toString(o));
                    f.attributes().put("emmqpartition", "0");
                    out.add(f);
                    offs.put(0, o + 1);
                    pulledOffsets.add(o);
                }
            }
            return out;
        }

        @Override
        public boolean alignPullOffset(String topic, int partition, long ackOffset) {
            ConcurrentHashMap<Integer, Long> offs = pullOffsets.computeIfAbsent(topic, k -> new ConcurrentHashMap<>());
            Long current = offs.get(0);
            if (current != null && current <= ackOffset) {
                return false;
            }
            offs.put(0, ackOffset);
            return true;
        }

        @Override
        public void init(Properties p) {
        }

        @Override
        public void send(String t, EventMeshFrame f, SendCallback cb) {
            cb.onSuccess(null);
        }

        @Override
        public void assignPartitions(String t, List<Integer> p) {
        }

        @Override
        public void commitOffset(String t, int p, long o) {
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void start() {
        }

        @Override
        public void shutdown() {
        }
    }

    private static final class AutoAckChannel implements PushChannel {
        @Override
        public void deliver(String deliveryId, EventMeshFrame event, AckCallback callback) {
            callback.ack(); // auto-ACK for the happy-path pre-crash phase
        }
    }

    private static final class NoAckChannel implements PushChannel {
        @Override
        public void deliver(String deliveryId, EventMeshFrame event, AckCallback callback) {
            // simulate crash: never ACKs
        }
    }

    @Test
    void restartReplaysGapBetweenCursorAndPullOffset() throws Exception {
        TrackingStorage storage = new TrackingStorage();
        OffsetStore offsets = new InMemoryOffsetStore();

        // Messages 0..9 exist on topic "orders" partition 0.
        for (long o = 0; o < 10; o++) {
            storage.put("orders", 0, o);
        }

        // Phase 1 (pre-crash): runtime polls 0..4, all ACKed — cursor advances to 5.
        org.apache.eventmesh.runtime.ingress.UniIngressService ingress1 =
            new org.apache.eventmesh.runtime.ingress.UniIngressService(storage, offsets,
                new org.apache.eventmesh.runtime.subscription.SubscriptionManager(),
                new org.apache.eventmesh.runtime.push.PushService(),
                ACK_TIMEOUT, 3, System::currentTimeMillis);
        // Wire a subscriber that auto-ACKs, then dispatch.
        // We drive dispatch manually: poll → deliver → ack (via dispatcher), repeated.
        for (int i = 0; i < 5; i++) {
            ingress1.getDispatcher().deliver("orders", 0, i,
                storage.poll("orders", 0, -1, 1, 0).get(0), "client-1", new AutoAckChannel());
        }
        // After 5 ACKed messages, the recorded MQ cursor = 4 (last ACKed physical offset).
        assertEquals(4L, offsets.readOffset("orders",
            org.apache.eventmesh.runtime.delivery.ReliableDispatcher.MQ_CURSOR_CLIENT, 0));

        // Phase 2 (the crash): messages 5..7 are PULLED (cursor advances to 8) but never ACKed.
        for (int i = 0; i < 3; i++) {
            ingress1.getDispatcher().deliver("orders", 0, 100 + i,
                storage.poll("orders", 0, -1, 1, 0).get(0), "client-1", new NoAckChannel());
        }
        // Storage pull cursor is now at 8; __mqcursor__ still at 4 → gap [4,8) at risk.
        assertEquals(8L, storage.pullOffsets.get("orders").get(0));

        // Phase 3 (restart): a NEW UniRuntime boots with the same storage + offset store.
        // alignPullOffsetsToAck should rewind the pull cursor from 8 back to 5 (cursor+1... i.e.
        // to the recorded cursor so the ACKed-up-to message is re-pulled? No: the recorded cursor
        // IS 4 = last ACKed offset; rewind to 4+1 = next un-acked... but our implementation
        // rewinds to the recorded value directly — replaying [4] once more is the safe direction.)
        UniRuntime runtime2 = new UniRuntime(storage, offsets, 50L, 50L, 10, 0L);
        runtime2.start(); // triggers alignPullOffsetsToAck in start()

        // The pull cursor was rewound from 8 to <= 5 (at or just past the recorded cursor 4).
        Long rewound = storage.pullOffsets.get("orders").get(0);
        assertTrue(rewound <= 5, "pull cursor must be rewound from 8 to <= 5, got " + rewound);
        assertTrue(rewound >= 4, "pull cursor must not rewind past the recorded cursor 4, got " + rewound);

        runtime2.shutdown();

        // Phase 4: post-restart poll re-delivers the gap (and possibly the one already-ACKed
        // message at 4 — at-least-once direction).
        storage.pulledOffsets.clear();
        storage.poll("orders", 0, -1, 10, 0);
        assertTrue(storage.pulledOffsets.contains(5L), "gap message 5 must be re-pulled after restart");
        assertTrue(storage.pulledOffsets.contains(6L), "gap message 6 must be re-pulled after restart");
        assertTrue(storage.pulledOffsets.contains(7L), "gap message 7 must be re-pulled after restart");
    }

    @Test
    void firstRunWithNoOffsetsSkipsAlignment() throws Exception {
        TrackingStorage storage = new TrackingStorage();
        storage.put("fresh", 0, 0);
        storage.pullOffsets.computeIfAbsent("fresh", k -> new ConcurrentHashMap<>()).put(0, 42L);

        UniRuntime runtime = new UniRuntime(storage, new InMemoryOffsetStore(), 50L, 50L, 10, 0L);
        runtime.start(); // no persisted offsets → alignment skipped, cursor untouched

        assertEquals(42L, storage.pullOffsets.get("fresh").get(0), "no persisted cursor → cursor untouched");
        runtime.shutdown();
    }
}
