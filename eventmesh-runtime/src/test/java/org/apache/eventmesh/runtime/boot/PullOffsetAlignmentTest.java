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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;

/**
 * Verifies that {@link UniRuntime} rewinds the storage plugin's pull cursor to the ACK offset
 * on startup, so that messages pulled-but-not-ACKed before a restart are re-pulled (at-least-once).
 */
class PullOffsetAlignmentTest {

    private UniRuntime runtime;

    @AfterEach
    void tearDown() {
        if (runtime != null) {
            runtime.shutdown();
        }
    }

    /**
     * Scenario: OffsetStore has ACK offset 80 for (orders, client-1, partition 0).
     * The storage plugin's pullOffsets (simulating file-recovered state) is at 100.
     * On start(), UniRuntime should call alignPullOffset("orders", 0, 80) to rewind.
     */
    @Test
    void alignsPullOffsetToAckOffsetOnStart() throws Exception {
        // Pre-populate the ACK offset store with offset 80 (simulating RocksDB recovery)
        InMemoryOffsetStore offsetStore = new InMemoryOffsetStore();
        offsetStore.writeOffset("orders", "client-1", 0, 80L);

        TrackingStorage storage = new TrackingStorage();
        // Simulate the pull offset recovered from file = 100 (ahead of ACK 80)
        storage.setPullOffset("orders", 0, 100L);

        runtime = new UniRuntime(storage, offsetStore, 20L, 50L, 100, 50L);
        runtime.start();

        // Verify alignPullOffset was called with the correct ACK offset
        List<AlignCall> calls = storage.getAlignCalls();
        assertFalse(calls.isEmpty(), "alignPullOffset should have been called on start");
        boolean found = false;
        for (AlignCall c : calls) {
            if ("orders".equals(c.topic) && c.partition == 0 && c.ackOffset == 80L) {
                found = true;
                break;
            }
        }
        assertTrue(found, "expected alignPullOffset(orders, 0, 80) but got " + calls);

        // Verify the pull offset was rewound from 100 to 80
        assertEquals(80L, storage.getPullOffset("orders", 0),
            "pull offset should be rewound to ACK offset 80");
    }

    /**
     * Scenario: Multiple clients subscribed to the same topic with different ACK progress.
     * client-1 ACKed to 80, client-2 ACKed to 50. The min ACK offset (50) should be used
     * for rewind so the slowest client still receives its gap messages.
     */
    @Test
    void usesMinAckOffsetAcrossClients() throws Exception {
        InMemoryOffsetStore offsetStore = new InMemoryOffsetStore();
        offsetStore.writeOffset("orders", "client-1", 0, 80L);
        offsetStore.writeOffset("orders", "client-2", 0, 50L);

        TrackingStorage storage = new TrackingStorage();
        storage.setPullOffset("orders", 0, 100L);

        runtime = new UniRuntime(storage, offsetStore, 20L, 50L, 100, 50L);
        runtime.start();

        // Should rewind to min(80, 50) = 50
        assertEquals(50L, storage.getPullOffset("orders", 0),
            "pull offset should be rewound to min ACK offset 50");
    }

    /**
     * Scenario: No persisted ACK offsets (first run). alignPullOffset should NOT be called
     * — the storage plugin keeps its default cursor.
     */
    @Test
    void skipsAlignmentOnFirstRun() throws Exception {
        InMemoryOffsetStore offsetStore = new InMemoryOffsetStore();
        TrackingStorage storage = new TrackingStorage();

        runtime = new UniRuntime(storage, offsetStore, 20L, 50L, 100, 50L);
        runtime.start();

        assertTrue(storage.getAlignCalls().isEmpty(),
            "alignPullOffset should not be called when no ACK offsets exist");
    }

    /**
     * Scenario: ACK offset (80) is ahead of pull offset (50) — no rewind needed.
     * alignPullOffset should be called but the storage plugin returns false (no rewind).
     */
    @Test
    void noRewindWhenAckAheadOfPull() throws Exception {
        InMemoryOffsetStore offsetStore = new InMemoryOffsetStore();
        offsetStore.writeOffset("orders", "client-1", 0, 80L);

        TrackingStorage storage = new TrackingStorage();
        storage.setPullOffset("orders", 0, 50L); // pull < ACK, no gap

        runtime = new UniRuntime(storage, offsetStore, 20L, 50L, 100, 50L);
        runtime.start();

        // alignPullOffset is called, but storage should NOT rewind (50 < 80)
        assertEquals(50L, storage.getPullOffset("orders", 0),
            "pull offset should remain 50 when already behind ACK offset");
    }

    // ---- Test doubles ----

    private static final class AlignCall {

        final String topic;
        final int partition;
        final long ackOffset;

        AlignCall(String topic, int partition, long ackOffset) {
            this.topic = topic;
            this.partition = partition;
            this.ackOffset = ackOffset;
        }

        @Override
        public String toString() {
            return "AlignCall(" + topic + "#" + partition + " -> " + ackOffset + ")";
        }
    }

    /**
     * A minimal MeshStoragePlugin that tracks alignPullOffset calls and simulates a pull-offset
     * cursor (like the file-recovered pullOffsets in Kafka/RocketMQ-4.x plugins).
     */
    private static final class TrackingStorage implements MeshStoragePlugin {

        private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, Long>> pullOffsets = new ConcurrentHashMap<>();
        private final List<AlignCall> alignCalls = new ArrayList<>();

        void setPullOffset(String topic, int partition, long offset) {
            pullOffsets.computeIfAbsent(topic, k -> new ConcurrentHashMap<>()).put(partition, offset);
        }

        long getPullOffset(String topic, int partition) {
            return pullOffsets.getOrDefault(topic, new ConcurrentHashMap<>()).getOrDefault(partition, -1L);
        }

        List<AlignCall> getAlignCalls() {
            return alignCalls;
        }

        @Override
        public boolean alignPullOffset(String topic, int partition, long ackOffset) {
            alignCalls.add(new AlignCall(topic, partition, ackOffset));
            if (ackOffset < 0) {
                return false;
            }
            ConcurrentHashMap<Integer, Long> topicOffsets = pullOffsets.computeIfAbsent(topic, k -> new ConcurrentHashMap<>());
            if (partition >= 0) {
                Long current = topicOffsets.get(partition);
                if (current != null && current <= ackOffset) {
                    return false; // no rewind needed
                }
                topicOffsets.put(partition, ackOffset);
                return true;
            }
            return false;
        }

        // ---- rest is no-op / minimal ----

        private final ConcurrentHashMap<String, ConcurrentLinkedQueue<CloudEvent>> queues = new ConcurrentHashMap<>();

        @Override
        public void init(Properties properties) {
        }

        @Override
        public void send(String topic, CloudEvent event, SendCallback callback) {
            queues.computeIfAbsent(topic, k -> new ConcurrentLinkedQueue<>()).offer(event);
            SendResult r = new SendResult();
            r.setMessageId(event.getId());
            r.setTopic(topic);
            callback.onSuccess(r);
        }

        @Override
        public List<CloudEvent> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            ConcurrentLinkedQueue<CloudEvent> q = queues.get(topic);
            if (q == null) {
                return new ArrayList<>();
            }
            List<CloudEvent> out = new ArrayList<>();
            CloudEvent e;
            while (out.size() < maxEvents && (e = q.poll()) != null) {
                out.add(e);
            }
            return out;
        }

        @Override
        public void assignPartitions(String topic, List<Integer> partitions) {
        }

        @Override
        public void commitOffset(String topic, int partition, long offset) {
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
}
