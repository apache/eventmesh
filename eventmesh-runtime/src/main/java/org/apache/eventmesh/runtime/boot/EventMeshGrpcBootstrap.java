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
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.registry.Registry;

public class EventMeshGrpcBootstrap implements EventMeshBootstrap {

    private final EventMeshGrpcConfiguration eventMeshGrpcConfiguration;

    private EventMeshGrpcServer eventMeshGrpcServer;

    private final Registry registry;

    public EventMeshGrpcBootstrap(ConfigurationWrapper configurationWrapper, Registry registry) {
        this.registry = registry;
        this.eventMeshGrpcConfiguration = new EventMeshGrpcConfiguration(configurationWrapper);
        eventMeshGrpcConfiguration.init();
        ConfigurationContextUtil.putIfAbsent(ConfigurationContextUtil.GRPC, eventMeshGrpcConfiguration);
    }

    @Override
    public void init() throws Exception {
        // registry init
        if (eventMeshGrpcConfiguration != null && eventMeshGrpcConfiguration.eventMeshServerRegistryEnable) {
            registry.init(eventMeshGrpcConfiguration.eventMeshRegistryPluginType);
        }

        // server init
        if (eventMeshGrpcConfiguration != null) {
            eventMeshGrpcServer = new EventMeshGrpcServer(eventMeshGrpcConfiguration, registry);
            eventMeshGrpcServer.init();
        }
    }

    @Override
    public void start() throws Exception {
        // registry start
        if (eventMeshGrpcConfiguration != null && eventMeshGrpcConfiguration.eventMeshServerRegistryEnable) {
            registry.start();
        }

        // server start
        if (eventMeshGrpcConfiguration != null) {
            eventMeshGrpcServer.start();
        }
    }

    @Override
    public void shutdown() throws Exception {
        if (eventMeshGrpcConfiguration != null) {
            eventMeshGrpcServer.shutdown();
        }

        if (eventMeshGrpcConfiguration != null
                && eventMeshGrpcConfiguration.eventMeshServerRegistryEnable) {
            registry.shutdown();
        }
    }
}
