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

import org.apache.eventmesh.common.protocol.HeartbeatItem;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEventBatch;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.ProtoSupport;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat;
import org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat.ClientType;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.GrpcType;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;

public class ServiceUtils {

    public static boolean validateHeader(RequestHeader header) {
        return StringUtils.isNotEmpty(header.getIdc())
            && StringUtils.isNotEmpty(header.getEnv())
            && StringUtils.isNotEmpty(header.getIp())
            && StringUtils.isNotEmpty(header.getPid())
            && StringUtils.isNumeric(header.getPid())
            && StringUtils.isNotEmpty(header.getSys())
            && StringUtils.isNotEmpty(header.getUsername())
            && StringUtils.isNotEmpty(header.getPassword())
            && StringUtils.isNotEmpty(header.getLanguage());
    }

    public static boolean validateCloudEventAttributes(CloudEvent cloudEvent) {
        return StringUtils.isNotEmpty(EventMeshCloudEventUtils.getIdc(cloudEvent))
            && StringUtils.isNotEmpty(EventMeshCloudEventUtils.getEnv(cloudEvent))
            && StringUtils.isNotEmpty(EventMeshCloudEventUtils.getIp(cloudEvent))
            && StringUtils.isNotEmpty(EventMeshCloudEventUtils.getPid(cloudEvent))
            && StringUtils.isNumeric(EventMeshCloudEventUtils.getPid(cloudEvent))
            && StringUtils.isNotEmpty(EventMeshCloudEventUtils.getSys(cloudEvent))
            && StringUtils.isNotEmpty(EventMeshCloudEventUtils.getUserName(cloudEvent))
            && StringUtils.isNotEmpty(EventMeshCloudEventUtils.getPassword(cloudEvent))
            && StringUtils.isNotEmpty(EventMeshCloudEventUtils.getLanguage(cloudEvent));
    }

    public static boolean validateCloudEventBatchAttributes(CloudEventBatch cloudEventBatch) {
        if (null == cloudEventBatch || cloudEventBatch.getEventsCount() < 1) {
            return false;
        }
        List<CloudEvent> eventsList = cloudEventBatch.getEventsList();
        for (CloudEvent cloudEvent : eventsList) {
            if (validateCloudEventAttributes(cloudEvent)) {
                continue;
            }
            return false;
        }
        return true;
    }

    public static boolean validateMessage(SimpleMessage message) {
        return StringUtils.isNotEmpty(message.getUniqueId())
            && StringUtils.isNotEmpty(message.getProducerGroup())
            && StringUtils.isNotEmpty(message.getTopic())
            && StringUtils.isNotEmpty(message.getContent())
            && StringUtils.isNotEmpty(message.getTtl());
    }

    public static boolean validateCloudEventData(CloudEvent cloudEvent) {
        boolean flag = StringUtils.isNotEmpty(EventMeshCloudEventUtils.getUniqueId(cloudEvent))
            && StringUtils.isNotEmpty(EventMeshCloudEventUtils.getProducerGroup(cloudEvent))
            && StringUtils.isNotEmpty(EventMeshCloudEventUtils.getSubject(cloudEvent))
            && StringUtils.isNotEmpty(EventMeshCloudEventUtils.getTtl(cloudEvent));
        if (!flag) {
            return false;
        }
        final String dataContentType = EventMeshCloudEventUtils.getDataContentType(cloudEvent);
        if (ProtoSupport.isTextContent(dataContentType)) {
            return flag & (StringUtils.isNotEmpty(cloudEvent.getTextData()));
        }
        if (ProtoSupport.isProtoContent(dataContentType)) {
            return flag & (cloudEvent.getProtoData() != Any.getDefaultInstance());
        }

        return flag & (cloudEvent.getBinaryData() != ByteString.EMPTY);
    }

    public static boolean validateCloudEventBatchData(CloudEventBatch cloudEventBatch) {
        if (null == cloudEventBatch || cloudEventBatch.getEventsCount() < 1) {
            return false;
        }
        List<CloudEvent> eventsList = cloudEventBatch.getEventsList();
        for (CloudEvent cloudEvent : eventsList) {
            if (validateCloudEventData(cloudEvent)) {
                continue;
            }
            return false;
        }
        return true;
    }

    public static boolean validateBatchMessage(BatchMessage batchMessage) {
        if (StringUtils.isEmpty(batchMessage.getTopic()) || StringUtils.isEmpty(batchMessage.getProducerGroup())) {
            return false;
        }
        for (BatchMessage.MessageItem item : batchMessage.getMessageItemList()) {
            if (StringUtils.isEmpty(item.getContent()) || StringUtils.isEmpty(item.getSeqNum())
                || StringUtils.isEmpty(item.getTtl())
                || StringUtils.isEmpty(item.getUniqueId())) {
                return false;
            }
        }
        return true;
    }

