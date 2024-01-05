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
import org.apache.eventmesh.common.enums.EventMeshProtocolType;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEventBatch;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.PublisherServiceGrpc.PublisherServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.Response;
import org.apache.eventmesh.common.utils.LogUtils;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CloudEventProducer implements GrpcProducer<io.cloudevents.CloudEvent> {

    private static final EventMeshProtocolType PROTOCOL_TYPE = EventMeshProtocolType.CLOUD_EVENTS;

    private final transient EventMeshGrpcClientConfig clientConfig;

    private final transient PublisherServiceBlockingStub publisherClient;

    public CloudEventProducer(final EventMeshGrpcClientConfig clientConfig,
        final PublisherServiceBlockingStub publisherClient) {
        this.clientConfig = clientConfig;
        this.publisherClient = publisherClient;
    }

    @Override
    public Response publish(final List<io.cloudevents.CloudEvent> events) {
        log.info("BatchPublish message, batch size={}", events.size());

        if (CollectionUtils.isEmpty(events)) {
            return null;
        }

        final CloudEventBatch cloudEventBatch = EventMeshCloudEventBuilder.buildEventMeshCloudEventBatch(events, clientConfig, PROTOCOL_TYPE);
        try {
            final CloudEvent response = publisherClient.batchPublish(cloudEventBatch);
            Response parsedResponse = Response.builder()
                .respCode(EventMeshCloudEventUtils.getResponseCode(response))
                .respMsg(EventMeshCloudEventUtils.getResponseMessage(response))
                .respTime(EventMeshCloudEventUtils.getResponseTime(response))
                .build();

            log.info("Received response:{}", parsedResponse);
            return parsedResponse;
        } catch (Exception e) {
            log.error("Error in BatchPublish message {}", events, e);
        }
        return null;
    }

    @Override
    public Response publish(final io.cloudevents.CloudEvent cloudEvent) {
        LogUtils.info(log, "Publish message: {}", cloudEvent::toString);
        CloudEvent enhancedMessage = EventMeshCloudEventBuilder.buildEventMeshCloudEvent(cloudEvent, clientConfig, PROTOCOL_TYPE);
        try {
            final CloudEvent response = publisherClient.publish(enhancedMessage);
            Response parsedResponse = Response.builder()
                .respCode(EventMeshCloudEventUtils.getResponseCode(response))
                .respMsg(EventMeshCloudEventUtils.getResponseMessage(response))
                .respTime(EventMeshCloudEventUtils.getResponseTime(response))
                .build();
            log.info("Received response:{} ", parsedResponse);
            return parsedResponse;
        } catch (Exception e) {
            log.error("Error in publishing message {}", cloudEvent, e);
        }
        return null;
    }

    @Override
    public io.cloudevents.CloudEvent requestReply(final io.cloudevents.CloudEvent cloudEvent, final long timeout) {
        log.info("RequestReply message {}", cloudEvent);
        final CloudEvent enhancedMessage = EventMeshCloudEventBuilder.buildEventMeshCloudEvent(cloudEvent, clientConfig, PROTOCOL_TYPE);
        try {
            final CloudEvent reply = publisherClient.requestReply(enhancedMessage);
            log.info("Received reply message:{}", reply);
            return EventMeshCloudEventBuilder.buildMessageFromEventMeshCloudEvent(reply, PROTOCOL_TYPE);
        } catch (Exception e) {
            log.error("Error in RequestReply message {}", cloudEvent, e);
        }
        return null;
    }
}
