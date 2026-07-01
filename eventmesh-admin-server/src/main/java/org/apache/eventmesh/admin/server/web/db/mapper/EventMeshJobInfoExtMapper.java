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

package org.apache.eventmesh.admin.server.web.db.mapper;

import org.apache.eventmesh.admin.server.web.db.entity.EventMeshJobInfo;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * etx operator for table event_mesh_job_info
 */
@Mapper
public interface EventMeshJobInfoExtMapper extends BaseMapper<EventMeshJobInfo> {

    @Insert("<script>"
        + "insert into event_mesh_job_info(jobID, jobDesc, taskID, transportType, sourceData, "
        + "targetData, jobState, jobType, fromRegion, runningRegion, "
        + "createUid, updateUid) values"
        + "<foreach collection= 'jobs' item='job' separator=','>"
        + "(#{job.jobID}, #{job.jobDesc}, #{job.taskID}, #{job.transportType}, "
        + "#{job.sourceData}, #{job.targetData}, #{job.jobState}, #{job.jobType}, "
        + "#{job.fromRegion}, #{job.runningRegion}, #{job.createUid}, #{job.updateUid})"
        + "</foreach>"
        + "</script>")
    @Transactional(rollbackFor = Exception.class)
    int saveBatch(@Param("jobs") List<EventMeshJobInfo> jobInfoList);
}




