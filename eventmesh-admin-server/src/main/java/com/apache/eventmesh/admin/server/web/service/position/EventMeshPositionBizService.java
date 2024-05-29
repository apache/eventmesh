package com.apache.eventmesh.admin.server.web.service.position;

import com.apache.eventmesh.admin.server.AdminServerRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.job.DataSourceType;
import org.apache.eventmesh.common.remote.offset.RecordPosition;
import org.apache.eventmesh.common.remote.request.FetchPositionRequest;
import org.apache.eventmesh.common.remote.request.ReportPositionRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EventMeshPositionBizService {
    @Autowired
    PositionHandlerFactory factory;

    // called isValidateReportRequest before call this
    public RecordPosition getPosition(FetchPositionRequest request, Metadata metadata) {
        if (request == null) {
            return null;
        }
        isValidatePositionRequest(request.getDataSourceType());
        IFetchPositionHandler handler = factory.getHandler(request.getDataSourceType());
        return handler.handler(request, metadata);
    }

    public void isValidatePositionRequest(DataSourceType type) {
        if (type == null) {
            throw new AdminServerRuntimeException(ErrorCode.BAD_REQUEST, "data source type is null");
        }
        IReportPositionHandler handler = factory.getHandler(type);
        if (handler == null) {
            throw new AdminServerRuntimeException(ErrorCode.BAD_REQUEST, String.format("illegal data base " +
                            "type [%s], it not match any report position handler", type));
        }
    }

    // called isValidateReportRequest before call this
    public boolean reportPosition(ReportPositionRequest request, Metadata metadata) {
        if (request == null) {
            return false;
        }
        isValidatePositionRequest(request.getDataSourceType());
        IReportPositionHandler handler = factory.getHandler(request.getDataSourceType());
        return handler.handler(request, metadata);
    }

    public RecordPosition getPositionByJobID(Integer jobID, DataSourceType type) {
        if (jobID == null || type == null) {
            return null;
        }
        isValidatePositionRequest(type);
        PositionHandler handler = factory.getHandler(type);
        FetchPositionRequest request = new FetchPositionRequest();
        request.setJobID(String.valueOf(jobID));
        return handler.handler(request, null);
    }
}
