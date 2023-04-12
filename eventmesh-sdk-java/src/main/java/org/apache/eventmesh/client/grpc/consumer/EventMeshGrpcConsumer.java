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
import org.apache.eventmesh.client.grpc.util.EventMeshClientUtil;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.common.enums.EventMeshProtocolType;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.grpc.protos.ConsumerServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.ConsumerServiceGrpc.ConsumerServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.protos.ConsumerServiceGrpc.ConsumerServiceStub;
import org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat;
import org.apache.eventmesh.common.protocol.grpc.protos.HeartbeatServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.HeartbeatServiceGrpc.HeartbeatServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class EventMeshGrpcConsumer implements AutoCloseable {

    private static final transient String SDK_STREAM_URL = "grpc_stream";
    private transient ManagedChannel channel;
    private final transient EventMeshGrpcClientConfig clientConfig;

    private final transient Map<String, SubscriptionInfo> subscriptionMap = new ConcurrentHashMap<>();

    private final transient ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors(),
        new EventMeshThreadFactory("GRPCClientScheduler", true));

    private transient ConsumerServiceBlockingStub consumerClient;
    private transient ConsumerServiceStub consumerAsyncClient;
    private transient HeartbeatServiceBlockingStub heartbeatClient;

    private transient ReceiveMsgHook<?> listener;
    private transient SubStreamHandler<?> subStreamHandler;

    public EventMeshGrpcConsumer(final EventMeshGrpcClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public void init() {
        channel = ManagedChannelBuilder.forAddress(clientConfig.getServerAddr(), clientConfig.getServerPort())
            .usePlaintext().build();

        consumerClient = ConsumerServiceGrpc.newBlockingStub(channel);
        consumerAsyncClient = ConsumerServiceGrpc.newStub(channel);
        heartbeatClient = HeartbeatServiceGrpc.newBlockingStub(channel);

        heartBeat();
    }

    public Response subscribe(final List<SubscriptionItem> subscriptionItems, final String url) {
        if (log.isInfoEnabled()) {
            log.info("Create subscription: {} , url: {}", subscriptionItems, url);
        }

        addSubscription(subscriptionItems, url);

        Subscription subscription = buildSubscription(subscriptionItems, url);
        try {
            Response response = consumerClient.subscribe(subscription);
            if (log.isInfoEnabled()) {
                log.info("Received response:{}", response);
            }
            return response;
        } catch (Exception e) {
            log.error("Error in subscribe.", e);
        }
        return null;
    }

    public void subscribe(final List<SubscriptionItem> subscriptionItems) {
        if (log.isInfoEnabled()) {
            log.info("Create streaming subscription: {}", subscriptionItems);
        }

        if (listener == null) {
            log.error("Error in subscriber, no Event Listener is registered.");
            return;
        }

        addSubscription(subscriptionItems, SDK_STREAM_URL);

        final Subscription subscription = buildSubscription(subscriptionItems, null);

        synchronized (this) {
            if (subStreamHandler == null) {
                subStreamHandler = new SubStreamHandler<>(consumerAsyncClient, clientConfig, listener);
                subStreamHandler.start();
            }
        }
        subStreamHandler.sendSubscription(subscription);
    }

    private void addSubscription(final List<SubscriptionItem> subscriptionItems, final String url) {
        for (SubscriptionItem item : subscriptionItems) {
            subscriptionMap.putIfAbsent(item.getTopic(), new SubscriptionInfo(item, url));
        }
    }

    private void removeSubscription(final List<SubscriptionItem> subscriptionItems) {
        Objects.requireNonNull(subscriptionItems, "subscriptionItems can not be null");
        subscriptionItems.forEach(item -> {
            subscriptionMap.remove(item.getTopic());
        });
    }

    public Response unsubscribe(final List<SubscriptionItem> subscriptionItems, final String url) {
        if (log.isInfoEnabled()) {
            log.info("Removing subscription: {}, url:{}", subscriptionItems, url);
        }

        removeSubscription(subscriptionItems);

        Subscription subscription = buildSubscription(subscriptionItems, url);

        try {
            Response response = consumerClient.unsubscribe(subscription);
            if (log.isInfoEnabled()) {
                log.info("Received response:{}", response);
            }
            return response;
        } catch (Exception e) {
            log.error("Error in unsubscribe.", e);
        }
        return null;
    }

    public Response unsubscribe(final List<SubscriptionItem> subscriptionItems) {
        Objects.requireNonNull(subscriptionItems, "subscriptionItems can not be null");
        if (log.isInfoEnabled()) {
            log.info("Removing subscription stream: {}", subscriptionItems);
        }

        removeSubscription(subscriptionItems);

        Subscription subscription = buildSubscription(subscriptionItems, null);

        try {
            Response response = consumerClient.unsubscribe(subscription);

            if (log.isInfoEnabled()) {
                log.info("Received response:{}", response);
            }

            // there is no stream subscriptions, stop the subscription stream handler
            synchronized (this) {
                if (MapUtils.isEmpty(subscriptionMap) && subStreamHandler != null) {
                    subStreamHandler.close();
                }
            }
            return response;
        } catch (Exception e) {
            log.error("Error in unsubscribe.", e);
        }
        return null;
    }

    public Subscription buildSubscription(final List<SubscriptionItem> subscriptionItems, final String url) {
        Objects.requireNonNull(subscriptionItems, "subscriptionItems can not be null");

        final Subscription.Builder builder = Subscription.newBuilder()
            .setHeader(EventMeshClientUtil.buildHeader(clientConfig, EventMeshProtocolType.EVENT_MESH_MESSAGE))
            .setConsumerGroup(clientConfig.getConsumerGroup());

        if (StringUtils.isNotEmpty(url)) {
            builder.setUrl(url);
        }

        Set<SubscriptionItem> subscriptionItemSet = new HashSet<>();
        subscriptionItemSet.addAll(subscriptionItems);

        for (SubscriptionItem subscriptionItem : subscriptionItemSet) {
            Subscription.SubscriptionItem.SubscriptionMode mode;
            if (SubscriptionMode.CLUSTERING == subscriptionItem.getMode()) {
                mode = Subscription.SubscriptionItem.SubscriptionMode.CLUSTERING;
            } else if (SubscriptionMode.BROADCASTING == subscriptionItem.getMode()) {
                mode = Subscription.SubscriptionItem.SubscriptionMode.BROADCASTING;
            } else {
                mode = Subscription.SubscriptionItem.SubscriptionMode.UNRECOGNIZED;
            }

            Subscription.SubscriptionItem.SubscriptionType type;
            if (SubscriptionType.ASYNC == subscriptionItem.getType()) {
                type = Subscription.SubscriptionItem.SubscriptionType.ASYNC;
            } else if (SubscriptionType.SYNC == subscriptionItem.getType()) {
                type = Subscription.SubscriptionItem.SubscriptionType.SYNC;
            } else {
                type = Subscription.SubscriptionItem.SubscriptionType.UNRECOGNIZED;
            }
            final Subscription.SubscriptionItem item = Subscription.SubscriptionItem.newBuilder()
                .setTopic(subscriptionItem.getTopic())
                .setMode(mode)
                .setType(type)
                .build();
            builder.addSubscriptionItems(item);
        }

        return builder.build();
    }

    public synchronized void registerListener(final ReceiveMsgHook<?> listener) {
        if (this.listener == null) {
            this.listener = listener;
        }
    }

    private void heartBeat() {
        final RequestHeader header = EventMeshClientUtil.buildHeader(clientConfig, EventMeshProtocolType.EVENT_MESH_MESSAGE);

        scheduler.scheduleAtFixedRate(() -> {
            if (MapUtils.isEmpty(subscriptionMap)) {
                return;
            }

            final Heartbeat.Builder heartbeatBuilder = Heartbeat.newBuilder()
                .setHeader(header)
                .setConsumerGroup(clientConfig.getConsumerGroup())
                .setClientType(Heartbeat.ClientType.SUB);

            subscriptionMap.forEach((topic, subscriptionInfo) -> {
                Heartbeat.HeartbeatItem heartbeatItem = Heartbeat.HeartbeatItem
                    .newBuilder()
                    .setTopic(topic)
                    .setUrl(subscriptionInfo.getUrl())
                    .build();
                heartbeatBuilder.addHeartbeatItems(heartbeatItem);
            });

            Heartbeat heartbeat = heartbeatBuilder.build();

            try {
                final Response response = heartbeatClient.heartbeat(heartbeat);
                if (log.isDebugEnabled()) {
                    log.debug("Grpc Consumer Heartbeat response: {}", response);
                }

                if (StatusCode.CLIENT_RESUBSCRIBE.getRetCode().equals(response.getRespCode())) {
                    resubscribe();
                }
            } catch (Exception e) {
                log.error("Error in sending out heartbeat.", e);
            }
        }, 10_000, EventMeshCommon.HEARTBEAT, TimeUnit.MILLISECONDS);

        if (log.isInfoEnabled()) {
            log.info("Grpc Consumer Heartbeat started.");
        }
    }

    private void resubscribe() {
        if (subscriptionMap.isEmpty()) {
            return;
        }

        final Map<String, List<SubscriptionItem>> subscriptionGroup =
            subscriptionMap.values().stream()
                .collect(Collectors.groupingBy(SubscriptionInfo::getUrl,
                    mapping(SubscriptionInfo::getSubscriptionItem, toList())));

        subscriptionGroup.forEach((url, items) -> {
            Subscription subscription = buildSubscription(items, url);
            subStreamHandler.sendSubscription(subscription);
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

    private static class SubscriptionInfo {

        private transient SubscriptionItem subscriptionItem;
        private transient String url;

        SubscriptionInfo(final SubscriptionItem subscriptionItem, final String url) {
            this.subscriptionItem = subscriptionItem;
            this.url = url;
        }

        public SubscriptionItem getSubscriptionItem() {
            return subscriptionItem;
        }

        public void setSubscriptionItem(final SubscriptionItem subscriptionItem) {
            this.subscriptionItem = subscriptionItem;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(final String url) {
            this.url = url;
        }
    }
}
