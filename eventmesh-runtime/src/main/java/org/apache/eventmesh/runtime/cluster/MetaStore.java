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

/**
 * The control-plane store (§13.2 / §15.5) — the single source of truth for the uni runtime.
 *
 * <p>Replaces the legacy Registry: it holds instance heartbeats, partition-assignment tables,
 * cluster-wide subscription views, clientId→instance routing, offset remote replicas, and dynamic
 * rules. The production implementation adapts {@code eventmesh-meta} (MetaService, nacos/etcd/…);
 * tests use {@link InMemoryMetaStore}. Callers must not assume values survive a Meta outage — the
 * runtime degrades to self-allocated state (§13.2.9).</p>
 */
public interface MetaStore {

    /**
     * Watch a key prefix; the listener fires on every {@code put} or {@code delete} under it.
     */
    void watch(String prefix, MetaListener listener);

    void put(String key, String value);

    /**
     * @return true if the key was absent and is now set (atomic compare-and-set)
     */
    boolean putIfAbsent(String key, String value);

    String get(String key);

    /**
     * All key→value entries whose key starts with {@code prefix}.
     */
    Map<String, String> getWithPrefix(String prefix);

    /**
     * @return true if the key existed and was removed
     */
    boolean delete(String key);

    /**
     * Atomic compare-and-set on a single key (§13.2.8④ fencing). Succeeds only when the current
     * value equals {@code expectedOldValue} (or {@code expectedOldValue} is null and the key is
     * absent); on success the key is set to {@code newValue} and {@code true} is returned.
     *
     * <p>On a false return the caller must re-read with {@link #get(String)} and decide whether
     * to retry, fence itself, or give up. The Nacos ConfigService implementation uses
     * {@code publishConfigCas(..., casMd5)}; the in-memory implementation uses
     * {@code AtomicReference.compareAndSet}.</p>
     *
     * @param key              target key
     * @param expectedOldValue the value we expect to find (null = key absent)
     * @param newValue         the value to install if the expectation holds
     * @return true on success, false on expectation mismatch or backend failure
     */
    boolean tryAcquire(String key, String expectedOldValue, String newValue);
}
