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
import org.apache.eventmesh.common.protocol.grpc.protos.ConsumerServiceGrpc.ConsumerServiceStub;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.stub.StreamObserver;

public class SubStreamHandler<T> extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(SubStreamHandler.class);

    private final CountDownLatch latch = new CountDownLatch(1);

    private final ConsumerServiceStub consumerAsyncClient;

    private final EventMeshGrpcClientConfig clientConfig;

    private StreamObserver<Subscription> sender;

    private final ReceiveMsgHook<T> listener;

    public SubStreamHandler(ConsumerServiceStub consumerAsyncClient, EventMeshGrpcClientConfig clientConfig,
                            ReceiveMsgHook<T> listener) {
        this.consumerAsyncClient = consumerAsyncClient;
        this.clientConfig = clientConfig;
        this.listener = listener;
    }

    public void sendSubscription(Subscription subscription) {
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
            public void onNext(SimpleMessage message) {
                T msg = EventMeshClientUtil.buildMessage(message, listener.getProtocolType());

                if (msg instanceof Map) {
                    logger.info("Received message from Server." + message);
                } else {
                    logger.info("Received message from Server.|seq={}|uniqueId={}|", message.getSeqNum(), message.getUniqueId());
                    Subscription streamReply = null;
                    try {
                        Optional<T> reply = listener.handle(msg);
                        if (reply.isPresent()) {
                            streamReply = buildReplyMessage(message, reply.get());
                        }
                    } catch (Throwable t) {
                        logger.error("Error in handling reply message.|seq={}|uniqueId={}|", message.getSeqNum(), message.getUniqueId(), t);
                    }
                    if (streamReply != null) {
                        logger.info("Sending reply message to Server.|seq={}|uniqueId={}|", streamReply.getReply().getSeqNum(),
                            streamReply.getReply().getUniqueId());
                        senderOnNext(streamReply);
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.error("Received Server side error: " + t.getMessage());
                close();
            }

            @Override
            public void onCompleted() {
                logger.info("Finished receiving messages from server.");
                close();
            }
        };
    }

    private Subscription buildReplyMessage(SimpleMessage reqMessage, T replyMessage) {
        SimpleMessage simpleMessage = EventMeshClientUtil.buildSimpleMessage(replyMessage, clientConfig, listener.getProtocolType());

        Subscription.Reply reply = Subscription.Reply.newBuilder()
            .setProducerGroup(clientConfig.getConsumerGroup())
            .setTopic(simpleMessage.getTopic())
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
            logger.error("SubStreamHandler Thread interrupted." + e.getMessage());
        }
    }

    public void close() {
        if (this.sender != null) {
            senderOnComplete();
            this.sender = null;
        }
        latch.countDown();
        logger.info("SubStreamHandler closed.");
    }

    private void senderOnNext(Subscription subscription) {
        try {
            synchronized (sender) {
                sender.onNext(subscription);
            }
        } catch (Throwable t) {
            logger.warn("StreamObserver Error onNext {}", t.getMessage());
        }
    }

    private void senderOnComplete() {
        try {
            synchronized (sender) {
                sender.onCompleted();
            }
        } catch (Throwable t) {
            logger.warn("StreamObserver Error onComplete {}", t.getMessage());
        }
    }
}
