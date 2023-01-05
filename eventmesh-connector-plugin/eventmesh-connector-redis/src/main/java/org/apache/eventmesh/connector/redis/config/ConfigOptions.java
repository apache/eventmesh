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

/**
 * Redis connector related configuration options.
 */
public interface ConfigOptions {

    /**
     * The redis server configuration to be used, default is SINGLE.
     */
    String SERVER_TYPE = "eventMesh.server.redis.serverType";

    /**
     * The master server name used by Redis Sentinel servers and master change monitoring task, default is master.
     */
    String SERVER_MASTER_NAME = "eventMesh.server.redis.serverMasterName";

    /**
     * The address of the redis server following format -- host1:port1,host2:port2,……
     */
    String SERVER_ADDRESS = "eventMesh.server.redis.serverAddress";

    /**
     * The password for redis authentication.
     */
    String SERVER_PASSWORD = "eventMesh.server.redis.serverPassword";

    /**
     * The redisson options, redisson properties, please refer to the redisson manual.
     * <p>
     * For example, the redisson timeout property is configured as eventMesh.server.redis.redisson.timeout
     */
    String REDISSON_PROPERTIES_PREFIX = "eventMesh.server.redis.redisson";
}
