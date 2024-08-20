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

import org.apache.eventmesh.admin.server.web.db.DBThreadPool;
import org.apache.eventmesh.admin.server.web.handler.BaseRequestHandler;
import org.apache.eventmesh.admin.server.web.pojo.JobDetail;
import org.apache.eventmesh.admin.server.web.service.job.JobInfoBizService;
import org.apache.eventmesh.admin.server.web.service.position.PositionBizService;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.request.ReportPositionRequest;
import org.apache.eventmesh.common.remote.response.SimpleResponse;

import org.apache.commons.lang3.StringUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ReportPositionHandler extends BaseRequestHandler<ReportPositionRequest, SimpleResponse> {
    @Autowired
    private JobInfoBizService jobInfoBizService;

    @Autowired
    private DBThreadPool executor;

    @Autowired
    private PositionBizService positionBizService;

    @Override
    protected SimpleResponse handler(ReportPositionRequest request, Metadata metadata) {
        log.info("receive report position request:{}", request);
        if (StringUtils.isBlank(request.getJobID())) {
            log.info("request [{}] illegal job id", request);
            return SimpleResponse.fail(ErrorCode.BAD_REQUEST, "illegal job id, it's empty");
        }
        if (request.getDataSourceType() == null) {
            log.info("request [{}] illegal data type", request);
            return SimpleResponse.fail(ErrorCode.BAD_REQUEST, "illegal data type, it's empty");
        }
        if (StringUtils.isBlank(request.getJobID())) {
            log.info("request [{}] illegal job id", request);
            return SimpleResponse.fail(ErrorCode.BAD_REQUEST, "illegal job id, it's empty");
        }
        if (request.getRecordPositionList() == null || request.getRecordPositionList().isEmpty()) {
            log.info("request [{}] illegal record position", request);
            return SimpleResponse.fail(ErrorCode.BAD_REQUEST, "illegal record position list, it's empty");
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
                    JobDetail detail = jobInfoBizService.getJobDetail(request.getJobID());
                    if (detail != null && !detail.getState().equals(request.getState()) && !jobInfoBizService.updateJobState(request.getJobID(),
                        request.getState())) {
                        log.warn("update job [{}] old state [{}] to [{}] fail", request.getJobID(), detail.getState(), request.getState());
                    }
                } catch (Exception e) {
                    log.warn("update job id [{}] type [{}] state [{}] fail", request.getJobID(),
                        request.getDataSourceType(), request.getState(), e);
                }
            }
        });
        return SimpleResponse.success();
    }
}
