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

import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.offset.OffsetStore;

import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

/**
 * Lifecycle owner for the uni runtime (§7). Boots the storage plugin and offset store, then
 * runs two periodic tasks on a scheduler: the pull-loop (poll each subscribed topic and dispatch to
 * subscribers) and the dispatcher tick (drive ACK-timeout redelivery / DLQ).
 *
 * <p>This is the single entry point that wires {@link UniIngressService} into a running system.
 * The HTTP ingress (§6) and real push transports (§5 WebSocket/SSE) attach to the
 * {@link UniIngressService} this exposes. Java 21 virtual-thread executors can replace the
 * scheduled thread pool once the project moves off Java 8 source level.</p>
 */
@Slf4j
public class UniRuntime {

    private final MeshStoragePlugin storage;
    private final OffsetStore offsetStore;
    private final UniIngressService ingress;

    private final long pollIntervalMs;
    private final long tickIntervalMs;
    private final int maxBatchPerTopic;
    private final long pollTimeoutMs;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;

    /**
     * Config handed to {@code storage.init} on {@link #start()} (namesrv, etc.). Populated from
     * {@code eventmesh.properties} + system properties by {@link EventMeshApplication}; empty by
     * default so unit tests booting a mock/standalone storage are unaffected.
     */
    private Properties storageConfig = new Properties();

    /** Inject storage config before {@link #start()}; additive, chainable. */
    public UniRuntime withStorageConfig(Properties storageConfig) {
        this.storageConfig = storageConfig;
        return this;
    }

    public UniRuntime(MeshStoragePlugin storage, OffsetStore offsetStore,
        long pollIntervalMs, long tickIntervalMs, int maxBatchPerTopic, long pollTimeoutMs) {
        this.storage = storage;
        this.offsetStore = offsetStore;
        this.ingress = new UniIngressService(storage, offsetStore,
            new org.apache.eventmesh.runtime.subscription.SubscriptionManager(),
            new org.apache.eventmesh.runtime.push.PushService(),
            org.apache.eventmesh.runtime.delivery.ReliableDispatcher.DEFAULT_ACK_TIMEOUT_MS,
            org.apache.eventmesh.runtime.delivery.ReliableDispatcher.DEFAULT_MAX_ATTEMPTS,
            System::currentTimeMillis);
        this.pollIntervalMs = pollIntervalMs;
        this.tickIntervalMs = tickIntervalMs;
        this.maxBatchPerTopic = maxBatchPerTopic;
        this.pollTimeoutMs = pollTimeoutMs;
    }

