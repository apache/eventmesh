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

package org.apache.eventmesh.admin.server.web.service.datasource;

import org.apache.eventmesh.admin.server.AdminServerRuntimeException;
import org.apache.eventmesh.admin.server.web.db.entity.EventMeshDataSource;
import org.apache.eventmesh.admin.server.web.db.service.EventMeshDataSourceService;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.request.CreateOrUpdateDataSourceReq;
import org.apache.eventmesh.common.utils.JsonUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DataSourceBizService {
    @Autowired
    private EventMeshDataSourceService dataSourceService;

    public EventMeshDataSource createDataSource(CreateOrUpdateDataSourceReq dataSource) {
        EventMeshDataSource entity = new EventMeshDataSource();
        entity.setConfiguration(JsonUtils.toJSONString(dataSource.getConfig()));
        entity.setDataType(dataSource.getType().name());
        entity.setCreateUid(dataSource.getOperator());
        entity.setUpdateUid(dataSource.getOperator());
        entity.setRegion(dataSource.getRegion());
        entity.setDescription(dataSource.getDesc());
        if (dataSourceService.save(entity)) {
            return entity;
        }
        throw new AdminServerRuntimeException(ErrorCode.BAD_DB_DATA, "save data source fail");
    }
}
