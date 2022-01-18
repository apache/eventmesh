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
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.GrpcType;

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

    public static void sendResp(StatusCode code, EventEmitter<Response> emitter) {
        Response response = Response.newBuilder()
            .setRespCode(code.getRetCode())
            .setRespMsg(code.getErrMsg())
            .setRespTime(String.valueOf(System.currentTimeMillis()))
            .build();
        emitter.onNext(response);
        emitter.onCompleted();
    }

    public static void sendResp(StatusCode code, String message, EventEmitter<Response> emitter) {
        Response response = Response.newBuilder()
            .setRespCode(code.getRetCode())
            .setRespMsg(code.getErrMsg() + " " + message)
            .setRespTime(String.valueOf(System.currentTimeMillis()))
            .build();
        emitter.onNext(response);
        emitter.onCompleted();
    }

}
