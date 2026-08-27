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

import org.apache.eventmesh.runtime.state.SubscriptionStore;
import org.apache.eventmesh.runtime.subscription.DistributionMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


import lombok.extern.slf4j.Slf4j;

/**
 * Cluster-wide subscription view backed by {@link MetaStore} (§13.2.6).
 *
 * <p>Subscriptions live under {@code /em/subs/<topic>/<clientId>}; every instance watches that
 * prefix and maintains a local cache, so a message pulled on the partition-owner instance can be
 * dispatched to a subscriber that registered on a different instance. This is what lets
 * "subscribe on A, pull on B" still deliver.</p>
 */
@Slf4j
public class ClusterSubscriptionStore implements SubscriptionStore {

    public static final String SUB_PREFIX = "/em/subs/";

    private final MetaStore meta;

    /** topic → clientId → ClusterSub (rebuilt from Meta on watch). */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ClusterSub>> cache = new ConcurrentHashMap<>();

    public ClusterSubscriptionStore(MetaStore meta) {
        this.meta = meta;
        // Seed the cache with the current Meta state, then keep it fresh via watch.
        for (Map.Entry<String, String> e : meta.getWithPrefix(SUB_PREFIX).entrySet()) {
            applyChange(e.getKey(), e.getValue(), false);
        }
        meta.watch(SUB_PREFIX, this::applyChange);
    }

    /**
     * Register a subscription cluster-wide.
     */
    public void put(String topic, String clientId, String instanceId, DistributionMode mode, String filterSpec) {
        ClusterSub sub = new ClusterSub(clientId, instanceId, mode, filterSpec);
        String k = key(topic, clientId);
        meta.put(k, sub.encode());
        // Update the local cache immediately — the MetaStore watch is for cross-instance propagation
        // and may not fire (Nacos ConfigService is per-dataId, not prefix-scan), so don't rely on it
        // to reflect this instance's own subscription back.
        applyChange(k, sub.encode(), false);
    }

    public boolean remove(String topic, String clientId) {
        String k = key(topic, clientId);
        boolean removed = meta.delete(k);
        applyChange(k, null, true);
        return removed;
    }

    /**
     * All subscribers (across instances) on a topic whose filter matches {@code event}, after
     * applying the distribution mode. Simple per-topic round-robin for LOAD_BALANCE is the caller's
     * responsibility (the coordinator picks one of the returned subscribers).
     */
    public List<ClusterSub> targetsFor(String topic, org.apache.eventmesh.common.wire.EventMeshFrame event) {
        ConcurrentHashMap<String, ClusterSub> subs = cache.get(topic);
        if (subs == null || subs.isEmpty()) {
            return new ArrayList<>();
        }
        List<ClusterSub> matched = new ArrayList<>();
        for (ClusterSub s : subs.values()) {
            if (s.getMode() == DistributionMode.BROADCAST || s.filter().match(event)) {
                matched.add(s);
            }
        }
        return matched;
    }

    /**
     * Which instance a clientId currently lives on (for forward-vs-local routing).
     */
    public String instanceOf(String clientId) {
        for (ConcurrentHashMap<String, ClusterSub> subs : cache.values()) {
            ClusterSub s = subs.get(clientId);
            if (s != null) {
                return s.getInstanceId();
            }
        }
        return null;
    }

    public Set<String> topics() {
        return new HashSet<>(cache.keySet());
    }

    private void applyChange(String key, String value, boolean deleted) {
        // key = /em/subs/<topic>/<clientId>
        String rest = key.substring(SUB_PREFIX.length());
        int sep = rest.indexOf('/');
        if (sep < 0) {
            return;
        }
        String topic = rest.substring(0, sep);
        String clientId = rest.substring(sep + 1);
        if (deleted) {
            ConcurrentHashMap<String, ClusterSub> subs = cache.get(topic);
            if (subs != null) {
                subs.remove(clientId);
            }
            return;
        }
        try {
            ClusterSub sub = ClusterSub.decode(value);
            cache.computeIfAbsent(topic, t -> new ConcurrentHashMap<>()).put(clientId, sub);
        } catch (RuntimeException e) {
            log.warn("ignoring malformed cluster subscription {}: {}", key, value);
        }
    }

    private static String key(String topic, String clientId) {
        return SUB_PREFIX + topic + "/" + clientId;
    }
}
