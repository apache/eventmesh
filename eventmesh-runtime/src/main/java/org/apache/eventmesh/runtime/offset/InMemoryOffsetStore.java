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
 * In-memory {@link OffsetStore}. Thread-safe, not durable across process restarts.
 *
 * <p>Used for unit tests and as a degraded-mode store when no local RocksDB path is configured
 * (the dispatch loop still works; only crash recovery is lost). Production uses
 * {@link RocksDBOffsetStore}.</p>
 */
public class InMemoryOffsetStore implements OffsetStore {

    private final ConcurrentHashMap<String, AtomicLong> table = new ConcurrentHashMap<>();

    @Override
    public long readOffset(String topic, String clientId, int partition) {
        AtomicLong offset = table.get(OffsetStore.buildKey(topic, clientId, partition));
        return offset == null ? -1L : offset.get();
    }

    @Override
    public boolean writeOffset(String topic, String clientId, int partition, long offset) {
        // Monotonic write: offset only advances, never regresses. Prevents a slow group's replay
        // (after restart) from overwriting a fast group's already-acked progress. In-memory writes
        // cannot fail, so this always returns true (issue #5289 / #5290).
        table.computeIfAbsent(OffsetStore.buildKey(topic, clientId, partition), k -> new AtomicLong(-1L))
            .accumulateAndGet(offset, Math::max);
        return true;
    }

    @Override
    public Map<String, Long> readAllOffsets(String topic) {
        String prefix = topic + "#";
        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<String, AtomicLong> e : table.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                result.put(e.getKey().substring(prefix.length()), e.getValue().get());
            }
        }
        return result;
    }

    @Override
    public java.util.Set<String> readAllTopics() {
        java.util.Set<String> topics = new java.util.HashSet<>();
        for (String key : table.keySet()) {
            int sep = key.indexOf('#');
            if (sep > 0) {
                topics.add(key.substring(0, sep));
            }
        }
        return topics;
    }

    @Override
    public void flush() {
        // nothing buffered
    }

    @Override
    public void close() {
        table.clear();
    }
}
