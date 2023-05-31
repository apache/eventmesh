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

import java.io.Serializable;
import java.util.Objects;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.util.EventMeshClientUtil;
import org.apache.eventmesh.common.protocol.grpc.protos.ConsumerServiceGrpc.ConsumerServiceStub;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import io.grpc.stub.StreamObserver;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SubStreamHandler<T> extends Thread implements Serializable {

    private final transient CountDownLatch latch = new CountDownLatch(1);

    private final transient ConsumerServiceStub consumerAsyncClient;

    private final transient EventMeshGrpcClientConfig clientConfig;

    private transient StreamObserver<Subscription> sender;

    private final transient ReceiveMsgHook<T> listener;

    public SubStreamHandler(final ConsumerServiceStub consumerAsyncClient, final EventMeshGrpcClientConfig clientConfig,
        final ReceiveMsgHook<T> listener) {
        this.consumerAsyncClient = consumerAsyncClient;
        this.clientConfig = clientConfig;
        this.listener = listener;
    }

    public void sendSubscription(final Subscription subscription) {
        synchronized (this) {
            if (this.sender == null) {
                this.sender = consumerAsyncClient.subscribeStream(createReceiver());
            }
        }
        senderOnNext(subscription);
    }

    private StreamObserver<SimpleMessage> createReceiver() {
        return new StreamObserver<SimpleMessage>() {
            @Override
            public void onNext(final SimpleMessage message) {
                T msg = EventMeshClientUtil.buildMessage(message, listener.getProtocolType());

                if (msg instanceof Map) {
                    if (log.isInfoEnabled()) {
                        log.info("Received message from Server:{}", message);
                    }
                } else {
                    if (log.isInfoEnabled()) {
                        log.info("Received message from Server.|seq={}|uniqueId={}|", message.getSeqNum(),
                            message.getUniqueId());
                    }
                    Subscription streamReply = null;
                    try {
                        Optional<T> reply = listener.handle(msg);
                        if (reply.isPresent()) {
                            streamReply = buildReplyMessage(message, reply.get());
                        }
                    } catch (Exception e) {
                        if (log.isErrorEnabled()) {
                            log.error("Error in handling reply message.|seq={}|uniqueId={}|",
                                message.getSeqNum(), message.getUniqueId(), e);
                        }
                    }
                    if (streamReply != null) {
                        if (log.isInfoEnabled()) {
                            log.info("Sending reply message to Server.|seq={}|uniqueId={}|",
                                streamReply.getReply().getSeqNum(),
                                streamReply.getReply().getUniqueId());
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

    private Subscription buildReplyMessage(final SimpleMessage reqMessage, final T replyMessage) {
        final SimpleMessage simpleMessage = EventMeshClientUtil.buildSimpleMessage(replyMessage,
            clientConfig, listener.getProtocolType());

        final Subscription.Reply reply = Subscription.Reply.newBuilder()
            .setProducerGroup(clientConfig.getConsumerGroup())
            .setTopic(Objects.requireNonNull(simpleMessage).getTopic())
            .setContent(simpleMessage.getContent())
            .setSeqNum(simpleMessage.getSeqNum())
            .setUniqueId(simpleMessage.getUniqueId())
            .setTtl(simpleMessage.getTtl())
            .putAllProperties(reqMessage.getPropertiesMap())
            .putAllProperties(simpleMessage.getPropertiesMap())
            .build();

        return Subscription.newBuilder()
            .setHeader(simpleMessage.getHeader())
            .setReply(reply).build();
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

    private void senderOnNext(final Subscription subscription) {
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
