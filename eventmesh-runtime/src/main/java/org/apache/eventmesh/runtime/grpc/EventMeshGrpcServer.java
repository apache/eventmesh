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

package org.apache.eventmesh.runtime.grpc;

import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEventBatch;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.ConsumerServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.HeartbeatServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.PublisherServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.util.JsonUtils;
import org.apache.eventmesh.runtime.delivery.AckCallback;
import org.apache.eventmesh.runtime.delivery.CloudEventSerializer;
import org.apache.eventmesh.runtime.delivery.HttpCaller;
import org.apache.eventmesh.runtime.delivery.WebHookChannel;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.push.BufferedEvent;
import org.apache.eventmesh.runtime.push.PushService;
import org.apache.eventmesh.runtime.subscription.DistributionMode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.protobuf.Empty;

import lombok.extern.slf4j.Slf4j;

/**
 * The legacy SDK gRPC protocol compatibility bridge (issue #5411): serves
 * {@code org.apache.eventmesh.cloudevents.v1.PublisherService/ConsumerService/HeartbeatService} on
 * the reserved port {@code eventmesh.grpc.port} (1.x default 10205), mapping everything onto the
 * v2 ingress path - no second messaging engine, the same {@link UniIngressService} the HTTP plane
 * uses (WAL durability, at-least-once delivery, shared retry/DLQ).
 *
 * <p>Opt-in like the WS port: {@code eventmesh.grpc.port} unset / 0 = disabled. Graceful shutdown
 * with the application lifecycle.</p>
 *
 * <p>Mapping summary (compat table in docs/feature/protocols.md):</p>
 * <ul>
 *   <li>publish / batchPublish / publishOneWay / batchPublishOneWay -&gt; proto CloudEvent mapped to
 *       v2 and persisted via {@code UniIngressService.publish} (topic = proto {@code subject}).</li>
 *   <li>requestReply -&gt; the v2 request/reply correlation ({@code UniIngressService.request});
 *       the reply maps back to a proto CloudEvent. Legacy {@code Response} codes on errors.</li>
 *   <li>subscribe (webhook url) -&gt; a {@link WebHookChannel} push target + v2 subscriptions
 *       (LOAD_BALANCE for the SDK's default CLUSTERING, BROADCAST for BROADCASTING).</li>
 *   <li>subscribeStream (bidi) -&gt; a {@link GrpcStreamChannel} push target pumping the v2 push
 *       pipeline into the stream; client ACKs ride back as stream replies.</li>
 *   <li>heartbeat -&gt; refreshes the {@link GrpcClientRegistry} TTL; the reaper unsubscribes
 *       stale clients (same model as the agent plane).</li>
 * </ul>
 */
@Slf4j
public class EventMeshGrpcServer {

    /** Attribute key carrying the delivery id on stream pushes (client echoes it to ACK). */
    public static final String DELIVERY_ID_ATTR = "emdeliveryid";

    private final UniIngressService ingress;
    private final int port;
    private final HttpCaller httpCaller;
    private final CloudEventSerializer serializer;

    private Server server;
    private GrpcClientRegistry clientRegistry = new GrpcClientRegistry();
    private ScheduledExecutorService reaper;
    /** Pending stream deliveries: deliveryId -> ACK callback (fired on client stream ACK). */
    private final Map<String, AckCallback> pendingStreamAcks = new ConcurrentHashMap<>();
    /** Per-client stream channel (latest stream wins), for push targeting. */
    private final Map<String, GrpcStreamChannel> streamChannels = new ConcurrentHashMap<>();

    public EventMeshGrpcServer(UniIngressService ingress, int port,
        HttpCaller httpCaller, CloudEventSerializer serializer) {
        this.ingress = ingress;
        this.port = port;
        this.httpCaller = httpCaller;
        this.serializer = serializer;
    }

