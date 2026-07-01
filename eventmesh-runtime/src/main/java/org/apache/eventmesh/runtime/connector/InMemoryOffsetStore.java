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

package org.apache.eventmesh.runtime.connector;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * In-memory implementation of {@link OffsetStore}.
 * Suitable for development and testing.
 *
 * <p>For production, use RocksDB-backed implementation.
 */
@Slf4j
public class InMemoryOffsetStore implements OffsetStore {

    // Key: connectorName:topic:partition → position
    private final Map<String, String> store;

    public InMemoryOffsetStore() {
        this.store = new ConcurrentHashMap<>();
    }

    @Override
    public void save(String connectorName, String topic, int partition, String position) {
        String key = buildKey(connectorName, topic, partition);
        store.put(key, position);
        log.debug("Saved offset: {} → {}", key, position);
    }

    @Override
    public String load(String connectorName, String topic, int partition) {
        return store.get(buildKey(connectorName, topic, partition));
    }

    @Override
    public Map<String, String> loadAll(String connectorName) {
        String prefix = connectorName + ":";
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : store.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public void flush() {
        // No-op for in-memory store
    }

    @Override
    public void close() {
        store.clear();
        log.info("InMemoryOffsetStore closed, cleared {} entries", store.size());
    }

    // ---- helpers ----

    private static String buildKey(String connectorName, String topic, int partition) {
        return connectorName + ":" + topic + ":" + partition;
    }
}
