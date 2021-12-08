package org.apache.eventmesh.runtime.core.protocol.grpc.service;

import io.grpc.stub.StreamObserver;
import org.apache.eventmesh.common.protocol.grpc.protos.Message;
import org.apache.eventmesh.common.protocol.grpc.protos.PublisherServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;

public class PublisherServiceImpl extends PublisherServiceGrpc.PublisherServiceImplBase {

    public PublisherServiceImpl() {
        
    }

    public void publish(Message request, StreamObserver<Response> responseObserver) {
        String content = request.getContent();

        Response response = Response.newBuilder().setRespMsg(content).setRespCode("200").build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

}
