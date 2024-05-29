package com.apache.eventmesh.admin.server.web.service.position;

import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.remote.request.ReportPositionRequest;

public interface IReportPositionHandler {
    boolean handler(ReportPositionRequest request, Metadata metadata);
}
