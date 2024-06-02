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

import static org.apache.eventmesh.common.Constants.ADMIN;

import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.runtime.configuration.EventMeshAdminConfiguration;

import lombok.Getter;

public class EventMeshAdminBootstrap implements EventMeshBootstrap {

    @Getter
    private EventMeshAdminServer eventMeshAdminServer;

    private final EventMeshAdminConfiguration eventMeshAdminConfiguration;

    private final EventMeshServer eventMeshServer;

    public EventMeshAdminBootstrap(EventMeshServer eventMeshServer) {
        this.eventMeshServer = eventMeshServer;

        ConfigService configService = ConfigService.getInstance();
        this.eventMeshAdminConfiguration = configService.buildConfigInstance(EventMeshAdminConfiguration.class);

        ConfigurationContextUtil.putIfAbsent(ADMIN, eventMeshAdminConfiguration);
    }

    @Override
    public void init() throws Exception {
        if (eventMeshServer != null) {
            eventMeshAdminServer = new EventMeshAdminServer(eventMeshServer, eventMeshAdminConfiguration);
            eventMeshAdminServer.init();
        }
    }

    @Override
    public void start() throws Exception {
        if (eventMeshAdminServer != null) {
            eventMeshAdminServer.start();
        }
    }

    @Override
    public void shutdown() throws Exception {
        if (eventMeshAdminServer != null) {
            eventMeshAdminServer.shutdown();
        }
    }
}
