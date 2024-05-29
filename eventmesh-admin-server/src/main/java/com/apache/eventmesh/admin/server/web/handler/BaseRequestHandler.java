package com.apache.eventmesh.admin.server.web.handler;

import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.remote.request.BaseRemoteRequest;
import org.apache.eventmesh.common.remote.response.BaseRemoteResponse;

public abstract class BaseRequestHandler<T extends BaseRemoteRequest, S extends BaseRemoteResponse> {
    public BaseRemoteResponse handlerRequest(T request, Metadata metadata) {
        return handler(request, metadata);
    }

    protected abstract S handler(T request, Metadata metadata);
}
