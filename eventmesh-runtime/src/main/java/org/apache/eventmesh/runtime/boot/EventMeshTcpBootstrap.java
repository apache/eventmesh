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

package org.apache.eventmesh.runtime.boot;

import static org.apache.eventmesh.common.Constants.TCP;

import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;

public class EventMeshTcpBootstrap implements EventMeshBootstrap {

    private EventMeshTCPServer eventMeshTcpServer;

    private final EventMeshTCPConfiguration eventMeshTcpConfiguration;

    private final EventMeshServer eventMeshServer;

    public EventMeshTcpBootstrap(EventMeshServer eventMeshServer) {
        this.eventMeshServer = eventMeshServer;

        ConfigService configService = ConfigService.getInstance();
        this.eventMeshTcpConfiguration = configService.buildConfigInstance(EventMeshTCPConfiguration.class);

        ConfigurationContextUtil.putIfAbsent(TCP, eventMeshTcpConfiguration);
    }

    @Override
    public void init() throws Exception {
        // server init
        if (eventMeshTcpConfiguration != null) {
            eventMeshTcpServer = new EventMeshTCPServer(eventMeshServer, eventMeshTcpConfiguration);
            eventMeshTcpServer.init();
        }
    }

    @Override
    public void start() throws Exception {
        // server start
        if (eventMeshTcpConfiguration != null) {
            eventMeshTcpServer.start();
        }
    }

    @Override
    public void shutdown() throws Exception {
        if (eventMeshTcpConfiguration != null) {
            eventMeshTcpServer.shutdown();
        }
    }

    public EventMeshTCPServer getEventMeshTcpServer() {
        return eventMeshTcpServer;
    }

    public void setEventMeshTcpServer(EventMeshTCPServer eventMeshTcpServer) {
        this.eventMeshTcpServer = eventMeshTcpServer;
    }

}
