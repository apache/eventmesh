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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import lombok.extern.slf4j.Slf4j;

/**
 * Instance membership via heartbeats in {@link MetaStore} (§13.2.3).
 *
 * <p>Each instance periodically writes {@code /em/instances/<id> = <heartbeatMs>}; the live set is
 * the set of heartbeat keys whose timestamp is within {@code ttlMs}. {@link PartitionAssigner} reads
 * the live set to decide partition ownership. When Meta is unavailable the membership layer returns
 * only the local instance, so the runtime degrades to self-allocated single-instance mode (§13.2.9).
 * </p>
 */
@Slf4j
public class ClusterMembership {

    public static final String INSTANCE_PREFIX = "/em/instances/";

    private final MetaStore meta;
    private final String selfInstanceId;
    private final String selfAddress;
    private final long ttlMs;
    private final LongSupplier clock;

    /** Cached live set, refreshed on demand. */
    private final ConcurrentHashMap<String, Boolean> liveCache = new ConcurrentHashMap<>();

    public ClusterMembership(MetaStore meta, String selfInstanceId, String selfAddress, long ttlMs, LongSupplier clock) {
        this.meta = meta;
        this.selfInstanceId = selfInstanceId;
        this.selfAddress = selfAddress;
        this.ttlMs = ttlMs;
        this.clock = clock;
    }

    /**
     * Refresh and cache the local instance's heartbeat so peers see it as live. The value carries
     * both the timestamp (for TTL pruning) and the instance's HTTP address (for cross-instance
     * forwarding, §13.2.5): {@code "<timestamp>|<address>"}.
     *
     * @return true if the Meta write succeeded; false if Meta is unreachable (the caller -
     *         PartitionOwnership - uses this as a lease: on failure it stops polling to avoid
     *         split-brain duplicate consumption)
     */
    public boolean heartbeat() {
        long now = clock.getAsLong();
        try {
            meta.put(INSTANCE_PREFIX + selfInstanceId, now + "|" + selfAddress);
            liveCache.put(selfInstanceId, Boolean.TRUE);
            return true;
        } catch (RuntimeException e) {
            log.warn("heartbeat (Meta put) failed - lease invalid: {}", e.toString());
            return false;
        }
    }

    /**
     * @return the sorted set of live instance ids (this instance always included).
     */
    public List<String> liveInstances() {
        long now = clock.getAsLong();
        TreeSet<String> live = new TreeSet<>();
        for (Map.Entry<String, String> e : meta.getWithPrefix(INSTANCE_PREFIX).entrySet()) {
            try {
                long ts = parseTimestamp(e.getValue());
                if (now - ts <= ttlMs) {
                    live.add(e.getKey().substring(INSTANCE_PREFIX.length()));
                }
            } catch (NumberFormatException ex) {
                // ignore malformed heartbeat
            }
        }
        live.add(selfInstanceId); // self is always live from its own perspective
        List<String> out = new ArrayList<>(live);
        Collections.sort(out);
        return out;
    }

    /**
     * Look up another instance's HTTP address (for cross-instance forwarding, §13.2.5 / §17.6).
     *
     * @return {@code host:port}, or {@code null} if the instance is unknown / has no registered address.
     */
    public String addressOf(String instanceId) {
        if (instanceId == null) {
            return null;
        }
        if (instanceId.equals(selfInstanceId)) {
            return selfAddress;
        }
        String val = meta.get(INSTANCE_PREFIX + instanceId);
        if (val == null) {
            return null;
        }
        int sep = val.indexOf('|');
        return sep > 0 ? val.substring(sep + 1) : null;
    }

    /** Mark this instance as leaving (drops the heartbeat key so peers stop seeing it as live). */
    public void leave() {
        meta.delete(INSTANCE_PREFIX + selfInstanceId);
        liveCache.remove(selfInstanceId);
    }

    private static long parseTimestamp(String heartbeatValue) {
        int sep = heartbeatValue.indexOf('|');
        String ts = sep > 0 ? heartbeatValue.substring(0, sep) : heartbeatValue;
        return Long.parseLong(ts);
    }

    public String self() {
        return selfInstanceId;
    }
}
