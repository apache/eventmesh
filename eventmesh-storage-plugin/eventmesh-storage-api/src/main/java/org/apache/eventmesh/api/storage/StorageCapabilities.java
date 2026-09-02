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

import java.util.List;

/**
 * Capability marker for {@link MeshStoragePlugin} backends.
 *
 * <p>Following the same pattern as {@link LiteTopicCapable} (which itself was modeled on the
 * existing {@code createTopic} / {@code Admin} discovery-via-{@code instanceof} convention), this
 * interface groups every optional / backend-specific capability into a set of <i>nested
 * sub-interfaces</i> a plugin can pick up by adding them to its {@code implements} clause.
 * Callers gate on capabilities with {@code storage instanceof StorageCapabilities.X}.
 *
 * <h2>Why sub-interfaces, not enums / booleans</h2>
 * <ul>
 *   <li><b>Compile-time check</b>: dropping a capability from {@code implements} is a Java
 *       compile error caught by the build, not a runtime flag check.</li>
 *   <li><b>No reflection</b>: gate with {@code instanceof}, no {@code Class.forName} or
 *       capability-string lookup.</li>
 *   <li><b>Self-documenting</b>: the {@code implements} clause is the canonical contract —
 *       reviewers see exactly which capabilities a backend supports without reading docs.</li>
 * </ul>
 *
 * <h2>The 7 capabilities</h2>
 * <p><b>Universal (3)</b> — every backend MUST implement these (they are part of the
 * {@link MeshStoragePlugin} contract already; declaring them via {@code StorageCapabilities} is a
 * self-audit so dropping a method body by accident fails the TCK, not a smoke test):</p>
 * <ul>
 *   <li>{@link TopicManagement} — exposes {@code createTopic}.</li>
 *   <li>{@link PartitionAssignment} — overrides {@code assignPartitions} to do real work.</li>
 *   <li>{@link ExplicitOffsetCommit} — overrides {@code commitOffset} to persist.</li>
 * </ul>
 * <p><b>Backend-specific (4)</b> — declared only by the backends that actually implement them:</p>
 * <ul>
 *   <li>{@link EndOffsetQuery} — Kafka; exposes MQ high-watermark via {@code endOffset}.</li>
 *   <li>{@link AlignPullOffset} — Kafka + RocketMQ 4.x; rewinds client-side pull cursor on restart
 *       to recover messages in the ACK-pull gap.</li>
 *   <li>{@link DeferredPopAck} — RocketMQ 5.x POP mode; broker-side ACK deferred until client
 *       ACKs (at-least-once on top of broker-managed consumption).</li>
 *   <li>{@link LiteTopic} — RocketMQ 5.x RIP-83; lite sub-topics with separate consume queues.
 *       This is the same shape as the existing {@link LiteTopicCapable} — the new
 *       {@link LiteTopic} sub-interface exists so the TCK can enumerate all capabilities via a
 *       single instanceof check, while {@link LiteTopicCapable} stays as the historical SPI for
 *       callers that already gate on it.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * public class KafkaMeshStoragePlugin
 *         implements MeshStoragePlugin,
 *                    StorageCapabilities.TopicManagement,
 *                    StorageCapabilities.PartitionAssignment,
 *                    StorageCapabilities.ExplicitOffsetCommit,
 *                    StorageCapabilities.EndOffsetQuery,
 *                    StorageCapabilities.AlignPullOffset {
 *     ...
 * }
 *
 * // caller
 * if (storage instanceof StorageCapabilities.EndOffsetQuery) {
 *     long end = ((StorageCapabilities.EndOffsetQuery) storage).endOffset(topic, partition);
 * }
 * }</pre>
 *
 * <h2>Why a single outer interface with nested sub-interfaces</h2>
 * <p>Grouping the sub-interfaces under {@code StorageCapabilities} (rather than 7 top-level
 * types in {@code org.apache.eventmesh.api.storage}) gives reviewers a single FQN to grep for
 * when auditing the capability matrix, and lets callers write
 * {@code StorageCapabilities.EndOffsetQuery} instead of inventing 7 names. The outer
 * {@code StorageCapabilities} interface itself is <b>not</b> meant to be implemented directly —
 * implement one or more of the nested sub-interfaces.
 */
