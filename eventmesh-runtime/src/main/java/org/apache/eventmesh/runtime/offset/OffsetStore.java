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

package org.apache.eventmesh.runtime.offset;

import java.util.Map;

/**
 * EventMesh's self-managed distribution offset store.
 *
 * <p>EventMesh owns the offset entirely — it never commits the underlying MQ's consumer-group
 * offset (§12.6). The key is {@code topic#clientId#partition} → the offset up to which that
 * subscriber has been served. {@code readOffset} returning {@code -1} means "never served".
 *
 * <p>Two implementations: {@link InMemoryOffsetStore} (tests / degraded mode when no local disk is
 * available) and {@link RocksDBOffsetStore} (production, §12.6.3). Multi-instance coordination
 * (Phase 2.5) layers a remote copy in Meta on top of this local store.</p>
 */
public interface OffsetStore {

    /**
     * @return the saved offset, or {@code -1} if no offset has ever been written for this key
     */
    long readOffset(String topic, String clientId, int partition);

    /**
     * Persist (or buffer) the offset for this subscriber/partition.
     *
     * <p>The write is <em>monotonically non-decreasing</em>: an offset at or below the stored value
     * is a no-op returning {@code true} (stored progress already covers it); the cursor must never
     * move backwards (issue #5289). Implementations that cannot make the write durable return
     * {@code false} so the caller keeps the delivery in flight instead of retiring it
     * (issue #5290).</p>
     *
     * @return {@code true} if the stored offset is now at or beyond {@code offset};
     *         {@code false} if the durable write failed — the caller must NOT retire the delivery
     */
    boolean writeOffset(String topic, String clientId, int partition, long offset);

    /**
     * All offsets for a topic, keyed by {@code clientId#partition}. Used by the admin view
     * (Phase 7.5) and by replay/recovery.
     */
    Map<String, Long> readAllOffsets(String topic);

    /**
     * All topics that have at least one persisted offset entry. Used by the restart recovery
     * path ({@code UniRuntime.alignPullOffsetsToAck}) to discover which topics need pull-cursor
     * rewind. Returns an empty set on first run (no offsets persisted yet).
     */
    default java.util.Set<String> readAllTopics() {
        return java.util.Collections.emptySet();
    }

    /**
     * Force any buffered writes to durable storage.
     */
    void flush();

    /**
     * Release resources. After close the store must not be used.
     */
    void close();

    /**
     * The composite key used by both implementations.
     */
    static String buildKey(String topic, String clientId, int partition) {
        return topic + "#" + clientId + "#" + partition;
    }
}
