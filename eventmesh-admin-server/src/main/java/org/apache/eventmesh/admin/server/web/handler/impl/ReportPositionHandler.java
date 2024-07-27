/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.admin.server.web.handler.impl;

import org.apache.eventmesh.admin.server.AdminServerRuntimeException;
import org.apache.eventmesh.admin.server.web.db.DBThreadPool;
import org.apache.eventmesh.admin.server.web.db.entity.EventMeshJobInfo;
import org.apache.eventmesh.admin.server.web.handler.BaseRequestHandler;
import org.apache.eventmesh.admin.server.web.service.job.EventMeshJobInfoBizService;
import org.apache.eventmesh.admin.server.web.service.position.EventMeshPositionBizService;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.request.ReportPositionRequest;
import org.apache.eventmesh.common.remote.response.EmptyAckResponse;

import org.apache.commons.lang3.StringUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ReportPositionHandler extends BaseRequestHandler<ReportPositionRequest, EmptyAckResponse> {
    @Autowired
    private EventMeshJobInfoBizService jobInfoBizService;

    @Autowired
    private DBThreadPool executor;

    @Autowired
    private EventMeshPositionBizService positionBizService;


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
                    EventMeshJobInfo detail = jobInfoBizService.getJobDetail(jobID);
                    if (detail != null && !detail.getState().equals(request.getState()) && !jobInfoBizService.updateJobState(jobID,
                        request.getState())) {
                        log.warn("update job [{}] old state [{}] to [{}] fail", jobID, detail.getState(), request.getState());
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
