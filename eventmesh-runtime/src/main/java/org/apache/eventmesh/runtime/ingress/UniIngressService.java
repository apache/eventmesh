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

package org.apache.eventmesh.runtime.ingress;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.runtime.delivery.DeadLetterSink;
import org.apache.eventmesh.runtime.delivery.PushChannel;
import org.apache.eventmesh.runtime.delivery.ReliableDispatcher;
import org.apache.eventmesh.runtime.metrics.UniMetrics;
import org.apache.eventmesh.runtime.metrics.UniTrace;
import org.apache.eventmesh.runtime.offset.OffsetStore;
import org.apache.eventmesh.runtime.push.BufferedEvent;
import org.apache.eventmesh.runtime.push.LongPollingChannel;
import org.apache.eventmesh.runtime.push.PushService;
import org.apache.eventmesh.runtime.ratelimit.RateLimitedException;
import org.apache.eventmesh.runtime.ratelimit.TokenBucketRateLimiter;
import org.apache.eventmesh.runtime.subscription.CloudEventFilter;
import org.apache.eventmesh.runtime.subscription.DistributionMode;
import org.apache.eventmesh.runtime.subscription.Subscription;
import org.apache.eventmesh.runtime.subscription.SubscriptionManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * Facade that wires the uni-architecture layers into the end-to-end CloudEvents-over-MQ
 * flow (§6):
 * <pre>
 *   publish  ─▶ MeshStoragePlugin.send
 *   pullLoop ─▶ MeshStoragePlugin.poll ─▶ SubscriptionManager.targetsFor ─▶ ReliableDispatcher.deliver ─▶ PushService
 *   poll     ◀─ client long-polls PushService
 *   ack      ─▶ PushService.ack ─▶ ReliableDispatcher.ack ─▶ OffsetStore (offset advances only on ACK)
 * </pre>
 *
 * <p>This is the orchestration core; the actual HTTP endpoint wiring (§6 UniIngressHandler)
 * delegates here. Phase-1 thin storage adapter means {@code partition} is {@code -1} and the offset
 * is a per-topic monotonic logical counter until the native storage reimplementation supplies real
 * partition/offset (Phase 1 step 2).</p>
 */
@Slf4j
public class UniIngressService {

    private final MeshStoragePlugin storage;
    private final OffsetStore offsetStore;
    private final SubscriptionManager subscriptionManager;
    private final ReliableDispatcher dispatcher;
    private final PushService pushService;
    private org.apache.eventmesh.runtime.cluster.ClusterCoordinator cluster;
    private org.apache.eventmesh.runtime.cluster.PartitionOwnership partitionOwnership;

    /** Per-topic poll stats for the {@code poll_idle_ratio} gauge (§13.5.1). */
    private final ConcurrentHashMap<String, Long> pollCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> pollEmptyCount = new ConcurrentHashMap<>();

    /** Connector offset store (remote side) — String key → String offset value (§8.9). */
    private final java.util.concurrent.ConcurrentHashMap<String, String> connectorOffsets = new java.util.concurrent.ConcurrentHashMap<>();
    private final UniMetrics metrics;

    private final ConcurrentHashMap<String, PushChannel> channels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> topicOffsetSeq = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<CloudEvent>> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicLong requestSeq = new AtomicLong();
    private final ConcurrentHashMap<String, TokenBucketRateLimiter> topicLimiters = new ConcurrentHashMap<>();

    /**
     * CloudEvents extension carrying the request's correlation id (§17).
     *
     * <p>Named without hyphens because the CloudEvents spec restricts extension attribute names to
     * lower-case ASCII letters and digits — the redesign doc's {@code x-em-correlation-id} spelling
     * is rejected by the SDK's name validation.</p>
     */
    public static final String EXT_CORRELATION_ID = "emcorrelationid";

