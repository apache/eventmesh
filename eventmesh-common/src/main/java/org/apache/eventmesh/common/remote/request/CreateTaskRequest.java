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

package org.apache.eventmesh.common.remote.request;

import org.apache.eventmesh.common.remote.TransportType;
import org.apache.eventmesh.common.remote.job.JobType;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * Description: create task without task id, otherwise update task
 */
@Data
public class CreateTaskRequest {

    private String taskId;

    // task name
    private String taskName;

    // task description
    private String taskDesc;

    // task owner or updater
    private String uid;

    private List<JobDetail> jobs;

    // task source region
    private String sourceRegion;

    // task target region
    private String targetRegion;

    // mark request send by other region admin, default is false
    private boolean flag = false;

    @Data
    public static class JobDetail {

        private String jobId;

        private String jobDesc;

        // full/increase/check
        private JobType jobType;

        private Map<String, Object> sourceDataSource;

        private String sourceConnectorDesc;

        private Map<String, Object> sinkDataSource;

        private String sinkConnectorDesc;

        private TransportType transportType;

        // job request from region
        private String fromRegion;

        // job actually running region
        private String runningRegion;
    }
}
