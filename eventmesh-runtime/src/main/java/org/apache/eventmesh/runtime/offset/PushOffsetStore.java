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

import org.apache.eventmesh.api.storage.OffsetExtensions;

import java.util.Map;

/**
 * Records the push watermark — the last MQ physical offset that has been
 * successfully pushed to a subscriber (before ACK).
 *
 * <p>This is the "push offset" in the four-offset model:
 * <pre>
 *   write offset   (MQ physical offset at send time)
 *   pull offset   (MQ physical offset at poll time)
 *   push offset   (this store — offset handed to ReliableDispatcher.deliver)
 *   ACK offset    (OffsetStore — offset confirmed by client ACK)
 * </pre>
 *
 * <p>PushOffsetStore is <b>in-memory only</b> (per design decision #3): it
 * serves as a real-time tracking gauge, not a recovery mechanism. On restart
 * the watermarks are reset — the system recovers from MQ's pull offset
 * (rewind / replay) instead of from the push watermark.</p>
 *
 * <p>Implementations must be thread-safe.</p>
 */
public interface PushOffsetStore {

    /**
     * Record that an event with the given MQ physical offset has been
     * handed to the reliability layer for delivery to {@code clientId}.
     *
     * @param topic     MQ topic
     * @param clientId  subscriber client id
     * @param partition MQ partition / queue id
     * @param offset    MQ physical offset (from {@link OffsetExtensions})
     */
    void writePushOffset(String topic, String clientId, int partition, long offset);

    /**
     * @return the last pushed offset for (topic, clientId, partition),
     *         or {@code -1L} if never recorded
     */
    long readPushOffset(String topic, String clientId, int partition);

    /**
     * Read max pushed offset across all partitions for a given (topic, clientId).
     * Used by the offset_lag gauge (offset_store vs push_store diff).
     *
     * @return max pushed offset, or {@code -1L} if never recorded
     */
    long readMaxPushOffset(String topic, String clientId);

    /**
     * All push offsets for a topic, keyed by {@code clientId#partition}.
     *
     * @return unmodifiable map
     */
    Map<String, Long> readAllPushOffsets(String topic);

    /**
     * Remove all push offset tracking for a client (e.g. on unsubscribe).
     */
    void removeClient(String clientId);

    /**
     * Clear all entries (used on shutdown / tests).
     */
    void clear();

    /**
     * The composite key used by implementations: {@code topic#clientId#partition}.
     */
    static String buildKey(String topic, String clientId, int partition) {
        return topic + "#" + clientId + "#" + partition;
    }
}
