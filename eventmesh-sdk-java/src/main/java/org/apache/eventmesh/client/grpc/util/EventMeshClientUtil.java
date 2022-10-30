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

package org.apache.eventmesh.client.grpc.util;

import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

import com.fasterxml.jackson.core.type.TypeReference;

public class EventMeshClientUtil {

    private static final Logger logger = LoggerFactory.getLogger(EventMeshClientUtil.class);

    public static RequestHeader buildHeader(EventMeshGrpcClientConfig clientConfig, String protocolType) {
        return RequestHeader.newBuilder()
            .setEnv(clientConfig.getEnv())
            .setIdc(clientConfig.getIdc())
            .setIp(IPUtils.getLocalAddress())
            .setPid(Long.toString(ThreadUtils.getPID()))
            .setSys(clientConfig.getSys())
            .setLanguage(clientConfig.getLanguage())
            .setUsername(clientConfig.getUserName())
            .setPassword(clientConfig.getPassword())
            .setProtocolType(protocolType)
            .setProtocolDesc(Constants.PROTOCOL_GRPC)
            // default CloudEvents version is V1
            .setProtocolVersion(SpecVersion.V1.toString())
            .build();
    }

    @SuppressWarnings("unchecked")
    public static <T> T buildMessage(SimpleMessage message, String protocolType) {
        String seq = message.getSeqNum();
        String uniqueId = message.getUniqueId();
        String content = message.getContent();

        // This is GRPC response message
        if (StringUtils.isEmpty(seq) && StringUtils.isEmpty(uniqueId)) {
            HashMap<String, String> response = JsonUtils.deserialize(content, new TypeReference<HashMap<String, String>>() {
            });
            return (T) response;
        }

        if (EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME.equals(protocolType)) {
            String contentType = message.getPropertiesOrDefault(ProtocolKey.CONTENT_TYPE, JsonFormat.CONTENT_TYPE);
            try {
                CloudEvent cloudEvent = Objects.requireNonNull(EventFormatProvider.getInstance().resolveFormat(contentType))
                    .deserialize(content.getBytes(StandardCharsets.UTF_8));

                CloudEventBuilder cloudEventBuilder = CloudEventBuilder.from(cloudEvent)
                    .withSubject(message.getTopic())
                    .withExtension(ProtocolKey.SEQ_NUM, message.getSeqNum())
                    .withExtension(ProtocolKey.UNIQUE_ID, message.getUniqueId());

                message.getPropertiesMap().forEach(cloudEventBuilder::withExtension);

                return (T) cloudEventBuilder.build();
            } catch (Throwable t) {
                logger.warn("Error in building message. {}", t.getMessage());
                return null;
            }
        } else {
            EventMeshMessage eventMeshMessage = EventMeshMessage.builder()
                .content(content)
                .topic(message.getTopic())
                .bizSeqNo(seq)
                .uniqueId(uniqueId)
                .prop(message.getPropertiesMap())
                .build();
            return (T) eventMeshMessage;
        }
    }

    public static <T> SimpleMessage buildSimpleMessage(T message, EventMeshGrpcClientConfig clientConfig,
                                                       String protocolType) {
        if (EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME.equals(protocolType)) {
            CloudEvent cloudEvent = (CloudEvent) message;
            String contentType = StringUtils.isEmpty(cloudEvent.getDataContentType()) ? Constants.CONTENT_TYPE_CLOUDEVENTS_JSON
                : cloudEvent.getDataContentType();
            byte[] bodyByte = Objects.requireNonNull(EventFormatProvider.getInstance().resolveFormat(contentType))
                .serialize(cloudEvent);
            String content = new String(bodyByte, StandardCharsets.UTF_8);
            String ttl = cloudEvent.getExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL) == null ? Constants.DEFAULT_EVENTMESH_MESSAGE_TTL
                : Objects.requireNonNull(cloudEvent.getExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL)).toString();

            String seqNum = cloudEvent.getExtension(ProtocolKey.SEQ_NUM) == null ? RandomStringUtils.generateNum(30)
                : Objects.requireNonNull(cloudEvent.getExtension(ProtocolKey.SEQ_NUM)).toString();

            String uniqueId = cloudEvent.getExtension(ProtocolKey.UNIQUE_ID) == null ? RandomStringUtils.generateNum(30)
                : Objects.requireNonNull(cloudEvent.getExtension(ProtocolKey.UNIQUE_ID)).toString();

            SimpleMessage.Builder builder = SimpleMessage.newBuilder()
                .setHeader(EventMeshClientUtil.buildHeader(clientConfig, protocolType))
                .setProducerGroup(clientConfig.getProducerGroup())
                .setTopic(cloudEvent.getSubject())
                .setTtl(ttl)
                .setSeqNum(seqNum)
                .setUniqueId(uniqueId)
                .setContent(content)
                .putProperties(ProtocolKey.CONTENT_TYPE, contentType);

