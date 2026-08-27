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

package org.apache.eventmesh.runtime.state;

import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.subscription.DistributionMode;

import java.util.List;
import java.util.Set;

/**
 * Cluster-shared subscription registry (issue #5301 §SubscriptionStore).
 *
 * <p>Subscriptions live under {@code /em/subs/<topic>/<clientId>}. Every instance watches that
 * prefix so a message pulled on the partition-owner instance can be dispatched to a subscriber
 * that registered on a different instance — this is what makes "subscribe on A, pull on B"
 * deliver in the {@code LOCAL_STICKY_PULL} topology.</p>
 *
 * <p>The default implementation is Meta-backed ({@code ClusterSubscriptionStore} in
 * {@code org.apache.eventmesh.runtime.cluster}). Tests use an in-process implementation that
 * keeps state in a {@code ConcurrentHashMap}. The two implementations are equivalent in their
 * public contract; this interface is the seam for swapping.</p>
 *
 * <p>Subscription state is updated via plain {@code put} (not CAS): the latest write wins, and
 * subscribers are expected to send keep-alive heartbeats so a stale entry from a dead client is
 * overwritten by a reconnect. See issue #5301 for the wider design context.</p>
 */
public interface SubscriptionStore {

    /**
     * Register (or update) a subscription cluster-wide.
     *
     * @param topic       the topic the subscriber is interested in
     * @param clientId    the subscriber's identifier
     * @param instanceId  the instance the subscriber currently lives on
     * @param mode        the distribution mode for the matching events
     * @param filterSpec  CloudEvents filter spec; {@code null} or empty means "match everything"
     */
    void put(String topic, String clientId, String instanceId, DistributionMode mode, String filterSpec);

    /**
     * Remove a subscription cluster-wide.
     *
     * @return true if a subscription was removed
     */
    boolean remove(String topic, String clientId);

    /**
     * All subscribers (across instances) on a topic whose filter matches {@code event}, after
     * applying the distribution mode. Simple per-topic round-robin for {@code LOAD_BALANCE} is
     * the caller's responsibility.
     */
    List<org.apache.eventmesh.runtime.cluster.ClusterSub> targetsFor(String topic, EventMeshFrame event);

    /**
     * Which instance a clientId currently lives on (for routing decisions on the partition-owner
     * instance). Returns {@code null} if the client is not subscribed cluster-wide.
     */
    String instanceOf(String clientId);

    /**
     * All topics with at least one registered subscription.
     */
    Set<String> topics();
}
