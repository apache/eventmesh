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
import org.apache.eventmesh.client.grpc.exception.ProtocolNotSupportException;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.enums.EventMeshProtocolType;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEventBatch;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.ProtoSupport;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.protobuf.ProtobufFormat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshCloudEventBuilder {

    private static final String CLOUD_EVENT_TYPE = "org.apache.eventmesh";

    private static final EventFormat eventProtoFormat = EventFormatProvider.getInstance().resolveFormat(ProtobufFormat.PROTO_CONTENT_TYPE);

    public static Map<String, CloudEventAttributeValue> buildCommonCloudEventAttributes(EventMeshGrpcClientConfig clientConfig,
        EventMeshProtocolType protocolType) {
        final Map<String, CloudEventAttributeValue> attributeValueMap = new HashMap<>(64);
        attributeValueMap.put(ProtocolKey.ENV, CloudEventAttributeValue.newBuilder().setCeString(clientConfig.getEnv()).build());
        attributeValueMap.put(ProtocolKey.IDC, CloudEventAttributeValue.newBuilder().setCeString(clientConfig.getIdc()).build());
        attributeValueMap.put(ProtocolKey.IP, CloudEventAttributeValue.newBuilder().setCeString(IPUtils.getLocalAddress()).build());
        attributeValueMap.put(ProtocolKey.PID, CloudEventAttributeValue.newBuilder().setCeString(Long.toString(ThreadUtils.getPID())).build());
        attributeValueMap.put(ProtocolKey.SYS, CloudEventAttributeValue.newBuilder().setCeString(clientConfig.getSys()).build());
        attributeValueMap.put(ProtocolKey.LANGUAGE, CloudEventAttributeValue.newBuilder().setCeString(clientConfig.getLanguage()).build());
        attributeValueMap.put(ProtocolKey.USERNAME, CloudEventAttributeValue.newBuilder().setCeString(clientConfig.getUserName()).build());
        attributeValueMap.put(ProtocolKey.PASSWD, CloudEventAttributeValue.newBuilder().setCeString(clientConfig.getPassword()).build());
        attributeValueMap.put(ProtocolKey.PROTOCOL_TYPE, CloudEventAttributeValue.newBuilder().setCeString(protocolType.protocolTypeName()).build());
        attributeValueMap.put(ProtocolKey.PROTOCOL_VERSION, CloudEventAttributeValue.newBuilder().setCeString(SpecVersion.V1.toString()).build());

        return attributeValueMap;
    }

    public static CloudEvent buildEventSubscription(EventMeshGrpcClientConfig clientConfig, EventMeshProtocolType protocolType, String url,
        List<SubscriptionItem> subscriptionItems) {

        if (CollectionUtils.isEmpty(subscriptionItems)) {
            return null;
        }
        Set<SubscriptionItem> subscriptionItemSet = new HashSet<>();
        subscriptionItemSet.addAll(subscriptionItems);

        final Map<String, CloudEventAttributeValue> attributeValueMap = buildCommonCloudEventAttributes(clientConfig, protocolType);
        attributeValueMap.put(ProtocolKey.CONSUMERGROUP, CloudEventAttributeValue.newBuilder().setCeString(clientConfig.getConsumerGroup()).build());
        attributeValueMap.put(ProtocolKey.DATA_CONTENT_TYPE, CloudEventAttributeValue.newBuilder().setCeString("application/json").build());
        if (StringUtils.isNotBlank(url)) {
            attributeValueMap.put(ProtocolKey.URL, CloudEventAttributeValue.newBuilder().setCeString(url).build());
        }
        return CloudEvent.newBuilder()
            .setId(RandomStringUtils.generateUUID())
            .setSource(URI.create("/").toString())
            .setSpecVersion(SpecVersion.V1.toString())
            .setType(CLOUD_EVENT_TYPE)
            .setTextData(JsonUtils.toJSONString(subscriptionItemSet))
            .putAllAttributes(attributeValueMap)
            .build();
    }

    /**
     * @param message
     * @param clientConfig
     * @param protocolType
     * @param <T>
     * @return CloudEvent
     * @see <a href="https://github.com/cloudevents/spec/blob/v1.0.2/cloudevents/spec.md#context-attributes">context-attributes</a>
     */
    public static <T> CloudEvent buildEventMeshCloudEvent(final T message, final EventMeshGrpcClientConfig clientConfig,
        final EventMeshProtocolType protocolType) {

        switch (protocolType) {
            case CLOUD_EVENTS: {
                if (!(message instanceof io.cloudevents.CloudEvent)) {
                    throw new ClassCastException(message.getClass().getName() + " can not cast io.cloudevents.CloudEvent");
                }
                return switchCloudEvent2EventMeshCloudEvent((io.cloudevents.CloudEvent) message, clientConfig, protocolType);
            }
            case EVENT_MESH_MESSAGE: {
                if (!(message instanceof EventMeshMessage)) {
                    throw new ClassCastException(message.getClass().getName() + " can not cast" + EventMeshMessage.class.getName());
                }
                return switchEventMeshMessage2EventMeshCloudEvent((EventMeshMessage) message, clientConfig, protocolType);
            }
            case OPEN_MESSAGE:
                return null;
            default:
                throw new ProtocolNotSupportException("Protocol Type [" + protocolType + "] not support");
        }
    }

    private static CloudEvent switchEventMeshMessage2EventMeshCloudEvent(EventMeshMessage message, EventMeshGrpcClientConfig clientConfig,
        EventMeshProtocolType protocolType) {
        final EventMeshMessage eventMeshMessage = message;
        final String ttl = eventMeshMessage.getProp(Constants.EVENTMESH_MESSAGE_CONST_TTL) == null
            ? Constants.DEFAULT_EVENTMESH_MESSAGE_TTL : eventMeshMessage.getProp(Constants.EVENTMESH_MESSAGE_CONST_TTL);
        final Map<String, String> props = eventMeshMessage.getProp() == null ? new HashMap<>() : eventMeshMessage.getProp();
        final String seqNum = eventMeshMessage.getBizSeqNo() == null ? RandomStringUtils.generateNum(30) : eventMeshMessage.getBizSeqNo();
        final String uniqueId = eventMeshMessage.getUniqueId() == null ? RandomStringUtils.generateNum(30) : eventMeshMessage.getUniqueId();
        final String dataContentType = props.computeIfAbsent(ProtocolKey.DATA_CONTENT_TYPE, (key) -> "text/plain");
        final Map<String, CloudEventAttributeValue> attributeValueMap = buildCommonCloudEventAttributes(clientConfig, protocolType);

        attributeValueMap.put(ProtocolKey.TTL, CloudEventAttributeValue.newBuilder().setCeString(ttl).build());
        attributeValueMap.put(ProtocolKey.SEQ_NUM, CloudEventAttributeValue.newBuilder().setCeString(seqNum).build());
        attributeValueMap.put(ProtocolKey.UNIQUE_ID, CloudEventAttributeValue.newBuilder().setCeString(uniqueId).build());
        attributeValueMap.put(ProtocolKey.PRODUCERGROUP,
            CloudEventAttributeValue.newBuilder().setCeString(clientConfig.getProducerGroup()).build());
        if (null != eventMeshMessage.getTopic()) {
            attributeValueMap.put(ProtocolKey.SUBJECT, CloudEventAttributeValue.newBuilder().setCeString(eventMeshMessage.getTopic()).build());
        }
        attributeValueMap.put(ProtocolKey.DATA_CONTENT_TYPE, CloudEventAttributeValue.newBuilder().setCeString("text/plain").build());
        props.entrySet()
            .forEach(
                entry -> attributeValueMap.put(entry.getKey(), CloudEventAttributeValue.newBuilder().setCeString(entry.getValue()).build()));
        CloudEvent.Builder builder = CloudEvent.newBuilder()
            .setId(RandomStringUtils.generateUUID())
            .setSource(URI.create("/").toString())
            .setSpecVersion(SpecVersion.V1.toString())
            .setType(CLOUD_EVENT_TYPE)
            .putAllAttributes(attributeValueMap);
        final String content = eventMeshMessage.getContent();
        if (StringUtils.isNotEmpty(content)) {
            if (ProtoSupport.isTextContent(dataContentType)) {
                builder.setTextData(content);
            } else if (ProtoSupport.isProtoContent(dataContentType)) {
                try {
                    Any dataAsAny = Any.parseFrom(content.getBytes(Constants.DEFAULT_CHARSET));
                    builder.setProtoData(dataAsAny);
                } catch (InvalidProtocolBufferException e) {
                    throw new IllegalArgumentException("parse from byte[] to com.google.protobuf.Any error", e);
                }
            } else {
                ByteString byteString = ByteString.copyFrom(content.getBytes(Constants.DEFAULT_CHARSET));
                builder.setBinaryData(byteString);
            }
        }
        return builder.build();
    }

    private static CloudEvent switchCloudEvent2EventMeshCloudEvent(io.cloudevents.CloudEvent message, EventMeshGrpcClientConfig clientConfig,
        EventMeshProtocolType protocolType) {
        final io.cloudevents.CloudEvent cloudEvent = message;
        CloudEventBuilder cloudEventBuilder = CloudEventBuilder.from(cloudEvent);
        if (null == cloudEvent.getExtension(ProtocolKey.ENV)) {
            cloudEventBuilder.withExtension(ProtocolKey.ENV, clientConfig.getEnv());
        }
        if (null == cloudEvent.getExtension(ProtocolKey.IDC)) {
            cloudEventBuilder.withExtension(ProtocolKey.IDC, clientConfig.getEnv());
        }
        if (null == cloudEvent.getExtension(ProtocolKey.IP)) {
            cloudEventBuilder.withExtension(ProtocolKey.IP, Objects.requireNonNull(IPUtils.getLocalAddress()));
        }
        if (null == cloudEvent.getExtension(ProtocolKey.PID)) {
            cloudEventBuilder.withExtension(ProtocolKey.PID, Long.toString(ThreadUtils.getPID()));
        }
        if (null == cloudEvent.getExtension(ProtocolKey.SYS)) {
            cloudEventBuilder.withExtension(ProtocolKey.SYS, clientConfig.getSys());
        }

        if (null == cloudEvent.getExtension(ProtocolKey.LANGUAGE)) {
            cloudEventBuilder.withExtension(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA);
        }

        if (null == cloudEvent.getExtension(ProtocolKey.PROTOCOL_TYPE)) {
            cloudEventBuilder.withExtension(ProtocolKey.PROTOCOL_TYPE, protocolType.protocolTypeName());
        }
        if (null == cloudEvent.getExtension(ProtocolKey.PROTOCOL_DESC)) {
            cloudEventBuilder.withExtension(ProtocolKey.PROTOCOL_DESC, "grpc-cloud-event");
        }
        if (null == cloudEvent.getExtension(ProtocolKey.PROTOCOL_VERSION)) {
            cloudEventBuilder.withExtension(ProtocolKey.PROTOCOL_VERSION, cloudEvent.getSpecVersion().toString());
        }
        if (null == cloudEvent.getExtension(ProtocolKey.UNIQUE_ID)) {
            cloudEventBuilder.withExtension(ProtocolKey.UNIQUE_ID, RandomStringUtils.generateNum(30));
        }
        if (null == cloudEvent.getExtension(ProtocolKey.SEQ_NUM)) {
            cloudEventBuilder.withExtension(ProtocolKey.SEQ_NUM, RandomStringUtils.generateNum(30));
        }
        if (null == cloudEvent.getExtension(ProtocolKey.USERNAME)) {
            cloudEventBuilder.withExtension(ProtocolKey.USERNAME, clientConfig.getUserName());
        }
        if (null == cloudEvent.getExtension(ProtocolKey.PASSWD)) {
            cloudEventBuilder.withExtension(ProtocolKey.PASSWD, clientConfig.getPassword());
        }
        if (null == cloudEvent.getExtension(ProtocolKey.PRODUCERGROUP)) {
            cloudEventBuilder.withExtension(ProtocolKey.PRODUCERGROUP, clientConfig.getProducerGroup());
        }
        if (null == cloudEvent.getExtension(ProtocolKey.TTL)) {
            final String ttl = Constants.DEFAULT_EVENTMESH_MESSAGE_TTL;
            cloudEventBuilder.withExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL, ttl);
        }
        try {
            return CloudEvent.parseFrom(eventProtoFormat.serialize(cloudEventBuilder.build()));
        } catch (InvalidProtocolBufferException exc) {
            log.error("Parse from CloudEvents CloudEvent bytes to EventMesh CloudEvent error", exc);
        }
        return null;
    }

    public static <T> CloudEventBatch buildEventMeshCloudEventBatch(final List<T> messageList, final EventMeshGrpcClientConfig clientConfig,
        final EventMeshProtocolType protocolType) {
        if (CollectionUtils.isEmpty(messageList)) {
            return null;
        }
        List<CloudEvent> cloudEventList = messageList.stream().map(item -> buildEventMeshCloudEvent(item, clientConfig, protocolType))
            .collect(Collectors.toList());
        return CloudEventBatch.newBuilder().addAllEvents(cloudEventList).build();
    }

    public static <T> T buildMessageFromEventMeshCloudEvent(final CloudEvent cloudEvent, final EventMeshProtocolType protocolType) {

        if (null == cloudEvent) {
            return null;
        }
        final String seq = EventMeshCloudEventUtils.getSeqNum(cloudEvent);
        final String uniqueId = EventMeshCloudEventUtils.getUniqueId(cloudEvent);
        final String content = EventMeshCloudEventUtils.getDataContent(cloudEvent);

        // This is GRPC response cloudEvent
        if (StringUtils.isEmpty(seq) && StringUtils.isEmpty(uniqueId)) {
            //The SubscriptionItem collection contains the content for the subscription.
            return (T) JsonUtils.parseTypeReferenceObject(content,
                new TypeReference<Set<HashMap<String, String>>>() {

                });
        }
        if (null == protocolType) {
            return null;
        }

        switch (protocolType) {
            case CLOUD_EVENTS:
                return (T) switchEventMeshCloudEvent2CloudEvent(cloudEvent);
            case EVENT_MESH_MESSAGE:
                return (T) switchEventMeshCloudEvent2EventMeshMessage(cloudEvent);
            case OPEN_MESSAGE:
            default:
                return null;
        }
    }

    private static io.cloudevents.CloudEvent switchEventMeshCloudEvent2CloudEvent(final CloudEvent cloudEvent) {

        return eventProtoFormat.deserialize(Objects.requireNonNull(cloudEvent).toByteArray());
    }

    private static EventMeshMessage switchEventMeshCloudEvent2EventMeshMessage(final CloudEvent cloudEvent) {
        Map<String, String> prop = new HashMap<>();
        Objects.requireNonNull(cloudEvent).getAttributesMap().forEach((key, value) -> prop.put(key, value.getCeString()));
        return EventMeshMessage.builder()
            .content(cloudEvent.getTextData())
            .topic(EventMeshCloudEventUtils.getSubject(cloudEvent))
            .bizSeqNo(EventMeshCloudEventUtils.getSeqNum(cloudEvent))
            .uniqueId(EventMeshCloudEventUtils.getUniqueId(cloudEvent))
            .prop(prop)
            .build();
    }

}
