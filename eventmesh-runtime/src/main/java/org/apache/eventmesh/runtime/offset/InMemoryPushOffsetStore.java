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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory {@link PushOffsetStore}. Thread-safe, not durable.
 *
 * <p>Per design decision #3, push offsets do not survive restart — the
 * system relies on MQ pull offset (rewind) for recovery. This store is
 * purely a runtime gauge for:
 * <ul>
 *   <li>offset_lag = max(push_offset) - max(ack_offset) per topic/partition</li>
 *   <li>push watermark visibility in admin panel</li>
 * </ul>
 */
public class InMemoryPushOffsetStore implements PushOffsetStore {

    /**
     * Primary index: {@code topic#clientId#partition → offset} (AtomicLong for concurrent writes).
     */
    private final ConcurrentHashMap<String, AtomicLong> table = new ConcurrentHashMap<>();

    /**
     * Secondary index for client-level cleanup: {@code clientId → set of composite keys}.
     * Avoids full-table scan on {@link #removeClient}.
     */
    private final ConcurrentHashMap<String, java.util.Set<String>> clientKeys = new ConcurrentHashMap<>();

    @Override
    public void writePushOffset(String topic, String clientId, int partition, long offset) {
        String key = PushOffsetStore.buildKey(topic, clientId, partition);
        AtomicLong prev = table.computeIfAbsent(key, k -> new AtomicLong(Long.MIN_VALUE));
        // Track max offset for this key (monotonic watermark)
        long current = prev.get();
        while (offset > current && !prev.compareAndSet(current, offset)) {
            current = prev.get();
        }
        // Register key under client for fast removal
        clientKeys.computeIfAbsent(clientId, k -> java.util.Collections.newSetFromMap(new ConcurrentHashMap<>()))
            .add(key);
    }

    @Override
    public long readPushOffset(String topic, String clientId, int partition) {
        AtomicLong val = table.get(PushOffsetStore.buildKey(topic, clientId, partition));
        return val == null ? -1L : val.get();
    }

    @Override
    public long readMaxPushOffset(String topic, String clientId) {
        long max = -1L;
        String prefix = topic + "#" + clientId + "#";
        for (Map.Entry<String, AtomicLong> e : table.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                long v = e.getValue().get();
                if (v > max) {
                    max = v;
                }
            }
        }
        return max;
    }

    @Override
    public Map<String, Long> readAllPushOffsets(String topic) {
        String prefix = topic + "#";
        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<String, AtomicLong> e : table.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                result.put(e.getKey().substring(prefix.length()), e.getValue().get());
            }
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    @Override
    public void removeClient(String clientId) {
        java.util.Set<String> keys = clientKeys.remove(clientId);
        if (keys != null) {
            for (String key : keys) {
                table.remove(key);
            }
        }
    }

    @Override
    public void clear() {
        table.clear();
        clientKeys.clear();
    }
}
