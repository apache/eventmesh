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
import org.apache.eventmesh.admin.server.web.db.entity.EventMeshJobDetail;
import org.apache.eventmesh.admin.server.web.handler.BaseRequestHandler;
import org.apache.eventmesh.admin.server.web.service.job.EventMeshJobInfoBizService;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.request.FetchJobRequest;
import org.apache.eventmesh.common.remote.response.FetchJobResponse;

import org.apache.commons.lang3.StringUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class FetchJobRequestHandler extends BaseRequestHandler<FetchJobRequest, FetchJobResponse> {

    @Autowired
    EventMeshJobInfoBizService jobInfoBizService;

    @Override
    public FetchJobResponse handler(FetchJobRequest request, Metadata metadata) {
        if (StringUtils.isBlank(request.getJobID())) {
            throw new AdminServerRuntimeException(ErrorCode.BAD_REQUEST, "job id is empty");
        }
        int jobID;
        try {
            jobID = Integer.parseInt(request.getJobID());
        } catch (NumberFormatException e) {
            throw new AdminServerRuntimeException(ErrorCode.BAD_REQUEST, String.format("illegal job id %s",
                request.getJobID()));
        }
        FetchJobResponse response = FetchJobResponse.successResponse();
        EventMeshJobDetail detail = jobInfoBizService.getJobDetail(request, metadata);
        if (detail == null) {
            return response;
        }
        response.setId(detail.getId());
        response.setName(detail.getName());
        response.setSourceConnectorConfig(detail.getSourceConnectorConfig());
        response.setSourceConnectorDesc(detail.getSourceConnectorDesc());
        response.setTransportType(detail.getTransportType());
        response.setSinkConnectorConfig(detail.getSinkConnectorConfig());
        response.setSourceConnectorDesc(detail.getSinkConnectorDesc());
        response.setState(detail.getState());
        response.setPosition(detail.getPosition());
        return response;
    }
}
