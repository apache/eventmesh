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

import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.runtime.lifecircle.EventMeshSwitchableComponent;

import java.util.List;

public class EventMeshAdminBootstrap extends EventMeshSwitchableComponent implements EventMeshBootstrap {

    private EventMeshAdminServer eventMeshAdminServer;

    private EventMeshServer eventMeshServer;

    // http grpc tcp
    private final int exposeServerChannelCount = 3;

    public EventMeshAdminBootstrap(EventMeshServer eventMeshServer) {
        this.eventMeshServer = eventMeshServer;
    }

    @Override
    protected boolean shouldTurn(CommonConfiguration configuration) {
        final List<String> provideServerProtocols = configuration.getEventMeshProvideServerProtocols();
        return !(provideServerProtocols.size() == exposeServerChannelCount);
    }

    @Override
    protected void componentInit() throws Exception {
        if (eventMeshServer != null) {
            eventMeshAdminServer = new EventMeshAdminServer(eventMeshServer);
            eventMeshAdminServer.init();
        }

    }

    @Override
    protected void componentStart() throws Exception {
        if (eventMeshAdminServer != null) {
            eventMeshAdminServer.start();
        }

    }

    @Override
    protected void componentStop() throws Exception {
        if (eventMeshAdminServer != null) {
            eventMeshAdminServer.shutdown();
        }
    }
}
