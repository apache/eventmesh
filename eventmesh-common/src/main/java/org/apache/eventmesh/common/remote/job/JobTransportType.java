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

package org.apache.eventmesh.common.remote.job;

import java.util.HashMap;
import java.util.Map;

public enum JobTransportType {
    MYSQL_MYSQL(DataSourceType.MYSQL, DataSourceType.MYSQL),
    REDIS_REDIS(DataSourceType.REDIS, DataSourceType.REDIS),
    ROCKETMQ_ROCKETMQ(DataSourceType.ROCKETMQ, DataSourceType.ROCKETMQ);
    private static final Map<String, JobTransportType> INDEX_TYPES = new HashMap<>();
    private static final JobTransportType[] TYPES = JobTransportType.values();
    private static final String SEPARATOR = "@";

    static {
        for (JobTransportType type : TYPES) {
            INDEX_TYPES.put(generateKey(type.src, type.dst), type);
        }
    }

    DataSourceType src;

    DataSourceType dst;

    JobTransportType(DataSourceType src, DataSourceType dst) {
        this.src = src;
        this.dst = dst;
    }

    private static String generateKey(DataSourceType src, DataSourceType dst) {
        return src.ordinal() + SEPARATOR + dst.ordinal();
    }

    public DataSourceType getSrc() {
        return src;
    }

    public DataSourceType getDst() {
        return dst;
    }

    public static JobTransportType getJobTransportType(DataSourceType src, DataSourceType dst) {
        return INDEX_TYPES.get(generateKey(src, dst));
    }

    public static JobTransportType getJobTransportType(Integer index) {
        if (index == null || index < 0 || index >= TYPES.length) {
            return null;
        }
        return TYPES[index];
    }
}
