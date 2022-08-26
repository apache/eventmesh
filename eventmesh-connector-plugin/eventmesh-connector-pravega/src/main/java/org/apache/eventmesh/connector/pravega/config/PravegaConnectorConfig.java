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

import org.apache.commons.lang3.StringUtils;

import java.net.URI;

import lombok.Getter;

@Getter
public class PravegaConnectorConfig {
    public static String EVENTMESH_PRAVEGA_CONTROLLER_URI = "eventMesh.server.pravega.controller.uri";
    public static String EVENTMESH_PRAVEGA_SCOPE = "eventMesh.server.pravega.scope";
    public static String EVENTMESH_PRAVEGA_CLIENTPOOL_SIZE = "eventMesh.server.pravega.clientPool.size";
    public static String EVENTMESH_PRAVEGA_QUEUE_SIZE = "eventMesh.server.pravega.queue.size";

    private URI controllerURI = URI.create("tcp://127.0.0.1:9090");
    private String scope = "eventmesh.pravega";
    private int clientPoolSize = 8;
    private int queueSize = 512;

    private static PravegaConnectorConfig INSTANCE = null;

    private PravegaConnectorConfig() {
    }

    public static synchronized PravegaConnectorConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new PravegaConnectorConfig();
            INSTANCE.init();
        }
        return INSTANCE;
    }

    private void init() {
        String controllerURIStr = PravegaConnectorConfigWrapper.getProp(EVENTMESH_PRAVEGA_CONTROLLER_URI);
        if (StringUtils.isNotBlank(controllerURIStr)) {
            controllerURI = URI.create(StringUtils.trim(controllerURIStr));
        }

        String scopeStr = PravegaConnectorConfigWrapper.getProp(EVENTMESH_PRAVEGA_SCOPE);
        if (StringUtils.isNotBlank(scopeStr)) {
            scope = StringUtils.trim(scopeStr);
        }

        String clientPoolSizeStr = PravegaConnectorConfigWrapper.getProp(EVENTMESH_PRAVEGA_CLIENTPOOL_SIZE);
        if (StringUtils.isNumeric(clientPoolSizeStr)) {
            clientPoolSize = Integer.parseInt(clientPoolSizeStr);
        }

        String queueSizeStr = PravegaConnectorConfigWrapper.getProp(EVENTMESH_PRAVEGA_QUEUE_SIZE);
        if (StringUtils.isNumeric(queueSizeStr)) {
            queueSize = Integer.parseInt(queueSizeStr);
        }
    }
}
