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

package org.apache.eventmesh.client.grpc.producer;

import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.support.Producer;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.PublisherServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.PublisherServiceGrpc.PublisherServiceBlockingStub;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

import io.cloudevents.CloudEvent;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class EventMeshGrpcProducer implements Producer, AutoCloseable {

    private static final String PROTOCOL_TYPE = EventMeshCommon.EM_MESSAGE_PROTOCOL_NAME;

    private final transient EventMeshGrpcClientConfig clientConfig;

    private final transient ManagedChannel channel;

    private PublisherServiceBlockingStub publisherClient;

    private CloudEventProducer cloudEventProducer;

    private EventMeshMessageProducer eventMeshMessageProducer;

    public EventMeshGrpcProducer(EventMeshGrpcClientConfig clientConfig) {
        this.clientConfig = clientConfig;
        this.channel = ManagedChannelBuilder.forAddress(clientConfig.getServerAddr(), clientConfig.getServerPort()).usePlaintext().build();
        this.publisherClient = PublisherServiceGrpc.newBlockingStub(channel);
        this.cloudEventProducer = new CloudEventProducer(clientConfig, publisherClient);
        this.eventMeshMessageProducer = new EventMeshMessageProducer(clientConfig, publisherClient);
    }

    @Override
    public <T> void publish(T message) {
        if (log.isInfoEnabled()) {
            log.info("Publish message " + message.toString());
        }
        if (message instanceof CloudEvent) {
            cloudEventProducer.publish((CloudEvent) message);
        } else if (message instanceof EventMeshMessage) {
            eventMeshMessageProducer.publish((EventMeshMessage) message);
        } else {
            throw new IllegalArgumentException("Not support message " + message.getClass().getName());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void publish(List<T> messageList) {
        if (log.isInfoEnabled()) {
            log.info("BatchPublish message :{}", messageList);
        }

        if (CollectionUtils.isEmpty(messageList)) {
            return;
        }

        T target = messageList.get(0);
        if (target instanceof CloudEvent) {
            cloudEventProducer.publish((List<CloudEvent>) messageList);
        } else if (target instanceof EventMeshMessage) {
            eventMeshMessageProducer.publish((List<EventMeshMessage>) messageList);
        } else {
            throw new IllegalArgumentException("Not support message " + target.getClass().getName());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T request(final T message, final long timeout) {

        if (message instanceof CloudEvent) {
            return (T) cloudEventProducer.requestReply((CloudEvent) message, timeout);
        } else if (message instanceof EventMeshMessage) {
            return (T) eventMeshMessageProducer.requestReply((EventMeshMessage) message, timeout);
        } else {
            throw new IllegalArgumentException("Not support message " + message.getClass().getName());
        }
    }

    @Override
    public void close() {
        channel.shutdown();
    }
}
