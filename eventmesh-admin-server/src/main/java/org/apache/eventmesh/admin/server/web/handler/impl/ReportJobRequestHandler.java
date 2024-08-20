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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.admin.server.web.db.entity.EventMeshJobInfo;
import org.apache.eventmesh.admin.server.web.handler.BaseRequestHandler;
import org.apache.eventmesh.admin.server.web.service.job.JobInfoBizService;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.request.ReportJobRequest;
import org.apache.eventmesh.common.remote.response.SimpleResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ReportJobRequestHandler extends BaseRequestHandler<ReportJobRequest, SimpleResponse> {

    @Autowired
    JobInfoBizService jobInfoBizService;

    @Override
    public SimpleResponse handler(ReportJobRequest request, Metadata metadata) {
        log.info("receive report job request:{}", request);
        if (StringUtils.isBlank(request.getJobID())) {
            return SimpleResponse.fail(ErrorCode.BAD_REQUEST, "illegal job id, it's empty");
        }
        EventMeshJobInfo jobInfo = jobInfoBizService.getJobInfo(request.getJobID());
        if (jobInfo == null) {
            return SimpleResponse.fail(ErrorCode.BAD_REQUEST, "illegal job id, not exist target job,jobID:" + request.getJobID());
        }
        boolean result = jobInfoBizService.updateJobState(jobInfo.getJobID(), request.getState());
        if (result) {
            return SimpleResponse.success();
        } else {
            return SimpleResponse.fail(ErrorCode.INTERNAL_ERR, "update job failed.");
        }
    }
}
