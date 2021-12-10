package org.apache.eventmesh.runtime.core.protocol.grpc.service;

import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.grpc.protos.Message;
import org.apache.eventmesh.common.protocol.grpc.protos.PublisherServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.ProducerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadPoolExecutor;

public class PublisherServiceImpl extends PublisherServiceGrpc.PublisherServiceImplBase {

    private Logger logger = LoggerFactory.getLogger(PublisherServiceImpl.class);

    private EventMeshGrpcServer eventMeshGrpcServer;

    private EventMeshGrpcConfiguration grpcConfiguration;

    private ThreadPoolExecutor threadPoolExecutor;

    private ProducerManager producerManager;

    public PublisherServiceImpl(EventMeshGrpcServer eventMeshGrpcServer,
                                ThreadPoolExecutor threadPoolExecutor) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.threadPoolExecutor = threadPoolExecutor;
        this.grpcConfiguration = eventMeshGrpcServer.getEventMeshGrpcConfiguration();
        this.producerManager = eventMeshGrpcServer.getProducerManager();
    }

    public void publish(Message request, StreamObserver<Response> responseObserver) {
        if (!validateHeader(request.getHeader())) {
            sendErrorResp(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, responseObserver);
            return;
        }

        if (!validateMessage(request)) {
            sendErrorResp(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseObserver);
            return;
        }
        String producerGroup = request.getProducerGroup();

        String content = request.getContent();

        Response response = Response.newBuilder().setRespMsg(content).setRespCode("200").build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private boolean validateHeader(RequestHeader header) {
        if (StringUtils.isBlank(header.getIdc())
            || StringUtils.isBlank(header.getPid())
            || !StringUtils.isNumeric(header.getPid())
            || StringUtils.isBlank(header.getSys())) {
            return false;
        }
        return true;
    }

    private boolean validateMessage(Message message) {
        if (StringUtils.isBlank(message.getUniqueId())
            || StringUtils.isBlank(message.getProducerGroup())
            || StringUtils.isBlank(message.getTopic())
            || StringUtils.isBlank(message.getContent())
            || (StringUtils.isBlank(message.getTtl()))) {
            return false;
        }
        return true;
    }

    private void sendErrorResp(EventMeshRetCode code, StreamObserver<Response> responseObserver) {
        Response response = Response.newBuilder()
            .setRespCode(code.getRetCode().toString())
            .setRespMsg(code.getErrMsg()).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
