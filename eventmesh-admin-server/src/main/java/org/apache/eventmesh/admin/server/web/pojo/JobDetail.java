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

package org.apache.eventmesh.admin.server.web.pojo;

import org.apache.eventmesh.common.remote.TaskState;
import org.apache.eventmesh.common.remote.TransportType;
import org.apache.eventmesh.common.remote.datasource.DataSource;
import org.apache.eventmesh.common.remote.job.JobType;
import org.apache.eventmesh.common.remote.offset.RecordPosition;

import java.util.Date;
import java.util.List;

import lombok.Data;

@Data
public class JobDetail {
    private Integer id;

    private String jobID;

    private String jobDesc;

    private String taskID;

    private TaskState state;

    private JobType jobType;

    private Date createTime;

    private Date updateTime;

    private String createUid;

    private String updateUid;

    // job request from region
    private String fromRegion;

    // job actually running region
    private String runningRegion;

    private DataSource sourceDataSource;

    private String sourceConnectorDesc;

    private DataSource sinkDataSource;

    private String sinkConnectorDesc;

    private TransportType transportType;

    private List<RecordPosition> positions;
}
