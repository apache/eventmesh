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
import org.apache.eventmesh.client.grpc.util.EventMeshCloudEventBuilder;
import org.apache.eventmesh.common.enums.EventMeshDataContentType;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.ConsumerServiceGrpc.ConsumerServiceStub;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.common.SubscriptionReply;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import io.grpc.stub.StreamObserver;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SubStreamHandler<T> extends Thread {

    private final transient CountDownLatch latch = new CountDownLatch(1);

    private final transient ConsumerServiceStub consumerAsyncClient;

    private final transient EventMeshGrpcClientConfig clientConfig;

    private transient StreamObserver<CloudEvent> sender;

    private final ReceiveMsgHook<T> listener;

    public SubStreamHandler(final ConsumerServiceStub consumerAsyncClient, final EventMeshGrpcClientConfig clientConfig,
        final ReceiveMsgHook<T> listener) {
        this.consumerAsyncClient = consumerAsyncClient;
        this.clientConfig = clientConfig;
        this.listener = listener;
    }

    public void sendSubscription(final CloudEvent subscription) {
        synchronized (this) {
            if (this.sender == null) {
                this.sender = consumerAsyncClient.subscribeStream(createReceiver());
            }
        }
        senderOnNext(subscription);
    }

    private StreamObserver<CloudEvent> createReceiver() {
        return new StreamObserver<CloudEvent>() {
            @Override
            public void onNext(final CloudEvent message) {
                T msg = EventMeshCloudEventBuilder.buildMessageFromEventMeshCloudEvent(message, listener.getProtocolType());
                if (msg instanceof Set) {
                    if (log.isInfoEnabled()) {
                        log.info("Received message from Server:{}", message);
                    }
                } else {
                    if (log.isInfoEnabled()) {
                        log.info("Received message from Server.|seq={}|uniqueId={}|", EventMeshCloudEventUtils.getSeqNum(message),
                            EventMeshCloudEventUtils.getUniqueId(message));
                    }
                    CloudEvent streamReply = null;
                    try {
                        Optional<T> reply = listener.handle(msg);
                        if (reply.isPresent()) {
                            streamReply = buildReplyMessage(message, reply.get());
                        }
                    } catch (Exception e) {
                        if (log.isErrorEnabled()) {
                            log.error("Error in handling reply message.|seq={}|uniqueId={}|",
                                EventMeshCloudEventUtils.getSeqNum(message), EventMeshCloudEventUtils.getUniqueId(message), e);
                        }
                    }
                    if (streamReply != null) {
                        if (log.isInfoEnabled()) {
                            log.info("Sending reply message to Server.|seq={}|uniqueId={}|",
                                EventMeshCloudEventUtils.getSeqNum(streamReply),
                                EventMeshCloudEventUtils.getUniqueId(streamReply));
                        }
                        senderOnNext(streamReply);
                    }
                }
            }

            @Override
            public void onError(final Throwable t) {
                if (log.isErrorEnabled()) {
                    log.error("Received Server side error", t);
                }
                close();
            }

            @Override
            public void onCompleted() {
                if (log.isInfoEnabled()) {
                    log.info("Finished receiving messages from server.");
                }
                close();
            }
        };
    }

    private CloudEvent buildReplyMessage(final CloudEvent reqMessage, final T replyMessage) {
        final CloudEvent cloudEvent = EventMeshCloudEventBuilder.buildEventMeshCloudEvent(replyMessage,
            clientConfig, listener.getProtocolType());
        SubscriptionReply subscriptionReply = SubscriptionReply.builder().producerGroup(clientConfig.getConsumerGroup())
            .topic(EventMeshCloudEventUtils.getSubject(cloudEvent))
            .content(EventMeshCloudEventUtils.getDataContent(cloudEvent))
            .seqNum(EventMeshCloudEventUtils.getSeqNum(cloudEvent))
            .uniqueId(EventMeshCloudEventUtils.getUniqueId(cloudEvent))
            .ttl(EventMeshCloudEventUtils.getTtl(cloudEvent)).build();

        Map<String, String> prop = new HashMap<>();
        Map<String, CloudEventAttributeValue> reqMessageMap = reqMessage.getAttributesMap();
        reqMessageMap.entrySet().forEach(entry -> prop.put(entry.getKey(), entry.getValue().getCeString()));
        Map<String, CloudEventAttributeValue> cloudEventMap = reqMessage.getAttributesMap();
        cloudEventMap.entrySet().forEach(entry -> prop.put(entry.getKey(), entry.getValue().getCeString()));
        subscriptionReply.putAllProperties(prop);

        return CloudEvent.newBuilder().putAllAttributes(cloudEvent.getAttributesMap())
            .putAttributes(ProtocolKey.DATA_CONTENT_TYPE,
                CloudEventAttributeValue.newBuilder().setCeString(EventMeshDataContentType.JSON.getCode()).build())
            .setTextData(JsonUtils.toJSONString(subscriptionReply)).build();
    }

    @Override
    public void run() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            log.error("SubStreamHandler Thread interrupted", e);
        }
    }

    public void close() {
        if (this.sender != null) {
            senderOnComplete();
        }

        latch.countDown();

        if (log.isInfoEnabled()) {
            log.info("SubStreamHandler closed.");
        }
    }

    private void senderOnNext(final CloudEvent subscription) {
        try {
            synchronized (sender) {
                sender.onNext(subscription);
            }
        } catch (Exception e) {
            log.error("StreamObserver Error onNext", e);
        }
    }

    private void senderOnComplete() {
        try {
            synchronized (sender) {
                sender.onCompleted();
            }
        } catch (Exception e) {
            log.error("StreamObserver Error onComplete", e);
        }
    }
}
