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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.eventmesh.connector.redis.config;

import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.common.config.ConfigFiled;

import java.util.Properties;

@Config(prefix = "eventMesh.server.redis", path = "classPath://redis-client.properties")
public class RedisProperties {

    /**
     * The redis server configuration to be used.
     */
    @ConfigFiled(field = "serverType")
    private ServerType serverType = ServerType.SINGLE;

    /**
     * The master server name used by Redis Sentinel servers and master change monitoring task.
     */
    @ConfigFiled(field = "serverMasterName")
    private String serverMasterName = "master";

    /**
     * The address of the redis server following format -- host1:port1,host2:port2,……
     */
    @ConfigFiled(field = "serverAddress")
    private String serverAddress;

    /**
     * The password for redis authentication.
     */
    @ConfigFiled(field = "serverPassword")
    private String serverPassword;

    /**
     * The redisson options, redisson properties
     * prefix is `eventMesh.server.redis.redisson`
     */
    @ConfigFiled(field = "redisson")
    private Properties redissonProperties;

    public ServerType getServerType() {
        return serverType;
    }

    public void setServerType(ServerType serverType) {
        this.serverType = serverType;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public String getServerPassword() {
        return serverPassword;
    }

    public void setServerPassword(String serverPassword) {
        this.serverPassword = serverPassword;
    }

    public String getServerMasterName() {
        return serverMasterName;
    }

    public void setServerMasterName(String serverMasterName) {
        this.serverMasterName = serverMasterName;
    }

    public Properties getRedissonProperties() {
        return redissonProperties;
    }

    public void setRedissonProperties(Properties redissonProperties) {
        this.redissonProperties = redissonProperties;
    }

    public enum ServerType {
        SINGLE,
        CLUSTER,
        SENTINEL
    }
}