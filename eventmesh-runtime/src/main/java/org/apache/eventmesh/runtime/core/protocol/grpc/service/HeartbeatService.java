package org.apache.eventmesh.runtime.core.protocol.grpc.service;

import io.grpc.stub.StreamObserver;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat;
import org.apache.eventmesh.common.protocol.grpc.protos.HeartbeatServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.processor.HeartbeatProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ThreadPoolExecutor;

public class HeartbeatService extends HeartbeatServiceGrpc.HeartbeatServiceImplBase {

    private final Logger logger = LoggerFactory.getLogger(ProducerService.class);

    private final EventMeshGrpcServer eventMeshGrpcServer;

    private final ThreadPoolExecutor threadPoolExecutor;

    public HeartbeatService(EventMeshGrpcServer eventMeshGrpcServer,
                            ThreadPoolExecutor threadPoolExecutor) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.threadPoolExecutor = threadPoolExecutor;
    }

    public void heartbeat(Heartbeat request, StreamObserver<Response> responseObserver) {
        logger.info("cmd={}|{}|client2eventMesh|from={}|to={}",
            "heartbeat", EventMeshConstants.PROTOCOL_GRPC,
            request.getHeader().getIp(), eventMeshGrpcServer.getEventMeshGrpcConfiguration().eventMeshIp);

        EventEmitter<Response> emitter = new EventEmitter<>(responseObserver);
        threadPoolExecutor.submit(() -> {
            HeartbeatProcessor heartbeatProcessor = new HeartbeatProcessor(eventMeshGrpcServer);
            try {
                heartbeatProcessor.process(request, emitter);
            } catch (Exception e) {
                logger.error("Error code {}, error message {}", StatusCode.EVENTMESH_HEARTBEAT_ERR.getRetCode(),
                    StatusCode.EVENTMESH_HEARTBEAT_ERR.getErrMsg(), e);
                ServiceUtils.sendResp(StatusCode.EVENTMESH_HEARTBEAT_ERR, e.getMessage(), emitter);
            }
        });
    }
}
