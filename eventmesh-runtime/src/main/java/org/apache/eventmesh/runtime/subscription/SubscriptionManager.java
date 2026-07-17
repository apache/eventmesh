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

package org.apache.eventmesh.runtime.subscription;

import org.apache.eventmesh.api.storage.MeshStoragePlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * EventMesh's self-managed subscription dispatcher — the heart of the uni architecture.
 *
 * <p>Unlike the legacy model (which delegates distribution to a MQ consumer group), this component
 * pulls CloudEvents from {@link MeshStoragePlugin#poll} and decides — per event, per the
 * subscription rules — which subscribers receive it (§4). Distribution modes:
 * {@link DistributionMode#LOAD_BALANCE} (round-robin one subscriber),
 * {@link DistributionMode#BROADCAST} (all subscribers),
 * {@link DistributionMode#MULTICAST} (filter-matched subscribers),
 * {@link DistributionMode#LOAD_BALANCE_STICKY} (hash-of-partition-key one subscriber, §13.3.3).</p>
 *
 * <p>Phase 2 scope: single-instance dispatch logic, push→pull wiring against the storage plugin,
 * and heartbeat-based subscriber liveness. Multi-instance coordination (partition assignment,
 * cross-instance forwarding, cluster-wide subscription view) is Phase 2.5; offset persistence is
 * Phase 5.5. Those phases layer on top of this class without changing its dispatch contract.</p>
 */
@Slf4j
public class SubscriptionManager {

    /** Default subscriber idle timeout before a subscription is pruned (90s). */
    public static final long DEFAULT_MAX_IDLE_MS = 90_000L;

    private final ConcurrentHashMap<String, Set<Subscription>> topicSubscriptions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Subscription> subscriptionsById = new ConcurrentHashMap<>();
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    private final long maxIdleMs;
    private final LongSupplier clock;

    public SubscriptionManager() {
        this(DEFAULT_MAX_IDLE_MS, System::currentTimeMillis);
    }

    /**
     * @param maxIdleMs subscriber idle timeout for liveness pruning
     * @param clock     monotonic-ish time source (injectable for tests)
     */
    public SubscriptionManager(long maxIdleMs, LongSupplier clock) {
        this.maxIdleMs = maxIdleMs;
        this.clock = clock;
    }

    /**
     * Register a subscription.
     *
     * @return the new subscription id
     */
    public String subscribe(String topic, String clientId, DistributionMode mode,
        CloudEventFilter filter, Consumer<CloudEvent> handler) {
        String subId = UUID.randomUUID().toString();
        Subscription sub = new Subscription(subId, clientId, topic, mode, filter, handler, clock.getAsLong());
        topicSubscriptions.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet()).add(sub);
        subscriptionsById.put(subId, sub);
        return subId;
    }

    /**
     * Remove a subscription by id.
     *
     * @return true if a subscription was removed
     */
    public boolean unsubscribe(String subscriptionId) {
        Subscription sub = subscriptionsById.remove(subscriptionId);
        if (sub == null) {
            return false;
        }
        Set<Subscription> subs = topicSubscriptions.get(sub.getTopic());
        if (subs != null) {
            subs.remove(sub);
        }
        return true;
    }

    /**
     * Remove a client's subscription to one topic (any mode). Used by the HTTP {@code /events/unsubscribe}
     * endpoint so a client can unsubscribe from a specific topic without dropping its other subscriptions.
     *
     * @return true if a subscription was removed
     */
    public boolean unsubscribe(String topic, String clientId) {
        Set<Subscription> subs = topicSubscriptions.get(topic);
        if (subs == null) {
            return false;
        }
        Subscription toRemove = null;
        for (Subscription s : subs) {
            if (clientId.equals(s.getClientId())) {
                toRemove = s;
                break;
            }
        }
        if (toRemove == null) {
            return false;
        }
        subs.remove(toRemove);
        subscriptionsById.remove(toRemove.getSubscriptionId());
        return true;
    }

    /**
     *  @return all topics a client is subscribed to (for cluster-wide unsubscribeByClient propagation). 
     */
    public Set<String> topicsForClient(String clientId) {
        Set<String> topics = new java.util.HashSet<>();
        for (Map.Entry<String, Set<Subscription>> entry : topicSubscriptions.entrySet()) {
            for (Subscription s : entry.getValue()) {
                if (clientId.equals(s.getClientId())) {
                    topics.add(entry.getKey());
                    break;
                }
            }
        }
        return topics;
    }

    /**
     * Look up a subscription by id without removing it (used by the ingress to propagate a cluster
     * unsubscribe, which needs the topic + clientId).
     */
    public Subscription getSubscription(String subscriptionId) {
        return subscriptionsById.get(subscriptionId);
    }

    /**
     * Refresh a subscription's heartbeat so it is not pruned as idle.
     */
    public boolean heartbeat(String subscriptionId) {
        Subscription sub = subscriptionsById.get(subscriptionId);
        if (sub == null) {
            return false;
        }
        sub.refreshHeartbeat(clock.getAsLong());
        return true;
    }

    /**
     * Pull a batch from the storage plugin and dispatch each event to the matching subscribers.
     *
     * @return the number of events pulled (and considered for dispatch)
     */
    public int pollAndDispatch(String topic, MeshStoragePlugin storage, int maxEvents, long timeoutMs) {
        List<CloudEvent> events = storage.poll(topic, -1, -1, maxEvents, timeoutMs);
        if (events == null || events.isEmpty()) {
            return 0;
        }
        for (CloudEvent event : events) {
            dispatch(topic, event);
        }
        return events.size();
    }

    /**
     * Select the subscribers that should receive {@code event} on {@code topic}, applying the
     * distribution mode and pruning expired subscriptions. Exposed so an external reliability layer
     * ({@code ReliableDispatcher}) can own delivery/ACK while this class owns routing.
     */
    public List<Subscription> targetsFor(String topic, CloudEvent event) {
        return selectTargets(topic, event);
    }

    /**
     * Remove every subscription belonging to {@code clientId} (admin client-eviction, §13.5.4).
     *
     * @return the number of subscriptions removed
     */
    public int unsubscribeByClient(String clientId) {
        int removed = 0;
        for (Set<Subscription> subs : topicSubscriptions.values()) {
            for (Subscription s : subs) {
                if (s.getClientId().equals(clientId)) {
                    removeInternal(s);
                    removed++;
                }
            }
        }
        return removed;
    }

    /**
     * Topics that currently have at least one subscription — the set the runtime pull-loop polls.
     */
    public Set<String> activeTopics() {
        return topicSubscriptions.entrySet().stream()
            .filter(e -> !e.getValue().isEmpty())
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Snapshot of active (non-expired) subscriptions for a topic. Mainly for inspection/tests.
     */
    public List<Subscription> activeSubscriptions(String topic) {
        Set<Subscription> subs = topicSubscriptions.get(topic);
        if (subs == null || subs.isEmpty()) {
            return Collections.emptyList();
        }
        long now = clock.getAsLong();
        List<Subscription> active = new ArrayList<>(subs.size());
        for (Subscription s : subs) {
            if (!s.isExpired(now, maxIdleMs)) {
                active.add(s);
            }
        }
        return active;
    }

    private void dispatch(String topic, CloudEvent event) {
        List<Subscription> targets = selectTargets(topic, event);
        if (targets.isEmpty()) {
            return;
        }
        for (Subscription target : targets) {
            deliver(target, event);
        }
    }

    private List<Subscription> selectTargets(String topic, CloudEvent event) {
        Set<Subscription> subs = topicSubscriptions.get(topic);
        if (subs == null || subs.isEmpty()) {
            return Collections.emptyList();
        }
        long now = clock.getAsLong();

        // Build the live list, pruning expired subscriptions lazily.
        List<Subscription> active = new ArrayList<>(subs.size());
        List<Subscription> expired = null;
        for (Subscription s : subs) {
            if (s.isExpired(now, maxIdleMs)) {
                if (expired == null) {
                    expired = new ArrayList<>();
                }
                expired.add(s);
            } else {
                active.add(s);
            }
        }
        if (expired != null) {
            for (Subscription s : expired) {
                removeInternal(s);
                log.debug("pruned expired subscription {} on topic {}", s.getSubscriptionId(), topic);
            }
        }
        if (active.isEmpty()) {
            return Collections.emptyList();
        }

        // Route by the mode of the active set. When a topic is shared by subscribers in different
        // modes, the mode of the first live subscriber wins (documented limitation for Phase 2;
        // mixed-mode topics are not a target use case).
        switch (active.get(0).getMode()) {
            case BROADCAST:
                return active;
            case MULTICAST:
                List<Subscription> matched = new ArrayList<>(active.size());
                for (Subscription s : active) {
                    if (s.getFilter().match(event)) {
                        matched.add(s);
                    }
                }
                return matched;
            case LOAD_BALANCE_STICKY:
                return Collections.singletonList(active.get(stickyIndex(event, active.size())));
            case LOAD_BALANCE:
            default:
                return Collections.singletonList(active.get(nextIndex(active.size())));
        }
    }

    private void deliver(Subscription target, CloudEvent event) {
        try {
            target.getHandler().accept(event);
        } catch (RuntimeException e) {
            // A single broken delivery must not abort the batch or starve other subscribers.
            log.warn("delivery to subscription {} failed: {}", target.getSubscriptionId(), e.toString());
        }
    }

    private int nextIndex(int size) {
        // Math.abs(Integer.MIN_VALUE) is negative; mask instead.
        return (roundRobinCounter.getAndIncrement() & 0x7fffffff) % size;
    }

    private int stickyIndex(CloudEvent event, int size) {
        Object key = event.getExtension("partitionkey");
        int hash = key == null ? nextIndex(size) : key.hashCode();
        return Math.floorMod(hash, size);
    }

    private void removeInternal(Subscription sub) {
        subscriptionsById.remove(sub.getSubscriptionId());
        Set<Subscription> subs = topicSubscriptions.get(sub.getTopic());
        if (subs != null) {
            subs.remove(sub);
        }
    }
}

