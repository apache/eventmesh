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

package org.apache.eventmesh.runtime.cluster;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory {@link MetaStore} for tests and single-instance / degraded deployments.
 *
 * <p>Listeners fire synchronously on the calling thread. There is no persistence and no TTL —
 * instance liveness is whatever the membership layer writes/deletes. The production Meta-backed
 * implementation mirrors this contract over nacos/etcd/consul/zk/raft.
 */
public class InMemoryMetaStore implements MetaStore {

    private final ConcurrentHashMap<String, String> kv = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Watch> watches = new CopyOnWriteArrayList<>();

    @Override
    public void watch(String prefix, MetaListener listener) {
        watches.add(new Watch(prefix, listener));
    }

    @Override
    public void put(String key, String value) {
        kv.put(key, value);
        notify(key, value, false);
    }

    @Override
    public boolean putIfAbsent(String key, String value) {
        if (kv.putIfAbsent(key, value) == null) {
            notify(key, value, false);
            return true;
        }
        return false;
    }

    @Override
    public String get(String key) {
        return kv.get(key);
    }

    @Override
    public Map<String, String> getWithPrefix(String prefix) {
        TreeMap<String, String> out = new TreeMap<>();
        for (Map.Entry<String, String> e : kv.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    @Override
    public boolean delete(String key) {
        if (kv.remove(key) != null) {
            notify(key, null, true);
            return true;
        }
        return false;
    }

    @Override
    public boolean tryAcquire(String key, String expectedOldValue, String newValue) {
        if (expectedOldValue == null) {
            // key must be absent → use putIfAbsent
            if (kv.putIfAbsent(key, newValue) == null) {
                notify(key, newValue, false);
                return true;
            }
            return false;
        }
        // CAS on existing value — ConcurrentHashMap.replace(key, oldVal, newVal) is atomic
        boolean ok = kv.replace(key, expectedOldValue, newValue);
        if (ok) {
            notify(key, newValue, false);
        }
        return ok;
    }

    private void notify(String key, String value, boolean deleted) {
        for (Watch w : watches) {
            if (key.startsWith(w.prefix)) {
                w.listener.onChange(key, value, deleted);
            }
        }
    }

    private static final class Watch {

        final String prefix;
        final MetaListener listener;

        Watch(String prefix, MetaListener listener) {
            this.prefix = prefix;
            this.listener = listener;
        }
    }
}
