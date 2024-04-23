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

package org.apache.eventmesh.storage.redis.config;

import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.common.config.ConfigField;

import java.util.Properties;

import lombok.Data;

@Data
@Config(prefix = "eventMesh.server.redis", path = "classPath://redis-client.properties")
public class RedisProperties {

    /**
     * The redis server configuration to be used.
     */
    @ConfigField(field = "serverType")
    private ServerType serverType = ServerType.SINGLE;

    /**
     * The master server name used by Redis Sentinel servers and master change monitoring task.
     */
    @ConfigField(field = "serverMasterName")
    private String serverMasterName = "master";

    /**
     * The address of the redis server following format -- host1:port1,host2:port2,……
     */
    @ConfigField(field = "serverAddress")
    private String serverAddress;

    /**
     * The password for redis authentication.
     */
    @ConfigField(field = "serverPassword")
    private String serverPassword;

    /**
     * The redisson options, redisson properties prefix is `eventMesh.server.redis.redisson`
     */
    @ConfigField(field = "redisson")
    private Properties redissonProperties;

    public enum ServerType {
        SINGLE,
        CLUSTER,
        SENTINEL
    }
}
