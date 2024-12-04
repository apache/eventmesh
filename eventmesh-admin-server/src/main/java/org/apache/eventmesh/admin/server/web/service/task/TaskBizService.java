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
import org.apache.eventmesh.admin.server.web.Response;
import org.apache.eventmesh.admin.server.web.db.entity.EventMeshJobInfo;
import org.apache.eventmesh.admin.server.web.db.entity.EventMeshTaskInfo;
import org.apache.eventmesh.admin.server.web.db.service.EventMeshTaskInfoService;
import org.apache.eventmesh.admin.server.web.pojo.JobDetail;
import org.apache.eventmesh.admin.server.web.service.job.JobInfoBizService;
import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.remote.TaskState;
import org.apache.eventmesh.common.remote.datasource.DataSource;
import org.apache.eventmesh.common.remote.datasource.DataSourceType;
import org.apache.eventmesh.common.remote.request.CreateTaskRequest;
import org.apache.eventmesh.common.remote.response.CreateTaskResponse;
import org.apache.eventmesh.common.utils.JsonUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
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

    private static final String TYPE = "type";

    private static final String DESC = "desc";

    private static final String CONF_CLAZZ = "confClazz";

    private static final String CONF = "conf";

    private static final String REGION = "region";

    @Transactional
    public CreateTaskResponse createTask(CreateTaskRequest req) {
        String taskID = req.getTaskId();
        if (StringUtils.isEmpty(taskID)) {
            taskID = UUID.randomUUID().toString();
            req.setTaskId(taskID);
        }

        String targetRegion = req.getTargetRegion();
        String remoteResponse = "";
        // not from other admin && target not equals with self region
        if (!req.isFlag() && !properties.getRegion().equals(targetRegion)) {
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
            remoteResponse = response.getBody();
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

        EventMeshTaskInfo taskInfo = new EventMeshTaskInfo();
        taskInfo.setTaskID(finalTaskID);
        taskInfo.setTaskName(req.getTaskName());
        taskInfo.setTaskDesc(req.getTaskDesc());
        taskInfo.setTaskState(TaskState.INIT.name());
        taskInfo.setCreateUid(req.getUid());
        taskInfo.setSourceRegion(req.getSourceRegion());
        taskInfo.setTargetRegion(req.getTargetRegion());
        List<EventMeshJobInfo> eventMeshJobInfoList = jobInfoService.createJobs(jobs);
        taskInfoService.save(taskInfo);
        return buildCreateTaskResponse(finalTaskID, eventMeshJobInfoList, remoteResponse);
    }

    private JobDetail parse(CreateTaskRequest.JobDetail src) throws ClassNotFoundException {
        JobDetail dst = new JobDetail();
        dst.setJobDesc(src.getJobDesc());
        dst.setTransportType(src.getTransportType());
        dst.setSourceConnectorDesc(src.getSourceConnectorDesc());
        try {
            dst.setSourceDataSource(mapToDataSource(src.getSourceDataSource()));
            dst.setSinkDataSource(mapToDataSource(src.getSinkDataSource()));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to map data source", e);
        }
        dst.setSinkConnectorDesc(src.getSinkConnectorDesc());
        // full/increase/check
        dst.setJobType(src.getJobType());
        dst.setFromRegion(src.getFromRegion());
        dst.setRunningRegion(src.getRunningRegion());
        return dst;
    }

    private DataSource mapToDataSource(Map<String, Object> dataMap) throws ClassNotFoundException {
        DataSource dataSource = new DataSource();
        dataSource.setType(DataSourceType.fromString(dataMap.get(TYPE).toString()));
        dataSource.setDesc((String) dataMap.get(DESC));
        dataSource.setConfClazz((Class<? extends Config>) Class.forName(dataMap.get(CONF_CLAZZ).toString()));
        dataSource.setConf(JsonUtils.parseObject(JsonUtils.toJSONString(dataMap.get(CONF)), dataSource.getConfClazz()));
        dataSource.setRegion((String) dataMap.get(REGION));
        return dataSource;
    }

    private CreateTaskResponse buildCreateTaskResponse(String taskId, List<EventMeshJobInfo> eventMeshJobInfoList, String remoteResponse) {
        CreateTaskResponse createTaskResponse = new CreateTaskResponse();
        createTaskResponse.setTaskId(taskId);
        List<CreateTaskRequest.JobDetail> jobDetailList = new ArrayList<>();
        if (!eventMeshJobInfoList.isEmpty()) {
            for (EventMeshJobInfo eventMeshJobInfo : eventMeshJobInfoList) {
                CreateTaskRequest.JobDetail jobDetail = new CreateTaskRequest.JobDetail();
                jobDetail.setJobId(eventMeshJobInfo.getJobID());
                jobDetail.setRunningRegion(eventMeshJobInfo.getRunningRegion());
                jobDetailList.add(jobDetail);
            }
        }
        if (!StringUtils.isEmpty(remoteResponse)) {
            Response response = JsonUtils.parseObject(remoteResponse, Response.class);
            CreateTaskResponse remoteCreateTaskResponse = JsonUtils.convertValue(response.getData(), CreateTaskResponse.class);
            jobDetailList.addAll(remoteCreateTaskResponse.getJobIdList());
        }
        createTaskResponse.setJobIdList(jobDetailList);
        return createTaskResponse;
    }
}
