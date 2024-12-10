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

import org.apache.eventmesh.admin.server.AdminServerProperties;
import org.apache.eventmesh.admin.server.web.db.entity.EventMeshJobInfo;
import org.apache.eventmesh.admin.server.web.handler.BaseRequestHandler;
import org.apache.eventmesh.admin.server.web.service.job.JobInfoBizService;
import org.apache.eventmesh.admin.server.web.service.monitor.MonitorBizService;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.request.ReportMonitorRequest;
import org.apache.eventmesh.common.remote.response.SimpleResponse;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ReportMonitorHandler extends BaseRequestHandler<ReportMonitorRequest, SimpleResponse> {

    @Autowired
    private MonitorBizService monitorService;

    @Autowired
    JobInfoBizService jobInfoBizService;

    @Autowired
    private AdminServerProperties properties;

    @Override
    protected SimpleResponse handler(ReportMonitorRequest request, Metadata metadata) {
        if (StringUtils.isAnyBlank(request.getTaskID(), request.getJobID(), request.getAddress())) {
            log.info("report monitor request [{}] illegal", request);
            return SimpleResponse.fail(ErrorCode.BAD_REQUEST, "request task id,job id or address is none");
        }

        String jobID = request.getJobID();
        EventMeshJobInfo jobInfo = jobInfoBizService.getJobInfo(jobID);
        if (jobInfo == null || StringUtils.isBlank(jobInfo.getFromRegion())) {
            log.info("report monitor job info [{}] illegal", request);
            return SimpleResponse.fail(ErrorCode.BAD_REQUEST, "job info is null or fromRegion is blank,job id:" + jobID);
        }
        String fromRegion = jobInfo.getFromRegion();
        String transportType = jobInfo.getTransportType();
        if (StringUtils.isEmpty(request.getTransportType())) {
            request.setTransportType(transportType);
        }
        String localRegion = properties.getRegion();
        log.info("report monitor request from region:{},localRegion:{},request:{}", fromRegion, localRegion, request);
        if (fromRegion.equalsIgnoreCase(localRegion)) {
            return monitorService.reportMonitorRecord(request) ? SimpleResponse.success() :
                SimpleResponse.fail(ErrorCode.INTERNAL_ERR, "save monitor "
                    + "request fail");
        } else {
            List<String> adminServerList = Arrays.asList(properties.getAdminServerList().get(fromRegion).split(";"));
            if (adminServerList == null || adminServerList.isEmpty()) {
                throw new RuntimeException("No admin server available for region: " + fromRegion);
            }
            String targetUrl = adminServerList.get(new Random().nextInt(adminServerList.size())) + "/eventmesh/admin/reportMonitor";
            log.info("start transfer monitor request to from region admin server. from region:{}, targetUrl:{}", fromRegion, targetUrl);
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("transfer monitor request to from region admin server error. from region:{}, targetUrl:{}", fromRegion, targetUrl);
                return SimpleResponse.fail(ErrorCode.INTERNAL_ERR,
                    "save monitor request fail,code:" + response.getStatusCode() + ",msg:" + response.getBody());
            }
            return SimpleResponse.success();
        }
    }
}