public interface StorageCapabilities {

    /**
     * Backend exposes topic-management operations ({@code createTopic}). Required for callers
     * that need to materialize topics up front (admin tools, integration tests). Kafka and
     * RocketMQ 4.x/5.x all support this; the 3.x legacy {@code Admin} SPI is reused internally.
     */
    interface TopicManagement extends StorageCapabilities {
        /**
         * Create the topic (idempotent). Partition/queue count is backend-specific: Kafka takes
         * a partition count, RocketMQ takes a queue count.
         */
        void createTopic(String topic, int partitions) throws Exception;
    }

    /**
     * Backend actually assigns partitions instead of treating the topic as a single stream.
     * Phase 2.5 multi-instance coordination depends on this — backends that only support
     * poll-all must NOT implement this and the assigner will fall back to single-partition mode.
     */
    interface PartitionAssignment extends StorageCapabilities {
        void assignPartitions(String topic, List<Integer> partitions);
    }

    /**
     * Backend persists an explicit distribution offset passed by EventMesh (vs. implicitly
     * committing on every poll). All current backends support this; the marker makes the
     * capability explicit so a future pure-push backend without commit support can omit it
     * without breaking compile.
     */
    interface ExplicitOffsetCommit extends StorageCapabilities {
        void commitOffset(String topic, int partition, long offset);
    }

    /**
     * Backend can answer the MQ's high-watermark offset ({@code endOffset}). Used by the
     * {@code eventmesh_offset_lag} gauge. <b>Kafka only</b> — RocketMQ 4.x/5.x do not expose
     * an equivalent of Kafka's {@code endOffsets}; backends that lack it fall back to the
     * default {@code -1L} return and the gauge uses a different lag computation.
     */
    interface EndOffsetQuery extends StorageCapabilities {
        long endOffset(String topic, int partition);
    }

    /**
     * Backend can rewind the client-side pull cursor to an ACK offset. Used at restart to
     * recover messages in the pull-ACK gap (pulled but not yet ACKed). <b>Kafka + RocketMQ
     * 4.x</b>; RocketMQ 5.x POP mode is broker-managed and does not need rewind (broker
     * re-delivers on invisible-timeout).
     */
    interface AlignPullOffset extends StorageCapabilities {
        boolean alignPullOffset(String topic, int partition, long ackOffset);
    }

    /**
     * Backend supports broker-side POP ACK deferred until the client ACKs. <b>RocketMQ 5.x
     * only</b>. This is the at-least-once contract on top of broker-managed POP consumption:
     * the broker holds the message invisible until {@link MeshStoragePlugin#ackPulledMessage}
     * confirms client delivery.
     */
    interface DeferredPopAck extends StorageCapabilities {
        boolean ackPulledMessage(String topic, String ackKey);
    }

    /**
     * Backend supports RocketMQ 5.x Lite Topic (RIP-83) — secondary message container under a
     * parent topic, addressed by {@code (parentTopic, liteTopic)}. This is the unified
     * capability marker for the same set of ops as the historical {@link LiteTopicCapable};
     * see that interface for the operation contract. <b>RocketMQ 5.x only</b>.
     */
    interface LiteTopic extends StorageCapabilities {
        void createLiteTopic(String parentTopic, String liteTopic) throws Exception;

        void createLiteTopic(String parentTopic, String liteTopic, int queueCount) throws Exception;

        void sendLite(String parentTopic, String liteTopic, EventMeshFrame frame, SendCallback callback) throws Exception;

        List<EventMeshFrame> pullLite(String parentTopic, String liteTopic, int maxEvents, long timeoutMs);
    }
}
