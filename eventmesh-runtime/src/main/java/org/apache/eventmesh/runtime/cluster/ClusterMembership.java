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

import org.apache.eventmesh.runtime.ingress.LoadMeter;

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
    private volatile String selfAddress;
    private final long ttlMs;
    private final LongSupplier clock;
    /** Per-JVM fencing token (§13.2.8④). Shared with PartitionOwnership for CAS assignment. */
    private final FencingToken selfToken;
    /** Optional load snapshot supplier (LoadMeter.sample()+snapshot()); null = no load in heartbeat. */
    private volatile java.util.function.Supplier<String> loadSupplier;

    /** Cached live set, refreshed on demand. */
    private final ConcurrentHashMap<String, Boolean> liveCache = new ConcurrentHashMap<>();

    public ClusterMembership(MetaStore meta, String selfInstanceId, String selfAddress, long ttlMs,
                             LongSupplier clock, FencingToken selfToken) {
        this.meta = meta;
        this.selfInstanceId = selfInstanceId;
        this.selfAddress = selfAddress;
        this.ttlMs = ttlMs;
        this.clock = clock;
        this.selfToken = selfToken;
    }

    /**
     * Wire a load-snapshot supplier whose {@code get()} returns the trailing load fields
     * ({@code <active>|<inflow>|<outflow>|<cpu>}). The heartbeat appends it to the instance value so
     * {@code /session/recommend} can score instances globally (§3.2).
     */
    public void withLoadSupplier(java.util.function.Supplier<String> loadSupplier) {
        this.loadSupplier = loadSupplier;
    }

    /** Override the advertised self address (called once the HTTP server knows its real host:port). */
    public void setSelfAddress(String selfAddress) {
        this.selfAddress = selfAddress;
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
            // value = <ts>|<addr>[|<load...>] — load fields appended when a LoadMeter is wired.
            String value = now + "|" + selfAddress;
            if (loadSupplier != null) {
                String load = loadSupplier.get();
                if (load != null && !load.isEmpty()) {
                    value += "|" + load;
                }
            }
            meta.put(INSTANCE_PREFIX + selfInstanceId, value);
            liveCache.put(selfInstanceId, Boolean.TRUE);
            return true;
        } catch (RuntimeException e) {
            log.warn("heartbeat (Meta put) failed - lease invalid: {}", e.toString());
            return false;
        }
    }

    /**
     * Live instances with their advertised HTTP address + parsed load (for /session/recommend
     * scoring, §3.2). Each entry: {@code instanceId → InstanceInfo(addr, loadSnapshot)}.
     */
    public Map<String, InstanceInfo> liveInstancesWithLoad() {
        long now = clock.getAsLong();
        Map<String, InstanceInfo> out = new java.util.HashMap<>();
        for (Map.Entry<String, String> e : meta.getWithPrefix(INSTANCE_PREFIX).entrySet()) {
            String val = e.getValue();
            String id = e.getKey().substring(INSTANCE_PREFIX.length());
            int sep = val.indexOf('|');
            if (sep < 0) {
                continue;
            }
            try {
                long ts = Long.parseLong(val.substring(0, sep));
                if (now - ts > ttlMs) {
                    continue;
                }
                String rest = val.substring(sep + 1);
                int addrSep = rest.indexOf('|');
                String addr;
                LoadMeter.Snapshot load = null;
                if (addrSep < 0) {
                    addr = rest;
                } else {
                    addr = rest.substring(0, addrSep);
                    String[] tail = rest.substring(addrSep + 1).split("\\|", 4);
                    // tail may be 1-4 fields; pad to [active, inflow, outflow, cpu]
                    String[] padded = new String[4];
                    java.util.Arrays.fill(padded, "0");
                    System.arraycopy(tail, 0, padded, 0, Math.min(tail.length, 4));
                    load = LoadMeter.Snapshot.parseLoad(padded);
                }
                out.put(id, new InstanceInfo(addr, load));
            } catch (NumberFormatException ignored) {
                // malformed heartbeat — skip
            }
        }
        return out;
    }

    /** An instance's advertised address + its latest load snapshot (null if the peer reports none). */
    public static final class InstanceInfo {
        public final String address;
        public final LoadMeter.Snapshot load;

        public InstanceInfo(String address, LoadMeter.Snapshot load) {
            this.address = address;
            this.load = load;
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
