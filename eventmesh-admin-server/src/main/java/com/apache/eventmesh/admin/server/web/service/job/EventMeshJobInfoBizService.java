package com.apache.eventmesh.admin.server.web.service.job;

import com.apache.eventmesh.admin.server.AdminServerRuntimeException;
import com.apache.eventmesh.admin.server.web.db.entity.EventMeshDataSource;
import com.apache.eventmesh.admin.server.web.db.entity.EventMeshJobDetail;
import com.apache.eventmesh.admin.server.web.db.entity.EventMeshJobInfo;
import com.apache.eventmesh.admin.server.web.db.service.EventMeshDataSourceService;
import com.apache.eventmesh.admin.server.web.db.service.EventMeshJobInfoService;
import com.apache.eventmesh.admin.server.web.service.position.EventMeshPositionBizService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.remote.JobState;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.job.DataSourceType;
import org.apache.eventmesh.common.remote.job.JobTransportType;
import org.apache.eventmesh.common.remote.request.FetchJobRequest;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
* @author sodafang
* @description 针对表【event_mesh_job_info】的数据库操作Service实现
* @createDate 2024-05-09 15:51:45
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
        jobInfoService.update(jobInfo, Wrappers.<EventMeshJobInfo>update().notIn("state",JobState.DELETE.ordinal(),
                JobState.COMPLETE.ordinal()));
        return true;
    }

    public EventMeshJobDetail getJobDetail(FetchJobRequest request, Metadata metadata) {
        if (request == null) {
            return null;
        }
        EventMeshJobInfo job = jobInfoService.getById(request.getJobID());
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
                            new TypeReference<Map<String, Object>>() {}));
                } catch (Exception e) {
                    log.warn("parse source config id [{}] fail", job.getSourceData(), e);
                    throw new AdminServerRuntimeException(ErrorCode.BAD_DB_DATA,"illegal source data source config");
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
                            new TypeReference<Map<String, Object>>() {}));
                } catch (Exception e) {
                    log.warn("parse sink config id [{}] fail", job.getSourceData(), e);
                    throw new AdminServerRuntimeException(ErrorCode.BAD_DB_DATA,"illegal target data sink config");
                }
            }
            detail.setSinkConnectorDesc(target.getDescription());
        }

        JobState state = JobState.fromIndex(job.getState());
        if (state == null) {
            throw new AdminServerRuntimeException(ErrorCode.BAD_DB_DATA,"illegal job state in db");
        }
        detail.setState(state);
        detail.setTransportType(JobTransportType.getJobTransportType(job.getTransportType()));
        return detail;
    }
}




