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

package org.apache.eventmesh.runtime.grpc;

import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;

import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Maps the legacy SDK gRPC proto {@link CloudEvent} (org.apache.eventmesh.cloudevents.v1) to the v2
 * runtime's {@link io.cloudevents.CloudEvent} and back (issue #5411).
 *
 * <p>Inbound (proto -> v2): the proto's well-known attribute keys (subject carries the topic; the
 * {@code ProtocolKey.*} entries become CloudEvents extensions) and the oneof data field
 * (textData / binaryData / protoData) become the event data bytes. Outbound (v2 -> proto) is the
 * inverse, used by the subscribeStream push path so the legacy
 * {@code EventMeshGrpcConsumer} can deserialize what it receives
 * ({@code EventMeshCloudEventBuilder#buildMessageFromEventMeshCloudEvent} with the
 * EVENT_MESH_MESSAGE protocol type reads textData + subject/seqnum/uniqueid attributes).</p>
 *
 * <p>Pure functions, no runtime state - hermetically unit-testable.</p>
 */
public final class GrpcCloudEventMapper {

    private GrpcCloudEventMapper() {
    }

    /**
     * proto {@link CloudEvent} -> v2 {@link io.cloudevents.CloudEvent}.
     *
     * <p>The proto's {@code subject} attribute carries the destination topic (see
     * {@code EventMeshCloudEventBuilder#switchEventMeshMessage2EventMeshCloudEvent}); it is mapped
     * onto the CloudEvents {@code subject} attribute so the v2 pipeline keeps topic routing intact.
     * All other attributes become CloudEvents extensions (string-typed; numeric/boolean proto
     * attribute values are flattened to their string form).</p>
     */
    public static io.cloudevents.CloudEvent toV2(CloudEvent proto) {
        CloudEventBuilder builder = CloudEventBuilder.v1()
            .withId(proto.getId())
            .withSource(URI.create(proto.getSource().isEmpty() ? "/" : proto.getSource()))
            .withType(proto.getType().isEmpty() ? "org.apache.eventmesh" : proto.getType());
        if (proto.hasTextData()) {
            builder.withData(proto.getTextData().getBytes(StandardCharsets.UTF_8));
        } else if (proto.hasBinaryData()) {
            builder.withData(proto.getBinaryData().toByteArray());
        } else if (proto.hasProtoData()) {
            builder.withData(proto.getProtoData().toByteArray());
        }
        for (Map.Entry<String, CloudEventAttributeValue> attr : proto.getAttributesMap().entrySet()) {
            String key = attr.getKey();
            String value = stringify(attr.getValue());
            if (value == null) {
                continue;
            }
            if (ProtocolKey.SUBJECT.equals(key)) {
                builder.withSubject(value);
            } else {
                builder.withExtension(key, value);
            }
        }
        return builder.build();
    }

    /**
     * v2 {@link io.cloudevents.CloudEvent} -> proto {@link CloudEvent}, for the subscribeStream
     * push path. Mirrors what the legacy 1.x runtime sent: id/source/specversion/type top-level,
     * every attribute + extension in the attributes map, and the event data as
     * {@code textData} (the legacy SDK's EventMeshMessage deserializer reads textData; binary
     * payloads are surfaced through textData as UTF-8 like 1.x did for String contents).
     */
    public static CloudEvent toProto(io.cloudevents.CloudEvent event) {
        CloudEvent.Builder builder = CloudEvent.newBuilder()
            .setId(event.getId() == null ? "" : event.getId())
            .setSource(event.getSource() == null ? "/" : event.getSource().toString())
            .setSpecVersion(event.getSpecVersion() == null ? "1.0" : event.getSpecVersion().toString())
            .setType(event.getType() == null ? "org.apache.eventmesh" : event.getType());
        if (event.getSubject() != null) {
            builder.putAttributes(ProtocolKey.SUBJECT, CloudEventAttributeValue.newBuilder()
                .setCeString(event.getSubject()).build());
        }
        for (String name : event.getAttributeNames()) {
            Object v = event.getAttribute(name);
            if (v != null) {
                putAttr(builder, name, v.toString());
            }
        }
        for (String name : event.getExtensionNames()) {
            Object v = event.getExtension(name);
            if (v != null) {
                putAttr(builder, name, v.toString());
            }
        }
        if (event.getData() != null) {
            byte[] data = event.getData().toBytes();
            if (data.length > 0) {
                builder.setTextData(new String(data, StandardCharsets.UTF_8));
            }
        }
        return builder.build();
    }

    /**
     * Build a legacy {@link CloudEvent} response envelope: the 1.x contract answers publish /
     * subscribe / unsubscribe / heartbeat calls with a CloudEvent whose attributes carry
     * {@code statuscode} / {@code responsemessage} / {@code time} (see {@code StatusCode} +
     * {@code EventMeshCloudEventUtils#getResponseCode}). The SDK parses exactly these three keys.
     */
    public static CloudEvent response(String retCode, String message) {
        return CloudEvent.newBuilder()
            .setId("resp-" + java.util.UUID.randomUUID())
            .setSource("/")
            .setSpecVersion("1.0")
            .setType("org.apache.eventmesh")
            .putAttributes(ProtocolKey.GRPC_RESPONSE_CODE,
                CloudEventAttributeValue.newBuilder().setCeString(retCode).build())
            .putAttributes(ProtocolKey.GRPC_RESPONSE_MESSAGE,
                CloudEventAttributeValue.newBuilder().setCeString(message).build())
            .putAttributes(ProtocolKey.GRPC_RESPONSE_TIME,
                CloudEventAttributeValue.newBuilder()
                    .setCeTimestamp(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(System.currentTimeMillis() / 1000)
                        .build())
                    .build())
            .build();
    }

    /** Legacy success envelope (StatusCode SUCCESS = "0"). */
    public static CloudEvent okResponse() {
        return response(org.apache.eventmesh.common.protocol.grpc.common.StatusCode.SUCCESS.getRetCode(),
            org.apache.eventmesh.common.protocol.grpc.common.StatusCode.SUCCESS.getErrMsg());
    }

    /** Legacy error envelope for the given status code (+ detail appended to the stock message). */
    public static CloudEvent errorResponse(
        org.apache.eventmesh.common.protocol.grpc.common.StatusCode code, String detail) {
        return response(code.getRetCode(), code.getErrMsg() + (detail == null ? "" : detail));
    }

    private static void putAttr(CloudEvent.Builder builder, String key, String value) {
        if (key == null || value == null) {
            return;
        }
        builder.putAttributes(key, CloudEventAttributeValue.newBuilder().setCeString(value).build());
    }

    private static String stringify(CloudEventAttributeValue value) {
        if (value == null) {
            return null;
        }
        if (value.hasCeString()) {
            return value.getCeString();
        }
        if (value.hasCeInteger()) {
            return Integer.toString(value.getCeInteger());
        }
        if (value.hasCeBoolean()) {
            return Boolean.toString(value.getCeBoolean());
        }
        if (value.hasCeUri() || value.hasCeUriRef()) {
            return value.hasCeUri() ? value.getCeUri() : value.getCeUriRef();
        }
        if (value.hasCeBytes()) {
            return value.getCeBytes().toString(StandardCharsets.UTF_8);
        }
        if (value.hasCeTimestamp()) {
            OffsetDateTime t = OffsetDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(value.getCeTimestamp().getSeconds(),
                    value.getCeTimestamp().getNanos()),
                java.time.ZoneOffset.UTC);
            return t.toString();
        }
        return null;
    }

    /** The destination topic of a publish/subscribe proto event ({@code subject} attribute). */
    public static String topicOf(CloudEvent proto) {
        return EventMeshCloudEventUtils.getSubject(proto, "");
    }

    /** The consumer group of a subscribe/heartbeat proto event. */
    public static String consumerGroupOf(CloudEvent proto) {
        return EventMeshCloudEventUtils.getConsumerGroup(proto, "");
    }

    /** The webhook URL of a subscribe proto event (empty for stream subscriptions). */
    public static String urlOf(CloudEvent proto) {
        return EventMeshCloudEventUtils.getURL(proto, "");
    }
}
