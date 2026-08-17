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

import org.apache.eventmesh.runtime.subscription.DistributionMode;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


import lombok.extern.slf4j.Slf4j;

/**
 * Routes a CloudEvent to the right instance per the cluster subscription view (§13.2.5 / §13.2.6).
 *
 * <p>On dispatch the coordinator asks {@link ClusterSubscriptionStore} for the matching subscribers
 * (cluster-wide), applies the distribution mode, then for each selected subscriber either delivers
 * locally (subscriber's instance == self) or forwards to its home instance. This is the piece that
 * makes "subscribe on A, pull-and-dispatch on B (the partition owner)" still reach the subscriber
 * on A.</p>
 */
@Slf4j
public class ClusterCoordinator {

    private final String selfInstanceId;
    private final ClusterSubscriptionStore subscriptions;
    private final LocalDeliverer localDeliverer;
    private final Forwarder forwarder;
    private final AtomicInteger roundRobin = new AtomicInteger(0);

    public ClusterCoordinator(String selfInstanceId, ClusterSubscriptionStore subscriptions,
        LocalDeliverer localDeliverer, Forwarder forwarder) {
        this.selfInstanceId = selfInstanceId;
        this.subscriptions = subscriptions;
        this.localDeliverer = localDeliverer;
        this.forwarder = forwarder;
    }

    /**
     * Register a subscription on this instance (cluster-wide).
     */
    public void subscribe(String topic, String clientId, DistributionMode mode, String filterSpec) {
        subscriptions.put(topic, clientId, selfInstanceId, mode, filterSpec);
    }

    public void unsubscribe(String topic, String clientId) {
        subscriptions.remove(topic, clientId);
    }

    /**
     * Topics with any cluster-wide (possibly remote) subscriber. An instance must pull/assign such a
     * topic even when it has no local subscriber, so messages landing on its partitions can be
     * forwarded to the remote subscriber (§13.2.5: "subscribe on A, pull-and-dispatch on B").
     */
    public java.util.Set<String> subscriptionTopics() {
        return subscriptions.topics();
    }

    /**
     * Route {@code event} to its subscribers across the cluster.
     *
     * @return number of subscribers the event was handed to (locally or forwarded)
     */
    public int dispatch(String topic, org.apache.eventmesh.common.wire.EventMeshFrame event) {
        List<ClusterSub> targets = subscriptions.targetsFor(topic, event);
        if (targets.isEmpty()) {
            return 0;
        }
        List<ClusterSub> selected = selectByMode(targets, event);
        int delivered = 0;
        for (ClusterSub target : selected) {
            if (selfInstanceId.equals(target.getInstanceId())) {
                if (localDeliverer.deliver(topic, target.getClientId(), event)) {
                    delivered++;
                }
            } else {
                if (forwarder.forward(target.getInstanceId(), target.getClientId(), topic, event)) {
                    delivered++;
                }
            }
        }
        return delivered;
    }

    private List<ClusterSub> selectByMode(List<ClusterSub> targets, org.apache.eventmesh.common.wire.EventMeshFrame event) {
        DistributionMode mode = targets.get(0).getMode();
        switch (mode) {
            case LOAD_BALANCE_STICKY: {
                // §13.3.3: stable hash(partitionkey) → one subscriber, so the same key always lands
                // on the same worker across the whole cluster (order preserved). Sort by clientId
                // first so every instance computes the same index for the same key/subscriber-set.
                java.util.List<ClusterSub> ordered = new java.util.ArrayList<>(targets);
                ordered.sort(java.util.Comparator.comparing(ClusterSub::getClientId));
                String key = event.attributes().get("partitionkey");
                int idx = (key == null)
                    ? (roundRobin.getAndIncrement() & 0x7fffffff) % ordered.size()
                    : Math.floorMod(key.hashCode(), ordered.size());
                return java.util.Collections.singletonList(ordered.get(idx));
            }
            case LOAD_BALANCE: {
                int idx = (roundRobin.getAndIncrement() & 0x7fffffff) % targets.size();
                return java.util.Collections.singletonList(targets.get(idx));
            }
            case BROADCAST:
            case MULTICAST:
            default:
                return targets;
        }
    }
}
