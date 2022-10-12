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

import org.apache.eventmesh.common.config.ConfigurationWrapper;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import org.apache.eventmesh.runtime.registry.Registry;

public class EventMeshTcpBootstrap implements EventMeshBootstrap {

    private EventMeshTCPServer eventMeshTCPServer;

    private EventMeshTCPConfiguration eventMeshTcpConfiguration;

    private final EventMeshServer eventMeshServer;

    private final ConfigurationWrapper configurationWrapper;

    private final Registry registry;

    public EventMeshTcpBootstrap(EventMeshServer eventMeshServer,
                                 ConfigurationWrapper configurationWrapper,
                                 Registry registry) {
        this.eventMeshServer = eventMeshServer;
        this.configurationWrapper = configurationWrapper;
        this.registry = registry;
    }

    @Override
    public void init() throws Exception {
        this.eventMeshTcpConfiguration = new EventMeshTCPConfiguration(configurationWrapper);
        eventMeshTcpConfiguration.init();
        ConfigurationContextUtil.putIfAbsent(ConfigurationContextUtil.TCP, eventMeshTcpConfiguration);

        // server init
        if (eventMeshTcpConfiguration != null) {
            eventMeshTCPServer = new EventMeshTCPServer(eventMeshServer, eventMeshTcpConfiguration, registry);
            if (eventMeshTcpConfiguration.eventMeshTcpServerEnabled) {
                eventMeshTCPServer.init();
            }
        }
    }

    @Override
    public void start() throws Exception {
        // server start
        if (eventMeshTcpConfiguration != null && eventMeshTcpConfiguration.eventMeshTcpServerEnabled) {
            eventMeshTCPServer.start();
        }
    }

    @Override
    public void shutdown() throws Exception {
        if (eventMeshTcpConfiguration != null
                && eventMeshTcpConfiguration.eventMeshTcpServerEnabled) {
            eventMeshTCPServer.shutdown();
        }
    }
}
