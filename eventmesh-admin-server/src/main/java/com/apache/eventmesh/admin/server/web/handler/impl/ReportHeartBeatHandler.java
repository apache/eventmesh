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

package com.apache.eventmesh.admin.server.web.handler.impl;

import com.apache.eventmesh.admin.server.web.db.DBThreadPool;
import com.apache.eventmesh.admin.server.web.db.entity.EventMeshRuntimeHeartbeat;
import com.apache.eventmesh.admin.server.web.handler.BaseRequestHandler;
import com.apache.eventmesh.admin.server.web.service.heatbeat.EventMeshRuntimeHeartbeatBizService;

import lombok.extern.slf4j.Slf4j;

import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.remote.request.ReportHeartBeatRequest;
import org.apache.eventmesh.common.remote.response.EmptyAckResponse;
import org.apache.eventmesh.common.utils.IPUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ReportHeartBeatHandler extends BaseRequestHandler<ReportHeartBeatRequest, EmptyAckResponse> {

    @Autowired
    EventMeshRuntimeHeartbeatBizService heartbeatBizService;

    @Autowired
    DBThreadPool executor;

    @Override
    protected EmptyAckResponse handler(ReportHeartBeatRequest request, Metadata metadata) {
        executor.getExecutors().execute(() -> {
            EventMeshRuntimeHeartbeat heartbeat = new EventMeshRuntimeHeartbeat();
            int job;
            try {
                job = Integer.parseInt(request.getJobID());
            } catch (NumberFormatException e) {
                log.warn("runtime {} report heartbeat fail, illegal job id {}", request.getAddress(), request.getJobID());
                return;
            }
            heartbeat.setJobID(job);
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

        return new EmptyAckResponse();
    }
}
