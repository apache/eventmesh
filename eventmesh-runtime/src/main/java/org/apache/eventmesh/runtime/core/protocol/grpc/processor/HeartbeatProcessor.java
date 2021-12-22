package org.apache.eventmesh.runtime.core.protocol.grpc.processor;

import io.grpc.stub.StreamObserver;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat;
import org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat.ClientType;
import org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat.HeartbeatItem;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.ConsumerManager;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupClient;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Date;

public class HeartbeatProcessor {
    private Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private EventMeshGrpcServer eventMeshGrpcServer;

    public HeartbeatProcessor(EventMeshGrpcServer eventMeshGrpcServer) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
    }

    public void process(Heartbeat heartbeat, StreamObserver<Response> responseObserver) throws Exception {
        RequestHeader header = heartbeat.getHeader();

        if (!ServiceUtils.validateHeader(header)) {
            ServiceUtils.sendResp(StatusCode.EVENTMESH_PROTOCOL_HEADER_ERR, responseObserver);
            return;
        }

        if (!ServiceUtils.validateHeartBeat(heartbeat)) {
            ServiceUtils.sendResp(StatusCode.EVENTMESH_PROTOCOL_BODY_ERR, responseObserver);
            return;
        }

        // only handle heartbeat for consumers
        ClientType clientType = heartbeat.getClientType();
        if (!ClientType.SUB.equals(clientType)) {
            return;
        }

        ConsumerManager consumerManager = eventMeshGrpcServer.getConsumerManager();

        String consumerGroup = heartbeat.getConsumerGroup();

        // update clients' timestamp in the heartbeat items
        for (Heartbeat.HeartbeatItem item : heartbeat.getHeartbeatItemsList()) {
            ConsumerGroupClient hbClient = ConsumerGroupClient.builder()
                .env(header.getEnv())
                .idc(header.getIdc())
                .sys(header.getSys())
                .ip(header.getIp())
                .pid(header.getPid())
                .consumerGroup(consumerGroup)
                .topic(item.getTopic())
                .lastUpTime(new Date())
                .build();
            consumerManager.updateClientTime(hbClient);
        }

        ServiceUtils.sendResp(StatusCode.SUCCESS, "heartbeat success", responseObserver);
    }
}
