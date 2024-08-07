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

package org.apache.eventmesh.admin.server.web.service.job;

import org.apache.eventmesh.admin.server.AdminServerProperties;
import org.apache.eventmesh.admin.server.AdminServerRuntimeException;
import org.apache.eventmesh.admin.server.web.db.entity.EventMeshDataSource;
import org.apache.eventmesh.admin.server.web.db.entity.EventMeshJobInfo;
import org.apache.eventmesh.admin.server.web.db.service.EventMeshDataSourceService;
import org.apache.eventmesh.admin.server.web.db.service.EventMeshJobInfoExtService;
import org.apache.eventmesh.admin.server.web.db.service.EventMeshJobInfoService;
import org.apache.eventmesh.admin.server.web.pojo.JobDetail;
import org.apache.eventmesh.admin.server.web.service.datasource.DataSourceBizService;
import org.apache.eventmesh.admin.server.web.service.position.PositionBizService;
import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.remote.TaskState;
import org.apache.eventmesh.common.remote.TransportType;
import org.apache.eventmesh.common.remote.datasource.DataSource;
import org.apache.eventmesh.common.remote.datasource.DataSourceType;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.request.CreateOrUpdateDataSourceReq;
import org.apache.eventmesh.common.utils.JsonUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import lombok.extern.slf4j.Slf4j;

/**
 * for table 'event_mesh_job_info' db operation
 */
@Service
@Slf4j
public class JobInfoBizService {

    @Autowired
    private EventMeshJobInfoService jobInfoService;

    @Autowired
    private EventMeshJobInfoExtService jobInfoExtService;

    @Autowired
    private DataSourceBizService dataSourceBizService;

    @Autowired
    private EventMeshDataSourceService dataSourceService;

    @Autowired
    private PositionBizService positionBizService;

    @Autowired
    private AdminServerProperties properties;

    public boolean updateJobState(String jobID, TaskState state) {
        if (jobID == null || state == null) {
            return false;
        }
        EventMeshJobInfo jobInfo = new EventMeshJobInfo();
        jobInfo.setJobState(state.name());
        return jobInfoService.update(jobInfo, Wrappers.<EventMeshJobInfo>update().eq("jobID", jobID).ne("state", TaskState.DELETE.name()));
    }

    @Transactional
    public List<EventMeshJobInfo> createJobs(List<JobDetail> jobs) {
        if (jobs == null || jobs.isEmpty() || jobs.stream().anyMatch(job -> StringUtils.isBlank(job.getTaskID()))) {
            log.warn("when create jobs, task id is empty or jobs config is empty ");
            return null;
        }
        List<EventMeshJobInfo> entityList = new LinkedList<>();

        for (JobDetail job : jobs) {
            // if running region not equal with admin region continue
            if (!job.getRunningRegion().equals(properties.getRegion())) {
                continue;
            }
            EventMeshJobInfo entity = new EventMeshJobInfo();
            entity.setJobState(TaskState.INIT.name());
            entity.setTaskID(job.getTaskID());
            entity.setJobType(job.getJobType().name());
            entity.setJobDesc(job.getJobDesc());
            String jobID = UUID.randomUUID().toString();
            entity.setJobID(jobID);
            entity.setTransportType(job.getTransportType().name());
            entity.setCreateUid(job.getCreateUid());
            entity.setUpdateUid(job.getUpdateUid());
            entity.setFromRegion(job.getFromRegion());
            entity.setRunningRegion(job.getRunningRegion());
            CreateOrUpdateDataSourceReq source = new CreateOrUpdateDataSourceReq();
            source.setType(job.getTransportType().getSrc());
            source.setOperator(job.getCreateUid());
            source.setRegion(job.getSourceDataSource().getRegion());
            source.setDesc(job.getSourceConnectorDesc());
            source.setConfig(job.getSourceDataSource().getConf());
            source.setConfigClass(job.getSourceDataSource().getConfClazz().getName());
            EventMeshDataSource createdSource = dataSourceBizService.createDataSource(source);
            entity.setSourceData(createdSource.getId());

            CreateOrUpdateDataSourceReq sink = new CreateOrUpdateDataSourceReq();
            sink.setType(job.getTransportType().getDst());
            sink.setOperator(job.getCreateUid());
            sink.setRegion(job.getSinkDataSource().getRegion());
            sink.setDesc(job.getSinkConnectorDesc());
            sink.setConfig(job.getSinkDataSource().getConf());
            sink.setConfigClass(job.getSinkDataSource().getConfClazz().getName());
            EventMeshDataSource createdSink = dataSourceBizService.createDataSource(sink);
            entity.setTargetData(createdSink.getId());

            entityList.add(entity);
        }
        int changed = jobInfoExtService.batchSave(entityList);
        if (changed != jobs.size()) {
            throw new AdminServerRuntimeException(ErrorCode.INTERNAL_ERR, String.format("create [%d] jobs of not match expect [%d]",
                changed, jobs.size()));
        }
        return entityList;
    }


    public JobDetail getJobDetail(String jobID) {
        if (jobID == null) {
            return null;
        }
        EventMeshJobInfo job = jobInfoService.getOne(Wrappers.<EventMeshJobInfo>query().eq("jobID", jobID));
        if (job == null) {
            return null;
        }
        JobDetail detail = new JobDetail();
        detail.setTaskID(job.getTaskID());
        detail.setJobID(job.getJobID());
        EventMeshDataSource source = dataSourceService.getById(job.getSourceData());
        EventMeshDataSource target = dataSourceService.getById(job.getTargetData());
        if (source != null) {
            if (!StringUtils.isBlank(source.getConfiguration())) {
                try {
                    DataSource sourceDataSource = new DataSource();
                    Class<?> configClass = Class.forName(source.getConfigurationClass());
                    sourceDataSource.setConf((Config) JsonUtils.parseObject(source.getConfiguration(), configClass));
                    detail.setSourceDataSource(sourceDataSource);
                } catch (Exception e) {
                    log.warn("parse source config id [{}] fail", job.getSourceData(), e);
                    throw new AdminServerRuntimeException(ErrorCode.BAD_DB_DATA, "illegal source data source config");
                }
            }
            detail.setSourceConnectorDesc(source.getDescription());
            if (source.getDataType() != null) {
                detail.setPositions(positionBizService.getPositionByJobID(job.getJobID(),
                    DataSourceType.getDataSourceType(source.getDataType())));

            }
        }
        if (target != null) {
            if (!StringUtils.isBlank(target.getConfiguration())) {
                try {
                    DataSource sinkDataSource = new DataSource();
                    Class<?> configClass = Class.forName(target.getConfigurationClass());
                    sinkDataSource.setConf((Config) JsonUtils.parseObject(target.getConfiguration(), configClass));
                    detail.setSinkDataSource(sinkDataSource);
                } catch (Exception e) {
                    log.warn("parse sink config id [{}] fail", job.getSourceData(), e);
                    throw new AdminServerRuntimeException(ErrorCode.BAD_DB_DATA, "illegal target data sink config");
                }
            }
            detail.setSinkConnectorDesc(target.getDescription());
        }

        TaskState state = TaskState.fromIndex(job.getJobState());
        if (state == null) {
            throw new AdminServerRuntimeException(ErrorCode.BAD_DB_DATA, "illegal job state in db");
        }
        detail.setState(state);
        detail.setTransportType(TransportType.getTransportType(job.getTransportType()));
        return detail;
    }
}