    public UniIngressService(MeshStoragePlugin storage, OffsetStore offsetStore) {
        this(storage, offsetStore, new SubscriptionManager(), new PushService(),
            ReliableDispatcher.DEFAULT_ACK_TIMEOUT_MS, ReliableDispatcher.DEFAULT_MAX_ATTEMPTS,
            System::currentTimeMillis);
    }

    /**
     * Test-friendly constructor with an injectable clock and retry parameters.
     */
    public UniIngressService(MeshStoragePlugin storage, OffsetStore offsetStore,
        SubscriptionManager subscriptionManager, PushService pushService,
        long ackTimeoutMs, int maxAttempts, java.util.function.LongSupplier clock) {
        this.storage = storage;
        this.offsetStore = offsetStore;
        this.subscriptionManager = subscriptionManager;
        this.pushService = pushService;
        this.metrics = new UniMetrics();
        this.dispatcher = new ReliableDispatcher(ackTimeoutMs, maxAttempts, clock, offsetStore, deadLetterSink(),
            metrics, ReliableDispatcher.DEFAULT_JITTER_RATIO);
    }

    // ---- connector offset (remote side, §8.9) ----

    public String getConnectorOffset(String connectorId) {
        return connectorOffsets.get(connectorId);
    }

    public void putConnectorOffset(String connectorId, String offset) {
        connectorOffsets.put(connectorId, offset);
    }

