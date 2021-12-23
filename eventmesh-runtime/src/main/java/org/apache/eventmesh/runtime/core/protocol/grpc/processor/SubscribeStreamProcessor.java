package org.apache.eventmesh.runtime.core.protocol.grpc.processor;

import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.ConsumerManager;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.EventMeshConsumer;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupClient;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.GrpcType;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class SubscribeStreamProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private final EventMeshGrpcServer eventMeshGrpcServer;

    private final GrpcType grpcType = GrpcType.STREAM;

    public SubscribeStreamProcessor(EventMeshGrpcServer eventMeshGrpcServer) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
    }

    public void process(Subscription subscription, EventEmitter<EventMeshMessage> emitter) throws Exception {

        RequestHeader header = subscription.getHeader();

        if (!ServiceUtils.validateHeader(header)) {
            sendResp(subscription, StatusCode.EVENTMESH_PROTOCOL_HEADER_ERR, emitter);
            return;
        }

        if (!ServiceUtils.validateSubscription(grpcType, subscription)) {
            sendResp(subscription, StatusCode.EVENTMESH_PROTOCOL_BODY_ERR, emitter);
            return;
        }

        ConsumerManager consumerManager = eventMeshGrpcServer.getConsumerManager();

        String consumerGroup = subscription.getConsumerGroup();
        List<Subscription.SubscriptionItem> subscriptionItems = subscription.getSubscriptionItemsList();

        // Collect new clients in subscription
        List<ConsumerGroupClient> newClients = new LinkedList<>();
        for (Subscription.SubscriptionItem item : subscriptionItems) {
            ConsumerGroupClient newClient = ConsumerGroupClient.builder()
                .env(header.getEnv())
                .idc(header.getIdc())
                .sys(header.getSys())
                .ip(header.getIp())
                .pid(header.getPid())
                .consumerGroup(consumerGroup)
                .topic(item.getTopic())
                .subscriptionMode(item.getMode())
                .grpcType(grpcType)
                .eventEmitter(emitter)
                .lastUpTime(new Date())
                .build();
            newClients.add(newClient);
        }

        // register new clients into ConsumerManager
        for (ConsumerGroupClient newClient : newClients) {
            consumerManager.registerClient(newClient);
        }

        // register new clients into EventMeshConsumer
        EventMeshConsumer eventMeshConsumer = consumerManager.getEventMeshConsumer(consumerGroup);

        boolean requireRestart = false;
        for (ConsumerGroupClient newClient : newClients) {
            if (eventMeshConsumer.registerClient(newClient)) {
                requireRestart = true;
            }
        }

        // restart consumer group if required
        if (requireRestart) {
            logger.info("ConsumerGroup {} topic info changed, restart EventMesh Consumer", consumerGroup);
            consumerManager.restartEventMeshConsumer(consumerGroup);
        } else {
            logger.warn("EventMesh consumer [{}] didn't restart.", consumerGroup);
        }

        // send back subscribe success response
        Map<String, String> resp = new HashMap<>();
        resp.put("respCode", StatusCode.SUCCESS.getRetCode());
        resp.put("respMsg", "subscribe success");

        EventMeshMessage eventMeshMessage = EventMeshMessage.newBuilder()
            .setHeader(header)
            .setContent(JsonUtils.serialize(resp))
            .build();
        emitter.onNext(eventMeshMessage);
    }

    private void sendResp(Subscription subscription, StatusCode code, EventEmitter<EventMeshMessage> emitter) {
        Map<String, String> resp = new HashMap<>();
        resp.put("respCode", code.getRetCode());
        resp.put("respMsg", code.getErrMsg());

        RequestHeader header = subscription.getHeader();
        EventMeshMessage eventMeshMessage = EventMeshMessage.newBuilder()
            .setHeader(header)
            .setContent(JsonUtils.serialize(resp))
            .build();

        emitter.onNext(eventMeshMessage);
        emitter.onCompleted();
    }
}