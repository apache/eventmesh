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

package org.apache.eventmesh.runtime.state.fault;

import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.cluster.ClusterSub;
import org.apache.eventmesh.runtime.state.SubscriptionStore;
import org.apache.eventmesh.runtime.subscription.DistributionMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process {@link SubscriptionStore} for issue #5314 scenario 4. Mirrors the contract of
 * {@code ClusterSubscriptionStore} without the Meta dependency, so two Runtime instances can
 * share the table directly and a test can simulate "two views of the world during a split" by
 * holding two different snapshots.
 *
 * <p>For split-brain simulation we use {@link MetaPartitionSwitch} — a single shared
 * table is enough because the test injects the partition between put and read.</p>
 */
public final class InMemorySubscriptionStore implements SubscriptionStore {

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ClusterSub>> table =
        new ConcurrentHashMap<>();

    @Override
    public void put(String topic, String clientId, String instanceId, DistributionMode mode, String filterSpec) {
        table.computeIfAbsent(topic, k -> new ConcurrentHashMap<>())
            .put(clientId, new ClusterSub(clientId, instanceId, mode, filterSpec));
    }

    @Override
    public boolean remove(String topic, String clientId) {
        ConcurrentHashMap<String, ClusterSub> bucket = table.get(topic);
        if (bucket == null) {
            return false;
        }
        return bucket.remove(clientId) != null;
    }

    @Override
    public List<ClusterSub> targetsFor(String topic, EventMeshFrame event) {
        ConcurrentHashMap<String, ClusterSub> bucket = table.get(topic);
        if (bucket == null) {
            return List.of();
        }
        List<ClusterSub> out = new ArrayList<>();
        for (ClusterSub sub : bucket.values()) {
            // Issue #5314 scenario 4 is about convergence, not filter semantics; accept-all
            // is fine here (the production filter() handles the rest).
            out.add(sub);
        }
        return out;
    }

    @Override
    public String instanceOf(String clientId) {
        for (ConcurrentHashMap<String, ClusterSub> bucket : table.values()) {
            ClusterSub sub = bucket.get(clientId);
            if (sub != null) {
                return sub.getInstanceId();
            }
        }
        return null;
    }

    @Override
    public Set<String> topics() {
        return new HashSet<>(table.keySet());
    }
}
