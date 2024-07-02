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

package org.apache.eventmesh.admin.server.web.service.position;

import org.apache.eventmesh.admin.server.AdminServerRuntimeException;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.job.DataSourceType;
import org.apache.eventmesh.common.remote.offset.RecordPosition;
import org.apache.eventmesh.common.remote.request.FetchPositionRequest;
import org.apache.eventmesh.common.remote.request.ReportPositionRequest;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EventMeshPositionBizService {

    @Autowired
    PositionHandlerFactory factory;

    // called isValidateReportRequest before call this
    public List<RecordPosition> getPosition(FetchPositionRequest request, Metadata metadata) {
        if (request == null) {
            return null;
        }
        isValidatePositionRequest(request.getDataSourceType());
        IFetchPositionHandler handler = factory.getHandler(request.getDataSourceType());
        return handler.handler(request, metadata);
    }

    public void isValidatePositionRequest(DataSourceType type) {
        if (type == null) {
            throw new AdminServerRuntimeException(ErrorCode.BAD_REQUEST, "data source type is null");
        }
        IReportPositionHandler handler = factory.getHandler(type);
        if (handler == null) {
            throw new AdminServerRuntimeException(ErrorCode.BAD_REQUEST,
                String.format("illegal data base type [%s], it not match any report position handler", type));
        }
    }

    // called isValidateReportRequest before call this
    public boolean reportPosition(ReportPositionRequest request, Metadata metadata) {
        if (request == null) {
            return false;
        }
        isValidatePositionRequest(request.getDataSourceType());
        IReportPositionHandler handler = factory.getHandler(request.getDataSourceType());
        return handler.handler(request, metadata);
    }

    public List<RecordPosition> getPositionByJobID(Integer jobID, DataSourceType type) {
        if (jobID == null || type == null) {
            return null;
        }
        isValidatePositionRequest(type);
        PositionHandler handler = factory.getHandler(type);
        FetchPositionRequest request = new FetchPositionRequest();
        request.setJobID(String.valueOf(jobID));
        return handler.handler(request, null);
    }
}
