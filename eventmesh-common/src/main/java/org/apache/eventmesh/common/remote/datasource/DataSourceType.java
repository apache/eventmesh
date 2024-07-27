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

public enum DataSourceType {
    MYSQL("MySQL", DataSourceDriverType.MYSQL, DataSourceClassify.RDB),
    REDIS("Redis", DataSourceDriverType.REDIS, DataSourceClassify.CACHE),
    ROCKETMQ("RocketMQ", DataSourceDriverType.ROCKETMQ, DataSourceClassify.MQ);
    private final String name;
    private final DataSourceDriverType driverType;
    private final DataSourceClassify classify;

    DataSourceType(String name, DataSourceDriverType driverType, DataSourceClassify classify) {
        this.name = name;
        this.driverType = driverType;
        this.classify = classify;
    }

    public String getName() {
        return name;
    }

    public DataSourceDriverType getDriverType() {
        return driverType;
    }

    public DataSourceClassify getClassify() {
        return classify;
    }

    private static final DataSourceType[] TYPES = DataSourceType.values();

    public static DataSourceType getDataSourceType(Integer index) {
        if (index == null || index < 0 || index >= TYPES.length) {
            return null;
        }
        return TYPES[index];
    }
}
