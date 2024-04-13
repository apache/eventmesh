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

import static org.apache.eventmesh.common.Constants.GRPC;

import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.lifecircle.EventMeshComponent;

public class EventMeshGrpcBootstrap extends EventMeshComponent implements EventMeshBootstrap {

    private final EventMeshGrpcConfiguration eventMeshGrpcConfiguration;

    private EventMeshGrpcServer eventMeshGrpcServer;

    private final EventMeshServer eventMeshServer;

    public EventMeshGrpcBootstrap(final EventMeshServer eventMeshServer) {
        this.eventMeshServer = eventMeshServer;
        ConfigService configService = ConfigService.getInstance();
        this.eventMeshGrpcConfiguration = configService.buildConfigInstance(EventMeshGrpcConfiguration.class);

        ConfigurationContextUtil.putIfAbsent(GRPC, eventMeshGrpcConfiguration);
    }

    @Override
    public void componentInit() throws Exception {
        // server init
        if (eventMeshGrpcConfiguration != null) {
            eventMeshGrpcServer = new EventMeshGrpcServer(this.eventMeshServer, this.eventMeshGrpcConfiguration);
            eventMeshGrpcServer.init();
        }
    }

    @Override
    protected void componentStart() throws Exception {
        // server start
        if (eventMeshGrpcConfiguration != null) {
            eventMeshGrpcServer.start();
        }
    }

    @Override
    protected void componentStop() throws Exception {
        if (eventMeshGrpcConfiguration != null) {
            eventMeshGrpcServer.shutdown();
        }
    }

    protected EventMeshGrpcServer getEventMeshGrpcServer() {
        return eventMeshGrpcServer;
    }

    public void setEventMeshGrpcServer(EventMeshGrpcServer eventMeshGrpcServer) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
    }
}
