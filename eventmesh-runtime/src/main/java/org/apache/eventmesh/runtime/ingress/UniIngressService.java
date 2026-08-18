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
    private volatile org.apache.eventmesh.runtime.cluster.ClusterCoordinator cluster;
    private volatile org.apache.eventmesh.runtime.cluster.PartitionOwnership partitionOwnership;
    /** Self-collected load metrics for session-distribution load balancing (§3). Null until wired. */
    private volatile LoadMeter loadMeter;

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
        if (loadMeter != null && event.getData() != null) {
            loadMeter.recordInflow(event.getData().toBytes().length);
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        TokenBucketRateLimiter limiter = topicLimiters.get(topic);
        if (limiter != null && !limiter.tryAcquire()) {
            metrics.incRateLimited();
            future.completeExceptionally(new RateLimitedException(topic));
            return future;
        }
        try {
            // Ingress boundary: CloudEvent (the public/external format) → EventMeshFrame (the internal
            // wire unit). From here on the event is a Frame internally; egress converts back to the
            // client's protocol (CloudEvents / MeshMessage) at the delivery boundary.
            org.apache.eventmesh.common.wire.EventMeshFrame frame =
                org.apache.eventmesh.common.wire.EventMeshFrame.fromCloudEvent(event);
            storage.send(topic, frame, new SendCallback() {
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
     * Publish an already-internal {@link org.apache.eventmesh.common.wire.EventMeshFrame} (no
     * CloudEvent→Frame boundary conversion — the caller built the frame directly, e.g. the legacy
     * MeshMessage TCP path via {@code MeshMessageFrameCodec}). Completes when storage acks the write.
     */
    public CompletableFuture<Void> publish(String topic, org.apache.eventmesh.common.wire.EventMeshFrame frame) {
        if (loadMeter != null && frame.data() != null) {
            loadMeter.recordInflow(frame.data().length);
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        TokenBucketRateLimiter limiter = topicLimiters.get(topic);
        if (limiter != null && !limiter.tryAcquire()) {
            metrics.incRateLimited();
            future.completeExceptionally(new RateLimitedException(topic));
            return future;
        }
        try {
            storage.send(topic, frame, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    metrics.incPublish();
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
        // Pull EventMeshFrames (internal wire); dispatch each as a Frame through the internal
        // pipeline (filter/dispatcher/egress all carry Frame now; egress converts to the client's
        // protocol at the wire boundary).
        List<org.apache.eventmesh.common.wire.EventMeshFrame> frames = storage.poll(topic, partition, -1, maxEvents, timeoutMs);
        if (frames == null || frames.isEmpty()) {
            return 0;
        }
        long start = System.nanoTime();
        for (org.apache.eventmesh.common.wire.EventMeshFrame f : frames) {
            if (isExpired(f)) {
                // §13.3.4 TTL: drop expired events instead of dispatching.
                log.debug("dropping expired event {} on topic {} (emttl elapsed)", f.attributes().get("id"), topic);
                continue;
            }
            io.opentelemetry.api.trace.Span dispatchSpan = UniTrace.startDispatch(topic, f);
            if (cluster != null) {
                // Multi-instance: route via the cluster coordinator (local vs cross-instance forward).
                cluster.dispatch(topic, f);
            } else {
                // develop's OffsetExtensions (CE-extension-carried MQ offset) is superseded by the
                // Frame architecture: the POP check key rides in frame attributes (empopck) and the
                // deferred broker ACK fires on client ACK — same at-least-once goal, Frame-native.
                long offset = nextOffset(topic);
                // P2 fix: if the frame carries a POP check key (RocketMQ 5.x deferred ACK), build a
                // callback that ACKs the broker on client ACK (restoring at-least-once).
                String popCk = f.attributes().get("empopck");
                Runnable mqAck = (popCk != null) ? () -> storage.ackPulledMessage(topic, popCk) : null;
                for (Subscription target : subscriptionManager.targetsFor(topic, f)) {
                    dispatcher.deliver(topic, partition, offset, f, target.getClientId(),
                        channelFor(target.getClientId()), mqAck);
                }
            }
            UniTrace.end(dispatchSpan);
        }
        metrics.addDispatchLatencyNanos(System.nanoTime() - start);
        metrics.incDispatched(frames.size());
        return frames.size();
    }

    /**
     * TTL expiry check (§13.3.4): an event with an {@code emttl} attribute (ms) and a {@code time}
     * is expired when {@code now > time + emttl}. Events without either field never expire. Reads the
     * attributes off the internal EventMeshFrame (emttl/time are preserved in its KV section).
     */
    private boolean isExpired(org.apache.eventmesh.common.wire.EventMeshFrame event) {
        String ttl = event.attributes().get("emttl");
        String time = event.attributes().get("time");
        if (ttl == null || time == null) {
            return false;
        }
        try {
            long ttlMs = Long.parseLong(ttl);
            long eventTime = java.time.OffsetDateTime.parse(time).toInstant().toEpochMilli();
            return System.currentTimeMillis() > eventTime + ttlMs;
        } catch (NumberFormatException | java.time.format.DateTimeParseException e) {
            return false;
        }
    }

    /**
     * Local delivery for one subscriber — handed to the reliability layer + the subscriber's push
     * channel. Exposed so a {@link org.apache.eventmesh.runtime.cluster.ClusterCoordinator} can route
     * same-instance targets here while forwarding remote ones.
     */
    public boolean deliverLocal(String topic, String clientId, org.apache.eventmesh.common.wire.EventMeshFrame event) {
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
            // Boundary: CloudEvent → EventMeshFrame (internal wire unit).
            org.apache.eventmesh.common.wire.EventMeshFrame frame =
                org.apache.eventmesh.common.wire.EventMeshFrame.fromCloudEvent(event);
            ((org.apache.eventmesh.api.storage.LiteTopicCapable) storage).sendLite(parentTopic, liteTopic, frame,
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
     * Frames pulled from the LMQ are decoded back to CloudEvents at this boundary. Empty list if lite
     * is not supported.
     */
    public List<CloudEvent> pollLite(String parentTopic, String liteTopic, int maxEvents, long timeoutMs) {
        if (!(storage instanceof org.apache.eventmesh.api.storage.LiteTopicCapable)) {
            return java.util.Collections.emptyList();
        }
        java.util.List<CloudEvent> out = new java.util.ArrayList<>();
        for (org.apache.eventmesh.common.wire.EventMeshFrame f
            : ((org.apache.eventmesh.api.storage.LiteTopicCapable) storage).pullLite(parentTopic, liteTopic, maxEvents, timeoutMs)) {
            out.add(f.toCloudEvent());
        }
        return out;
    }

    /**
     * Publish a pre-encoded EventMeshFrame byte payload to a lite topic (the internal streaming wire
     * path — SessionRouter publishes frame bytes). The payload IS an encoded EventMeshFrame; decode
     * to the Frame object the SPI now expects.
     */
    public CompletableFuture<Void> publishLiteBytes(String parentTopic, String liteTopic, byte[] payload) {
        if (loadMeter != null) {
            loadMeter.recordInflow(payload.length);
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (!(storage instanceof org.apache.eventmesh.api.storage.LiteTopicCapable)) {
            future.completeExceptionally(new UnsupportedOperationException("storage does not support lite topic"));
            return future;
        }
        try {
            org.apache.eventmesh.common.wire.EventMeshFrame frame = org.apache.eventmesh.common.wire.EventMeshFrame.decode(payload);
            ((org.apache.eventmesh.api.storage.LiteTopicCapable) storage).sendLite(parentTopic, liteTopic, frame,
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
     * Pull a batch of pre-encoded EventMeshFrame byte payloads from a lite topic (the byte counterpart
     * of {@link #pollLite}, for the internal streaming wire). Each entry is an encoded frame.
     * Empty list if lite is not supported.
     */
    public List<byte[]> pollLiteBytes(String parentTopic, String liteTopic, int maxEvents, long timeoutMs) {
        if (!(storage instanceof org.apache.eventmesh.api.storage.LiteTopicCapable)) {
            return java.util.Collections.emptyList();
        }
        java.util.List<byte[]> out = new java.util.ArrayList<>();
        for (org.apache.eventmesh.common.wire.EventMeshFrame f
            : ((org.apache.eventmesh.api.storage.LiteTopicCapable) storage).pullLite(parentTopic, liteTopic, maxEvents, timeoutMs)) {
            out.add(f.encode());
        }
        return out;
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
     * Ensure {@code parentTopic} is lite-capable with the given {@code queueCount} and declare
     * {@code liteTopic} under it. Use {@code queueCount=1} for strict in-order delivery (e.g. a
     * streaming-call response channel). Throws if the storage does not support lite.
     */
    public void createLiteTopic(String parentTopic, String liteTopic, int queueCount) throws Exception {
        if (!(storage instanceof org.apache.eventmesh.api.storage.LiteTopicCapable)) {
            throw new UnsupportedOperationException("storage does not support lite topic");
        }
        ((org.apache.eventmesh.api.storage.LiteTopicCapable) storage).createLiteTopic(parentTopic, liteTopic, queueCount);
    }

    /**
     * @return true iff the storage plugin implements {@link org.apache.eventmesh.api.storage.LiteTopicCapable}.
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

    /** Wire the load meter; ingress/egress points call its record* methods. */
    public void withLoadMeter(LoadMeter loadMeter) {
        this.loadMeter = loadMeter;
    }

    /**
     * @return the wired load meter, or null if not configured (single-instance / tests).
     */
    public LoadMeter loadMeter() {
        return loadMeter;
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
     * @return the multi-instance partition ownership (null when clustering is disabled).
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
            "MQ end offset - max ACK offset (per topic/partition) — total consumer lag",
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
                    // Max ACK offset per partition across all clients (key = clientId#partition).
                    // Exclude the reserved __mqcursor__ key (MQ physical offset — different
                    // magnitude from the logical sequence numbers, mixing them corrupts the gauge).
                    java.util.Map<Integer, Long> maxAckByPart = new java.util.HashMap<>();
                    String reservedPrefix =
                        org.apache.eventmesh.runtime.delivery.ReliableDispatcher.MQ_CURSOR_CLIENT + "#";
                    for (java.util.Map.Entry<String, Long> e : offsetStore.readAllOffsets(t).entrySet()) {
                        if (e.getKey().startsWith(reservedPrefix)) {
                            continue;
                        }
                        int sep = e.getKey().lastIndexOf('#');
                        if (sep > 0) {
                            try {
                                int p = Integer.parseInt(e.getKey().substring(sep + 1));
                                maxAckByPart.merge(p, e.getValue(), Math::max);
                            } catch (NumberFormatException expected) {
                                // offset key suffix is not a numeric partition; skip
                            }
                        }
                    }
                    for (int p : owned) {
                        long end = storage.endOffset(t, p);
                        long ack = maxAckByPart.getOrDefault(p, -1L);
                        if (end >= 0 && ack >= 0) {
                            out.add(new UniMetrics.LabelledLong(
                                io.opentelemetry.api.common.Attributes.of(
                                    io.opentelemetry.api.common.AttributeKey.stringKey("topic"), t,
                                    io.opentelemetry.api.common.AttributeKey.longKey("partition"), (long) p),
                                Math.max(0, end - ack)));
                        }
                    }
                }
                return out;
            });

        metrics.registerLabelledGauge("eventmesh_push_ack_lag",
            "max push offset - max ACK offset (per topic/partition) — in-flight deliveries",
            () -> {
                java.util.List<UniMetrics.LabelledLong> out = new java.util.ArrayList<>();
                for (String t : subscriptionManager.activeTopics()) {
                    // Frame architecture: the MQ physical cursor per partition is recorded by the
                    // dispatcher on client ACK under the reserved key MQ_CURSOR_CLIENT (frame
                    // attribute emmqoffset, stamped by Kafka / RocketMQ-4.x at poll). Lag =
                    // storage endOffset (physical watermark) − recorded cursor. Per-subscriber
                    // entries hold logical sequence numbers and are excluded.
                    java.util.Map<Integer, Long> cursorByPart = new java.util.HashMap<>();
                    String cursorPrefix =
                        org.apache.eventmesh.runtime.delivery.ReliableDispatcher.MQ_CURSOR_CLIENT + "#";
                    for (java.util.Map.Entry<String, Long> e : offsetStore.readAllOffsets(t).entrySet()) {
                        if (!e.getKey().startsWith(cursorPrefix)) {
                            continue;
                        }
                        try {
                            int p = Integer.parseInt(e.getKey().substring(cursorPrefix.length()));
                            cursorByPart.merge(p, e.getValue(), Math::max);
                        } catch (NumberFormatException expected) {
                        }
                    }
                    for (java.util.Map.Entry<Integer, Long> cursorEntry : cursorByPart.entrySet()) {
                        int p = cursorEntry.getKey();
                        long end = storage.endOffset(t, p);
                        long cursor = cursorEntry.getValue();
                        if (end >= 0 && cursor >= 0) {
                            out.add(new UniMetrics.LabelledLong(
                                io.opentelemetry.api.common.Attributes.of(
                                    io.opentelemetry.api.common.AttributeKey.stringKey("topic"), t,
                                    io.opentelemetry.api.common.AttributeKey.longKey("partition"), (long) p),
                                Math.max(0, end - cursor)));
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

    /** Per-topic monotonic delivery sequence (EventMesh's logical offset for the dispatcher). */
    private long nextOffset(String topic) {
        return topicOffsetSeq.computeIfAbsent(topic, k -> new AtomicLong()).incrementAndGet();
    }

    private DeadLetterSink deadLetterSink() {
        return (originalTopic, event, reason, attempts) -> {
            String dlqTopic = originalTopic + "_DLQ";
            // Issue #5292: the dispatcher retires the delivery only once this future reports the
            // DLQ write as durably recorded by the storage plugin.
            java.util.concurrent.CompletableFuture<Boolean> future = new java.util.concurrent.CompletableFuture<>();
            try {
                // event is already an EventMeshFrame (internal); store it directly to the DLQ topic.
                storage.send(dlqTopic, event, new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                        log.info("event {} dead-lettered to {} after {} attempts: {}",
                            event.attributes().get("id"), dlqTopic, attempts, reason);
                        future.complete(Boolean.TRUE);
                    }

                    @Override
                    public void onException(org.apache.eventmesh.api.exception.OnExceptionContext context) {
                        log.error("failed to write DLQ event {} to {}", event.attributes().get("id"), dlqTopic, context.getException());
                        future.complete(Boolean.FALSE);
                    }
                });
            } catch (Exception e) {
                log.error("failed to send DLQ event {} to {}", event.attributes().get("id"), dlqTopic, e);
                future.complete(Boolean.FALSE);
            }
            return future;
        };
    }
}