    /**
     * Start storage, offset store, and the pull/tick scheduler.
     */
    public void start() throws Exception {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        storage.init(storageConfig);
        storage.start();

        // Restart recovery: align the storage's pull cursor to the ACK offset so that messages
        // pulled-but-not-ACKed before the restart are re-pulled (at-least-once). Without this,
        // the persisted pull offset (ahead of ACK offset after a crash) would skip the gap
        // messages — they are neither in the MQ's unconsumed range nor in the (lost) in-memory
        // pending deliveries.
        alignPullOffsetsToAck();

        scheduler = Executors.newScheduledThreadPool(3, r -> {
            Thread t = new Thread(r, "eventmesh-uni");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::pullLoop, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(ingress::dispatcherTick, tickIntervalMs, tickIntervalMs, TimeUnit.MILLISECONDS);
        // §13.6.5: periodically evict subscribers that stopped polling (zombie-poll cleanup).
        scheduler.scheduleAtFixedRate(() -> ingress.cleanupStaleClients(60_000L), 60_000L, 60_000L, TimeUnit.MILLISECONDS);
        log.info("uni runtime started (poll={}ms, tick={}ms)", pollIntervalMs, tickIntervalMs);
    }

    /**
     * Restart recovery: rewind the storage plugin's pull cursor to the recorded MQ physical offset
     * so messages in the gap [cursorOffset, pullOffset) — pulled before the crash but not yet
     * ACKed by any client — are re-pulled and re-delivered (at-least-once).
     *
     * <p>Frame-architecture semantics: the dispatcher records the MQ PHYSICAL offset on each
     * client ACK under the reserved clientId {@code __mqcursor__} (frame attributes
     * {@code emmqoffset}/{@code emmqpartition} are stamped by the Kafka / RocketMQ-4.x plugins at
     * poll time; RocketMQ-5.x POP stamps none — the broker re-delivers on invisible-timeout, so
     * alignment is a no-op there). Rewind reads ONLY the reserved key: per-subscriber entries
     * hold logical sequence numbers, which must never be passed to
     * {@code alignPullOffset} as MQ offsets.</p>
     *
     * <p>Crash-window note: a hard crash (kill -9) relies on the RocksDB WAL for cursor
     * durability — the recorded cursor may be a few entries stale, so alignment replays slightly
     * more than the exact gap (at-least-once direction, safe).</p>
     *
     * <p>Topics without any persisted cursor (first run / new topic) are skipped — the storage
     * plugin keeps its default cursor (beginning or latest per its own init logic).</p>
     */
    private void alignPullOffsetsToAck() {
        // Discover all topics that have persisted ACK offsets via OffsetStore.readAllTopics().
        // This is the reliable recovery path: the store knows what it persisted across restarts.
        java.util.Set<String> topicsWithAckOffsets = offsetStore.readAllTopics();
        if (topicsWithAckOffsets.isEmpty()) {
            log.info("pull-offset alignment: no persisted offsets (first run), skipping");
            return;
        }

        // Frame-architecture semantics: the MQ PHYSICAL pull cursor is recorded by the dispatcher
        // on client ACK under the reserved clientId MQ_CURSOR_CLIENT (frame attributes
        // emmqoffset/emmqpartition stamped by Kafka / RocketMQ-4.x at poll time). Rewind reads
        // ONLY that reserved key — the per-subscriber entries hold logical sequence numbers, which
        // must never be passed to alignPullOffset as MQ offsets.
        int aligned = 0;
        for (String topic : topicsWithAckOffsets) {
            java.util.Map<Integer, Long> cursorByPartition = new java.util.HashMap<>();
            String prefix = org.apache.eventmesh.runtime.delivery.ReliableDispatcher.MQ_CURSOR_CLIENT + "#";
            for (java.util.Map.Entry<String, Long> e : offsetStore.readAllOffsets(topic).entrySet()) {
                if (!e.getKey().startsWith(prefix)) {
                    continue; // per-subscriber logical offset — not a physical cursor
                }
                try {
                    int partition = Integer.parseInt(e.getKey().substring(prefix.length()));
                    cursorByPartition.merge(partition, e.getValue(), Math::min);
                } catch (NumberFormatException ignored) {
                    // key format mismatch — skip
                }
            }

            for (java.util.Map.Entry<Integer, Long> e : cursorByPartition.entrySet()) {
                long ackOffset = e.getValue();
                if (ackOffset >= 0) {
                    boolean rewound = storage.alignPullOffset(topic, e.getKey(), ackOffset);
                    if (rewound) {
                        aligned++;
                        log.info("pull-offset alignment: {}#{} rewound to MQ offset {}", topic, e.getKey(), ackOffset);
                    }
                }
            }
        }
        log.info("pull-offset alignment complete: {} partition(s) rewound across {} topic(s)", aligned, topicsWithAckOffsets.size());
    }

    /**
     * The unified ingress — publish/subscribe/poll/ack/request-reply attach here.
     */
    public UniIngressService ingress() {
        return ingress;
    }

    /**
     * The storage plugin this runtime boots - exposed for cluster wiring (PartitionOwnership's
     * partitionCount / assignPartitions calls, 13.2.3).
     */
    public MeshStoragePlugin storage() {
        return storage;
    }

    /**
     * Pull-loop: poll each active topic from storage + dispatch to subscribers. Synchronized to
     * prevent concurrent {@code storage.poll} calls on the same consumer (the 3-thread scheduler
     * can otherwise overlap ticks when poll blocks, racing the consumer's internal state and losing
     * messages).
     */
    private synchronized void pullLoop() {
        for (String topic : ingress.activeTopicsClustered()) {
            try {
                ingress.pullAndDispatch(topic, maxBatchPerTopic, pollTimeoutMs);
            } catch (Exception e) {
                log.warn("pullLoop error on topic {}", topic, e);
            }
        }
    }

    /**
     * Graceful shutdown (§13.6.4): stop pull-loop → drain pending → wait in-flight ACK → flush offset → close.
     * Idempotent.
     *
     * @param gracefulMs time to wait for in-flight ACKs after draining (0 = no wait)
     */
    public void shutdown(long gracefulMs) {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        log.info("graceful shutdown starting (graceful={}ms)", gracefulMs);

        // 1. Stop the pull-loop scheduler (no new events pulled)
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                scheduler.shutdownNow();
            }
        }

        // 2. Final retry/DLQ sweep (drain pending deliveries)
        try {
            ingress.dispatcherTick();
        } catch (Exception e) {
            log.warn("final dispatcher tick failed", e);
        }

        // 3. Wait for in-flight ACKs (graceful period)
        if (gracefulMs > 0) {
            long deadline = System.currentTimeMillis() + gracefulMs;
            int pending = ingress.getDispatcher().pendingCount();
            while (pending > 0 && System.currentTimeMillis() < deadline) {
                log.info("graceful shutdown: waiting for {} in-flight deliveries", pending);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                // Run dispatcher tick to process any late ACKs / timeouts
                try {
                    ingress.dispatcherTick();
                } catch (Exception expected) {
                    log.debug("dispatcher tick during graceful shutdown: {}", expected.toString());
                }
                pending = ingress.getDispatcher().pendingCount();
            }
            if (pending > 0) {
                log.warn("graceful shutdown: {} deliveries still in-flight after {}ms, proceeding", pending, gracefulMs);
            }
        }

        // 4. Flush + close offset store
        try {
            offsetStore.flush();
        } catch (Exception e) {
            log.warn("offset flush failed", e);
        }
        try {
            offsetStore.close();
        } catch (Exception e) {
            log.warn("offset close failed", e);
        }

        // 6. Close storage
        try {
            storage.shutdown();
        } catch (Exception e) {
            log.warn("storage shutdown failed", e);
        }
        log.info("graceful shutdown complete");
    }

    /** Backward-compatible shutdown with default 10s graceful period. */
    public void shutdown() {
        shutdown(10_000L);
    }
}
