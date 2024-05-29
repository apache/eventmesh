package com.apache.eventmesh.admin.server.web.handler.impl;

import com.apache.eventmesh.admin.server.AdminServerRuntimeException;
import com.apache.eventmesh.admin.server.web.db.DBThreadPool;
import com.apache.eventmesh.admin.server.web.handler.BaseRequestHandler;
import com.apache.eventmesh.admin.server.web.service.position.EventMeshPositionBizService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.request.FetchPositionRequest;
import org.apache.eventmesh.common.remote.response.FetchPositionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FetchPositionHandler extends BaseRequestHandler<FetchPositionRequest, FetchPositionResponse> {

    @Autowired
    DBThreadPool executor;

    @Autowired
    EventMeshPositionBizService positionBizService;

    @Override
    protected FetchPositionResponse handler(FetchPositionRequest request, Metadata metadata) {
        if (request.getDataSourceType() == null) {
            throw new AdminServerRuntimeException(ErrorCode.BAD_REQUEST, "illegal data type, it's empty");
        }
        if (StringUtils.isBlank(request.getJobID())) {
            throw new AdminServerRuntimeException(ErrorCode.BAD_REQUEST, "illegal job id, it's empty");
        }
        return FetchPositionResponse.successResponse(positionBizService.getPosition(request, metadata));
    }
}