    public static boolean validateSubscription(GrpcType grpcType, Subscription subscription) {
        if (GrpcType.WEBHOOK == grpcType && StringUtils.isEmpty(subscription.getUrl())) {
            return false;
        }
        if (CollectionUtils.isEmpty(subscription.getSubscriptionItemsList())
            || StringUtils.isEmpty(subscription.getConsumerGroup())) {
            return false;
        }
        for (Subscription.SubscriptionItem item : subscription.getSubscriptionItemsList()) {
            if (StringUtils.isEmpty(item.getTopic())
                || item.getMode() == Subscription.SubscriptionItem.SubscriptionMode.UNRECOGNIZED
                || item.getType() == Subscription.SubscriptionItem.SubscriptionType.UNRECOGNIZED) {
                return false;
            }
        }
        return true;
    }

    public static boolean validateSubscription(GrpcType grpcType, CloudEvent subscription) {
        if (GrpcType.WEBHOOK == grpcType && StringUtils.isEmpty(EventMeshCloudEventUtils.getURL(subscription))) {
            return false;
        }
        List<SubscriptionItem> subscriptionItems = JsonUtils.parseTypeReferenceObject(subscription.getTextData(),
            new TypeReference<List<SubscriptionItem>>() {
            });
        if (CollectionUtils.isEmpty(subscriptionItems)
            || StringUtils.isEmpty(EventMeshCloudEventUtils.getConsumerGroup(subscription))) {
            return false;
        }
        for (SubscriptionItem item : subscriptionItems) {
            if (StringUtils.isEmpty(item.getTopic())
                || item.getMode() == SubscriptionMode.UNRECOGNIZED
                || item.getType() == SubscriptionType.UNRECOGNIZED) {
                return false;
            }
        }
        return true;
    }

    public static boolean validateHeartBeat(Heartbeat heartbeat) {
        if (ClientType.SUB == heartbeat.getClientType() && StringUtils.isEmpty(heartbeat.getConsumerGroup())) {
            return false;
        }
        if (ClientType.PUB == heartbeat.getClientType() && StringUtils.isEmpty(heartbeat.getProducerGroup())) {
            return false;
        }
        for (Heartbeat.HeartbeatItem item : heartbeat.getHeartbeatItemsList()) {
            if (StringUtils.isEmpty(item.getTopic())) {
                return false;
            }
        }
        return true;
    }

    public static boolean validateHeartBeat(CloudEvent heartbeat) {
        org.apache.eventmesh.common.protocol.grpc.common.ClientType clientType = EventMeshCloudEventUtils.getClientType(heartbeat);
        if (org.apache.eventmesh.common.protocol.grpc.common.ClientType.SUB == clientType && StringUtils.isEmpty(
            EventMeshCloudEventUtils.getConsumerGroup(heartbeat))) {
            return false;
        }
        if (org.apache.eventmesh.common.protocol.grpc.common.ClientType.PUB == clientType && StringUtils.isEmpty(
            EventMeshCloudEventUtils.getProducerGroup(heartbeat))) {
            return false;
        }
        List<HeartbeatItem> heartbeatItems = JsonUtils.parseTypeReferenceObject(heartbeat.getTextData(),
            new TypeReference<List<HeartbeatItem>>() {
            });
        for (HeartbeatItem item : heartbeatItems) {
            if (StringUtils.isEmpty(item.getTopic())) {
                return false;
            }
        }
        return true;
    }

    public static void sendRespAndDone(StatusCode code, EventEmitter<Response> emitter) {
        Response response = Response.newBuilder()
            .setRespCode(code.getRetCode())
            .setRespMsg(code.getErrMsg())
            .setRespTime(String.valueOf(System.currentTimeMillis()))
            .build();
        emitter.onNext(response);
        emitter.onCompleted();
    }

    public static void sendRespAndDone(StatusCode code, String message, EventEmitter<Response> emitter) {
        Response response = Response.newBuilder()
            .setRespCode(code.getRetCode())
            .setRespMsg(code.getErrMsg() + EventMeshConstants.BLANK_SPACE + message)
            .setRespTime(String.valueOf(System.currentTimeMillis()))
            .build();
        emitter.onNext(response);
        emitter.onCompleted();
    }

