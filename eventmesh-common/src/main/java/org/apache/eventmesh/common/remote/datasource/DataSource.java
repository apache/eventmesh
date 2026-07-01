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

package org.apache.eventmesh.common.remote.datasource;

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSinkConfig;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSourceConfig;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import lombok.Data;

@Data
public class DataSource {

    private DataSourceType type;

    private String desc;
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = CanalSourceConfig.class, name = "CanalSourceConfig"),
        @JsonSubTypes.Type(value = CanalSinkConfig.class, name = "CanalSinkConfig")
    })
    private Config conf;

    private Class<? extends Config> confClazz;

    private String region;

}
