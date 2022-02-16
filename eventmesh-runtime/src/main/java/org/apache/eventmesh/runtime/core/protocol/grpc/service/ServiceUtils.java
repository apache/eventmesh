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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat;
import org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat.ClientType;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.GrpcType;

import java.util.HashMap;
import java.util.Map;

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

    public static boolean validateMessage(SimpleMessage message) {
        return StringUtils.isNotEmpty(message.getUniqueId())
            && StringUtils.isNotEmpty(message.getProducerGroup())
            && StringUtils.isNotEmpty(message.getTopic())
            && StringUtils.isNotEmpty(message.getContent())
            && StringUtils.isNotEmpty(message.getTtl());
    }

    public static boolean validateBatchMessage(BatchMessage batchMessage) {
         if (StringUtils.isEmpty(batchMessage.getTopic())
             || StringUtils.isEmpty(batchMessage.getProducerGroup())) {
             return false;
         }
         for (BatchMessage.MessageItem item : batchMessage.getMessageItemList()) {
             if (StringUtils.isEmpty(item.getContent()) || StringUtils.isEmpty(item.getSeqNum())
                 || StringUtils.isEmpty(item.getTtl()) || StringUtils.isEmpty(item.getUniqueId())) {
                 return false;
             }
         }
         return true;
    }

    public static boolean validateSubscription(GrpcType grpcType, Subscription subscription) {
        if (GrpcType.WEBHOOK.equals(grpcType) && StringUtils.isEmpty(subscription.getUrl())) {
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

    public static boolean validateHeartBeat(Heartbeat heartbeat) {
        if (ClientType.SUB.equals(heartbeat.getClientType())
            && StringUtils.isEmpty(heartbeat.getConsumerGroup())) {
            return false;
        }
        if (ClientType.PUB.equals(heartbeat.getClientType())
            && StringUtils.isEmpty(heartbeat.getProducerGroup())) {
            return false;
        }
        for (Heartbeat.HeartbeatItem item : heartbeat.getHeartbeatItemsList()) {
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
            .setRespMsg(code.getErrMsg() + " " + message)
            .setRespTime(String.valueOf(System.currentTimeMillis()))
            .build();
        emitter.onNext(response);
        emitter.onCompleted();
    }

    public static void sendStreamResp(RequestHeader header, StatusCode code, String message, EventEmitter<SimpleMessage> emitter) {
        Map<String, String> resp = new HashMap<>();
        resp.put("respCode", code.getRetCode());
        resp.put("respMsg", code.getErrMsg() + " " + message);

        SimpleMessage simpleMessage = SimpleMessage.newBuilder()
            .setHeader(header)
            .setContent(JsonUtils.serialize(resp))
            .build();

        emitter.onNext(simpleMessage);
    }

    public static void sendStreamRespAndDone(RequestHeader header, StatusCode code, String message, EventEmitter<SimpleMessage> emitter) {
        sendStreamResp(header, code, message, emitter);
        emitter.onCompleted();
    }

    public static void sendStreamRespAndDone(RequestHeader header, StatusCode code, EventEmitter<SimpleMessage> emitter) {
        Map<String, String> resp = new HashMap<>();
        resp.put("respCode", code.getRetCode());
        resp.put("respMsg", code.getErrMsg());

        SimpleMessage simpleMessage = SimpleMessage.newBuilder()
            .setHeader(header)
            .setContent(JsonUtils.serialize(resp))
            .build();

        emitter.onNext(simpleMessage);
        emitter.onCompleted();
    }
}
