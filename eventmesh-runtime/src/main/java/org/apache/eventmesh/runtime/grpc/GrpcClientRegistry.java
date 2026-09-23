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

package org.apache.eventmesh.runtime.grpc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;

import lombok.extern.slf4j.Slf4j;

/**
 * In-memory registration + TTL table for gRPC clients (issue #5411 item 4/5): the 1.x heartbeat
 * model - a client registers on subscribe, refreshes on every
 * {@code HeartbeatService.heartbeat} call, and a reaper evicts clients whose last heartbeat is
 * older than the TTL (default 3x the SDK's 30s heartbeat interval, matching the 1.x slack).
 *
 * <p>Eviction drops the client's v2 subscriptions so a dead gRPC consumer does not pin delivery
 * targets forever (the dispatcher would keep buffering into its PushService slot).</p>
 */
@Slf4j
public class GrpcClientRegistry {

    /** Default TTL: 3x the SDK heartbeat interval (30s) - 1.x used the same slack. */
    public static final long DEFAULT_TTL_MS = 90_000L;

    /** Called with the clientId of every evicted client (unsubscribe hook, may be null in tests). */
    public interface EvictionListener {

        void onEvicted(String clientId);
    }

    private static final class Entry {

        final String clientId;
        final String consumerGroup;
        volatile long lastHeartbeatMs;

        Entry(String clientId, String consumerGroup, long now) {
            this.clientId = clientId;
            this.consumerGroup = consumerGroup;
            this.lastHeartbeatMs = now;
        }
    }

    private final long ttlMs;
    private final LongSupplier clock;
    private final Map<String, Entry> clients = new ConcurrentHashMap<>();
    private final List<EvictionListener> listeners = new CopyOnWriteArrayList<>();

    public GrpcClientRegistry() {
        this(DEFAULT_TTL_MS, System::currentTimeMillis);
    }

    public GrpcClientRegistry(long ttlMs, LongSupplier clock) {
        this.ttlMs = ttlMs;
        this.clock = clock;
    }

    public void addEvictionListener(EvictionListener listener) {
        listeners.add(listener);
    }

    /** Register (or refresh) a client. Idempotent. */
    public void register(String clientId, String consumerGroup) {
        long now = clock.getAsLong();
        clients.compute(clientId, (id, existing) -> {
            if (existing == null) {
                return new Entry(id, consumerGroup, now);
            }
            existing.lastHeartbeatMs = now;
            return existing;
        });
    }

    /** Refresh the TTL of a registered client. Returns false for unknown clients. */
    public boolean heartbeat(String clientId) {
        Entry entry = clients.get(clientId);
        if (entry == null) {
            return false;
        }
        entry.lastHeartbeatMs = clock.getAsLong();
        return true;
    }

    /** Deregister a client (unsubscribe / stream close). */
    public void deregister(String clientId) {
        clients.remove(clientId);
    }

    public boolean isRegistered(String clientId) {
        return clients.containsKey(clientId);
    }

    public int size() {
        return clients.size();
    }

    /**
     * Evict clients whose last heartbeat is older than the TTL.
     *
     * @return the number of clients evicted (each fires the eviction listeners)
     */
    public int reapStale() {
        long now = clock.getAsLong();
        int evicted = 0;
        for (Map.Entry<String, Entry> e : clients.entrySet()) {
            Entry entry = e.getValue();
            if (now - entry.lastHeartbeatMs > ttlMs) {
                if (clients.remove(e.getKey(), entry)) {
                    evicted++;
                    log.info("grpc client evicted (heartbeat TTL): clientId={} group={}",
                        entry.clientId, entry.consumerGroup);
                    for (EvictionListener listener : listeners) {
                        try {
                            listener.onEvicted(entry.clientId);
                        } catch (RuntimeException ex) {
                            log.warn("eviction listener failed for {}: {}", entry.clientId, ex.toString());
                        }
                    }
                }
            }
        }
        return evicted;
    }

    long lastHeartbeatMs(String clientId) {
        Entry entry = clients.get(clientId);
        return entry == null ? -1L : entry.lastHeartbeatMs;
    }
}
