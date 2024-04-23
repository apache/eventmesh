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

package org.apache.eventmesh.client.grpc.consumer;

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.util.EventMeshCloudEventBuilder;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.common.enums.EventMeshDataContentType;
import org.apache.eventmesh.common.enums.EventMeshProtocolType;
import org.apache.eventmesh.common.protocol.HeartbeatItem;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.ConsumerServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.ConsumerServiceGrpc.ConsumerServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.ConsumerServiceGrpc.ConsumerServiceStub;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.HeartbeatServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.HeartbeatServiceGrpc.HeartbeatServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.common.ClientType;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.GrpcType;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.common.Response;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.utils.JsonUtils;

import org.apache.commons.collections4.MapUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class EventMeshGrpcConsumer implements AutoCloseable {

    private static final String SDK_STREAM_URL = "grpc_stream";
    private ManagedChannel channel;
    private final EventMeshGrpcClientConfig clientConfig;

    private final Map<String /* topic */, SubscriptionInfo> subscriptionMap = new ConcurrentHashMap<>();

    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
        new EventMeshThreadFactory("GRPCClientScheduler", true));

    private ConsumerServiceBlockingStub consumerClient;
    private ConsumerServiceStub consumerAsyncClient;
    private HeartbeatServiceBlockingStub heartbeatClient;

    private ReceiveMsgHook<?> listener;
    private SubStreamHandler<?> subStreamHandler;

    public EventMeshGrpcConsumer(final EventMeshGrpcClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public void init() {
        this.channel = ManagedChannelBuilder.forAddress(clientConfig.getServerAddr(), clientConfig.getServerPort()).usePlaintext().build();
        this.consumerClient = ConsumerServiceGrpc.newBlockingStub(channel);
        this.consumerAsyncClient = ConsumerServiceGrpc.newStub(channel);
        this.heartbeatClient = HeartbeatServiceGrpc.newBlockingStub(channel);
        heartBeat();
    }

    /**
     * Subscribes to an event at a specified URL(Webhook).
     *
     * @param subscriptionItems The list of subscription items.
     * @param url               The URL to subscribe to.
     * @return A response containing information about the subscription result.
     */
    public Response subscribe(final List<SubscriptionItem> subscriptionItems, final String url) {
        log.info("Create subscription: {} , url: {}", subscriptionItems, url);

        addSubscription(subscriptionItems, url, GrpcType.WEBHOOK);

        return subscribeWebhook(subscriptionItems, url);
    }

    /**
     * Subscribes to a streaming.
     *
     * @param subscriptionItems The list of subscription items for streaming.
     */
    public void subscribe(final List<SubscriptionItem> subscriptionItems) {
        log.info("Create streaming subscription: {}", subscriptionItems);

        if (listener == null) {
            log.error("Error in subscriber, no Event Listener is registered.");
            return;
        }

        addSubscription(subscriptionItems, SDK_STREAM_URL, GrpcType.STREAM);

        CloudEvent subscription = EventMeshCloudEventBuilder.buildEventSubscription(
            clientConfig, EventMeshProtocolType.EVENT_MESH_MESSAGE, null, subscriptionItems);
        synchronized (this) {
            if (subStreamHandler == null) {
                subStreamHandler = new SubStreamHandler<>(consumerAsyncClient, clientConfig, listener);
                subStreamHandler.start();
            }
        }
        subStreamHandler.sendSubscription(subscription);
    }

    private Response subscribeWebhook(List<SubscriptionItem> subscriptionItems, String url) {
        final CloudEvent subscription = EventMeshCloudEventBuilder.buildEventSubscription(
            clientConfig, EventMeshProtocolType.EVENT_MESH_MESSAGE, url, subscriptionItems);
        try {
            CloudEvent response = consumerClient.subscribe(subscription);
            log.info("Received response:{}", response);
            return Response.builder()
                .respCode(EventMeshCloudEventUtils.getResponseCode(response))
                .respMsg(EventMeshCloudEventUtils.getResponseMessage(response))
                .respTime(EventMeshCloudEventUtils.getResponseTime(response))
                .build();
        } catch (Exception e) {
            log.error("Error in subscribe.", e);
        }
        return null;
    }

    private void addSubscription(final List<SubscriptionItem> subscriptionItems, final String url, final GrpcType grpcType) {
        for (SubscriptionItem item : subscriptionItems) {
            subscriptionMap.putIfAbsent(item.getTopic(), new SubscriptionInfo(item, url, grpcType));
        }
    }

    private void removeSubscription(final List<SubscriptionItem> subscriptionItems) {
        Objects.requireNonNull(subscriptionItems, "subscriptionItems can not be null");
        subscriptionItems.forEach(item -> subscriptionMap.remove(item.getTopic()));
    }

    public Response unsubscribe(final List<SubscriptionItem> subscriptionItems, final String url) {
        log.info("Removing subscription: {}, url:{}", subscriptionItems, url);

        removeSubscription(subscriptionItems);

        final CloudEvent cloudEvent = EventMeshCloudEventBuilder.buildEventSubscription(
            clientConfig, EventMeshProtocolType.EVENT_MESH_MESSAGE, url, subscriptionItems);
        try {
            final CloudEvent response = consumerClient.unsubscribe(cloudEvent);
            log.info("Received response:{}", response);
            return Response.builder()
                .respCode(EventMeshCloudEventUtils.getResponseCode(response))
                .respMsg(EventMeshCloudEventUtils.getResponseMessage(response))
                .respTime(EventMeshCloudEventUtils.getResponseTime(response))
                .build();
        } catch (Exception e) {
            log.error("Error in unsubscribe.", e);
        }
        return null;
    }

    public Response unsubscribe(final List<SubscriptionItem> subscriptionItems) {
        Objects.requireNonNull(subscriptionItems, "subscriptionItems can not be null");
        log.info("Removing subscription stream: {}", subscriptionItems);

        removeSubscription(subscriptionItems);

        final CloudEvent cloudEvent = EventMeshCloudEventBuilder.buildEventSubscription(
            clientConfig, EventMeshProtocolType.EVENT_MESH_MESSAGE, null, subscriptionItems);

        try {
            final CloudEvent response = consumerClient.unsubscribe(cloudEvent);
            Response parsedResponse = Response.builder()
                .respCode(EventMeshCloudEventUtils.getResponseCode(response))
                .respMsg(EventMeshCloudEventUtils.getResponseMessage(response))
                .respTime(EventMeshCloudEventUtils.getResponseTime(response))
                .build();
            log.info("Received response:{}", parsedResponse);

            // there is no stream subscriptions, stop the subscription stream handler
            synchronized (this) {
                if (MapUtils.isEmpty(subscriptionMap) && subStreamHandler != null) {
                    subStreamHandler.close();
                }
            }
            return parsedResponse;
        } catch (Exception e) {
            log.error("Error in unsubscribe.", e);
        }
        return null;
    }

    public synchronized void registerListener(final ReceiveMsgHook<?> listener) {
        if (this.listener == null) {
            this.listener = listener;
        }
    }

    private void heartBeat() {
        final Map<String, CloudEventAttributeValue> attributeValueMap = EventMeshCloudEventBuilder.buildCommonCloudEventAttributes(clientConfig,
            EventMeshProtocolType.EVENT_MESH_MESSAGE);

        scheduler.scheduleAtFixedRate(() -> {
            if (MapUtils.isEmpty(subscriptionMap)) {
                return;
            }
            Map<String, CloudEventAttributeValue> ext = new HashMap<>(attributeValueMap);
            ext.put(ProtocolKey.CONSUMERGROUP, CloudEventAttributeValue.newBuilder().setCeString(clientConfig.getConsumerGroup()).build());
            ext.put(ProtocolKey.CLIENT_TYPE, CloudEventAttributeValue.newBuilder().setCeInteger(ClientType.SUB.getType()).build());
            ext.put(ProtocolKey.DATA_CONTENT_TYPE,
                CloudEventAttributeValue.newBuilder().setCeString(EventMeshDataContentType.JSON.getCode()).build());
            final CloudEvent.Builder heartbeatBuilder = CloudEvent.newBuilder().putAllAttributes(ext);
            List<HeartbeatItem> heartbeatItems = subscriptionMap.entrySet().stream()
                .map(entry -> HeartbeatItem.builder().topic(entry.getKey()).url(entry.getValue().getUrl()).build()).collect(toList());
            CloudEvent heartbeat = heartbeatBuilder.setTextData(JsonUtils.toJSONString(heartbeatItems)).build();

            try {
                CloudEvent cloudEventResp = heartbeatClient.heartbeat(heartbeat);
                assert cloudEventResp != null;
                Response response = Response.builder()
                    .respCode(EventMeshCloudEventUtils.getResponseCode(cloudEventResp))
                    .respMsg(EventMeshCloudEventUtils.getResponseMessage(cloudEventResp))
                    .respTime(EventMeshCloudEventUtils.getResponseTime(cloudEventResp))
                    .build();
                log.debug("Grpc Consumer Heartbeat cloudEvent: {}", response);
                if (StatusCode.CLIENT_RESUBSCRIBE.getRetCode().equals(response.getRespCode())) {
                    resubscribe();
                }
            } catch (Exception e) {
                log.error("Error in sending out heartbeat.", e);
            }
        }, 10_000, EventMeshCommon.HEARTBEAT, TimeUnit.MILLISECONDS);

        log.info("Grpc Consumer Heartbeat started.");
    }

    private void resubscribe() {
        if (subscriptionMap.isEmpty()) {
            return;
        }
        final Collection<SubscriptionInfo> values = subscriptionMap.values();
        final AtomicBoolean isStreamSub = new AtomicBoolean(false);
        for (SubscriptionInfo info : values) {
            if (info.grpcType == GrpcType.STREAM) {
                isStreamSub.compareAndSet(false, true);
                break;
            }
        }
        final Map<String, List<SubscriptionItem>> subscriptionGroup = values.stream()
            .collect(Collectors.groupingBy(SubscriptionInfo::getUrl, mapping(SubscriptionInfo::getSubscriptionItem, toList())));

        subscriptionGroup.forEach((url, items) -> {
            if (isStreamSub.get()) {
                CloudEvent subscription = EventMeshCloudEventBuilder.buildEventSubscription(
                    clientConfig, EventMeshProtocolType.EVENT_MESH_MESSAGE, url, items);
                subStreamHandler.sendSubscription(subscription);
            } else {
                subscribeWebhook(items, url);
            }
        });
    }

    @Override
    public void close() {
        if (this.subStreamHandler != null) {
            this.subStreamHandler.close();
        }

        if (this.channel != null) {
            channel.shutdown();
        }

        if (this.scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Data
    private static class SubscriptionInfo {

        private transient SubscriptionItem subscriptionItem;
        private transient String url;
        private GrpcType grpcType;

        SubscriptionInfo(final SubscriptionItem subscriptionItem, final String url, final GrpcType grpcType) {
            this.subscriptionItem = subscriptionItem;
            this.url = url;
            this.grpcType = grpcType;
        }
    }
}
