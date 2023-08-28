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

import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;

public class EventMeshHttpBootstrap implements EventMeshBootstrap {

    private final EventMeshHTTPConfiguration eventMeshHttpConfiguration;

    public EventMeshHTTPServer eventMeshHttpServer;

    private final EventMeshServer eventMeshServer;

    public EventMeshHttpBootstrap(final EventMeshServer eventMeshServer) {
        this.eventMeshServer = eventMeshServer;

        ConfigService configService = ConfigService.getInstance();
        this.eventMeshHttpConfiguration = configService.buildConfigInstance(EventMeshHTTPConfiguration.class);

        ConfigurationContextUtil.putIfAbsent(ConfigurationContextUtil.HTTP, eventMeshHttpConfiguration);
    }

    @Override
    public void init() throws Exception {
        // server init
        if (eventMeshHttpConfiguration != null) {
            eventMeshHttpServer = new EventMeshHTTPServer(eventMeshServer, eventMeshHttpConfiguration);
            eventMeshHttpServer.init();
        }
    }

    @Override
    public void start() throws Exception {
        // server start
        if (eventMeshHttpServer != null) {
            eventMeshHttpServer.start();
        }
    }

    @Override
    public void shutdown() throws Exception {
        //server shutdown
        if (eventMeshHttpServer != null) {
            eventMeshHttpServer.shutdown();
        }
    }

    public EventMeshHTTPServer getEventMeshHttpServer() {
        return eventMeshHttpServer;
    }

    public void setEventMeshHttpServer(EventMeshHTTPServer eventMeshHttpServer) {
        this.eventMeshHttpServer = eventMeshHttpServer;
    }
}