    public static void completed(StatusCode code, String message, EventEmitter<CloudEvent> emitter) {

        Instant instant = now();
        CloudEvent.Builder builder = CloudEvent.newBuilder().setId(RandomStringUtils.generateUUID())
            .putAttributes(ProtocolKey.GRPC_RESPONSE_CODE, CloudEventAttributeValue.newBuilder().setCeString(code.getRetCode()).build())
            .putAttributes(ProtocolKey.GRPC_RESPONSE_MESSAGE,
                CloudEventAttributeValue.newBuilder().setCeString(code.getErrMsg() + EventMeshConstants.BLANK_SPACE + message).build())
            .putAttributes(ProtocolKey.GRPC_RESPONSE_TIME, CloudEventAttributeValue.newBuilder()
                .setCeTimestamp(Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build()).build());

        emitter.onNext(builder.build());
        emitter.onCompleted();
    }

    public static void completed(StatusCode code, EventEmitter<CloudEvent> emitter) {
        Instant instant = now();
        CloudEvent.Builder builder = CloudEvent.newBuilder()
            .putAttributes(ProtocolKey.GRPC_RESPONSE_CODE, CloudEventAttributeValue.newBuilder().setCeString(code.getRetCode()).build())
            .putAttributes(ProtocolKey.GRPC_RESPONSE_MESSAGE,
                CloudEventAttributeValue.newBuilder().setCeString(code.getErrMsg()).build())
            .putAttributes(ProtocolKey.GRPC_RESPONSE_TIME, CloudEventAttributeValue.newBuilder()
                .setCeTimestamp(Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build()).build());
        emitter.onNext(builder.build());
        emitter.onCompleted();
    }

    public static void sendStreamResp(RequestHeader header, StatusCode code, String message,
        EventEmitter<SimpleMessage> emitter) {
        Map<String, String> resp = new HashMap<>();
        resp.put(EventMeshConstants.RESP_CODE, code.getRetCode());
        resp.put(EventMeshConstants.RESP_MSG, code.getErrMsg() + EventMeshConstants.BLANK_SPACE + message);

        SimpleMessage simpleMessage = SimpleMessage.newBuilder()
            .setHeader(header)
            .setContent(JsonUtils.toJSONString(resp))
            .build();

        emitter.onNext(simpleMessage);
    }

    public static void sendStreamRespAndDone(RequestHeader header, StatusCode code, String message,
        EventEmitter<SimpleMessage> emitter) {
        sendStreamResp(header, code, message, emitter);
        emitter.onCompleted();
    }

    public static void sendStreamRespAndDone(RequestHeader header, StatusCode code,
        EventEmitter<SimpleMessage> emitter) {
        Map<String, String> resp = new HashMap<>();
        resp.put(EventMeshConstants.RESP_CODE, code.getRetCode());
        resp.put(EventMeshConstants.RESP_MSG, code.getErrMsg());

        SimpleMessage simpleMessage = SimpleMessage.newBuilder()
            .setHeader(header)
            .setContent(JsonUtils.toJSONString(resp))
            .build();

        emitter.onNext(simpleMessage);
        emitter.onCompleted();
    }

    public static void streamCompleted(CloudEvent cloudEvent, StatusCode code, EventEmitter<CloudEvent> emitter) {
        Instant instant = now();
        CloudEvent.Builder builder = CloudEvent.newBuilder(cloudEvent)
            .putAttributes(ProtocolKey.GRPC_RESPONSE_CODE, CloudEventAttributeValue.newBuilder().setCeString(code.getRetCode()).build())
            .putAttributes(ProtocolKey.GRPC_RESPONSE_MESSAGE,
                CloudEventAttributeValue.newBuilder().setCeString(code.getErrMsg()).build())
            .putAttributes(ProtocolKey.GRPC_RESPONSE_TIME, CloudEventAttributeValue.newBuilder()
                .setCeTimestamp(Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build()).build());

        emitter.onNext(builder.build());
        emitter.onCompleted();
    }

    public static void streamCompleted(CloudEvent cloudEvent, StatusCode code, String message, EventEmitter<CloudEvent> emitter) {
        Instant instant = OffsetDateTime.now().toInstant();
        CloudEvent.Builder builder = CloudEvent.newBuilder(cloudEvent)
            .putAttributes(ProtocolKey.GRPC_RESPONSE_CODE, CloudEventAttributeValue.newBuilder().setCeString(code.getRetCode()).build())
            .putAttributes(ProtocolKey.GRPC_RESPONSE_MESSAGE,
                CloudEventAttributeValue.newBuilder().setCeString(StringUtils.isEmpty(message) ? code.getErrMsg() : message).build())
            .putAttributes(ProtocolKey.GRPC_RESPONSE_TIME, CloudEventAttributeValue.newBuilder()
                .setCeTimestamp(Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build()).build());

        emitter.onNext(builder.build());
        emitter.onCompleted();
    }

    private static Instant now() {
        return OffsetDateTime.of(LocalDateTime.now(ZoneId.systemDefault()), ZoneOffset.UTC).toInstant();
    }
}
