package com.apache.eventmesh.admin.server.web.service.position;

import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.remote.offset.RecordPosition;
import org.apache.eventmesh.common.remote.request.FetchPositionRequest;

public interface IFetchPositionHandler {
    RecordPosition handler(FetchPositionRequest request, Metadata metadata);
}
