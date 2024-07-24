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

import org.apache.eventmesh.admin.server.AdminServerRuntimeException;
import org.apache.eventmesh.admin.server.web.db.entity.EventMeshDataSource;
import org.apache.eventmesh.admin.server.web.db.entity.EventMeshJobDetail;
import org.apache.eventmesh.admin.server.web.db.entity.EventMeshJobInfo;
import org.apache.eventmesh.admin.server.web.db.service.EventMeshDataSourceService;
import org.apache.eventmesh.admin.server.web.db.service.EventMeshJobInfoService;
import org.apache.eventmesh.admin.server.web.service.position.EventMeshPositionBizService;
import org.apache.eventmesh.common.remote.JobState;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.job.DataSourceType;
import org.apache.eventmesh.common.remote.job.JobTransportType;
import org.apache.eventmesh.common.utils.JsonUtils;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;

import lombok.extern.slf4j.Slf4j;

/**
 * for table 'event_mesh_job_info' db operation
 * 2024-05-09 15:51:45
 */
@Service
@Slf4j
public class EventMeshJobInfoBizService {

    @Autowired
    EventMeshJobInfoService jobInfoService;

    @Autowired
    EventMeshDataSourceService dataSourceService;

    @Autowired
    EventMeshPositionBizService positionBizService;

    public boolean updateJobState(Integer jobID, JobState state) {
        if (jobID == null || state == null) {
            return false;
        }
        EventMeshJobInfo jobInfo = new EventMeshJobInfo();
        jobInfo.setJobID(jobID);
        jobInfo.setState(state.ordinal());
        jobInfoService.update(jobInfo, Wrappers.<EventMeshJobInfo>update().notIn("state", JobState.DELETE.ordinal(),
            JobState.COMPLETE.ordinal()));
        return true;
    }

    public EventMeshJobDetail getJobDetail(Integer jobID) {
        if (jobID == null) {
            return null;
        }
        EventMeshJobInfo job = jobInfoService.getById(jobID);
        if (job == null) {
            return null;
        }
        EventMeshJobDetail detail = new EventMeshJobDetail();
        detail.setId(job.getJobID());
        detail.setName(job.getName());
        EventMeshDataSource source = dataSourceService.getById(job.getSourceData());
        EventMeshDataSource target = dataSourceService.getById(job.getTargetData());
        if (source != null) {
            if (!StringUtils.isBlank(source.getConfiguration())) {
                try {
                    detail.setSourceConnectorConfig(JsonUtils.parseTypeReferenceObject(source.getConfiguration(),
                        new TypeReference<Map<String, Object>>() {
                        }));
                } catch (Exception e) {
                    log.warn("parse source config id [{}] fail", job.getSourceData(), e);
                    throw new AdminServerRuntimeException(ErrorCode.BAD_DB_DATA, "illegal source data source config");
                }
            }
            detail.setSourceConnectorDesc(source.getDescription());
            if (source.getDataType() != null) {
                detail.setPosition(positionBizService.getPositionByJobID(job.getJobID(),
                    DataSourceType.getDataSourceType(source.getDataType())));

            }
        }
        if (target != null) {
            if (!StringUtils.isBlank(target.getConfiguration())) {
                try {
                    detail.setSinkConnectorConfig(JsonUtils.parseTypeReferenceObject(target.getConfiguration(),
                        new TypeReference<Map<String, Object>>() {
                        }));
                } catch (Exception e) {
                    log.warn("parse sink config id [{}] fail", job.getSourceData(), e);
                    throw new AdminServerRuntimeException(ErrorCode.BAD_DB_DATA, "illegal target data sink config");
                }
            }
            detail.setSinkConnectorDesc(target.getDescription());
        }

        JobState state = JobState.fromIndex(job.getState());
        if (state == null) {
            throw new AdminServerRuntimeException(ErrorCode.BAD_DB_DATA, "illegal job state in db");
        }
        detail.setState(state);
        detail.setTransportType(JobTransportType.getJobTransportType(job.getTransportType()));
        return detail;
    }
}




