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
import org.apache.eventmesh.client.grpc.util.EventMeshClientUtil;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.PublisherServiceGrpc.PublisherServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CloudEventProducer {

    private static final String PROTOCOL_TYPE = EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME;

    private final transient EventMeshGrpcClientConfig clientConfig;

    private final transient PublisherServiceBlockingStub publisherClient;

    public CloudEventProducer(final EventMeshGrpcClientConfig clientConfig,
        final PublisherServiceBlockingStub publisherClient) {
        this.clientConfig = clientConfig;
        this.publisherClient = publisherClient;
    }

    public Response publish(final List<CloudEvent> events) {
        if (log.isInfoEnabled()) {
            log.info("BatchPublish message, batch size={}", events.size());
        }

        if (CollectionUtils.isEmpty(events)) {
            return null;
        }

        final List<CloudEvent> enhancedEvents = events.stream()
            .map(event -> enhanceCloudEvent(event, null))
            .collect(Collectors.toList());

        final BatchMessage enhancedMessage = EventMeshClientUtil.buildBatchMessages(enhancedEvents, clientConfig, PROTOCOL_TYPE);
        try {
            final Response response = publisherClient.batchPublish(enhancedMessage);
            if (log.isInfoEnabled()) {
                log.info("Received response " + response.toString());
            }
            return response;
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error("Error in BatchPublish message {}", events, e);
            }
        }
        return null;
    }

    public Response publish(final CloudEvent cloudEvent) {
        if (log.isInfoEnabled()) {
            log.info("Publish message " + cloudEvent.toString());
        }
        final CloudEvent enhanceEvent = enhanceCloudEvent(cloudEvent, null);

        final SimpleMessage enhancedMessage = EventMeshClientUtil.buildSimpleMessage(enhanceEvent, clientConfig, PROTOCOL_TYPE);

        try {
            final Response response = publisherClient.publish(enhancedMessage);
            if (log.isInfoEnabled()) {
                log.info("Received response " + response.toString());
            }
            return response;
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error("Error in publishing message {}", cloudEvent, e);
            }
        }
        return null;
    }

    public CloudEvent requestReply(final CloudEvent cloudEvent, final int timeout) {
        if (log.isInfoEnabled()) {
            log.info("RequestReply message " + cloudEvent.toString());
        }
        final CloudEvent enhanceEvent = enhanceCloudEvent(cloudEvent, String.valueOf(timeout));

        final SimpleMessage enhancedMessage = EventMeshClientUtil.buildSimpleMessage(enhanceEvent, clientConfig,
            PROTOCOL_TYPE);
        try {
            final SimpleMessage reply = publisherClient.requestReply(enhancedMessage);
            if (log.isInfoEnabled()) {
                log.info("Received reply message:{}", reply.toString());
            }

            final Object msg = EventMeshClientUtil.buildMessage(reply, PROTOCOL_TYPE);
            if (msg instanceof CloudEvent) {
                return (CloudEvent) msg;
            } else {
                return null;
            }
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error("Error in RequestReply message {}", cloudEvent, e);
            }
        }
        return null;
    }

    private CloudEvent enhanceCloudEvent(final CloudEvent cloudEvent, final String timeout) {
        final CloudEventBuilder builder = CloudEventBuilder.from(cloudEvent)
            .withExtension(ProtocolKey.ENV, clientConfig.getEnv())
            .withExtension(ProtocolKey.IDC, clientConfig.getIdc())
            .withExtension(ProtocolKey.IP, Objects.requireNonNull(IPUtils.getLocalAddress()))
            .withExtension(ProtocolKey.PID, Long.toString(ThreadUtils.getPID()))
            .withExtension(ProtocolKey.SYS, clientConfig.getSys())
            .withExtension(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA)
            .withExtension(ProtocolKey.PROTOCOL_TYPE, PROTOCOL_TYPE)
            .withExtension(ProtocolKey.PROTOCOL_DESC, Constants.PROTOCOL_GRPC)
            .withExtension(ProtocolKey.PROTOCOL_VERSION, cloudEvent.getSpecVersion().toString())
            .withExtension(ProtocolKey.UNIQUE_ID, RandomStringUtils.generateNum(30))
            .withExtension(ProtocolKey.SEQ_NUM, RandomStringUtils.generateNum(30))
            .withExtension(ProtocolKey.USERNAME, clientConfig.getUserName())
            .withExtension(ProtocolKey.PASSWD, clientConfig.getPassword())
            .withExtension(ProtocolKey.PRODUCERGROUP, clientConfig.getProducerGroup());

        if (timeout != null) {
            builder.withExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL, timeout);
        }
        return builder.build();
    }
}
