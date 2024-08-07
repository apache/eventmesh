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

package org.apache.eventmesh.admin.server.web.service.task;

import org.apache.eventmesh.admin.server.AdminServerProperties;
import org.apache.eventmesh.admin.server.web.db.entity.EventMeshTaskInfo;
import org.apache.eventmesh.admin.server.web.db.service.EventMeshTaskInfoService;
import org.apache.eventmesh.admin.server.web.pojo.JobDetail;
import org.apache.eventmesh.admin.server.web.service.job.JobInfoBizService;
import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.remote.TaskState;
import org.apache.eventmesh.common.remote.datasource.DataSource;
import org.apache.eventmesh.common.remote.datasource.DataSourceType;
import org.apache.eventmesh.common.remote.request.CreateTaskRequest;
import org.apache.eventmesh.common.utils.JsonUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
public class TaskBizService {

    @Autowired
    private EventMeshTaskInfoService taskInfoService;

    @Autowired
    private JobInfoBizService jobInfoService;

    @Autowired
    private AdminServerProperties properties;

    @Transactional
    public String createTask(CreateTaskRequest req) {
        String taskID = req.getTaskId();
        if (StringUtils.isEmpty(taskID)) {
            taskID = UUID.randomUUID().toString();
            req.setTaskId(taskID);
        }

        String targetRegion = req.getTargetRegion();
        // not from other admin && target not equals with self region
        if (!req.isFlag() && !StringUtils.equals(properties.getRegion(), targetRegion)) {
            List<String> adminServerList = properties.getAdminServerList().get(targetRegion);
            if (adminServerList == null || adminServerList.isEmpty()) {
                throw new RuntimeException("No admin server available for region: " + targetRegion);
            }
            String targetUrl = adminServerList.get(new Random().nextInt(adminServerList.size())) + "/eventmesh/admin/createTask";

            RestTemplate restTemplate = new RestTemplate();
            req.setFlag(true);
            ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, req, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to create task on admin server: " + targetUrl);
            }
        }

        String finalTaskID = taskID;
        List<JobDetail> jobs = req.getJobs().stream().map(x -> {
            JobDetail job = null;
            try {
                job = parse(x);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            job.setTaskID(finalTaskID);
            job.setCreateUid(req.getUid());
            job.setUpdateUid(req.getUid());
            return job;
        }).collect(Collectors.toList());
        jobInfoService.createJobs(jobs);
        EventMeshTaskInfo taskInfo = new EventMeshTaskInfo();
        taskInfo.setTaskID(finalTaskID);
        taskInfo.setTaskName(req.getTaskName());
        taskInfo.setTaskDesc(req.getTaskDesc());
        taskInfo.setTaskState(TaskState.INIT.name());
        taskInfo.setCreateUid(req.getUid());
        taskInfo.setSourceRegion(req.getSourceRegion());
        taskInfo.setTargetRegion(req.getTargetRegion());
        taskInfoService.save(taskInfo);
        return finalTaskID;
    }

    private JobDetail parse(CreateTaskRequest.JobDetail src) throws ClassNotFoundException {
        JobDetail dst = new JobDetail();
        dst.setJobDesc(src.getJobDesc());
        dst.setTransportType(src.getTransportType());
        dst.setSourceConnectorDesc(src.getSourceConnectorDesc());
        Map<String, Object> sourceDataMap = src.getSourceDataSource();
        DataSource sourceDataSource = new DataSource();
        sourceDataSource.setType(DataSourceType.fromString(sourceDataMap.get("type").toString()));
        sourceDataSource.setDesc((String) sourceDataMap.get("desc"));
        sourceDataSource.setConfClazz((Class<? extends Config>) Class.forName(sourceDataMap.get("confClazz").toString()));
        sourceDataSource.setConf(JsonUtils.parseObject(JsonUtils.toJSONString(sourceDataMap.get("conf")), sourceDataSource.getConfClazz()));
        sourceDataSource.setRegion((String) sourceDataMap.get("region"));
        dst.setSourceDataSource(sourceDataSource);

        dst.setSinkConnectorDesc(src.getSinkConnectorDesc());
        Map<String, Object> sinkDataMap = src.getSinkDataSource();
        DataSource sinkDataSource = new DataSource();
        sinkDataSource.setType(DataSourceType.fromString(sinkDataMap.get("type").toString()));
        sinkDataSource.setDesc((String) sinkDataMap.get("desc"));
        sinkDataSource.setConfClazz((Class<? extends Config>) Class.forName(sinkDataMap.get("confClazz").toString()));
        sinkDataSource.setConf(JsonUtils.parseObject(JsonUtils.toJSONString(sinkDataMap.get("conf")), sinkDataSource.getConfClazz()));
        sinkDataSource.setRegion((String) sinkDataMap.get("region"));
        dst.setSinkDataSource(sinkDataSource);

        // full/increase/check
        dst.setJobType(src.getJobType());
        dst.setFromRegion(src.getFromRegion());
        dst.setRunningRegion(src.getRunningRegion());
        return dst;
    }
}
