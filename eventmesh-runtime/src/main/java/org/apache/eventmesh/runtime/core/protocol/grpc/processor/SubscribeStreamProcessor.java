package org.apache.eventmesh.runtime.core.protocol.grpc.processor;

import org.apache.eventmesh.api.exception.AclException;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.acl.Acl;
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

    private final Logger aclLogger = LoggerFactory.getLogger("acl");

    private final EventMeshGrpcServer eventMeshGrpcServer;

    private final GrpcType grpcType = GrpcType.STREAM;

    public SubscribeStreamProcessor(EventMeshGrpcServer eventMeshGrpcServer) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
    }

    public void process(Subscription subscription, EventEmitter<EventMeshMessage> emitter) throws Exception {

        RequestHeader header = subscription.getHeader();

        if (!ServiceUtils.validateHeader(header)) {
            sendRespAndComplete(subscription, StatusCode.EVENTMESH_PROTOCOL_HEADER_ERR, emitter);
            return;
        }

        if (!ServiceUtils.validateSubscription(grpcType, subscription)) {
            sendRespAndComplete(subscription, StatusCode.EVENTMESH_PROTOCOL_BODY_ERR, emitter);
            return;
        }

        try {
            doAclCheck(subscription);
        } catch (AclException e) {
            aclLogger.warn("CLIENT HAS NO PERMISSION to Subscribe. failed", e);
            sendRespAndComplete(subscription, StatusCode.EVENTMESH_ACL_ERR, e.getMessage(), emitter);
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

        sendResp(subscription, StatusCode.SUCCESS, "subscribe success", emitter);
    }

    private void sendRespAndComplete(Subscription subscription, StatusCode code, EventEmitter<EventMeshMessage> emitter) {
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

    private void sendRespAndComplete(Subscription subscription, StatusCode code, String message, EventEmitter<EventMeshMessage> emitter) {
        sendResp(subscription, code, message, emitter);
        emitter.onCompleted();
    }

    private void sendResp(Subscription subscription, StatusCode code, String message, EventEmitter<EventMeshMessage> emitter) {
        Map<String, String> resp = new HashMap<>();
        resp.put("respCode", code.getRetCode());
        resp.put("respMsg", code.getErrMsg() + " " + message);

        RequestHeader header = subscription.getHeader();
        EventMeshMessage eventMeshMessage = EventMeshMessage.newBuilder()
            .setHeader(header)
            .setContent(JsonUtils.serialize(resp))
            .build();

        emitter.onNext(eventMeshMessage);
    }

    private void doAclCheck(Subscription subscription) throws AclException {
        RequestHeader header = subscription.getHeader();
        if (eventMeshGrpcServer.getEventMeshGrpcConfiguration().eventMeshServerSecurityEnable) {
            String remoteAdd = header.getIp();
            String user = header.getUsername();
            String pass = header.getPassword();
            String subsystem = header.getSys();
            for (Subscription.SubscriptionItem item : subscription.getSubscriptionItemsList()) {
                Acl.doAclCheckInHttpReceive(remoteAdd, user, pass, subsystem, item.getTopic(),
                    RequestCode.SUBSCRIBE.getRequestCode());
            }
        }
    }
}