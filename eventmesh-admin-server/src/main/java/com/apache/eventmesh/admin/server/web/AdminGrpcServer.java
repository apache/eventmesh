package com.apache.eventmesh.admin.server.web;

import com.apache.eventmesh.admin.server.AdminServerRuntimeException;
import com.apache.eventmesh.admin.server.web.handler.BaseRequestHandler;
import com.apache.eventmesh.admin.server.web.handler.RequestHandlerFactory;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.grpc.adminserver.AdminServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Payload;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.payload.PayloadUtil;
import org.apache.eventmesh.common.remote.request.BaseRemoteRequest;
import org.apache.eventmesh.common.remote.response.BaseRemoteResponse;
import org.apache.eventmesh.common.remote.response.EmptyAckResponse;
import org.apache.eventmesh.common.remote.response.FailResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AdminGrpcServer extends AdminServiceGrpc.AdminServiceImplBase {
    @Autowired
    RequestHandlerFactory handlerFactory;

    private Payload process(Payload value) {
        if (value == null || StringUtils.isBlank(value.getMetadata().getType())) {
            return PayloadUtil.from(FailResponse.build(ErrorCode.BAD_REQUEST, "bad request: type not " +
                    "exists"));
        }
        try {
            BaseRequestHandler<BaseRemoteRequest, BaseRemoteResponse> handler =
                    handlerFactory.getHandler(value.getMetadata().getType());
            if (handler == null) {
                return PayloadUtil.from(FailResponse.build(BaseRemoteResponse.UNKNOWN,
                        "not match any request handler"));
            }
            BaseRemoteResponse response = handler.handlerRequest((BaseRemoteRequest) PayloadUtil.parse(value), value.getMetadata());
            if (response == null || response instanceof EmptyAckResponse) {
                return null;
            }
            return PayloadUtil.from(response);
        } catch (Exception e) {
            log.warn("process payload {} fail", value.getMetadata().getType(), e);
            if (e instanceof AdminServerRuntimeException) {
                return PayloadUtil.from(FailResponse.build(((AdminServerRuntimeException)e).getCode(),
                        e.getMessage()));
            }
            return PayloadUtil.from(FailResponse.build(ErrorCode.INTERNAL_ERR, "admin server internal err"));
        }
    }

    public StreamObserver<Payload> invokeBiStream(StreamObserver<Payload> responseObserver) {
        return new StreamObserver<Payload>() {
            @Override
            public void onNext(Payload value) {
                Payload payload = process(value);
                if (payload == null) {
                    return;
                }
                responseObserver.onNext(payload);
            }

            @Override
            public void onError(Throwable t) {
                if (responseObserver instanceof ServerCallStreamObserver) {
                    if (!((ServerCallStreamObserver<Payload>) responseObserver).isCancelled()) {
                        log.warn("admin gRPC server fail", t);
                    }
                }
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }

    public void invoke(Payload request, StreamObserver<Payload> responseObserver) {
        responseObserver.onNext(process(request));
        responseObserver.onCompleted();
    }
}
