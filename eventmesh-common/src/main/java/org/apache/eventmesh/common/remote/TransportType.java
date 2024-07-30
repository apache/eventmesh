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

package org.apache.eventmesh.common.remote;

import org.apache.eventmesh.common.remote.datasource.DataSourceType;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;

@Getter
public enum TransportType {
    MYSQL_MYSQL(DataSourceType.MYSQL, DataSourceType.MYSQL),
    REDIS_REDIS(DataSourceType.REDIS, DataSourceType.REDIS),
    ROCKETMQ_ROCKETMQ(DataSourceType.ROCKETMQ, DataSourceType.ROCKETMQ),
    MYSQL_HTTP(DataSourceType.MYSQL, DataSourceType.HTTP),
    HTTP_MYSQL(DataSourceType.HTTP, DataSourceType.MYSQL),
    REDIS_MQ(DataSourceType.REDIS, DataSourceType.ROCKETMQ);
    private static final Map<String, TransportType> INDEX_TYPES = new HashMap<>();
    private static final TransportType[] TYPES = TransportType.values();
    private static final String SEPARATOR = "@";

    static {
        for (TransportType type : TYPES) {
            INDEX_TYPES.put(type.name(), type);
        }
    }

    private final DataSourceType src;

    private final DataSourceType dst;

    TransportType(DataSourceType src, DataSourceType dst) {
        this.src = src;
        this.dst = dst;
    }


    public static TransportType getTransportType(String index) {
        if (index == null || index.isEmpty()) {
            return null;
        }
        return INDEX_TYPES.get(index);
    }

    public static TransportType getTransportType(Integer index) {
        if (index == null || index < 0 || index >= TYPES.length) {
            return null;
        }
        return TYPES[index];
    }
}
