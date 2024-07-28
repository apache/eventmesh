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

import lombok.Data;
import org.apache.eventmesh.common.remote.offset.RecordPosition;
import org.apache.eventmesh.common.remote.task.TransportType;

import java.util.Date;
import java.util.List;

@Data
public class JobDetail {
    private Integer id;
    private String jobID;

    private String taskID;

    private String state;

    private String jobType;

    private Date createTime;

    private Date updateTime;

    private String sourceConfig;

    private String sourceDesc;

    private String targetConfig;

    private String targetDesc;

    private TransportType transportType;

    private List<RecordPosition> positions;
}
