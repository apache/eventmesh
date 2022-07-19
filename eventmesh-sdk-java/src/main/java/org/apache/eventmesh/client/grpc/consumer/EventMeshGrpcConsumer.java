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

import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.util.EventMeshClientUtil;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.grpc.protos.ConsumerServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.ConsumerServiceGrpc.ConsumerServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.protos.ConsumerServiceGrpc.ConsumerServiceStub;
import org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat;
import org.apache.eventmesh.common.protocol.grpc.protos.HeartbeatServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.HeartbeatServiceGrpc.HeartbeatServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class EventMeshGrpcConsumer implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(EventMeshGrpcConsumer.class);

    private static final String SDK_STREAM_URL = "grpc_stream";

    private final EventMeshGrpcClientConfig clientConfig;

    private final Map<String, String> subscriptionMap = new ConcurrentHashMap<>();
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors(),
        new ThreadFactoryBuilder().setNameFormat("GRPCClientScheduler").setDaemon(true).build());

    private ManagedChannel channel;
    ConsumerServiceBlockingStub consumerClient;
    ConsumerServiceStub consumerAsyncClient;
    HeartbeatServiceBlockingStub heartbeatClient;

    private ReceiveMsgHook<?> listener;
    private SubStreamHandler<?> subStreamHandler;

    public EventMeshGrpcConsumer(EventMeshGrpcClientConfig clientConfig) {
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

    public Response subscribe(List<SubscriptionItem> subscriptionItems, String url) {
        logger.info("Create subscription: " + subscriptionItems + ", url: " + url);

        addSubscription(subscriptionItems, url);

        Subscription subscription = buildSubscription(subscriptionItems, url);
        try {
            Response response = consumerClient.subscribe(subscription);
            logger.info("Received response " + response.toString());
            return response;
        } catch (Exception e) {
            logger.error("Error in subscribe. error {}", e.getMessage());
            return null;
        }
    }

    public void subscribe(List<SubscriptionItem> subscriptionItems) {
        logger.info("Create streaming subscription: " + subscriptionItems);

        if (listener == null) {
            logger.error("Error in subscriber, no Event Listener is registered.");
            return;
        }

        addSubscription(subscriptionItems, SDK_STREAM_URL);

        Subscription subscription = buildSubscription(subscriptionItems, null);

        synchronized (this) {
            if (subStreamHandler == null) {
                subStreamHandler = new SubStreamHandler<>(consumerAsyncClient, clientConfig, listener);
                subStreamHandler.start();
            }
        }
        subStreamHandler.sendSubscription(subscription);
    }

    private void addSubscription(List<SubscriptionItem> subscriptionItems, String url) {
        for (SubscriptionItem item : subscriptionItems) {
            subscriptionMap.put(item.getTopic(), url);
        }
    }

    private void removeSubscription(List<SubscriptionItem> subscriptionItems) {
        for (SubscriptionItem item : subscriptionItems) {
            subscriptionMap.remove(item.getTopic());
        }
    }

    public Response unsubscribe(List<SubscriptionItem> subscriptionItems, String url) {
        logger.info("Removing subscription: " + subscriptionItems + ", url: " + url);

        removeSubscription(subscriptionItems);

        Subscription subscription = buildSubscription(subscriptionItems, url);

        try {
            Response response = consumerClient.unsubscribe(subscription);
            logger.info("Received response " + response.toString());
            return response;
        } catch (Exception e) {
            logger.error("Error in unsubscribe. error {}", e.getMessage());
            return null;
        }
    }

    public Response unsubscribe(List<SubscriptionItem> subscriptionItems) {
        logger.info("Removing subscription stream: " + subscriptionItems);

        removeSubscription(subscriptionItems);

        Subscription subscription = buildSubscription(subscriptionItems, null);

        try {
            Response response = consumerClient.unsubscribe(subscription);
            logger.info("Received response " + response.toString());

            // there is no stream subscriptions, stop the subscription stream handler
            synchronized (this) {
                if (!subscriptionMap.containsValue(SDK_STREAM_URL) && subStreamHandler != null) {
                    subStreamHandler.close();
                    subStreamHandler = null;
                }
            }
            return response;
        } catch (Exception e) {
            logger.error("Error in unsubscribe. error {}", e.getMessage());
            return null;
        }
    }

    private Subscription buildSubscription(List<SubscriptionItem> subscriptionItems, String url) {
        Subscription.Builder builder = Subscription.newBuilder()
            .setHeader(EventMeshClientUtil.buildHeader(clientConfig, EventMeshCommon.EM_MESSAGE_PROTOCOL_NAME))
            .setConsumerGroup(clientConfig.getConsumerGroup());

        if (StringUtils.isNotEmpty(url)) {
            builder.setUrl(url);
        }

        for (SubscriptionItem subscriptionItem : subscriptionItems) {
            Subscription.SubscriptionItem.SubscriptionMode mode;
            if (SubscriptionMode.CLUSTERING.equals(subscriptionItem.getMode())) {
                mode = Subscription.SubscriptionItem.SubscriptionMode.CLUSTERING;
            } else if (SubscriptionMode.BROADCASTING.equals(subscriptionItem.getMode())) {
                mode = Subscription.SubscriptionItem.SubscriptionMode.BROADCASTING;
            } else {
                mode = Subscription.SubscriptionItem.SubscriptionMode.UNRECOGNIZED;
            }

            Subscription.SubscriptionItem.SubscriptionType type;
            if (SubscriptionType.ASYNC.equals(subscriptionItem.getType())) {
                type = Subscription.SubscriptionItem.SubscriptionType.ASYNC;
            } else if (SubscriptionType.SYNC.equals(subscriptionItem.getType())) {
                type = Subscription.SubscriptionItem.SubscriptionType.SYNC;
            } else {
                type = Subscription.SubscriptionItem.SubscriptionType.UNRECOGNIZED;
            }

            Subscription.SubscriptionItem item = Subscription.SubscriptionItem.newBuilder()
                .setTopic(subscriptionItem.getTopic())
                .setMode(mode)
                .setType(type)
                .build();
            builder.addSubscriptionItems(item);
        }
        return builder.build();
    }

    public synchronized void registerListener(ReceiveMsgHook<?> listener) {
        if (this.listener == null) {
            this.listener = listener;
        }
    }

    private void heartBeat() {
        RequestHeader header = EventMeshClientUtil.buildHeader(clientConfig, EventMeshCommon.EM_MESSAGE_PROTOCOL_NAME);
        scheduler.scheduleAtFixedRate(() -> {
            if (subscriptionMap.isEmpty()) {
                return;
            }
            Heartbeat.Builder heartbeatBuilder = Heartbeat.newBuilder()
                .setHeader(header)
                .setConsumerGroup(clientConfig.getConsumerGroup())
                .setClientType(Heartbeat.ClientType.SUB);

            for (Map.Entry<String, String> entry : subscriptionMap.entrySet()) {
                Heartbeat.HeartbeatItem heartbeatItem = Heartbeat.HeartbeatItem
                    .newBuilder()
                    .setTopic(entry.getKey()).setUrl(entry.getValue())
                    .build();
                heartbeatBuilder.addHeartbeatItems(heartbeatItem);
            }
            Heartbeat heartbeat = heartbeatBuilder.build();

            try {
                Response response = heartbeatClient.heartbeat(heartbeat);
                if (logger.isDebugEnabled()) {
                    logger.debug("Grpc Consumer Heartbeat response: {}", response);
                }
            } catch (Exception e) {
                logger.error("Error in sending out heartbeat. error {}", e.getMessage());
            }
        }, 10000, EventMeshCommon.HEARTBEAT, TimeUnit.MILLISECONDS);

        logger.info("Grpc Consumer Heartbeat started.");
    }

    @Override
    public void close() {
        if (this.subStreamHandler != null) {
            this.subStreamHandler.close();
        }
        channel.shutdown();
        scheduler.shutdown();
    }
}