    /** Bind and start serving. Returns the actual bound port. */
    public int start() throws IOException {
        server = ServerBuilder.forPort(port)
            .addService(new PublisherImpl())
            .addService(new ConsumerImpl())
            .addService(new HeartbeatImpl())
            .build()
            .start();
        // Eviction reaper: same TTL model as the agent plane; unsubscribe dead gRPC clients.
        clientRegistry.addEvictionListener(clientId -> {
            ingress.unsubscribeByClient(clientId);
            GrpcStreamChannel channel = streamChannels.remove(clientId);
            if (channel != null) {
                channel.close();
            }
        });
        reaper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "em-grpc-reaper");
            t.setDaemon(true);
            return t;
        });
        reaper.scheduleAtFixedRate(clientRegistry::reapStale, 30, 30, TimeUnit.SECONDS);
        int bound = server.getPort();
        log.info("legacy gRPC bridge started on port {} ({} clients capacity)", bound,
            InetSocketAddress.createUnresolved("localhost", bound).getPort());
        return bound;
    }

    /** Graceful shutdown with the application lifecycle. */
    public void stop() {
        if (reaper != null) {
            reaper.shutdownNow();
        }
        if (server != null) {
            server.shutdown();
            try {
                if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                server.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("legacy gRPC bridge stopped");
    }

    public int port() {
        return server == null ? -1 : server.getPort();
    }

    // ------------------------------------------------------------------
    // PublisherService
    // ------------------------------------------------------------------

    private final class PublisherImpl extends PublisherServiceGrpc.PublisherServiceImplBase {

        @Override
        public void publish(CloudEvent request, StreamObserver<CloudEvent> responseObserver) {
            try {
                String topic = GrpcCloudEventMapper.topicOf(request);
                if (topic.isEmpty()) {
                    responseObserver.onNext(GrpcCloudEventMapper.errorResponse(
                        StatusCode.EVENTMESH_PROTOCOL_BODY_ERR, "missing subject(topic)"));
                    responseObserver.onCompleted();
                    return;
                }
                ingress.publish(topic, GrpcCloudEventMapper.toV2(request))
                    .whenComplete((v, err) -> {
                        if (err != null) {
                            responseObserver.onNext(GrpcCloudEventMapper.errorResponse(
                                StatusCode.EVENTMESH_SEND_ASYNC_MSG_ERR, err.toString()));
                        } else {
                            responseObserver.onNext(GrpcCloudEventMapper.okResponse());
                        }
                        responseObserver.onCompleted();
                    });
            } catch (RuntimeException e) {
                responseObserver.onNext(GrpcCloudEventMapper.errorResponse(
                    StatusCode.EVENTMESH_RUNTIME_ERR, e.toString()));
                responseObserver.onCompleted();
            }
        }

        @Override
        public void batchPublish(CloudEventBatch request, StreamObserver<CloudEvent> responseObserver) {
            publishBatchInternal(request, responseObserver, false);
        }

        @Override
        public void publishOneWay(CloudEvent request, StreamObserver<Empty> responseObserver) {
            String topic = GrpcCloudEventMapper.topicOf(request);
            if (topic.isEmpty()) {
                responseObserver.onError(new IllegalArgumentException("missing subject(topic)"));
                return;
            }
            ingress.publish(topic, GrpcCloudEventMapper.toV2(request));
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }

        @Override
        public void batchPublishOneWay(CloudEventBatch request, StreamObserver<Empty> responseObserver) {
            publishBatchInternal(request, null, true);
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }

        @SuppressWarnings("rawtypes")
        private void publishBatchInternal(CloudEventBatch request,
            StreamObserver<CloudEvent> responseObserver, boolean oneWay) {
            try {
                if (request.getEventsCount() == 0) {
                    if (!oneWay) {
                        responseObserver.onNext(GrpcCloudEventMapper.errorResponse(
                            StatusCode.EVENTMESH_PROTOCOL_BODY_ERR, "empty batch"));
                        responseObserver.onCompleted();
                    }
                    return;
                }
                String topic = GrpcCloudEventMapper.topicOf(request.getEvents(0));
                if (topic.isEmpty()) {
                    if (!oneWay) {
                        responseObserver.onNext(GrpcCloudEventMapper.errorResponse(
                            StatusCode.EVENTMESH_PROTOCOL_BODY_ERR, "missing subject(topic)"));
                        responseObserver.onCompleted();
                    }
                    return;
                }
                List<io.cloudevents.CloudEvent> events = request.getEventsList().stream()
                    .map(GrpcCloudEventMapper::toV2)
                    .collect(java.util.stream.Collectors.toList());
                CompletableFuture<Void> done = ingress.publishBatch(topic, events);
                if (oneWay) {
                    return;
                }
                done.whenComplete((v, err) -> {
                    if (err != null) {
                        responseObserver.onNext(GrpcCloudEventMapper.errorResponse(
                            StatusCode.EVENTMESH_BATCH_PUBLISH_ERR, err.toString()));
                    } else {
                        responseObserver.onNext(GrpcCloudEventMapper.okResponse());
                    }
                    responseObserver.onCompleted();
                });
            } catch (RuntimeException e) {
                if (!oneWay) {
                    responseObserver.onNext(GrpcCloudEventMapper.errorResponse(
                        StatusCode.EVENTMESH_RUNTIME_ERR, e.toString()));
                    responseObserver.onCompleted();
                }
            }
        }

        @Override
        public void requestReply(CloudEvent request, StreamObserver<CloudEvent> responseObserver) {
            try {
                String topic = GrpcCloudEventMapper.topicOf(request);
                if (topic.isEmpty()) {
                    responseObserver.onNext(GrpcCloudEventMapper.errorResponse(
                        StatusCode.EVENTMESH_PROTOCOL_BODY_ERR, "missing subject(topic)"));
                    responseObserver.onCompleted();
                    return;
                }
                io.cloudevents.CloudEvent reply = ingress.request(
                    topic, GrpcCloudEventMapper.toV2(request), ttlOf(request));
                responseObserver.onNext(GrpcCloudEventMapper.toProto(reply));
                responseObserver.onCompleted();
            } catch (Exception e) {
                responseObserver.onNext(GrpcCloudEventMapper.errorResponse(
                    StatusCode.EVENTMESH_REQUEST_REPLY_MSG_ERR, e.toString()));
                responseObserver.onCompleted();
            }
        }

        /** The legacy TTL attribute (ms) drives the request/reply timeout; default 10s like 1.x. */
        private long ttlOf(CloudEvent request) {
            String ttl = org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils
                .getTtl(request, "");
            try {
                return ttl.isEmpty() ? 10_000L : Long.parseLong(ttl);
            } catch (NumberFormatException e) {
                return 10_000L;
            }
        }
    }

    // ------------------------------------------------------------------
    // ConsumerService
    // ------------------------------------------------------------------

    private final class ConsumerImpl extends ConsumerServiceGrpc.ConsumerServiceImplBase {

        @Override
        public void subscribe(CloudEvent request, StreamObserver<CloudEvent> responseObserver) {
            try {
                String url = GrpcCloudEventMapper.urlOf(request);
                if (url.isEmpty() || "grpc_stream".equals(url)) {
                    // A unary subscribe without a webhook URL is invalid - stream subs go through
                    // subscribeStream.
                    responseObserver.onNext(GrpcCloudEventMapper.errorResponse(
                        StatusCode.EVENTMESH_SUBSCRIBE_ERR, "missing url (webhook)"));
                    responseObserver.onCompleted();
                    return;
                }
                String clientId = subscribeAll(request, new WebHookChannel(
                    url, webhookSecret(), httpCaller, serializer));
                responseObserver.onNext(GrpcCloudEventMapper.okResponse());
                responseObserver.onCompleted();
                log.info("grpc webhook subscriber: clientId={} url={}", clientId, url);
            } catch (RuntimeException e) {
                responseObserver.onNext(GrpcCloudEventMapper.errorResponse(
                    StatusCode.EVENTMESH_SUBSCRIBE_ERR, e.toString()));
                responseObserver.onCompleted();
            }
        }

        @Override
        public void unsubscribe(CloudEvent request, StreamObserver<CloudEvent> responseObserver) {
            try {
                String group = GrpcCloudEventMapper.consumerGroupOf(request);
                String env = org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils
                    .getEnv(request, "");
                String idc = org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils
                    .getIdc(request, "");
                String clientId = clientId(group, env, idc);
                Set<String> topics = topicsOf(request);
                int removed = 0;
                for (String topic : topics) {
                    if (ingress.unsubscribe(topic, clientId)) {
                        removed++;
                    }
                }
                clientRegistry.deregister(clientId);
                GrpcStreamChannel channel = streamChannels.remove(clientId);
                if (channel != null) {
                    channel.close();
                }
                responseObserver.onNext(GrpcCloudEventMapper.okResponse());
                responseObserver.onCompleted();
                log.info("grpc unsubscribe: clientId={} topics={} removed={}", clientId, topics, removed);
            } catch (RuntimeException e) {
                responseObserver.onNext(GrpcCloudEventMapper.errorResponse(
                    StatusCode.EVENTMESH_UNSUBSCRIBE_ERR, e.toString()));
                responseObserver.onCompleted();
            }
        }

        /**
         * subscribeStream (bidi): the first inbound message carries the subscription (same
         * envelope as unary subscribe); every later message is either a reply (SUB_REPLY_MESSAGE)
         * or an ACK ({@link #DELIVERY_ID_ATTR} echoed). Push goes through the v2 dispatcher onto
         * the {@link GrpcStreamChannel}.
         */
        @Override
        public StreamObserver<CloudEvent> subscribeStream(StreamObserver<CloudEvent> responseObserver) {
            return new StreamObserver<CloudEvent>() {

                private String clientId;

                @Override
                public void onNext(CloudEvent message) {
                    boolean isAck = message.getAttributesMap().containsKey(DELIVERY_ID_ATTR)
                        && !message.getAttributesMap().containsKey(ProtocolKey.SUB_MESSAGE_TYPE);
                    if (isAck) {
                        ackFromClient(message);
                        return;
                    }
                    if (clientId == null) {
                        // first message = the subscription envelope
                        GrpcStreamChannel channel = new GrpcStreamChannel(responseObserver);
                        clientId = subscribeAll(message, channel);
                        streamChannels.put(clientId, channel);
                        // acknowledge the subscription itself (1.x sent an ack envelope)
                        synchronized (responseObserver) {
                            responseObserver.onNext(GrpcCloudEventMapper.okResponse());
                        }
                    } else {
                        // later non-ack messages: a reply to a request/reply event routed over
                        // the stream - correlate via emcorrelationid if present.
                        String correlationId = attr(message, UniIngressService.EXT_CORRELATION_ID);
                        if (correlationId != null && !correlationId.isEmpty()) {
                            ingress.reply(correlationId, GrpcCloudEventMapper.toV2(message));
                        }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    closeStream();
                    log.info("grpc subscribeStream error: {}", t.toString());
                }

                @Override
                public void onCompleted() {
                    closeStream();
                }

                private void closeStream() {
                    if (clientId != null) {
                        GrpcStreamChannel channel = streamChannels.remove(clientId);
                        if (channel != null) {
                            channel.close();
                        }
                    }
                }
            };
        }

        private String attr(CloudEvent message, String key) {
            CloudEvent.CloudEventAttributeValue v = message.getAttributesMap().get(key);
            return v == null ? null : v.getCeString();
        }

        /** Fire the pending stream ACK when the client echoes the delivery id back. */
        private void ackFromClient(CloudEvent message) {
            String deliveryId = attr(message, DELIVERY_ID_ATTR);
            AckCallback callback = deliveryId == null ? null : pendingStreamAcks.remove(deliveryId);
            if (callback != null) {
                callback.ack();
            } else {
                // Fallback: long-polling style ACK via the ingress (delivery ids are shared).
                ingress.ack(deliveryId);
            }
        }

        /**
         * Shared subscribe path: parse the subscription items, register the client + push channel,
         * map each topic to a v2 subscription. Returns the derived clientId.
         */
        private String subscribeAll(CloudEvent request,
            org.apache.eventmesh.runtime.delivery.PushChannel channel) {
            String group = GrpcCloudEventMapper.consumerGroupOf(request);
            String env = org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils
                .getEnv(request, "");
            String idc = org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils
                .getIdc(request, "");
            String clientId = clientId(group, env, idc);
            clientRegistry.register(clientId, group);
            ingress.registerChannel(clientId, channel);
            for (SubscriptionItem item : itemsOf(request)) {
                DistributionMode mode = item != null && item.getMode() != null
                    && "BROADCASTING".equals(item.getMode().getMode())
                    ? DistributionMode.BROADCAST : DistributionMode.LOAD_BALANCE;
                ingress.subscribe(item.getTopic(), clientId, mode, null);
            }
            return clientId;
        }

        /** The 1.x clientId derivation: consumerGroup + env + idc (issue #5411 item 3). */
        private String clientId(String group, String env, String idc) {
            return group + "-" + env + "-" + idc;
        }

        private Set<String> topicsOf(CloudEvent request) {
            Set<String> topics = new HashSet<>();
            for (SubscriptionItem item : itemsOf(request)) {
                topics.add(item.getTopic());
            }
            return topics;
        }

        private List<SubscriptionItem> itemsOf(CloudEvent request) {
            String json = request.getTextData();
            if (json == null || json.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            return JsonUtils.parseTypeReferenceObject(json,
                new TypeReference<Set<SubscriptionItem>>() {
                }) == null ? java.util.Collections.emptyList()
                    : new java.util.ArrayList<>(JsonUtils.parseTypeReferenceObject(json,
                        new TypeReference<Set<SubscriptionItem>>() {
                        }));
        }
    }

    // ------------------------------------------------------------------
    // HeartbeatService
    // ------------------------------------------------------------------

    private final class HeartbeatImpl extends HeartbeatServiceGrpc.HeartbeatServiceImplBase {

        @Override
        public void heartbeat(CloudEvent request, StreamObserver<CloudEvent> responseObserver) {
            String group = GrpcCloudEventMapper.consumerGroupOf(request);
            String env = org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils
                .getEnv(request, "");
            String idc = org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils
                .getIdc(request, "");
            String clientId = new ConsumerImpl().clientId(group, env, idc);
            boolean known = clientRegistry.heartbeat(clientId);
            if (!known) {
                // 1.x answered CLIENT_RESUBSCRIBE for unknown clients - the SDK resubscribes.
                responseObserver.onNext(GrpcCloudEventMapper.response(
                    StatusCode.CLIENT_RESUBSCRIBE.getRetCode(),
                    StatusCode.CLIENT_RESUBSCRIBE.getErrMsg()));
                responseObserver.onCompleted();
                return;
            }
            responseObserver.onNext(GrpcCloudEventMapper.okResponse());
            responseObserver.onCompleted();
        }
    }

    /** Webhook signing secret (fixed default; per-URL secrets are a follow-up like 1.x). */
    private String webhookSecret() {
        return "eventmesh-grpc-webhook";
    }

    // ---- test accessors ----

    GrpcClientRegistry registryForTest() {
        return clientRegistry;
    }

    Map<String, AckCallback> pendingStreamAcksForTest() {
        return pendingStreamAcks;
    }

    PushService pushServiceForTest() {
        return ingress.getPushService();
    }

    List<BufferedEvent> pollForTest(String clientId, int max, long timeoutMs) {
        return ingress.poll(clientId, max, timeoutMs);
    }

    Map<String, GrpcStreamChannel> streamChannelsForTest() {
        return streamChannels;
    }

    Map<String, AckCallback> streamAckRegistry() {
        return pendingStreamAcks;
    }

    @SuppressWarnings("unused")
    private Map<String, String> diagnosticSnapshot() {
        Map<String, String> snap = new HashMap<>();
        snap.put("clients", Integer.toString(clientRegistry.size()));
        snap.put("streamChannels", Integer.toString(streamChannels.size()));
        return snap;
    }
}
