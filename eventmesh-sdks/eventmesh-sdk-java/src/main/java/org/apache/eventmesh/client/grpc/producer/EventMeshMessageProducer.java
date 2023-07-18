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
import org.apache.eventmesh.client.grpc.util.EventMeshCloudEventBuilder;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.enums.EventMeshProtocolType;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEventBatch;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.PublisherServiceGrpc.PublisherServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.Response;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshMessageProducer implements GrpcProducer<EventMeshMessage> {

    private static final EventMeshProtocolType PROTOCOL_TYPE = EventMeshProtocolType.EVENT_MESH_MESSAGE;

    private final EventMeshGrpcClientConfig clientConfig;

    private final PublisherServiceBlockingStub publisherClient;

    public EventMeshMessageProducer(EventMeshGrpcClientConfig clientConfig, PublisherServiceBlockingStub publisherClient) {
        this.clientConfig = clientConfig;
        this.publisherClient = publisherClient;
    }

    @Override
    public Response publish(EventMeshMessage message) {

        if (null == message) {
            return null;
        }

        if (log.isDebugEnabled()) {
            log.info("Publish message: {}", message);
        }
        CloudEvent cloudEvent = EventMeshCloudEventBuilder.buildEventMeshCloudEvent(message, clientConfig, PROTOCOL_TYPE);
        try {
            CloudEvent response = publisherClient.publish(cloudEvent);
            Response parsedResponse = Response.builder()
                .respCode(EventMeshCloudEventUtils.getResponseCode(response))
                .respMsg(EventMeshCloudEventUtils.getResponseMessage(response))
                .respTime(EventMeshCloudEventUtils.getResponseTime(response))
                .build();
            if (log.isInfoEnabled()) {
                log.info("Received response:{}", parsedResponse);
            }
            return parsedResponse;
        } catch (Exception e) {
            log.error("Error in publishing message {}", message, e);
        }
        return null;
    }

    @Override
    public Response publish(List<EventMeshMessage> messages) {

        if (CollectionUtils.isEmpty(messages)) {
            return null;
        }
        CloudEventBatch cloudEventBatch = EventMeshCloudEventBuilder.buildEventMeshCloudEventBatch(messages, clientConfig, PROTOCOL_TYPE);
        try {
            CloudEvent response = publisherClient.batchPublish(cloudEventBatch);
            Response parsedResponse = Response.builder()
                .respCode(EventMeshCloudEventUtils.getResponseCode(response))
                .respMsg(EventMeshCloudEventUtils.getResponseMessage(response))
                .respTime(EventMeshCloudEventUtils.getResponseTime(response))
                .build();
            if (log.isInfoEnabled()) {
                log.info("Received response:{}", parsedResponse);
            }
            return parsedResponse;
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error("Error in BatchPublish message {}", messages, e);
            }
        }
        return null;
    }

    @Override
    public EventMeshMessage requestReply(EventMeshMessage message, long timeout) {
        if (log.isInfoEnabled()) {
            log.info("RequestReply message:{}", message);
        }

        final CloudEvent cloudEvent = EventMeshCloudEventBuilder.buildEventMeshCloudEvent(message, clientConfig, PROTOCOL_TYPE);
        try {
            final CloudEvent reply = publisherClient.withDeadlineAfter(timeout, TimeUnit.MILLISECONDS).requestReply(cloudEvent);
            if (log.isInfoEnabled()) {
                log.info("Received reply message:{}", reply);
            }
            return EventMeshCloudEventBuilder.buildMessageFromEventMeshCloudEvent(reply, PROTOCOL_TYPE);
        } catch (Exception e) {
            log.error("Error in RequestReply message {}", message, e);
        }
        return null;
    }
}
