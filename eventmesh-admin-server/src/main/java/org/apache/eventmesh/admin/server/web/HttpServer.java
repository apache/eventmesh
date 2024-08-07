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

import org.apache.eventmesh.admin.server.web.service.task.TaskBizService;
import org.apache.eventmesh.common.remote.request.CreateTaskRequest;
import org.apache.eventmesh.common.remote.response.CreateTaskResponse;
import org.apache.eventmesh.common.utils.JsonUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/eventmesh/admin")
public class HttpServer {

    @Autowired
    private TaskBizService taskService;

    @RequestMapping(value = "/createTask", method = RequestMethod.POST)
    public ResponseEntity<Object> createOrUpdateTask(@RequestBody CreateTaskRequest task) {
        CreateTaskResponse createTaskResponse = taskService.createTask(task);
        return ResponseEntity.ok(JsonUtils.toJSONString(Response.success(createTaskResponse)));
    }

    public boolean deleteTask(Long id) {
        return false;
    }


}
