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

package org.apache.eventmesh.common.remote.response;

import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QueryTaskInfoResponse {

    // event_mesh_task_info
    private Integer id;

    private String taskID;

    private String taskName;

    private String taskDesc;

    private String taskState;

    private String sourceRegion;

    private String targetRegion;

    private String createUid;

    private String updateUid;

    private Date createTime;

    private Date updateTime;

    List<EventMeshJobInfo> eventMeshJobInfoList;

    @Data
    public static class EventMeshJobInfo {
        // event_mesh_job_info
        private Integer id;

        private String jobID;

        private String jobDesc;

        private String taskID;

        private String transportType;

        private Integer sourceData;

        private Integer targetData;

        private String jobState;

        private String jobType;

        // job request from region
        private String fromRegion;

        // job actually running region
        private String runningRegion;

        private String createUid;

        private String updateUid;

        private Date createTime;

        private Date updateTime;

        // private List<EventMeshDataSource> eventMeshDataSource;

        private EventMeshDataSource dataSource;

        private EventMeshDataSource dataSink;

        private EventMeshMysqlPosition eventMeshMysqlPosition;

    }

    @Data
    public static class EventMeshDataSource {

        private Integer id;

        private String dataType;

        private String description;

        private String configuration;

        private String configurationClass;

        private String region;

        private String createUid;

        private String updateUid;

        private Date createTime;

        private Date updateTime;
    }

    @Data
    public static class EventMeshMysqlPosition {

        private Integer id;

        private String jobID;

        private String serverUUID;

        private String address;

        private Long position;

        private String gtid;

        private String currentGtid;

        private Long timestamp;

        private String journalName;

        private Date createTime;

        private Date updateTime;
    }

}