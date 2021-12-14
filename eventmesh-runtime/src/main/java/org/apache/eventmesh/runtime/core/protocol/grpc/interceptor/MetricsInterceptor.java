package org.apache.eventmesh.runtime.core.protocol.grpc.interceptor;

import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricsInterceptor implements ServerInterceptor {

    private Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        logger.info("cmd={}|{}|client2eventMesh|from={}|to={}", call.getMethodDescriptor().getFullMethodName(),
            EventMeshConstants.PROTOCOL_GRPC, call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR),IPUtils.getLocalAddress());
        return next.startCall(call, headers);
    }
}