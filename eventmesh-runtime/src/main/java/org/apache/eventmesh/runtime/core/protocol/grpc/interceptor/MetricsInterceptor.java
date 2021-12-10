package org.apache.eventmesh.runtime.core.protocol.grpc.interceptor;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricsInterceptor implements ServerInterceptor {

    private Logger logger = LoggerFactory.getLogger("grpc-services");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        logger.info("Call: {}", call.getMethodDescriptor().getFullMethodName());
        return next.startCall(call, headers);
    }
}
