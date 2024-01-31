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

import org.apache.eventmesh.runtime.admin.controller.HttpHandlerManager;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;

public class EventMeshAdminBootstrap implements EventMeshBootstrap {

    private EventMeshAdminServer eventMeshAdminServer;

    private int port;

    private boolean useTLS;

    private EventMeshHTTPConfiguration eventMeshHttpConfiguration;

    private HttpHandlerManager httpHandlerManager;

    public EventMeshAdminBootstrap(int port, boolean useTLS,
        EventMeshHTTPConfiguration eventMeshHttpConfiguration, HttpHandlerManager httpHandlerManager) {
        this.port = port;
        this.useTLS = useTLS;
        this.eventMeshHttpConfiguration = eventMeshHttpConfiguration;
        this.httpHandlerManager = httpHandlerManager;

    }

    @Override
    public void init() throws Exception {
        if (eventMeshHttpConfiguration != null && httpHandlerManager != null) {
            eventMeshAdminServer = new EventMeshAdminServer(port, useTLS, eventMeshHttpConfiguration, httpHandlerManager);
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
