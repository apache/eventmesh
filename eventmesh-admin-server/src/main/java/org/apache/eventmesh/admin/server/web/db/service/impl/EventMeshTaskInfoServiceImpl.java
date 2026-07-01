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

package org.apache.eventmesh.admin.server.web.db.service.impl;

import org.apache.eventmesh.admin.server.web.db.entity.EventMeshDataSource;
import org.apache.eventmesh.admin.server.web.db.entity.EventMeshJobInfo;
import org.apache.eventmesh.admin.server.web.db.entity.EventMeshMysqlPosition;
import org.apache.eventmesh.admin.server.web.db.entity.EventMeshTaskInfo;
import org.apache.eventmesh.admin.server.web.db.mapper.EventMeshTaskInfoMapper;
import org.apache.eventmesh.admin.server.web.db.service.EventMeshDataSourceService;
import org.apache.eventmesh.admin.server.web.db.service.EventMeshJobInfoService;
import org.apache.eventmesh.admin.server.web.db.service.EventMeshMysqlPositionService;
import org.apache.eventmesh.admin.server.web.db.service.EventMeshTaskInfoService;
import org.apache.eventmesh.common.remote.JobState;
import org.apache.eventmesh.common.remote.TaskState;
import org.apache.eventmesh.common.remote.request.QueryTaskInfoRequest;
import org.apache.eventmesh.common.remote.response.QueryTaskInfoResponse;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import lombok.extern.slf4j.Slf4j;

/**
 * event_mesh_task_info
 */
