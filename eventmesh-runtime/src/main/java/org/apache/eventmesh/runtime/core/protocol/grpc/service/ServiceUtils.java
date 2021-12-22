package org.apache.eventmesh.runtime.core.protocol.grpc.service;

import io.grpc.stub.StreamObserver;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.GrpcType;

public class ServiceUtils {

    public static boolean validateHeader(RequestHeader header) {
        return !StringUtils.isBlank(header.getIdc())
            && !StringUtils.isBlank(header.getPid())
            && StringUtils.isNumeric(header.getPid())
            && !StringUtils.isBlank(header.getSys());
    }

    public static boolean validateMessage(EventMeshMessage message) {
        return !StringUtils.isBlank(message.getUniqueId())
            && !StringUtils.isBlank(message.getProducerGroup())
            && !StringUtils.isBlank(message.getTopic())
            && !StringUtils.isBlank(message.getContent())
            && (!StringUtils.isBlank(message.getTtl()));
    }

    public static boolean validateSubscription(GrpcType grpcType, Subscription subscription) {
        if (GrpcType.WEBHOOK.equals(grpcType) && StringUtils.isBlank(subscription.getUrl())) {
            return false;
        }
        if (CollectionUtils.isEmpty(subscription.getSubscriptionItemsList())
            || StringUtils.isBlank(subscription.getConsumerGroup())) {
            return false;
        }
        for (Subscription.SubscriptionItem item : subscription.getSubscriptionItemsList()) {
            if (StringUtils.isBlank(item.getTopic())
                || item.getMode() == Subscription.SubscriptionItem.SubscriptionMode.UNRECOGNIZED
                || item.getType() == Subscription.SubscriptionItem.SubscriptionType.UNRECOGNIZED) {
                return false;
            }
        }
        return true;
    }

    public static void sendResp(StatusCode code, StreamObserver<Response> responseObserver) {
        Response response = Response.newBuilder()
            .setRespCode(code.getRetCode())
            .setRespMsg(code.getErrMsg()).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public static void sendResp(StatusCode code, String message, StreamObserver<Response> responseObserver) {
        Response response = Response.newBuilder()
            .setRespCode(code.getRetCode())
            .setRespMsg(code.getErrMsg() + " " + message).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

}
