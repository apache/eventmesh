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
import org.apache.eventmesh.protocol.api.EventMeshProtocolPluginFactory;
import org.apache.eventmesh.protocol.api.EventMeshProtocolServer;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.connector.ConnectorResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class EventMeshServer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private final List<EventMeshProtocolServer> eventMeshProtocolServers;

    private ServiceState serviceState;

    private ConnectorResource connectorResource;

    public EventMeshServer() {
        this.eventMeshProtocolServers = EventMeshProtocolPluginFactory.getEventMeshProtocolServers(
                CommonConfiguration.eventMeshProtocolServerPluginTypes);

        this.connectorResource = new ConnectorResource();
    }

    public void init() throws Exception {
        for (EventMeshProtocolServer eventMeshProtocolServer : eventMeshProtocolServers) {
            eventMeshProtocolServer.init();
        }
        connectorResource.init(CommonConfiguration.eventMeshConnectorPluginType);


        serviceState = ServiceState.INITED;
        logger.info("server state:{}", serviceState);
    }

    public void start() throws Exception {

        for (EventMeshProtocolServer eventMeshProtocolServer : eventMeshProtocolServers) {
            eventMeshProtocolServer.start();
        }
        serviceState = ServiceState.RUNNING;
        logger.info("server state:{}", serviceState);
    }

    public void shutdown() throws Exception {
        serviceState = ServiceState.STOPING;
        logger.info("server state:{}", serviceState);
        for (EventMeshProtocolServer eventMeshProtocolServer : eventMeshProtocolServers) {
            eventMeshProtocolServer.shutdown();
        }

        connectorResource.release();

        serviceState = ServiceState.STOPED;
        logger.info("server state:{}", serviceState);
    }

    public List<EventMeshProtocolServer> getEventMeshProtocolServers() {
        return eventMeshProtocolServers;
    }

    public ServiceState getServiceState() {
        return serviceState;
    }
}