            for (String extName : cloudEvent.getExtensionNames()) {
                builder.putProperties(extName, Objects.requireNonNull(cloudEvent.getExtension(extName)).toString());
            }

            return builder.build();
        } else {
            EventMeshMessage eventMeshMessage = (EventMeshMessage) message;

            String ttl = eventMeshMessage.getProp(Constants.EVENTMESH_MESSAGE_CONST_TTL) == null ? Constants.DEFAULT_EVENTMESH_MESSAGE_TTL
                : eventMeshMessage.getProp(Constants.EVENTMESH_MESSAGE_CONST_TTL);
            Map<String, String> props = eventMeshMessage.getProp() == null ? new HashMap<>() : eventMeshMessage.getProp();

            String seqNum = eventMeshMessage.getBizSeqNo() == null ? RandomStringUtils.generateNum(30)
                : eventMeshMessage.getBizSeqNo();

            String uniqueId = eventMeshMessage.getUniqueId() == null ? RandomStringUtils.generateNum(30)
                : eventMeshMessage.getUniqueId();

            return SimpleMessage.newBuilder()
                .setHeader(EventMeshClientUtil.buildHeader(clientConfig, protocolType))
                .setProducerGroup(clientConfig.getProducerGroup())
                .setTopic(eventMeshMessage.getTopic())
                .setContent(eventMeshMessage.getContent())
                .setSeqNum(seqNum)
                .setUniqueId(uniqueId)
                .setTtl(ttl)
                .putAllProperties(props)
                .build();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> BatchMessage buildBatchMessages(List<T> messageList, EventMeshGrpcClientConfig clientConfig,
                                                      String protocolType) {
        if (EventMeshCommon.CLOUD_EVENTS_PROTOCOL_NAME.equals(protocolType)) {
            List<CloudEvent> events = (List<CloudEvent>) messageList;
            BatchMessage.Builder messageBuilder = BatchMessage.newBuilder()
                .setHeader(EventMeshClientUtil.buildHeader(clientConfig, protocolType))
                .setProducerGroup(clientConfig.getProducerGroup())
                .setTopic(events.get(0).getSubject());

            for (CloudEvent event : events) {
                String contentType = StringUtils.isEmpty(event.getDataContentType()) ? Constants.CONTENT_TYPE_CLOUDEVENTS_JSON
                    : event.getDataContentType();
                byte[] bodyByte = Objects.requireNonNull(EventFormatProvider.getInstance().resolveFormat(contentType))
                    .serialize(event);
                String content = new String(bodyByte, StandardCharsets.UTF_8);

                String ttl = event.getExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL) == null ? Constants.DEFAULT_EVENTMESH_MESSAGE_TTL
                    : Objects.requireNonNull(event.getExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL)).toString();

                BatchMessage.MessageItem messageItem = BatchMessage.MessageItem.newBuilder()
                    .setContent(content)
                    .setTtl(ttl)
                    .setSeqNum(Objects.requireNonNull(event.getExtension(ProtocolKey.SEQ_NUM)).toString())
                    .setUniqueId(Objects.requireNonNull(event.getExtension(ProtocolKey.UNIQUE_ID)).toString())
                    .putProperties(ProtocolKey.CONTENT_TYPE, contentType)
                    .build();

                messageBuilder.addMessageItem(messageItem);
            }
            return messageBuilder.build();
        } else {
            List<EventMeshMessage> eventMeshMessages = (List<EventMeshMessage>) messageList;
            BatchMessage.Builder messageBuilder = BatchMessage.newBuilder()
                .setHeader(EventMeshClientUtil.buildHeader(clientConfig, protocolType))
                .setProducerGroup(clientConfig.getProducerGroup())
                .setTopic(eventMeshMessages.get(0).getTopic());

            for (EventMeshMessage message : eventMeshMessages) {
                BatchMessage.MessageItem item = BatchMessage.MessageItem.newBuilder()
                    .setContent(message.getContent())
                    .setUniqueId(message.getUniqueId())
                    .setSeqNum(message.getBizSeqNo())
                    .setTtl(
                        Optional.ofNullable(message.getProp(Constants.EVENTMESH_MESSAGE_CONST_TTL)).orElse(Constants.DEFAULT_EVENTMESH_MESSAGE_TTL))
                    .putAllProperties(message.getProp())
                    .build();
                messageBuilder.addMessageItem(item);
            }
            return messageBuilder.build();
        }
    }
}