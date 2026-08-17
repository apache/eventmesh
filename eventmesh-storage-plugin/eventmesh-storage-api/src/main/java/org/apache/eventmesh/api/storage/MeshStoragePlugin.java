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

import org.apache.eventmesh.api.LifeCycle;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

import java.util.List;
import java.util.Properties;

/**
 * Unified storage plugin interface for the simplified EventMesh architecture.
 *
 * <p>Design contract (see {@code docs/eventmesh-simplified-architecture-redesign.md} §3):</p>
 * <ul>
 *   <li><b>MQ has no semantics</b>: the underlying Kafka/RocketMQ is treated as a persistent FIFO
 *       WAL. No {@code producerGroup} / {@code consumerGroup} / {@code tag} is exposed to callers.</li>
 *   <li><b>EventMesh owns the subscription</b>: EventMesh pulls messages via {@link #poll} and
 *       dispatches them according to its own subscription model; it never delegates distribution
 *       to a MQ consumer group.</li>
 *   <li><b>EventMesh owns the offset</b>: {@link #commitOffset} advances EventMesh's own
 *       distribution offset (persisted in RocksDB + Meta); the underlying MQ offset is never
 *       committed by EventMesh.</li>
 * </ul>
 *
 * <p>Phase 1 (current) ships a thin adapter that bridges the existing push-based
 * {@code Producer}/{@code Consumer} SPIs behind this interface. Partition-level control
 * ({@link #assignPartitions}) and self-managed offset ({@link #commitOffset}) are reserved as
 * no-ops here and get native implementations together with Phase 2 (SubscriptionManager) and
 * Phase 2.5 (multi-instance coordination).</p>
 */
@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.STORAGE)
public interface MeshStoragePlugin extends LifeCycle {

    /**
     * Initialize the plugin with MQ connection properties. No group/tag keys are required.
     *
     * @param properties bootstrap configuration (e.g. {@code namesrvAddr}); group/tag keys are ignored.
     */
    void init(Properties properties) throws Exception;

    /**
     * Publish a single {@link EventMeshFrame} (EventMesh's internal wire unit) to the given topic.
     * EventMesh holds the only producer; callers never pass a producerGroup.
     *
     * @param topic    EventMesh logical topic (mapped 1:1 to the MQ topic)
     * @param frame    the EventMeshFrame to persist (the plugin maps it to the MQ's native message)
     * @param callback async send callback
     */
    void send(String topic, EventMeshFrame frame, SendCallback callback) throws Exception;

    /**
     * Pull a batch of {@link EventMeshFrame}s for a topic. EventMesh drives the consumption pace; the
     * MQ never pushes.
     *
     * @param topic       EventMesh logical topic
     * @param partition   physical partition (-1 = any / step-1 adapter treats topic as a whole)
     * @param startOffset offset to read from (-1 = continue from last known position); step-1
     *                    adapter ignores this and drains its internal push buffer
     * @param maxEvents   upper bound of events to return in this call
     * @param timeoutMs   max wait when no event is immediately available
     * @return a possibly-empty list of EventMeshFrames; never null
     */
    List<EventMeshFrame> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs);

    /**
     * Assign the set of partitions EventMesh owns for a topic. Reserved for Phase 2.5 multi-instance
     * coordination (§13.2.3); the step-1 adapter treats the topic as a whole and ignores the call.
     *
     * @param topic      EventMesh logical topic
     * @param partitions physical partitions EventMesh is responsible for
     */
    void assignPartitions(String topic, List<Integer> partitions);

    /**
     * Advance EventMesh's own distribution offset for a partition. EventMesh persists this in its
     * own offset store; the underlying MQ offset is never committed. Step-1 adapter is a no-op.
     *
     * @param topic     EventMesh logical topic
     * @param partition physical partition
     * @param offset    the offset up to which EventMesh has dispatched
     */
    void commitOffset(String topic, int partition, long offset);

    /**
     * Trigger the MQ-layer ACK for a message that was pulled but not yet broker-acked (P2 fix:
     * RocketMQ 5.x POP mode — ACK broker only after the client ACKs, restoring at-least-once).
     * The {@code ackKey} is a backend-specific identifier stamped on the frame (e.g. POP check key).
     * Default no-op for backends that don't need deferred MQ ACK (Kafka, RocketMQ 4.x PULL).
     *
     * @return true if the ACK was found and executed; false if no pending ACK for this key.
     */
    default boolean ackPulledMessage(String topic, String ackKey) {
        return false;
    }

    /**
     * Number of physical partitions for {@code topic}, or {@code -1} if unknown. Used by the
     * multi-instance partition assigner (§13.2.3) to compute which partitions this instance owns;
     * {@code -1} makes the assigner treat the topic as single-partition (poll-all fallback).
     *
     * <p>Default {@code -1} for backends that don't expose partition metadata (e.g. RocketMQ queue
     * model differs from Kafka partitions — its native assign is tracked separately).</p>
     */
    default int partitionCount(String topic) {
        return -1;
    }

    /**
     * The MQ's current end (high-watermark) offset for {@code topic#partition}, or {@code -1} if
     * unknown. Used by the {@code eventmesh_offset_lag} gauge (§13.5.1) to compute
     * {@code endOffset - distributedOffset}. {@code partition = -1} means "max across partitions".
     * Default {@code -1} for backends that don't expose it.
     */
    default long endOffset(String topic, int partition) {
        return -1L;
    }

    /**
     * Rewind the pull cursor for {@code (topic, partition)} to {@code ackOffset} so that messages
     * already pulled but not yet ACKed by the client are re-pulled after a restart.
     *
     * <p>This is the recovery mechanism for the at-least-once contract on restart: the pull offset
     * (persisted to a local file by Kafka/RocketMQ-4.x plugins) may be ahead of the ACK offset
     * (persisted in RocksDB {@code OffsetStore}); without rewind, the gap messages are lost because
     * they are neither in the MQ's unconsumed range nor in the in-memory {@code pending} deliveries
     * (which is lost on restart).</p>
     *
     * <p>Implementations that manage their own pull cursor (Kafka {@code seek}, RocketMQ 4.x
     * {@code pullOffsets}) MUST override this to rewind that cursor. Broker-managed backends
     * (RocketMQ 5.x POP — broker re-delivers on invisible-timeout) can keep the default no-op.</p>
     *
     * @param topic     EventMesh logical topic
     * @param partition physical partition (-1 = all partitions of the topic)
     * @param ackOffset the ACK offset to rewind to (from {@code OffsetStore}); {@code -1} means
     *                  "no known ACK offset" → keep the existing pull cursor (new topic / first run)
     * @return {@code true} if the cursor was rewound; {@code false} if the backend does not support
     *         rewind or {@code ackOffset} was not applicable
     */
    default boolean alignPullOffset(String topic, int partition, long ackOffset) {
        return false;
    }
}
