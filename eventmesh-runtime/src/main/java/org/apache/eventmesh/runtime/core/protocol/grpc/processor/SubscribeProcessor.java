package org.apache.eventmesh.runtime.core.protocol.grpc.processor;

import io.grpc.stub.StreamObserver;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.ConsumerManager;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupClient;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.GrpcType;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

public class SubscribeProcessor {

    private Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private EventMeshGrpcServer eventMeshGrpcServer;

    private GrpcType grpcType = GrpcType.WEBHOOK;

    public SubscribeProcessor(EventMeshGrpcServer eventMeshGrpcServer) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
    }

    public void process(Subscription subscription, StreamObserver<Response> responseObserver) throws Exception {

        RequestHeader header = subscription.getHeader();

        if (!ServiceUtils.validateHeader(header)) {
            ServiceUtils.sendResp(StatusCode.EVENTMESH_PROTOCOL_HEADER_ERR, responseObserver);
            return;
        }

        if (!ServiceUtils.validateSubscription(grpcType, subscription)) {
            ServiceUtils.sendResp(StatusCode.EVENTMESH_PROTOCOL_BODY_ERR, responseObserver);
            return;
        }

        ConsumerManager consumerManager = eventMeshGrpcServer.getConsumerManager();

        String consumerGroup = subscription.getConsumerGroup();
        String url = subscription.getUrl();
        List<Subscription.SubscriptionItem> subscriptionItems = subscription.getSubscriptionItemsList();

        for (Subscription.SubscriptionItem item : subscriptionItems) {
            ConsumerGroupClient newClient = ConsumerGroupClient.builder()
                .env(header.getEnv())
                .idc(header.getIdc())
                .sys(header.getSys())
                .ip(header.getIp())
                .pid(header.getPid())
                .consumerGroup(consumerGroup)
                .topic(item.getTopic())
                .grpcType(grpcType)
                .subscriptionMode(item.getMode())
                .url(url)
                .lastUpTime(new Date())
                .build();

            consumerManager.registerClient(newClient);
        }

        boolean restartResult = consumerManager.restartEventMeshConsumer(consumerGroup);
        if (!restartResult) {
            logger.warn("EventMesh consumer [{}] didn't restart.", consumerGroup);
        }

        ServiceUtils.sendResp(StatusCode.SUCCESS, "subscribe success", responseObserver);
    }
}