@Slf4j
@Service
public class EventMeshTaskInfoServiceImpl extends ServiceImpl<EventMeshTaskInfoMapper, EventMeshTaskInfo>
    implements EventMeshTaskInfoService {

    @Autowired
    private EventMeshTaskInfoMapper taskInfoMapper;

    @Autowired
    private EventMeshJobInfoService jobInfoService;

    @Autowired
    private EventMeshDataSourceService dataSourceService;

    @Autowired
    private EventMeshMysqlPositionService mysqlPositionService;

    @Override
    public List<QueryTaskInfoResponse> queryTaskInfo(QueryTaskInfoRequest taskInfoRequest) {

        log.info("receive query task info request:{}", taskInfoRequest);

        List<QueryTaskInfoResponse> queryTaskInfoResponseList = new ArrayList<>();

        Integer currentPage = taskInfoRequest.getCurrentPage();
        Integer pageSize = taskInfoRequest.getPageSize();

        // query by page
        if (StringUtils.isEmpty(taskInfoRequest.getTaskID())
            && currentPage != null
            && pageSize != null) {

            Page<EventMeshTaskInfo> page = new Page<>();
            page.setCurrent(currentPage);
            page.setSize(pageSize);
            List<EventMeshTaskInfo> eventMeshTaskInfoList = taskInfoMapper.selectPage(page, Wrappers.<EventMeshTaskInfo>query()
                .ne("taskState", TaskState.DELETE.name())).getRecords();
            queryTaskInfoResponseList = getQueryTaskInfoResponses(eventMeshTaskInfoList, queryTaskInfoResponseList);

        }

        if (StringUtils.isNotEmpty(taskInfoRequest.getTaskID()) || StringUtils.isNotEmpty(taskInfoRequest.getTaskID())) {
            queryTaskInfoResponseList = eventMeshTaskInfoList(taskInfoRequest);
        }

        // if (StringUtils.isNotEmpty(taskInfoRequest.getJobType())) {
        //
        // }
        //
        // if (StringUtils.isNotEmpty(taskInfoRequest.getSourceDataID())) {
        //
        // }
        //
        // if (StringUtils.isNotEmpty(taskInfoRequest.getTargetDataID())) {
        //
        // }
        //
        // if (StringUtils.isNotEmpty(taskInfoRequest.getIp())) {
        //
        // }
        //
        // if (StringUtils.isNotEmpty(taskInfoRequest.getSourceTableName())) {
        //
        // }
        //
        // if (StringUtils.isNotEmpty(taskInfoRequest.getTaskMathID())) {
        //
        // }

        log.info("query event mesh task info response result:{}", queryTaskInfoResponseList);

        return queryTaskInfoResponseList;
    }

    @Transactional
    private List<QueryTaskInfoResponse> eventMeshTaskInfoList(QueryTaskInfoRequest taskInfoRequest) {

        List<EventMeshTaskInfo> eventMeshTaskInfoList = new ArrayList<>();

        Page<EventMeshTaskInfo> page = new Page<>();
        page.setCurrent(taskInfoRequest.getCurrentPage());
        page.setSize(taskInfoRequest.getPageSize());

        if (StringUtils.isNotEmpty(taskInfoRequest.getTaskID())) {
            eventMeshTaskInfoList = taskInfoMapper.selectPage(page, Wrappers.<EventMeshTaskInfo>query()
                    .eq("taskID", taskInfoRequest.getTaskID())
                    .ne("taskState", TaskState.DELETE.name()))
                .getRecords();
        }

        if (StringUtils.isNotEmpty(taskInfoRequest.getTaskDesc())) {
            eventMeshTaskInfoList = taskInfoMapper.selectPage(page, Wrappers.<EventMeshTaskInfo>query()
                    .like("taskDesc", taskInfoRequest.getTaskDesc())
                    .ne("jobState", JobState.DELETE.name()))
                .getRecords();
        }

        List<QueryTaskInfoResponse> eventMeshTaskInfos = new ArrayList<>();

        List<QueryTaskInfoResponse> queryTaskInfoResponse = getQueryTaskInfoResponses(eventMeshTaskInfoList, eventMeshTaskInfos);
        log.info("query task info result queryTaskInfoResponse:{}", queryTaskInfoResponse);

        return queryTaskInfoResponse;
    }

    private List<QueryTaskInfoResponse> getQueryTaskInfoResponses(List<EventMeshTaskInfo> eventMeshTaskInfoList,
                                                                  List<QueryTaskInfoResponse> eventMeshTaskInfos) {

        for (EventMeshTaskInfo meshTaskInfo : eventMeshTaskInfoList) {
            QueryTaskInfoResponse eventMeshTaskInfo = initEventMeshTaskInfo(meshTaskInfo);
            eventMeshTaskInfos.add(eventMeshTaskInfo);
        }

        if (!eventMeshTaskInfoList.isEmpty()) {
            List<QueryTaskInfoResponse.EventMeshJobInfo> eventMeshJobInfoList = new ArrayList<>();
            for (QueryTaskInfoResponse eventMeshTaskInfo : eventMeshTaskInfos) {
                List<EventMeshJobInfo> eventMeshJobInfos = jobInfoService.list(Wrappers.<EventMeshJobInfo>query()
                    .eq("taskID", eventMeshTaskInfo.getTaskID())
                    .ne("jobState", JobState.DELETE.name()));

                for (EventMeshJobInfo eventMeshJobInfo : eventMeshJobInfos) {
                    QueryTaskInfoResponse.EventMeshJobInfo eventMeshJobInfoCovert = initEventMeshJobInfo(eventMeshJobInfo);
                    eventMeshJobInfoList.add(eventMeshJobInfoCovert);
                }

                if (!eventMeshJobInfoList.isEmpty()) {
                    for (QueryTaskInfoResponse.EventMeshJobInfo eventMeshJobInfo : eventMeshJobInfoList) {
                        QueryTaskInfoResponse.EventMeshDataSource dataSource = covertEventMeshDataSource(
                            querySourceOrSinkData(eventMeshJobInfo.getSourceData()));
                        QueryTaskInfoResponse.EventMeshDataSource dataSink = covertEventMeshDataSource(
                            querySourceOrSinkData(eventMeshJobInfo.getTargetData()));

                        EventMeshMysqlPosition eventMeshMysqlPosition = mysqlPositionService.getOne(Wrappers.<EventMeshMysqlPosition>query().eq(
                            "jobID",
                            eventMeshJobInfo.getJobID()
                        ));


                        QueryTaskInfoResponse.EventMeshMysqlPosition mysqlPosition = covertEventMeshMysqlPosition(eventMeshMysqlPosition);

                        eventMeshJobInfo.setEventMeshMysqlPosition(mysqlPosition);
                        eventMeshJobInfo.setDataSource(dataSource);
                        eventMeshJobInfo.setDataSink(dataSink);
                    }
                }

                // set job info to same taskID
                eventMeshTaskInfo.setEventMeshJobInfoList(eventMeshJobInfoList);
            }
        }

        List<QueryTaskInfoResponse> queryTaskInfoResponse = new ArrayList<>();
        if (!eventMeshTaskInfos.isEmpty()) {
            queryTaskInfoResponse.addAll(eventMeshTaskInfos);
        }

        return queryTaskInfoResponse;
    }

    /**
     * QueryTaskInfoResponse.EventMeshDataSource covert
     *
     * @param eventMeshData EventMeshDataSource
     * @return meshData
     */
    private static QueryTaskInfoResponse.EventMeshDataSource covertEventMeshDataSource(EventMeshDataSource eventMeshData) {
        QueryTaskInfoResponse.EventMeshDataSource meshData = new QueryTaskInfoResponse.EventMeshDataSource();
        if (ObjectUtils.isEmpty(eventMeshData)) {
            return null;
        }
        meshData.setId(eventMeshData.getId());
        meshData.setDataType(eventMeshData.getDataType());
        meshData.setConfiguration(eventMeshData.getConfiguration());
        meshData.setConfigurationClass(eventMeshData.getConfigurationClass());
        meshData.setDescription(eventMeshData.getDescription());
        meshData.setRegion(eventMeshData.getRegion());
        meshData.setCreateUid(eventMeshData.getCreateUid());
        meshData.setUpdateUid(eventMeshData.getUpdateUid());
        meshData.setCreateTime(eventMeshData.getCreateTime());
        meshData.setUpdateTime(eventMeshData.getUpdateTime());
        return meshData;
    }

    /**
     * getSourceOrSinkData
     *
     * @param id id
     * @return EventMeshDataSource
     */
    private EventMeshDataSource querySourceOrSinkData(Integer id) {
        return dataSourceService.getOne(Wrappers.<EventMeshDataSource>query().eq(
            "id",
            id));
    }

    /**
     * QueryTaskInfoResponse.EventMeshMysqlPosition
     *
     * @param mysqlPosition EventMeshMysqlPosition
     * @return position
     */
    private static QueryTaskInfoResponse.EventMeshMysqlPosition covertEventMeshMysqlPosition(EventMeshMysqlPosition mysqlPosition) {
        QueryTaskInfoResponse.EventMeshMysqlPosition position = new QueryTaskInfoResponse.EventMeshMysqlPosition();
        if (ObjectUtils.isEmpty(mysqlPosition)) {
            return null;
        }
        position.setId(mysqlPosition.getId());
        position.setJobID(mysqlPosition.getJobID());
        position.setServerUUID(mysqlPosition.getServerUUID());
        position.setAddress(mysqlPosition.getAddress());
        position.setPosition(mysqlPosition.getPosition());
        position.setGtid(mysqlPosition.getGtid());
        position.setCurrentGtid(mysqlPosition.getCurrentGtid());
        position.setTimestamp(mysqlPosition.getTimestamp());
        position.setJournalName(mysqlPosition.getJournalName());
        position.setCreateTime(mysqlPosition.getCreateTime());
        position.setUpdateTime(mysqlPosition.getUpdateTime());
        return position;
    }

    /**
     * EventMeshJobInfo covert
     *
     * @param eventMeshJobInfo EventMeshJobInfo
     * @return QueryTaskInfoResponse.EventMeshJobInfo
     */
    private static QueryTaskInfoResponse.EventMeshJobInfo initEventMeshJobInfo(EventMeshJobInfo eventMeshJobInfo) {
        QueryTaskInfoResponse.EventMeshJobInfo eventMeshJobInfoCovert = new QueryTaskInfoResponse.EventMeshJobInfo();
        if (ObjectUtils.isEmpty(eventMeshJobInfo)) {
            return null;
        }
        eventMeshJobInfoCovert.setId(eventMeshJobInfo.getId());
        eventMeshJobInfoCovert.setJobID(eventMeshJobInfo.getJobID());
        eventMeshJobInfoCovert.setJobDesc(eventMeshJobInfo.getJobDesc());
        eventMeshJobInfoCovert.setTaskID(eventMeshJobInfo.getTaskID());
        eventMeshJobInfoCovert.setTransportType(eventMeshJobInfo.getTransportType());
        eventMeshJobInfoCovert.setSourceData(eventMeshJobInfo.getSourceData());
        eventMeshJobInfoCovert.setTargetData(eventMeshJobInfo.getTargetData());
        eventMeshJobInfoCovert.setJobState(eventMeshJobInfo.getJobState());
        eventMeshJobInfoCovert.setJobType(eventMeshJobInfo.getJobType());
        eventMeshJobInfoCovert.setFromRegion(eventMeshJobInfo.getFromRegion());
        eventMeshJobInfoCovert.setRunningRegion(eventMeshJobInfo.getRunningRegion());
        eventMeshJobInfoCovert.setCreateUid(eventMeshJobInfo.getCreateUid());
        eventMeshJobInfoCovert.setUpdateUid(eventMeshJobInfo.getUpdateUid());
        eventMeshJobInfoCovert.setCreateTime(eventMeshJobInfo.getCreateTime());
        eventMeshJobInfoCovert.setUpdateTime(eventMeshJobInfo.getUpdateTime());
        return eventMeshJobInfoCovert;
    }

    /**
     * EventMeshTaskInfo covert
     *
     * @param meshTaskInfo EventMeshTaskInfo
     * @return QueryTaskInfoResponse
     */
    private static QueryTaskInfoResponse initEventMeshTaskInfo(EventMeshTaskInfo meshTaskInfo) {
        QueryTaskInfoResponse eventMeshTaskInfo = new QueryTaskInfoResponse();
        eventMeshTaskInfo.setId(meshTaskInfo.getId());
        eventMeshTaskInfo.setTaskID(meshTaskInfo.getTaskID());
        eventMeshTaskInfo.setTaskDesc(meshTaskInfo.getTaskDesc());
        eventMeshTaskInfo.setTaskState(meshTaskInfo.getTaskState());
        eventMeshTaskInfo.setSourceRegion(meshTaskInfo.getSourceRegion());
        eventMeshTaskInfo.setTargetRegion(meshTaskInfo.getTargetRegion());
        eventMeshTaskInfo.setCreateUid(meshTaskInfo.getCreateUid());
        eventMeshTaskInfo.setUpdateUid(meshTaskInfo.getUpdateUid());
        eventMeshTaskInfo.setCreateTime(meshTaskInfo.getCreateTime());
        eventMeshTaskInfo.setUpdateTime(meshTaskInfo.getUpdateTime());
        return eventMeshTaskInfo;
    }

}