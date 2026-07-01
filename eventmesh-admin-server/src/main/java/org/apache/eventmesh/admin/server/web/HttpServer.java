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

package org.apache.eventmesh.admin.server.web;

import org.apache.eventmesh.admin.server.web.db.service.EventMeshTaskInfoService;
import org.apache.eventmesh.admin.server.web.service.monitor.MonitorBizService;
import org.apache.eventmesh.admin.server.web.service.task.TaskBizService;
import org.apache.eventmesh.admin.server.web.service.verify.VerifyBizService;
import org.apache.eventmesh.common.remote.request.CreateTaskRequest;
import org.apache.eventmesh.common.remote.request.QueryTaskInfoRequest;
import org.apache.eventmesh.common.remote.request.QueryTaskMonitorRequest;
import org.apache.eventmesh.common.remote.request.ReportMonitorRequest;
import org.apache.eventmesh.common.remote.request.ReportVerifyRequest;
import org.apache.eventmesh.common.remote.request.TaskBachRequest;
import org.apache.eventmesh.common.remote.request.TaskIDRequest;
import org.apache.eventmesh.common.remote.response.CreateTaskResponse;
import org.apache.eventmesh.common.remote.response.HttpResponseResult;
import org.apache.eventmesh.common.remote.response.QueryTaskInfoResponse;
import org.apache.eventmesh.common.remote.response.QueryTaskMonitorResponse;
import org.apache.eventmesh.common.remote.response.SimpleResponse;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/eventmesh/admin")
@Slf4j
public class HttpServer {

    @Autowired
    private TaskBizService taskService;

    @Autowired
    private VerifyBizService verifyService;

    @Autowired
    private MonitorBizService monitorService;

    @Autowired
    private EventMeshTaskInfoService taskInfoService;

    @RequestMapping(value = "/createTask", method = RequestMethod.POST)
    public String createOrUpdateTask(@RequestBody CreateTaskRequest task) {
        log.info("receive http proto create task:{}", task);
        CreateTaskResponse createTaskResponse = taskService.createTask(task);
        log.info("receive http proto create task result:{}", createTaskResponse);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.setData(createTaskResponse);
        return JsonUtils.toJSONString(simpleResponse);
    }


    @RequestMapping(value = "/reportVerify", method = RequestMethod.POST)
    public String reportVerify(@RequestBody ReportVerifyRequest request) {
        log.info("receive http proto report verify request:{}", request);
        boolean result = verifyService.reportVerifyRecord(request);
        log.info("receive http proto report verify result:{}", result);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.setData(result);
        return JsonUtils.toJSONString(simpleResponse);
    }

    @RequestMapping(value = "/reportMonitor", method = RequestMethod.POST)
    public String reportMonitor(@RequestBody ReportMonitorRequest request) {
        log.info("receive http proto report monitor request:{}", request);
        boolean result = monitorService.reportMonitorRecord(request);
        log.info("receive http proto report monitor result:{}", result);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.setData(result);
        return JsonUtils.toJSONString(simpleResponse);
    }

    @RequestMapping(value = "/queryTaskMonitor", method = RequestMethod.POST)
    public String queryTaskMonitor(@RequestBody QueryTaskMonitorRequest request) {
        log.info("receive http proto query task monitor request:{}", request);
        QueryTaskMonitorResponse result = monitorService.queryTaskMonitors(request);
        log.info("receive http proto query task monitor result:{}", result);
        SimpleResponse simpleResponse = new SimpleResponse();
        simpleResponse.setData(result);
        return JsonUtils.toJSONString(simpleResponse);
    }

    @RequestMapping(value = "/queryTaskInfo", method = RequestMethod.POST)
    public HttpResponseResult<Object> queryTaskInfo(@RequestBody QueryTaskInfoRequest taskInfoRequest) {
        log.info("receive http query task info request:{}", taskInfoRequest);
        List<QueryTaskInfoResponse> taskInfosResponse = taskService.queryTaskInfo(taskInfoRequest);
        log.info("receive http query task info taskInfosResponse:{}", taskInfoRequest);
        if (taskInfosResponse.isEmpty()) {
            return HttpResponseResult.failed("NOT FOUND");
        }
        return HttpResponseResult.success(taskInfosResponse);
    }

    @RequestMapping(value = "/deleteTask", method = RequestMethod.DELETE)
    public HttpResponseResult<Object> deleteTask(@RequestBody TaskIDRequest taskIDRequest) {
        log.info("receive need to delete taskID:{}", taskIDRequest.getTaskID());
        boolean result = taskService.deleteTaskByTaskID(taskIDRequest);
        if (result) {
            return HttpResponseResult.success();
        } else {
            return HttpResponseResult.failed();
        }
    }

    @RequestMapping(value = "/startTask", method = RequestMethod.POST)
    public HttpResponseResult<Object> startTask(@RequestBody TaskIDRequest taskIDRequest) {
        log.info("receive start task ID:{}", taskIDRequest.getTaskID());
        taskService.startTask(taskIDRequest);
        return HttpResponseResult.success();
    }

    @RequestMapping(value = "/restartTask", method = RequestMethod.POST)
    public HttpResponseResult<Object> restartTask(@RequestBody TaskIDRequest taskIDRequest) {
        log.info("receive restart task ID:{}", taskIDRequest.getTaskID());
        taskService.restartTask(taskIDRequest);
        return HttpResponseResult.success();
    }

    @RequestMapping(value = "/stopTask", method = RequestMethod.POST)
    public HttpResponseResult<Object> stopTask(@RequestBody TaskIDRequest taskIDRequest) {
        log.info("receive stop task ID:{}", taskIDRequest.getTaskID());
        taskService.stopTask(taskIDRequest);
        return HttpResponseResult.success();
    }

    @RequestMapping(value = "/restartBatch", method = RequestMethod.POST)
    public HttpResponseResult<Object> restartBatch(@RequestBody List<TaskBachRequest> taskBachRequestList) {
        log.info("receive restart batch task IDs:{}", taskBachRequestList);
        List<String> errorNames = new ArrayList<>();
        taskService.restartBatchTask(taskBachRequestList, errorNames);
        if (!errorNames.isEmpty()) {
            return HttpResponseResult.failed(errorNames);
        }
        return HttpResponseResult.success();
    }

    @RequestMapping(value = "stopBatch", method = RequestMethod.POST)
    public HttpResponseResult<Object> stopBatch(@RequestBody List<TaskBachRequest> taskBachRequestList) {
        log.info("receive stop batch task IDs:{}", taskBachRequestList);
        List<String> errorNames = new ArrayList<>();
        taskService.stopBatchTask(taskBachRequestList, errorNames);
        if (!errorNames.isEmpty()) {
            return HttpResponseResult.failed(errorNames);
        }
        return HttpResponseResult.success();
    }

    @RequestMapping(value = "/startBatch", method = RequestMethod.POST)
    public HttpResponseResult<Object> startBatch(@RequestBody List<TaskBachRequest> taskBachRequestList) {
        log.info("receive start batch task IDs:{}", taskBachRequestList);
        List<String> errorNames = new ArrayList<>();
        taskService.startBatchTask(taskBachRequestList, errorNames);
        if (!errorNames.isEmpty()) {
            return HttpResponseResult.failed(errorNames);
        }
        return HttpResponseResult.success();
    }

}