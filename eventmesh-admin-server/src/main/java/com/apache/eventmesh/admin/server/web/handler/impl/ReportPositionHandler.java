package com.apache.eventmesh.admin.server.web.handler.impl;

import com.apache.eventmesh.admin.server.AdminServerRuntimeException;
import com.apache.eventmesh.admin.server.web.db.DBThreadPool;
import com.apache.eventmesh.admin.server.web.handler.BaseRequestHandler;
import com.apache.eventmesh.admin.server.web.service.job.EventMeshJobInfoBizService;
import com.apache.eventmesh.admin.server.web.service.position.EventMeshPositionBizService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.request.ReportPositionRequest;
import org.apache.eventmesh.common.remote.response.EmptyAckResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ReportPositionHandler extends BaseRequestHandler<ReportPositionRequest, EmptyAckResponse> {
    @Autowired
    EventMeshJobInfoBizService jobInfoBizService;

    @Autowired
    DBThreadPool executor;

    @Autowired
    EventMeshPositionBizService positionBizService;


    @Override
    protected EmptyAckResponse handler(ReportPositionRequest request, Metadata metadata) {
        if (request.getDataSourceType() == null) {
            throw new AdminServerRuntimeException(ErrorCode.BAD_REQUEST, "illegal data type, it's empty");
        }
        if (StringUtils.isBlank(request.getJobID())) {
            throw new AdminServerRuntimeException(ErrorCode.BAD_REQUEST, "illegal job id, it's empty");
        }
        if (request.getRecordPositionList() == null || request.getRecordPositionList().isEmpty()) {
            throw new AdminServerRuntimeException(ErrorCode.BAD_REQUEST, "illegal record position list, it's empty");
        }
        int jobID;

        try {
            jobID = Integer.parseInt(request.getJobID());
        } catch (NumberFormatException e) {
            throw new AdminServerRuntimeException(ErrorCode.BAD_REQUEST, String.format("illegal job id [%s] format",
                    request.getJobID()));
        }

        positionBizService.isValidatePositionRequest(request.getDataSourceType());

        executor.getExecutors().execute(() -> {
            try {
                boolean reported = positionBizService.reportPosition(request, metadata);
                if (reported) {
                    if (log.isDebugEnabled()) {
                        log.debug("handle runtime [{}] report data type [{}] job [{}] position [{}] success",
                                request.getAddress(), request.getDataSourceType(), request.getJobID(),
                                request.getRecordPositionList());
                    }
                } else {
                    log.warn("handle runtime [{}] report data type [{}] job [{}] position [{}] fail",
                            request.getAddress(), request.getDataSourceType(), request.getJobID(),
                            request.getRecordPositionList());
                }
            } catch (Exception e) {
                log.warn("handle position request fail, request [{}]", request, e);
            } finally {
                try {
                    if (!jobInfoBizService.updateJobState(jobID, request.getState())) {
                        log.warn("update job [{}] state to [{}] fail", jobID, request.getState());
                    }
                } catch (Exception e) {
                    log.warn("update job id [{}] type [{}] state [{}] fail", request.getJobID(),
                            request.getDataSourceType(), request.getState(), e);
                }
            }
        });
        return new EmptyAckResponse();
    }
}
