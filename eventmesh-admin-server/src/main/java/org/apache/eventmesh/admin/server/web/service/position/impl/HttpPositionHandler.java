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

package org.apache.eventmesh.admin.server.web.service.position.impl;

import org.apache.eventmesh.admin.server.web.db.service.EventMeshPositionReporterHistoryService;
import org.apache.eventmesh.admin.server.web.service.position.PositionHandler;
import org.apache.eventmesh.common.protocol.grpc.adminserver.Metadata;
import org.apache.eventmesh.common.remote.datasource.DataSourceType;
import org.apache.eventmesh.common.remote.offset.RecordPosition;
import org.apache.eventmesh.common.remote.request.FetchPositionRequest;
import org.apache.eventmesh.common.remote.request.ReportPositionRequest;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class HttpPositionHandler extends PositionHandler {

    @Autowired
    EventMeshPositionReporterHistoryService historyService;

    @Override
    protected DataSourceType getSourceType() {
        return DataSourceType.HTTP;
    }

    @Override
    public boolean handler(ReportPositionRequest request, Metadata metadata) {
        log.info("receive http position report request:{}", request);
        // mock wemq postion report store
        return true;
    }

    @Override
    public List<RecordPosition> handler(FetchPositionRequest request, Metadata metadata) {
        // mock http position fetch request
        List<RecordPosition> recordPositionList = new ArrayList<>();
        return recordPositionList;
    }
}
