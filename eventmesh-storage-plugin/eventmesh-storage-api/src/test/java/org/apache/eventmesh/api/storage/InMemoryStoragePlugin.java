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

package org.apache.eventmesh.api.storage;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.common.wire.EventMeshFrame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory {@link MeshStoragePlugin} fixture that implements <b>all 7</b>
 * {@link StorageCapabilities} sub-interfaces.
 *
 * <p>Used by the TCK self-test ({@code MeshStoragePluginTCKSelfTest}) to verify the TCK
 * itself is well-formed: if the TCK is correct, running it against this plugin (which
 * declares every capability) should make every test pass.</p>
 *
 * <p><b>Not a production backend.</b> State is process-local and lost on shutdown. Do NOT
 * register this plugin via SPI — it lives in the {@code test} source set of
 * {@code eventmesh-storage-api}.</p>
 *
 * <h2>State</h2>
 * <ul>
 *   <li>{@code topics} — per-topic list of appended frames (WAL).</li>
 *   <li>{@code offsets} — per-{@code (topic, partition)} committed offset.</li>
 *   <li>{@code partitions} — per-topic set of assigned partitions.</li>
 *   <li>{@code pendingAcks} — per-{@code (topic, ackKey)} pending POP ACK callback.</li>
 *   <li>{@code liteAcks} — per-{@code (parent, lite)} list of frames, indexed by
 *       {@code (parent, lite)} pair.</li>
 * </ul>
 */
public class InMemoryStoragePlugin
    implements MeshStoragePlugin,
               StorageCapabilities.TopicManagement,
               StorageCapabilities.PartitionAssignment,
               StorageCapabilities.ExplicitOffsetCommit,
               StorageCapabilities.EndOffsetQuery,
               StorageCapabilities.AlignPullOffset,
               StorageCapabilities.DeferredPopAck,
               StorageCapabilities.LiteTopic {

    private final ConcurrentMap<String, List<EventMeshFrame>> topics = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> offsets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<Integer>> partitions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Runnable> pendingAcks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<EventMeshFrame>> liteTopics = new ConcurrentHashMap<>();

    private final AtomicLong pullCursors = new AtomicLong(0);
    private final AtomicLong litePullCursors = new AtomicLong(0);

    private volatile boolean started = false;
    private volatile boolean closed = false;
    private volatile int defaultLiteQueueCount = 4;

    @Override
    public void init(Properties properties) {
        // No-op: state is in-memory. If a caller sets "inmem.liteQueueCount" we honor it.
        if (properties != null && properties.getProperty("inmem.liteQueueCount") != null) {
            try {
                defaultLiteQueueCount = Integer.parseInt(properties.getProperty("inmem.liteQueueCount"));
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
    }

    @Override
    public void send(String topic, EventMeshFrame frame, SendCallback callback) {
        if (closed) {
            throw new IllegalStateException("plugin is closed");
        }
        topics.computeIfAbsent(topic, k -> Collections.synchronizedList(new ArrayList<>())).add(frame);
        if (callback != null) {
            callback.onSuccess(new org.apache.eventmesh.api.SendResult());
        }
    }

    @Override
    public List<EventMeshFrame> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
        List<EventMeshFrame> topicFrames = topics.get(topic);
        if (topicFrames == null) {
            return Collections.emptyList();
        }
        synchronized (topicFrames) {
            int from = startOffset < 0 ? 0 : (int) startOffset;
            if (from >= topicFrames.size()) {
                return Collections.emptyList();
            }
            int to = Math.min(from + maxEvents, topicFrames.size());
            return new ArrayList<>(topicFrames.subList(from, to));
        }
    }

    @Override
    public void assignPartitions(String topic, List<Integer> ps) {
        partitions.put(topic, new ArrayList<>(ps));
    }

    @Override
    public void commitOffset(String topic, int partition, long offset) {
        offsets.put(key(topic, partition), offset);
    }

    @Override
    public int partitionCount(String topic) {
        List<Integer> ps = partitions.get(topic);
        return ps == null ? -1 : ps.size();
    }

    @Override
    public long endOffset(String topic, int partition) {
        List<EventMeshFrame> frames = topics.get(topic);
        if (frames == null) {
            return -1L;
        }
        synchronized (frames) {
            return frames.size();
        }
    }

    @Override
    public boolean alignPullOffset(String topic, int partition, long ackOffset) {
        if (ackOffset < 0) {
            return false;
        }
        // In-memory: just remember the rewind position; poll() picks it up on next call.
        pullCursors.set(ackOffset);
        return true;
    }

    @Override
    public boolean ackPulledMessage(String topic, String ackKey) {
        Runnable r = pendingAcks.remove(ackKey);
        if (r == null) {
            return false;
        }
        r.run();
        return true;
    }

    @Override
    public void createTopic(String topic, int partitions) {
        topics.computeIfAbsent(topic, k -> Collections.synchronizedList(new ArrayList<>()));
    }

    // ============================== LiteTopic ==============================

    @Override
    public void createLiteTopic(String parentTopic, String liteTopic) {
        createLiteTopic(parentTopic, liteTopic, defaultLiteQueueCount);
    }

    @Override
    public void createLiteTopic(String parentTopic, String liteTopic, int queueCount) {
        String key = liteKey(parentTopic, liteTopic);
        liteTopics.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()));
    }

    @Override
    public void sendLite(String parentTopic, String liteTopic, EventMeshFrame frame, SendCallback callback) {
        if (closed) {
            throw new IllegalStateException("plugin is closed");
        }
        String key = liteKey(parentTopic, liteTopic);
        liteTopics.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>())).add(frame);
        // Also register a pending POP ACK so ackPulledMessage can demonstrate true.
        String ackKey = "ack-" + key + "-" + System.nanoTime();
        pendingAcks.put(ackKey, () -> { /* no-op for in-memory */ });
        if (callback != null) {
            callback.onSuccess(new org.apache.eventmesh.api.SendResult());
        }
    }

    @Override
    public List<EventMeshFrame> pullLite(String parentTopic, String liteTopic, int maxEvents, long timeoutMs) {
        String key = liteKey(parentTopic, liteTopic);
        List<EventMeshFrame> frames = liteTopics.get(key);
        if (frames == null) {
            return Collections.emptyList();
        }
        synchronized (frames) {
            int from = (int) Math.min(litePullCursors.get(), frames.size());
            int to = Math.min(from + maxEvents, frames.size());
            if (from >= to) {
                return Collections.emptyList();
            }
            return new ArrayList<>(frames.subList(from, to));
        }
    }

    // ============================== LifeCycle ==============================

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void start() {
        started = true;
    }

    @Override
    public void shutdown() {
        closed = true;
    }

    // ============================== helpers ==============================

    private static String key(String topic, int partition) {
        return topic + "#" + partition;
    }

    private static String liteKey(String parent, String lite) {
        return parent + "$" + lite;
    }

    /**
     * Test-only accessor for verifying round-trip behavior (send then poll returns the frame).
     */
    public Map<String, List<EventMeshFrame>> debugTopics() {
        return topics;
    }
}
