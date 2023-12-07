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

package org.apache.eventmesh.runtime.core.protocol.grpc.service;

import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.ConsumerServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.grpc.common.SubscriptionReply;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.processor.ReplyMessageProcessor;
import org.apache.eventmesh.runtime.core.protocol.grpc.processor.SubscribeProcessor;
import org.apache.eventmesh.runtime.core.protocol.grpc.processor.SubscribeStreamProcessor;
import org.apache.eventmesh.runtime.core.protocol.grpc.processor.UnsubscribeProcessor;

import org.apache.commons.lang3.StringUtils;

import java.util.Optional;
import java.util.concurrent.ThreadPoolExecutor;

import io.grpc.stub.StreamObserver;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConsumerService extends ConsumerServiceGrpc.ConsumerServiceImplBase {

    private final EventMeshGrpcServer eventMeshGrpcServer;

    private final ThreadPoolExecutor subscribeThreadPoolExecutor;

    private final ThreadPoolExecutor replyThreadPoolExecutor;

    public ConsumerService(EventMeshGrpcServer eventMeshGrpcServer,
        ThreadPoolExecutor subscribeThreadPoolExecutor,
        ThreadPoolExecutor replyThreadPoolExecutor) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.subscribeThreadPoolExecutor = subscribeThreadPoolExecutor;
        this.replyThreadPoolExecutor = replyThreadPoolExecutor;
    }

    @Override
    public void subscribe(CloudEvent request, StreamObserver<CloudEvent> responseObserver) {
        log.info("cmd={}|{}|client2eventMesh|from={}|to={}",
            "subscribe", EventMeshConstants.PROTOCOL_GRPC,
            EventMeshCloudEventUtils.getIp(request), eventMeshGrpcServer.getEventMeshGrpcConfiguration().getEventMeshIp());
        eventMeshGrpcServer.getMetricsMonitor().recordReceiveMsgFromClient();

        EventEmitter<CloudEvent> emitter = new EventEmitter<>(responseObserver);
        subscribeThreadPoolExecutor.submit(() -> {
            SubscribeProcessor subscribeProcessor = new SubscribeProcessor(eventMeshGrpcServer);
            try {
                subscribeProcessor.process(request, emitter);
            } catch (Exception e) {
                log.error("Error code {}, error message {}", StatusCode.EVENTMESH_SUBSCRIBE_ERR.getRetCode(),
                    StatusCode.EVENTMESH_SUBSCRIBE_ERR.getErrMsg(), e);
                ServiceUtils.sendResponseCompleted(StatusCode.EVENTMESH_SUBSCRIBE_ERR, e.getMessage(), emitter);
            }
        });
    }

    @Override
    public StreamObserver<CloudEvent> subscribeStream(StreamObserver<CloudEvent> responseObserver) {
        EventEmitter<CloudEvent> emitter = new EventEmitter<>(responseObserver);

        return new StreamObserver<CloudEvent>() {

            @Override
            public void onNext(CloudEvent subscription) {
                final String subMessageType = Optional.ofNullable(subscription.getAttributesMap().get(ProtocolKey.SUB_MESSAGE_TYPE))
                    .orElse(CloudEventAttributeValue.newBuilder().build()).getCeString();
                if (StringUtils.equals(subMessageType, SubscriptionReply.TYPE)) {
                    log.info("cmd={}|{}|client2eventMesh|from={}|to={}", "reply-to-server", EventMeshConstants.PROTOCOL_GRPC,
                        EventMeshCloudEventUtils.getIp(subscription), eventMeshGrpcServer.getEventMeshGrpcConfiguration().getEventMeshIp());
                    handleSubscribeReply(subscription, emitter);
                } else {
                    log.info("cmd={}|{}|client2eventMesh|from={}|to={}", "subscribeStream", EventMeshConstants.PROTOCOL_GRPC,
                        EventMeshCloudEventUtils.getIp(subscription), eventMeshGrpcServer.getEventMeshGrpcConfiguration().getEventMeshIp());

                    eventMeshGrpcServer.getMetricsMonitor().recordReceiveMsgFromClient();
                    handleSubscriptionStream(subscription, emitter);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("Receive error from client: {}", t.getMessage());
                emitter.onCompleted();
            }

            @Override
            public void onCompleted() {
                log.info("Client finish sending messages");
                emitter.onCompleted();
            }
        };
    }

    private void handleSubscriptionStream(CloudEvent request, EventEmitter<CloudEvent> emitter) {
        subscribeThreadPoolExecutor.submit(() -> {
            SubscribeStreamProcessor streamProcessor = new SubscribeStreamProcessor(eventMeshGrpcServer);
            try {
                streamProcessor.process(request, emitter);
            } catch (Exception e) {
                log.error("Error code {}, error message {}", StatusCode.EVENTMESH_SUBSCRIBE_ERR, e.getMessage(), e);
                ServiceUtils.sendStreamResponseCompleted(request, StatusCode.EVENTMESH_SUBSCRIBE_ERR, e.getMessage(), emitter);
            }
        });
    }

    private void handleSubscribeReply(CloudEvent subscription, EventEmitter<CloudEvent> emitter) {
        replyThreadPoolExecutor.submit(() -> {
            ReplyMessageProcessor replyMessageProcessor = new ReplyMessageProcessor(eventMeshGrpcServer);
            try {
                replyMessageProcessor.process(subscription, emitter);
            } catch (Exception e) {
                log.error("Error code {}, error message {}", StatusCode.EVENTMESH_SUBSCRIBE_ERR, e.getMessage(), e);
                ServiceUtils.sendStreamResponseCompleted(subscription, StatusCode.EVENTMESH_SUBSCRIBE_ERR, e.getMessage(), emitter);
            }
        });
    }

    @Override
    public void unsubscribe(CloudEvent request, StreamObserver<CloudEvent> responseObserver) {
        log.info("cmd={}|{}|client2eventMesh|from={}|to={}", "unsubscribe", EventMeshConstants.PROTOCOL_GRPC,
            EventMeshCloudEventUtils.getIp(request), eventMeshGrpcServer.getEventMeshGrpcConfiguration().getEventMeshIp());
        eventMeshGrpcServer.getMetricsMonitor().recordReceiveMsgFromClient();

        EventEmitter<CloudEvent> emitter = new EventEmitter<>(responseObserver);
        subscribeThreadPoolExecutor.submit(() -> {
            UnsubscribeProcessor unsubscribeProcessor = new UnsubscribeProcessor(eventMeshGrpcServer);
            try {
                unsubscribeProcessor.process(request, emitter);
            } catch (Exception e) {
                log.error("Error code {}, error message {}", StatusCode.EVENTMESH_UNSUBSCRIBE_ERR.getRetCode(),
                    StatusCode.EVENTMESH_UNSUBSCRIBE_ERR.getErrMsg(), e);
                ServiceUtils.sendResponseCompleted(StatusCode.EVENTMESH_UNSUBSCRIBE_ERR, e.getMessage(), emitter);
            }
        });
    }
}
