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

import org.apache.eventmesh.admin.server.web.handler.BaseRequestHandler;
import org.apache.eventmesh.admin.server.web.pojo.JobDetail;
import org.apache.eventmesh.admin.server.web.service.job.JobInfoBizService;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.job.JobConnectorConfig;
import org.apache.eventmesh.common.remote.request.FetchJobRequest;
import org.apache.eventmesh.common.remote.response.FetchJobResponse;
import org.apache.eventmesh.common.utils.JsonUtils;

import org.apache.commons.lang3.StringUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class FetchJobRequestHandler extends BaseRequestHandler<FetchJobRequest, FetchJobResponse> {

    @Autowired
    JobInfoBizService jobInfoBizService;

    @Override
    public FetchJobResponse handler(FetchJobRequest request, Metadata metadata) {
        if (StringUtils.isBlank(request.getJobID())) {
            return FetchJobResponse.failResponse(ErrorCode.BAD_REQUEST, "job id is empty");
        }
        FetchJobResponse response = FetchJobResponse.successResponse();
        JobDetail detail = jobInfoBizService.getJobDetail(request.getJobID());
        if (detail == null) {
            return response;
        }
        response.setId(detail.getJobID());
        JobConnectorConfig config = new JobConnectorConfig();
        config.setSourceConnectorConfig(JsonUtils.objectToMap(detail.getSourceDataSource().getConf()));
        config.setSourceConnectorDesc(detail.getSourceConnectorDesc());
        config.setSinkConnectorConfig(JsonUtils.objectToMap(detail.getSinkDataSource().getConf()));
        config.setSourceConnectorDesc(detail.getSinkConnectorDesc());
        response.setConnectorConfig(config);
        response.setTransportType(detail.getTransportType());
        response.setState(detail.getState());
        response.setPosition(detail.getPositions());
        response.setType(detail.getJobType());
        return response;
    }
}
