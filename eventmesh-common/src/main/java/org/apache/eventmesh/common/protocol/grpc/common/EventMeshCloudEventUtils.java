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

package org.apache.eventmesh.common.protocol.grpc.common;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;

public abstract class EventMeshCloudEventUtils {

    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(Constants.DATE_FORMAT_DEFAULT);

    private EventMeshCloudEventUtils() {

    }

    public static String getEnv(CloudEvent cloudEvent) {
        return getEnv(cloudEvent, null);
    }

    public static String getEnv(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.ENV);
    }

    private static String getValue(CloudEvent cloudEvent, String defaultValue, String protocolKey) {
        try {
            return cloudEvent.getAttributesOrThrow(protocolKey).getCeString();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static String getIdc(CloudEvent cloudEvent) {
        return getIdc(cloudEvent, null);
    }

    public static String getIdc(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.IDC);
    }

    public static String getSys(CloudEvent cloudEvent) {
        return getSys(cloudEvent, null);
    }

    public static String getSys(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.SYS);
    }

    public static String getPid(CloudEvent cloudEvent) {
        return getPid(cloudEvent, null);
    }

    public static String getPid(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.PID);
    }

    public static String getIp(CloudEvent cloudEvent) {
        return getIp(cloudEvent, null);
    }

    public static String getIp(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.IP);
    }

    public static String getUserName(CloudEvent cloudEvent) {
        return getUserName(cloudEvent, null);
    }

    public static String getUserName(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.USERNAME);
    }

    public static String getPassword(CloudEvent cloudEvent) {
        return getPassword(cloudEvent, null);
    }

    public static String getPassword(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.PASSWD);
    }

    public static String getLanguage(CloudEvent cloudEvent) {
        return getLanguage(cloudEvent, null);
    }

    public static String getLanguage(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.LANGUAGE);
    }

    public static String getProtocolType(CloudEvent cloudEvent) {
        return getProtocolType(cloudEvent, null);
    }

    public static String getProtocolType(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.PROTOCOL_TYPE);
    }

    public static String getProtocolVersion(CloudEvent cloudEvent) {
        return getProtocolVersion(cloudEvent, null);
    }

    public static String getProtocolVersion(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.PROTOCOL_VERSION);
    }

    public static String getProtocolDesc(CloudEvent cloudEvent) {
        return getProtocolDesc(cloudEvent, null);
    }

    public static String getProtocolDesc(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.PROTOCOL_DESC);
    }

    public static String getSeqNum(CloudEvent cloudEvent) {
        return getSeqNum(cloudEvent, null);
    }

    public static String getSeqNum(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.SEQ_NUM);
    }

    public static String getUniqueId(CloudEvent cloudEvent) {
        return getUniqueId(cloudEvent, null);
    }

    public static String getUniqueId(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.UNIQUE_ID);
    }

    public static String getTtl(CloudEvent cloudEvent) {
        return getTtl(cloudEvent, null);
    }

    public static String getTtl(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.TTL);
    }

    public static String getProducerGroup(CloudEvent cloudEvent) {
        return getProducerGroup(cloudEvent, null);
    }

    public static String getProducerGroup(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.PRODUCERGROUP);
    }

    public static String getTag(CloudEvent cloudEvent) {
        return getTag(cloudEvent, null);
    }

    public static String getTag(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.TAG);
    }

    public static String getContentType(CloudEvent cloudEvent) {
        return getContentType(cloudEvent, null);
    }

    public static String getContentType(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.CONTENT_TYPE);
    }

    public static String getSubject(CloudEvent cloudEvent) {
        return getSubject(cloudEvent, null);
    }

    public static String getSubject(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.SUBJECT);
    }

    public static String getDataContentType(CloudEvent cloudEvent) {
        return getDataContentType(cloudEvent, null);
    }

    public static String getDataContentType(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.DATA_CONTENT_TYPE);
    }

    public static String getResponseCode(CloudEvent cloudEvent) {
        return getResponseCode(cloudEvent, null);
    }

    public static String getResponseCode(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.GRPC_RESPONSE_CODE);
    }

    public static String getResponseMessage(CloudEvent cloudEvent) {
        return getResponseMessage(cloudEvent, null);
    }

    public static String getResponseMessage(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.GRPC_RESPONSE_MESSAGE);
    }

    public static String getResponseTime(CloudEvent cloudEvent) {
        return getResponseTime(cloudEvent, null);
    }

    public static String getResponseTime(CloudEvent cloudEvent, String defaultValue) {
        try {
            Timestamp timestamp = cloudEvent.getAttributesOrThrow(ProtocolKey.GRPC_RESPONSE_TIME).getCeTimestamp();
            return covertProtoTimestamp(timestamp).toString();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static String getCluster(CloudEvent cloudEvent) {
        return getCluster(cloudEvent, null);
    }

    public static String getCluster(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.PROPERTY_MESSAGE_CLUSTER);
    }

    public static String getConsumerGroup(CloudEvent cloudEvent) {
        return getConsumerGroup(cloudEvent, null);
    }

    public static String getConsumerGroup(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.CONSUMERGROUP);
    }

    public static ClientType getClientType(CloudEvent cloudEvent) {
        return getClientType(cloudEvent, null);
    }

    public static ClientType getClientType(CloudEvent cloudEvent, ClientType defaultValue) {
        try {
            int type = cloudEvent.getAttributesOrThrow(ProtocolKey.CLIENT_TYPE).getCeInteger();
            return ClientType.get(type);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static String getURL(CloudEvent cloudEvent) {
        return getURL(cloudEvent, null);
    }

    public static String getURL(CloudEvent cloudEvent, String defaultValue) {
        return getValue(cloudEvent, defaultValue, ProtocolKey.URL);
    }

    public static String getDataContent(CloudEvent cloudEvent) {
        return getDataContent(cloudEvent, null);
    }

    public static String getDataContent(final CloudEvent cloudEvent, String defaultValue) {
        String dataContentType = getDataContentType(cloudEvent);
        if (ProtoSupport.isTextContent(dataContentType)) {
            return Optional.of(cloudEvent.getTextData()).orElse(defaultValue);
        }
        if (ProtoSupport.isProtoContent(dataContentType)) {
            Any protoData = cloudEvent.getProtoData();
            return protoData == Any.getDefaultInstance() ? defaultValue
                : new String(protoData.toByteArray(), Constants.DEFAULT_CHARSET);
        }
        ByteString binaryData = cloudEvent.getBinaryData();
        return ByteString.EMPTY == binaryData ? defaultValue : binaryData.toStringUtf8();


    }

    public static Map<String, String> getAttributes(final CloudEvent cloudEvent) {
        if (Objects.isNull(cloudEvent)) {
            return new HashMap<>(0);
        }
        Map<String, CloudEventAttributeValue> attributesMap = Optional.of(cloudEvent.getAttributesMap()).orElse(new HashMap<>(0));
        Map<String, String> convertedAttributes = new HashMap<>(attributesMap.size());
        attributesMap.forEach((key, value) -> {
            if (Objects.isNull(value)) {
                return;
            }
            if (value.hasCeBoolean()) {
                convertedAttributes.put(key, Boolean.toString(value.getCeBoolean()));
                return;
            }
            if (value.hasCeInteger()) {
                convertedAttributes.put(key, Integer.toString(value.getCeInteger()));
                return;
            }
            if (value.hasCeString()) {
                convertedAttributes.put(key, value.getCeString());
                return;
            }
            if (value.hasCeBytes()) {
                convertedAttributes.put(key, value.getCeBytes().toString(Constants.DEFAULT_CHARSET));
                return;
            }
            if (value.hasCeUri()) {
                convertedAttributes.put(key, value.getCeUri());
                return;
            }
            if (value.hasCeUriRef()) {
                convertedAttributes.put(key, value.getCeUriRef());
                return;
            }
            if (value.hasCeTimestamp()) {
                OffsetDateTime offsetDateTime = covertProtoTimestamp(value.getCeTimestamp());
                convertedAttributes.put(key, dateTimeFormatter.format(offsetDateTime.toLocalDateTime()));
            }
        });
        return convertedAttributes;
    }

    private static OffsetDateTime covertProtoTimestamp(com.google.protobuf.Timestamp timestamp) {
        Instant instant = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
        return instant.atOffset(ZoneOffset.UTC);
    }

}
