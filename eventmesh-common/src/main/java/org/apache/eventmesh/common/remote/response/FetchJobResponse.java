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

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.job.JobConnectorConfig;
import org.apache.eventmesh.common.remote.job.JobState;
import org.apache.eventmesh.common.remote.offset.RecordPosition;
import org.apache.eventmesh.common.remote.task.TransportType;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class FetchJobResponse extends BaseRemoteResponse {

    private Integer id;

    private String name;

    private TransportType transportType;

    private JobConnectorConfig connectorConfig;

    private List<RecordPosition> position;

    private JobState state;

    public static FetchJobResponse successResponse() {
        FetchJobResponse response = new FetchJobResponse();
        response.setSuccess(true);
        response.setErrorCode(ErrorCode.SUCCESS);
        return response;
    }

    public static FetchJobResponse failResponse(int code, String desc) {
        FetchJobResponse response = new FetchJobResponse();
        response.setSuccess(false);
        response.setErrorCode(code);
        response.setDesc(desc);
        return response;
    }

}
