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

import org.apache.eventmesh.admin.server.web.db.entity.EventMeshTaskInfo;
import org.apache.eventmesh.admin.server.web.db.service.EventMeshTaskInfoService;
import org.apache.eventmesh.admin.server.web.pojo.JobDetail;
import org.apache.eventmesh.admin.server.web.service.job.JobInfoBizService;
import org.apache.eventmesh.common.remote.TaskState;
import org.apache.eventmesh.common.remote.request.CreateTaskRequest;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskBizService {
    @Autowired
    private EventMeshTaskInfoService taskInfoService;

    @Autowired
    private JobInfoBizService jobInfoService;

    @Transactional
    public String createTask(CreateTaskRequest req) {
        String taskID = UUID.randomUUID().toString();
        List<JobDetail> jobs = req.getJobs().stream().map(x -> {
            JobDetail job = parse(x);
            job.setTaskID(taskID);
            job.setRegion(req.getRegion());
            job.setCreateUid(req.getUid());
            job.setUpdateUid(req.getUid());
            return job;
        }).collect(Collectors.toList());
        jobInfoService.createJobs(jobs);
        EventMeshTaskInfo taskInfo = new EventMeshTaskInfo();
        taskInfo.setTaskID(taskID);
        taskInfo.setName(req.getName());
        taskInfo.setDesc(req.getDesc());
        taskInfo.setState(TaskState.INIT.name());
        taskInfo.setCreateUid(req.getUid());
        taskInfo.setFromRegion(req.getRegion());
        taskInfoService.save(taskInfo);
        return taskID;
    }

    private JobDetail parse(CreateTaskRequest.JobDetail src) {
        JobDetail dst = new JobDetail();
        dst.setDesc(src.getDesc());
        dst.setTransportType(src.getTransportType());
        dst.setSourceConnectorDesc(src.getSourceConnectorDesc());
        dst.setSourceDataSource(src.getSourceDataSource());
        dst.setSinkConnectorDesc(src.getSinkConnectorDesc());
        dst.setSinkDataSource(src.getSinkDataSource());
        dst.setJobType(src.getJobType());
        return dst;
    }
}
