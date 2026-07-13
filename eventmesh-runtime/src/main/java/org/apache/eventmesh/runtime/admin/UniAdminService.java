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

package org.apache.eventmesh.runtime.admin;

import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.metrics.UniMetrics;
import org.apache.eventmesh.runtime.offset.OffsetStore;
import org.apache.eventmesh.runtime.push.PushService;
import org.apache.eventmesh.runtime.subscription.Subscription;
import org.apache.eventmesh.runtime.subscription.SubscriptionManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Admin operations over the uni runtime (§7.5 / §13.5.4). Backed by the cluster-wide view
 * exposed by {@link UniIngressService}: live subscriptions, distribution offsets, in-flight
 * delivery counts, client eviction, DLQ replay, and the metrics snapshot.
 *
 * <p>This is the logic layer; the HTTP admin handlers (when wired) delegate here. Multi-instance
 * aggregation (Phase 2.5) merges these views across instances via Meta; here they reflect the local
 * instance until that layer lands.</p>
 */
@Slf4j
public class UniAdminService {

    private final UniIngressService ingress;
    private final SubscriptionManager subscriptionManager;
    private final OffsetStore offsetStore;
    private final PushService pushService;
    private final MeshStoragePlugin storage;

    public UniAdminService(UniIngressService ingress) {
        this.ingress = ingress;
        this.subscriptionManager = ingress.getSubscriptionManager();
        this.offsetStore = ingress.getOffsetStore();
        this.pushService = ingress.getPushService();
        this.storage = ingress.getStorage();
    }

    public UniIngressService getIngress() {
        return ingress;
    }

    /**
     * Live (non-expired) subscriptions on a topic.
     */
    public List<Subscription> subscriptions(String topic) {
        return subscriptionManager.activeSubscriptions(topic);
    }

    /**
     * Distribution offsets for a topic, keyed by {@code clientId#partition}.
     */
    public Map<String, Long> offsets(String topic) {
        return offsetStore.readAllOffsets(topic);
    }

    /**
     * Currently in-flight (delivered, un-ACKed) deliveries.
     */
    public int pendingDeliveries() {
        return ingress.getDispatcher().pendingCount();
    }

    /**
     * Buffered-but-not-yet-polled events for a client.
     */
    public int clientPending(String clientId) {
        return pushService.pending(clientId);
    }

    /**
     * Evict a client: drop all its subscriptions and its push buffer (§13.5.4).
     *
     * @return number of subscriptions removed
     */
    public int rejectClient(String clientId) {
        int removed = subscriptionManager.unsubscribeByClient(clientId);
        pushService.removeClient(clientId);
        log.info("evicted client {}: {} subscriptions removed", clientId, removed);
        return removed;
    }

    /**
     * Drain up to {@code maxEvents} dead-lettered events for a topic and re-publish them to the
     * original topic. The DLQ topic is {@code <topic>_DLQ}.
     *
     * @return number of events replayed
     */
    public int dlqReplay(String topic, int maxEvents) {
        String dlqTopic = topic + "_DLQ";
        List<CloudEvent> dead = storage.poll(dlqTopic, -1, -1, maxEvents, 0);
        if (dead == null || dead.isEmpty()) {
            return 0;
        }
        CompletableFuture<?>[] futures = dead.stream()
            .map(e -> ingress.publish(topic, e))
            .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();
        return dead.size();
    }

    /**
     * Browse (not replay) dead-lettered event ids for a topic (§13.5.4). Reads {@code <topic>_DLQ}
     * without re-publishing.
     */
    public List<String> dlqBrowse(String topic, int maxEvents) {
        List<CloudEvent> dead = storage.poll(topic + "_DLQ", -1, -1, maxEvents, 0);
        if (dead == null || dead.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<String> ids = new java.util.ArrayList<>(dead.size());
        for (CloudEvent e : dead) {
            ids.add(e.getId());
        }
        return ids;
    }

    /** Push a per-topic rate-limit rule to this instance (§13.5.4 / §13.6.1). */
    public void setRateLimit(String topic, long capacity, double permitsPerSecond) {
        ingress.setTopicRateLimit(topic, capacity, permitsPerSecond);
    }

    /**
     * Operational metrics counters.
     */
    public UniMetrics metrics() {
        return ingress.getMetrics();
    }
}
