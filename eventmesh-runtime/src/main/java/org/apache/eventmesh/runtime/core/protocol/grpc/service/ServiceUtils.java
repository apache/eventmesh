package org.apache.eventmesh.runtime.core.protocol.grpc.service;

import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.grpc.protos.Message;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;

public class ServiceUtils {

    static public boolean validateHeader(RequestHeader header) {
        if (StringUtils.isBlank(header.getIdc())
            || StringUtils.isBlank(header.getPid())
            || !StringUtils.isNumeric(header.getPid())
            || StringUtils.isBlank(header.getSys())) {
            return false;
        }
        return true;
    }

    static public boolean validateMessage(Message message) {
        if (StringUtils.isBlank(message.getUniqueId())
            || StringUtils.isBlank(message.getProducerGroup())
            || StringUtils.isBlank(message.getTopic())
            || StringUtils.isBlank(message.getContent())
            || (StringUtils.isBlank(message.getTtl()))) {
            return false;
        }
        return true;
    }

    static public void sendResp(EventMeshRetCode code, StreamObserver<Response> responseObserver) {
        Response response = Response.newBuilder()
            .setRespCode(code.getRetCode().toString())
            .setRespMsg(code.getErrMsg()).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    static public void sendResp(EventMeshRetCode code, String message, StreamObserver<Response> responseObserver) {
        Response response = Response.newBuilder()
            .setRespCode(code.getRetCode().toString())
            .setRespMsg(code.getErrMsg() + " " + message).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