    /**
     * Publish a CloudEvent to {@code topic} (persisted to MQ). Completes when the storage plugin
     * acknowledges the write.
     */
    public CompletableFuture<Void> publish(String topic, CloudEvent event) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        TokenBucketRateLimiter limiter = topicLimiters.get(topic);
        if (limiter != null && !limiter.tryAcquire()) {
            metrics.incRateLimited();
            future.completeExceptionally(new RateLimitedException(topic));
            return future;
        }
        try {
            storage.send(topic, event, new SendCallback() {

                @Override
                public void onSuccess(SendResult sendResult) {
                    metrics.incPublish();
                    UniTrace.end(UniTrace.startPublish(topic, event));
                    future.complete(null);
                }

                @Override
                public void onException(org.apache.eventmesh.api.exception.OnExceptionContext context) {
                    metrics.incPublishFailed();
                    future.completeExceptionally(context.getException());
                }
            });
        } catch (Exception e) {
            metrics.incPublishFailed();
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * Publish a batch of CloudEvents to {@code topic} (§13.7.3). Completes when all are persisted.
     * The storage plugin's own batching (e.g. Kafka producer accumulator) amortises the per-event
     * RTT; this layer fans out the per-event futures and joins them.
     */
    public CompletableFuture<Void> publishBatch(String topic, java.util.List<CloudEvent> events) {
        if (events == null || events.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<?>[] futures = events.stream()
            .map(e -> publish(topic, e))
            .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    /**
     * Register a custom push channel for a client (e.g. a WebHook URL for legacy HTTP webhook-push
     * subscribers, or a {@code TcpPushChannel} for legacy TCP clients). Overrides the default
     * long-polling channel for this {@code clientId} on subsequent dispatches.
     */
    public void registerChannel(String clientId, PushChannel channel) {
        pushService.register(clientId);
        channels.put(clientId, channel);
    }

    /**
     * Configure a per-topic rate limit (§6.6). Subsequent publishes above {@code permitsPerSecond}
     * (burst {@code capacity}) fail the publish future with {@link RateLimitedException}.
     */
    public void setTopicRateLimit(String topic, long capacity, double permitsPerSecond) {
        topicLimiters.put(topic, new TokenBucketRateLimiter(capacity, permitsPerSecond));
    }

    /**
     * Register a subscription. The subscriber retrieves events via {@link #poll}.
     *
     * @return the subscription id
     */
    public String subscribe(String topic, String clientId, DistributionMode mode, CloudEventFilter filter) {
        pushService.register(clientId);
        String subId = subscriptionManager.subscribe(topic, clientId, mode, filter, event -> {
            // No-op: the reliability layer (ReliableDispatcher) owns delivery, not this fire-and-
            // forget callback. Kept to satisfy the SubscriptionManager handler contract.
        });
        // §13.2: when clustered, also register cluster-wide so other instances can route events
        // for this subscriber here (via ClusterCoordinator → HttpForwarder /internal/forward).
        // Without this the HTTP /events/subscribe path is local-only and a publish on a peer
        // never reaches this subscriber.
        if (cluster != null) {
            cluster.subscribe(topic, clientId, mode, null);
        }
        return subId;
    }

    /**
     * Remove a subscription.
     */
    public boolean unsubscribe(String subscriptionId) {
        // Resolve topic + clientId before removing so a clustered subscription can be deregistered
        // cluster-wide (the coordinator's unsubscribe is keyed by topic+clientId, not subId).
        org.apache.eventmesh.runtime.subscription.Subscription sub = subscriptionManager.getSubscription(subscriptionId);
        boolean removed = subscriptionManager.unsubscribe(subscriptionId);
        if (removed && cluster != null && sub != null) {
            cluster.unsubscribe(sub.getTopic(), sub.getClientId());
        }
        return removed;
    }

    /** Remove one client's subscription to one topic (HTTP /events/unsubscribe with {clientId, topic}). */
    public boolean unsubscribe(String topic, String clientId) {
        boolean removed = subscriptionManager.unsubscribe(topic, clientId);
        if (removed && cluster != null) {
            cluster.unsubscribe(topic, clientId);
        }
        return removed;
    }

    /** Remove ALL subscriptions for a client (HTTP /events/unsubscribe with {clientId} only).
     *  Propagates cluster-wide + frees the PushService buffer. */
    public int unsubscribeByClient(String clientId) {
        java.util.Set<String> topics = subscriptionManager.topicsForClient(clientId);
        int removed = subscriptionManager.unsubscribeByClient(clientId);
        if (cluster != null) {
            for (String topic : topics) {
                cluster.unsubscribe(topic, clientId);
            }
        }
        pushService.removeClient(clientId);
        return removed;
    }

    /**
     * Pump: pull a batch from storage, route to each target subscriber via the reliability layer.
     *
     * @return number of events pulled
     */
    public int pullAndDispatch(String topic, int maxEvents, long timeoutMs) {
        pollCount.merge(topic, 1L, Long::sum);
        // Multi-instance (§13.2.3): poll only the partitions this instance owns. Single-instance
        // (no ownership) or unknown partition count → poll the whole topic (partition -1).
        java.util.List<Integer> owned = partitionOwnership == null ? null : partitionOwnership.ownedPartitions(topic);
        int total;
        if (owned == null) {
            total = pullAndDispatchPartition(topic, -1, maxEvents, timeoutMs);
        } else if (owned.isEmpty()) {
            total = 0; // owns none -> do not poll (avoids duplicate with the real owners)
        } else {
            total = 0;
            for (int p : owned) {
                total += pullAndDispatchPartition(topic, p, maxEvents, timeoutMs);
            }
        }
        if (total == 0) {
            pollEmptyCount.merge(topic, 1L, Long::sum);
        }
        return total;
    }

    private int pullAndDispatchPartition(String topic, int partition, int maxEvents, long timeoutMs) {
        List<CloudEvent> events = storage.poll(topic, partition, -1, maxEvents, timeoutMs);
        if (events == null || events.isEmpty()) {
            return 0;
        }
        long start = System.nanoTime();
        for (CloudEvent event : events) {
            if (isExpired(event)) {
                // §13.3.4 TTL: drop expired events instead of dispatching.
                log.debug("dropping expired event {} on topic {} (emttl elapsed)", event.getId(), topic);
                continue;
            }
            io.opentelemetry.api.trace.Span dispatchSpan = UniTrace.startDispatch(topic, event);
            if (cluster != null) {
                // Multi-instance: route via the cluster coordinator (local vs cross-instance forward).
                cluster.dispatch(topic, event);
            } else {
                long offset = nextOffset(topic);
                for (Subscription target : subscriptionManager.targetsFor(topic, event)) {
                    dispatcher.deliver(topic, partition, offset, event, target.getClientId(), channelFor(target.getClientId()));
                }
            }
            UniTrace.end(dispatchSpan);
        }
        metrics.addDispatchLatencyNanos(System.nanoTime() - start);
        metrics.incDispatched(events.size());
        return events.size();
    }

    /**
     * TTL expiry check (§13.3.4): an event with an {@code emttl} extension (ms) and a {@code time}
     * is expired when {@code now > time + emttl}. Events without either field never expire.
     */
    private boolean isExpired(CloudEvent event) {
        Object ttl = event.getExtension("emttl");
        if (ttl == null || event.getTime() == null) {
            return false;
        }
        try {
            long ttlMs = Long.parseLong(ttl.toString());
            long eventTime = event.getTime().toInstant().toEpochMilli();
            return System.currentTimeMillis() > eventTime + ttlMs;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Local delivery for one subscriber — handed to the reliability layer + the subscriber's push
     * channel. Exposed so a {@link org.apache.eventmesh.runtime.cluster.ClusterCoordinator} can route
     * same-instance targets here while forwarding remote ones.
     */
    public boolean deliverLocal(String topic, String clientId, CloudEvent event) {
        long offset = nextOffset(topic);
        dispatcher.deliver(topic, -1, offset, event, clientId, channelFor(clientId));
        return true;
    }

    /**
     * Enable multi-instance coordination: when set, {@link #pullAndDispatch} routes each event
     * through the cluster coordinator (local targets via {@link #deliverLocal}, remote via forward).
     */
    public void withCluster(org.apache.eventmesh.runtime.cluster.ClusterCoordinator cluster) {
        this.cluster = cluster;
    }

    /**
     * Topics this instance should pull and partition-assign: local active topics UNION cluster-wide
     * topics (topics with a remote subscriber discovered via the Meta watch). Without the cluster
     * half, an instance with no local subscriber for a topic never pulls it, so messages on its
     * partitions can't be forwarded to the remote subscriber (multi-instance message loss). Returns
     * local-only when not clustered. Both {@code UniRuntime.pullLoop} and {@code PartitionOwnership}'s
     * topic source use this so the pull set and the assignment set stay consistent (otherwise an
     * unassigned cluster topic would degrade to poll-all and duplicate).
     */
    public java.util.Set<String> activeTopicsClustered() {
        java.util.Set<String> topics = new java.util.HashSet<>(getSubscriptionManager().activeTopics());
        if (cluster != null) {
            topics.addAll(cluster.subscriptionTopics());
        }
        return topics;
    }

    // ===================== Lite Topic (RIP-83, 5.x-only) =====================
    // Lite topic ops are exposed only when the storage plugin implements LiteTopicCapable; otherwise
    // they fail fast (UnsupportedOperationException). The HTTP layer (/events/lite/*) delegates here.

    /**
     * Publish one CloudEvent to a lite topic (parentTopic, liteTopic). The storage plugin routes it
     * into the lite topic's LMQ. Requires a {@link org.apache.eventmesh.api.storage.LiteTopicCapable}
     * storage (the 5.x plugin); 4.x/kafka/standalone storages throw.
     */
    public CompletableFuture<Void> publishLite(String parentTopic, String liteTopic, CloudEvent event) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (!(storage instanceof org.apache.eventmesh.api.storage.LiteTopicCapable)) {
            future.completeExceptionally(new UnsupportedOperationException("storage does not support lite topic"));
            return future;
        }
        try {
            ((org.apache.eventmesh.api.storage.LiteTopicCapable) storage).sendLite(parentTopic, liteTopic, event,
                new SendCallback() {

                    @Override
                    public void onSuccess(SendResult sendResult) {
                        future.complete(null);
                    }

                    @Override
                    public void onException(org.apache.eventmesh.api.exception.OnExceptionContext context) {
                        future.completeExceptionally(context.getException());
                    }
                });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * Pull a batch of CloudEvents from a lite topic (direct pull from the LMQ; no deliveryId / no
     * EventMesh reliability layer — the lite consumer self-manages offset in the storage plugin).
     * Empty list if lite is not supported.
     */
    public List<CloudEvent> pollLite(String parentTopic, String liteTopic, int maxEvents, long timeoutMs) {
        if (!(storage instanceof org.apache.eventmesh.api.storage.LiteTopicCapable)) {
            return java.util.Collections.emptyList();
        }
        return ((org.apache.eventmesh.api.storage.LiteTopicCapable) storage).pullLite(parentTopic, liteTopic,
            maxEvents, timeoutMs);
    }

    /**
     * Ensure {@code parentTopic} is lite-capable and declare {@code liteTopic} under it. Throws if the
     * storage does not support lite.
     */
    public void createLiteTopic(String parentTopic, String liteTopic) throws Exception {
        if (!(storage instanceof org.apache.eventmesh.api.storage.LiteTopicCapable)) {
            throw new UnsupportedOperationException("storage does not support lite topic");
        }
        ((org.apache.eventmesh.api.storage.LiteTopicCapable) storage).createLiteTopic(parentTopic, liteTopic);
    }

    /**
     *  @return true iff the storage plugin implements {@link org.apache.eventmesh.api.storage.LiteTopicCapable}. 
     */
    public boolean isLiteCapable() {
        return storage instanceof org.apache.eventmesh.api.storage.LiteTopicCapable;
    }

    /**
     * Enable multi-instance partition ownership (§13.2.3): when set, {@link #pullAndDispatch} polls
     * only this instance's owned partitions instead of the whole topic.
     */
    public void withPartitionOwnership(org.apache.eventmesh.runtime.cluster.PartitionOwnership ownership) {
        this.partitionOwnership = ownership;
    }

    /**
     * Subscriber long-polls its buffered deliveries.
     */
    public List<BufferedEvent> poll(String clientId, int maxEvents, long timeoutMs) {
        return pushService.poll(clientId, maxEvents, timeoutMs);
    }

    /**
     * Subscriber acknowledges a delivery — the offset advances only on ACK (at-least-once).
     */
    public boolean ack(String deliveryId) {
        return pushService.ack(deliveryId);
    }

    /**
     * Drive retry / DLQ. Call periodically from a scheduler.
     */
    public int dispatcherTick() {
        return dispatcher.tick();
    }

    /**
     * Operational metrics counters (publish/dispatch/ack/retry/DLQ). The dispatcher and ingress
     * share one instance.
     */
    public UniMetrics getMetrics() {
        return metrics;
    }

    // Accessors for the admin facade (Phase 7.5). The service is the single owner of these
    // collaborators; exposing them avoids reconstructing them out of band.
    public SubscriptionManager getSubscriptionManager() {
        return subscriptionManager;
    }

    public PushService getPushService() {
        return pushService;
    }

    public ReliableDispatcher getDispatcher() {
        return dispatcher;
    }

    /**
     *  @return the multi-instance partition ownership (null when clustering is disabled). 
     */
    public org.apache.eventmesh.runtime.cluster.PartitionOwnership getPartitionOwnership() {
        return partitionOwnership;
    }

    /**
     * Stale-poll cleanup (§13.6.5): evict clients that haven't polled within {@code thresholdMs} —
     * drops their subscriptions and push buffer so zombie subscriptions don't leak.
     *
     * @return number of subscriptions removed
     */
    public int cleanupStaleClients(long thresholdMs) {
        int removed = 0;
        for (String cid : pushService.getStaleClientIds(thresholdMs)) {
            removed += subscriptionManager.unsubscribeByClient(cid);
            pushService.removeClient(cid);
            log.info("evicted stale client {} (no poll within {}ms)", cid, thresholdMs);
        }
        return removed;
    }

    /**
     * Register OTel observable gauges backed by live runtime state (§13.5.1 gauges with *).
     * Call once at boot. Gauges read on each OTel collection cycle.
     */
    public void registerRuntimeGauges() {
        metrics.registerGauge("eventmesh_pending_queue_size", "total buffered events across all clients",
            () -> {
                long sum = 0;
                for (String cid : pushService.clientIds()) {
                    sum += pushService.pending(cid);
                }
                return sum;
            });
        metrics.registerGauge("eventmesh_slow_consumer_count", "clients in SLOW or STALLED state",
            pushService::slowConsumerCount);
        metrics.registerGauge("eventmesh_active_topics", "topics with active subscribers",
            () -> subscriptionManager.activeTopics().size());
        metrics.registerGauge("eventmesh_active_subscribers", "active subscriptions across all topics",
            () -> {
                int sum = 0;
                for (String t : subscriptionManager.activeTopics()) {
                    sum += subscriptionManager.activeSubscriptions(t).size();
                }
                return sum;
            });

        // Labelled gauges (§13.5.1) — emit one reading per topic / partition.
        metrics.registerLabelledGauge("eventmesh_poll_idle_ratio",
            "fraction of poll cycles returning no events (per-mille, per topic)",
            () -> {
                java.util.List<UniMetrics.LabelledLong> out = new java.util.ArrayList<>();
                for (String t : pollCount.keySet()) {
                    long total = pollCount.getOrDefault(t, 0L);
                    long empty = pollEmptyCount.getOrDefault(t, 0L);
                    long perMille = total == 0 ? 0 : Math.round((double) empty / total * 1000);
                    out.add(new UniMetrics.LabelledLong(
                        io.opentelemetry.api.common.Attributes.of(io.opentelemetry.api.common.AttributeKey.stringKey("topic"), t),
                        perMille));
                }
                return out;
            });

        metrics.registerLabelledGauge("eventmesh_partition_owner",
            "1 for each partition this instance owns (per topic/partition)",
            () -> {
                java.util.List<UniMetrics.LabelledLong> out = new java.util.ArrayList<>();
                if (partitionOwnership != null) {
                    for (String t : subscriptionManager.activeTopics()) {
                        java.util.List<Integer> owned = partitionOwnership.ownedPartitions(t);
                        if (owned == null) {
                            continue;
                        }
                        for (int p : owned) {
                            out.add(new UniMetrics.LabelledLong(
                                io.opentelemetry.api.common.Attributes.of(
                                    io.opentelemetry.api.common.AttributeKey.stringKey("topic"), t,
                                    io.opentelemetry.api.common.AttributeKey.longKey("partition"), (long) p,
                                    io.opentelemetry.api.common.AttributeKey.stringKey("instance"), "self"),
                                1L));
                        }
                    }
                }
                return out;
            });

        metrics.registerLabelledGauge("eventmesh_offset_lag",
            "MQ end offset - distributed offset (per topic/partition)",
            () -> {
                java.util.List<UniMetrics.LabelledLong> out = new java.util.ArrayList<>();
                if (partitionOwnership == null) {
                    return out;
                }
                for (String t : subscriptionManager.activeTopics()) {
                    java.util.List<Integer> owned = partitionOwnership.ownedPartitions(t);
                    if (owned == null) {
                        continue;
                    }
                    // Max distributed offset per partition across all clients (key = clientId#partition).
                    java.util.Map<Integer, Long> maxByPart = new java.util.HashMap<>();
                    for (java.util.Map.Entry<String, Long> e : offsetStore.readAllOffsets(t).entrySet()) {
                        int sep = e.getKey().lastIndexOf('#');
                        if (sep > 0) {
                            try {
                                int p = Integer.parseInt(e.getKey().substring(sep + 1));
                                maxByPart.merge(p, e.getValue(), Math::max);
                            } catch (NumberFormatException expected) {
                            }
                        }
                    }
                    for (int p : owned) {
                        long end = storage.endOffset(t, p);
                        long dist = maxByPart.getOrDefault(p, -1L);
                        if (end >= 0 && dist >= 0) {
                            out.add(new UniMetrics.LabelledLong(
                                io.opentelemetry.api.common.Attributes.of(
                                    io.opentelemetry.api.common.AttributeKey.stringKey("topic"), t,
                                    io.opentelemetry.api.common.AttributeKey.longKey("partition"), (long) p),
                                Math.max(0, end - dist)));
                        }
                    }
                }
                return out;
            });
    }

    public OffsetStore getOffsetStore() {
        return offsetStore;
    }

    public MeshStoragePlugin getStorage() {
        return storage;
    }

    /**
     * Synchronous request-reply (§17). Publishes {@code event}, blocks for the matching reply
     * keyed by the {@code x-em-correlation-id} extension, and returns it. On timeout the future is
     * failed and a late reply is discarded. Request-reply is independent of the at-least-once
     * pub/sub path: it neither retries nor dead-letters.
     *
     * @throws Exception if the request times out, publishing fails, or the reply errors
     */
    public CloudEvent request(String topic, CloudEvent event, long timeoutMs) throws Exception {
        String correlationId = readCorrelationId(event);
        CloudEvent toPublish = event;
        if (correlationId == null) {
            correlationId = "req-" + requestSeq.incrementAndGet();
            toPublish = CloudEventBuilder.from(event).withExtension(EXT_CORRELATION_ID, correlationId).build();
        }
        CompletableFuture<CloudEvent> future = new CompletableFuture<>();
        pendingRequests.put(correlationId, future);
        try {
            publish(topic, toPublish).get();
        } catch (Exception e) {
            pendingRequests.remove(correlationId);
            throw e;
        }
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            pendingRequests.remove(correlationId);
            throw new TimeoutException("request-reply timed out: " + correlationId);
        } catch (ExecutionException ee) {
            throw ee;
        } finally {
            metrics.incRequestReply();
        }
    }

    /**
     * Deliver a reply to a pending request. Returns false if the request was unknown, already
     * replied, or had timed out (late reply discarded).
     */
    public boolean reply(String correlationId, CloudEvent replyEvent) {
        CompletableFuture<CloudEvent> future = pendingRequests.remove(correlationId);
        if (future == null) {
            log.debug("late/unknown reply for correlationId={} discarded", correlationId);
            return false;
        }
        return future.complete(replyEvent);
    }

    private static String readCorrelationId(CloudEvent event) {
        Object value = event.getExtension(EXT_CORRELATION_ID);
        return value == null ? null : value.toString();
    }

    private PushChannel channelFor(String clientId) {
        return channels.computeIfAbsent(clientId, id -> new LongPollingChannel(pushService, id));
    }

    private long nextOffset(String topic) {
        return topicOffsetSeq.computeIfAbsent(topic, k -> new AtomicLong()).incrementAndGet();
    }

    private DeadLetterSink deadLetterSink() {
        return (originalTopic, event, reason, attempts) -> {
            String dlqTopic = originalTopic + "_DLQ";
            try {
                storage.send(dlqTopic, event, new SendCallback() {

                    @Override
                    public void onSuccess(SendResult sendResult) {
                        log.info("event {} dead-lettered to {} after {} attempts: {}",
                            event.getId(), dlqTopic, attempts, reason);
                    }

                    @Override
                    public void onException(org.apache.eventmesh.api.exception.OnExceptionContext context) {
                        log.error("failed to write DLQ event {} to {}", event.getId(), dlqTopic, context.getException());
                    }
                });
            } catch (Exception e) {
                log.error("failed to send DLQ event {} to {}", event.getId(), dlqTopic, e);
            }
        };
    }
}
