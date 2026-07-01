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

import org.apache.eventmesh.admin.server.web.db.entity.EventMeshJobInfo;
import org.apache.eventmesh.admin.server.web.handler.BaseRequestHandler;
import org.apache.eventmesh.admin.server.web.pojo.TaskDetail;
import org.apache.eventmesh.admin.server.web.service.job.JobInfoBizService;
import org.apache.eventmesh.admin.server.web.service.position.PositionBizService;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.remote.JobState;
import org.apache.eventmesh.common.remote.TransportType;
import org.apache.eventmesh.common.remote.datasource.DataSourceType;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.offset.RecordPosition;
import org.apache.eventmesh.common.remote.request.RecordPositionRequest;
import org.apache.eventmesh.common.remote.request.ReportJobRequest;
import org.apache.eventmesh.common.remote.response.SimpleResponse;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;


@Component
@Slf4j
public class ReportJobRequestHandler extends BaseRequestHandler<ReportJobRequest, SimpleResponse> {

    @Autowired
    JobInfoBizService jobInfoBizService;

    @Autowired
    PositionBizService positionBizService;

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
        boolean recordResult = recordPosition(request, metadata, jobInfo);
        boolean result = recordResult && jobInfoBizService.updateJobState(jobInfo.getJobID(), request.getState());
        if (result) {
            return SimpleResponse.success();
        } else {
            return SimpleResponse.fail(ErrorCode.INTERNAL_ERR, "update job failed.");
        }
    }

    private boolean recordPosition(ReportJobRequest request, Metadata metadata, EventMeshJobInfo jobInfo) {
        if (!jobInfo.getJobState().equalsIgnoreCase(JobState.INIT.name()) || !JobState.RUNNING.name().equalsIgnoreCase(request.getState().name())) {
            log.info("skip record position because of job state not from init change to running.jobID:{}", jobInfo.getJobID());
            return true;
        }

        TaskDetail taskDetail = jobInfoBizService.getTaskDetail(jobInfo.getTaskID(), DataSourceType.MYSQL);
        if (taskDetail.getFullTask() == null || taskDetail.getIncreaseTask() == null) {
            log.info("skip record position because of not exist full and increase job.jobID:{}", jobInfo.getJobID());
            return true;
        }

        List<RecordPosition> recordPositionList =
            positionBizService.getPositionByJobID(taskDetail.getIncreaseTask().getJobID(), DataSourceType.MYSQL);
        if (!recordPositionList.isEmpty()) {
            log.info("skip record position because of increase job has exist position.jobID:{},position list size:{}", jobInfo.getJobID(),
                recordPositionList.size());
            return true;
        }

        RecordPositionRequest recordPositionRequest = new RecordPositionRequest();
        recordPositionRequest.setFullJobID(taskDetail.getFullTask().getJobID());
        recordPositionRequest.setIncreaseJobID(taskDetail.getIncreaseTask().getJobID());
        recordPositionRequest.setUpdateState(request.getState());
        recordPositionRequest.setAddress(request.getAddress());
        TransportType currentTransportType = TransportType.getTransportType(jobInfo.getTransportType());
        recordPositionRequest.setDataSourceType(currentTransportType.getSrc());
        return positionBizService.recordPosition(recordPositionRequest, metadata);
    }

}
