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
import org.apache.eventmesh.admin.server.web.db.entity.EventMeshRuntimeHeartbeat;
import org.apache.eventmesh.admin.server.web.handler.BaseRequestHandler;
import org.apache.eventmesh.admin.server.web.service.heatbeat.RuntimeHeartbeatBizService;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.request.ReportHeartBeatRequest;
import org.apache.eventmesh.common.remote.response.SimpleResponse;
import org.apache.eventmesh.common.utils.IPUtils;

import org.apache.commons.lang3.StringUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ReportHeartBeatHandler extends BaseRequestHandler<ReportHeartBeatRequest, SimpleResponse> {

    @Autowired
    RuntimeHeartbeatBizService heartbeatBizService;

    @Autowired
    DBThreadPool executor;

    @Override
    protected SimpleResponse handler(ReportHeartBeatRequest request, Metadata metadata) {
        if (StringUtils.isBlank(request.getJobID()) || StringUtils.isBlank(request.getAddress())) {
            log.info("request [{}] id or reporter address is empty", request);
            return SimpleResponse.fail(ErrorCode.BAD_REQUEST, "request id or reporter address is empty");
        }
        executor.getExecutors().execute(() -> {
            EventMeshRuntimeHeartbeat heartbeat = new EventMeshRuntimeHeartbeat();
            heartbeat.setJobID(request.getJobID());
            heartbeat.setReportTime(request.getReportedTimeStamp());
            heartbeat.setAdminAddr(IPUtils.getLocalAddress());
            heartbeat.setRuntimeAddr(request.getAddress());
            try {
                if (!heartbeatBizService.saveOrUpdateByRuntimeAddress(heartbeat)) {
                    log.warn("save or update heartbeat request [{}] fail", request);
                }
            } catch (Exception e) {
                log.warn("save or update heartbeat request [{}] fail", request, e);
            }
        });

        return SimpleResponse.success();
    }
}
