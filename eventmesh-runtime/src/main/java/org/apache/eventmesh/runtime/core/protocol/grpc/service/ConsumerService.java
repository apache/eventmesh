package org.apache.eventmesh.runtime.core.protocol.grpc.service;

import io.grpc.stub.StreamObserver;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.grpc.protos.ConsumerServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.core.protocol.grpc.processor.SubscribeProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadPoolExecutor;

public class ConsumerService extends ConsumerServiceGrpc.ConsumerServiceImplBase {

    private Logger logger = LoggerFactory.getLogger(ProducerService.class);

    private EventMeshGrpcServer eventMeshGrpcServer;

    private ThreadPoolExecutor threadPoolExecutor;

    public ConsumerService(EventMeshGrpcServer eventMeshGrpcServer,
                           ThreadPoolExecutor threadPoolExecutor) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.threadPoolExecutor = threadPoolExecutor;
    }

    public void subscribe(Subscription request, StreamObserver<Response> responseObserver) {
        threadPoolExecutor.submit(() -> {
            SubscribeProcessor subscribeProcessor = new SubscribeProcessor(eventMeshGrpcServer);
            try {
                subscribeProcessor.process(request, responseObserver);
            } catch (Exception e) {
                logger.error("Error code {}, error message {}", EventMeshRetCode.EVENTMESH_SUBSCRIBE_ERR.getRetCode(),
                    StatusCode.EVENTMESH_SUBSCRIBE_ERR.getErrMsg(), e);
                ServiceUtils.sendResp(StatusCode.EVENTMESH_SUBSCRIBE_ERR, e.getMessage(),
                    responseObserver);
            }
        });
    }
}
