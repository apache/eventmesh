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

package org.apache.eventmesh.connector.redis.config;

import org.apache.commons.lang3.StringUtils;

import java.time.Duration;

public class ClientConfiguration {

    public String host;

    public int port;

    public String username;

    public String password;

    public int index;

    public String masterId;

    public final long DEFAULT_TIMEOUT = 60;

    public Duration timeout = Duration.ofSeconds(DEFAULT_TIMEOUT);

    public final int retryTime = 5;

    public void init() {

        String host = ConfigurationWrapper.getProp(ConfKeys.EVENTMESH_SERVER_REDIS_HOST);
        if (StringUtils.isNotBlank(host)) {
            this.host = StringUtils.trim(host);
        }

        String port = ConfigurationWrapper.getProp(ConfKeys.EVENTMESH_SERVER_REDIS_PORT);
        if (StringUtils.isNotBlank(port)) {
            this.port = Integer.parseInt(StringUtils.trim(port));
        }

        String username = ConfigurationWrapper.getProp(ConfKeys.EVENTMESH_SERVER_REDIS_USERNAME);
        if (StringUtils.isNotBlank(username)) {
            this.username = StringUtils.trim(username);
        }

        String password = ConfigurationWrapper.getProp(ConfKeys.EVENTMESH_SERVER_REDIS_PASSWORD);
        if (StringUtils.isNotBlank(password)) {
            this.password = StringUtils.trim(password);
        }

        String index = ConfigurationWrapper.getProp(ConfKeys.EVENTMESH_SERVER_REDIS_INDEX);
        if (StringUtils.isNotBlank(index)) {
            this.index = Integer.parseInt(StringUtils.trim(index));
        }

        String masterId = ConfigurationWrapper.getProp(ConfKeys.EVENTMESH_SERVER_REDIS_MASTERID);
        if (StringUtils.isNotBlank(masterId)) {
            this.masterId = StringUtils.trim(masterId);
        }
    }

    static class ConfKeys {

        public static String EVENTMESH_SERVER_REDIS_HOST = "eventMesh.server.redis.host";

        public static String EVENTMESH_SERVER_REDIS_PORT = "eventMesh.server.redis.port";

        public static String EVENTMESH_SERVER_REDIS_USERNAME = "eventMesh.server.redis.username";

        public static String EVENTMESH_SERVER_REDIS_PASSWORD = "eventMesh.server.redis.password";

        public static String EVENTMESH_SERVER_REDIS_INDEX = "eventMesh.server.redis.index";

        public static String EVENTMESH_SERVER_REDIS_MASTERID = "eventMesh.server.redis.masterId";

    }
}