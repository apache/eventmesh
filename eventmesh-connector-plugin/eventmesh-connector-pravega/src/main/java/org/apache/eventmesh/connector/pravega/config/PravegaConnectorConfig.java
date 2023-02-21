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

package org.apache.eventmesh.connector.pravega.config;

import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.common.config.ConfigFiled;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Config(prefix = "eventMesh.server.pravega", path = "classPath://pravega-connector.properties")
public class PravegaConnectorConfig {

    @ConfigFiled(field = "controller.uri")
    private URI controllerURI = URI.create("tcp://127.0.0.1:9090");

    @ConfigFiled(field = "scope")
    private String scope = "eventmesh-pravega";

    @ConfigFiled(field = "clientPool.size")
    private int clientPoolSize = 8;

    @ConfigFiled(field = "queue.size")
    private int queueSize = 512;

    @ConfigFiled(field = "authEnabled", reload = true)
    private boolean authEnabled = false;

    @ConfigFiled(field = "username")
    private String username = "";

    @ConfigFiled(field = "password")
    private String password = "";

    @ConfigFiled(field = "tlsEnabled")
    private boolean tlsEnable = false;

    @ConfigFiled(field = "truststore")
    private String truststore = "";

    public void reload() {
        if (!authEnabled && StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            authEnabled = true;
        }
    }
}
