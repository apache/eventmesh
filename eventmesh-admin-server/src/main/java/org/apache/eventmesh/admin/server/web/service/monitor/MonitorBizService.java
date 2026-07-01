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

package org.apache.eventmesh.admin.server.web.service.monitor;

import org.apache.eventmesh.admin.server.web.db.entity.EventMeshMonitor;
import org.apache.eventmesh.admin.server.web.db.service.EventMeshMonitorService;
import org.apache.eventmesh.common.remote.request.QueryTaskMonitorRequest;
import org.apache.eventmesh.common.remote.request.ReportMonitorRequest;
import org.apache.eventmesh.common.remote.response.QueryTaskMonitorResponse;
import org.apache.eventmesh.common.remote.task.TaskMonitor;
import org.apache.eventmesh.common.utils.JsonUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MonitorBizService {

    @Autowired
    private EventMeshMonitorService monitorService;

    public boolean reportMonitorRecord(ReportMonitorRequest request) {
        EventMeshMonitor monitor = new EventMeshMonitor();
        monitor.setTaskID(request.getTaskID());
        monitor.setJobID(request.getJobID());
        monitor.setAddress(request.getAddress());
        monitor.setTransportType(request.getTransportType());
        monitor.setConnectorStage(request.getConnectorStage());
        monitor.setTotalReqNum(request.getTotalReqNum());
        monitor.setTotalTimeCost(request.getTotalTimeCost());
        monitor.setMaxTimeCost(request.getMaxTimeCost());
        monitor.setAvgTimeCost(request.getAvgTimeCost());
        monitor.setTps(request.getTps());
        return monitorService.save(monitor);
    }

    public QueryTaskMonitorResponse queryTaskMonitors(QueryTaskMonitorRequest request) {
        if (StringUtils.isBlank(request.getTaskID())) {
            throw new RuntimeException("task id is empty");
        }
        long limit = request.getLimit();
        if (limit <= 0) {
            log.info("query task monitor limit:{},use default value:{}", limit, 10);
            limit = 10;
        }

        Page<EventMeshMonitor> queryPage = new Page<>();
        queryPage.setCurrent(1);
        queryPage.setSize(limit);
        queryPage.addOrder(OrderItem.desc("createTime"));

        QueryWrapper<EventMeshMonitor> queryWrapper = new QueryWrapper<EventMeshMonitor>();
        queryWrapper.eq("taskID", request.getTaskID());
        if (StringUtils.isNotEmpty(request.getJobID())) {
            queryWrapper.eq("jobID", request.getJobID());
        }
        List<EventMeshMonitor> eventMeshMonitors = monitorService.list(queryPage, queryWrapper);
        List<TaskMonitor> taskMonitorList = new ArrayList<>();
        if (eventMeshMonitors != null) {
            log.info("query event mesh monitor size:{}", eventMeshMonitors.size());
            if (log.isDebugEnabled()) {
                log.debug("query event mesh monitor content:{}", JsonUtils.toJSONString(eventMeshMonitors));
            }
            for (EventMeshMonitor eventMeshMonitor : eventMeshMonitors) {
                TaskMonitor monitor = new TaskMonitor();
                monitor.setTaskID(eventMeshMonitor.getTaskID());
                monitor.setJobID(eventMeshMonitor.getJobID());
                monitor.setAddress(eventMeshMonitor.getAddress());
                monitor.setTransportType(eventMeshMonitor.getTransportType());
                monitor.setConnectorStage(eventMeshMonitor.getConnectorStage());
                monitor.setTotalReqNum(eventMeshMonitor.getTotalReqNum());
                monitor.setTotalTimeCost(eventMeshMonitor.getTotalTimeCost());
                monitor.setMaxTimeCost(eventMeshMonitor.getMaxTimeCost());
                monitor.setAvgTimeCost(eventMeshMonitor.getAvgTimeCost());
                monitor.setTps(eventMeshMonitor.getTps());
                monitor.setCreateTime(eventMeshMonitor.getCreateTime());
                taskMonitorList.add(monitor);
            }
        }
        QueryTaskMonitorResponse queryTaskMonitorResponse = new QueryTaskMonitorResponse();
        queryTaskMonitorResponse.setTaskMonitors(taskMonitorList);
        return queryTaskMonitorResponse;
    }
}
