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
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventMeshServer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public EventMeshHTTPServer eventMeshHTTPServer;

    private EventMeshTCPServer eventMeshTCPServer;

    private ServiceState serviceState;

    private Acl acl;

    private Registry registry;

    private ConnectorResource connectorResource;

    public EventMeshServer() {
        this.acl = new Acl();
        this.registry = new Registry();
        this.connectorResource = new ConnectorResource();
    }

    public void init() throws Exception {
        if (CommonConfiguration.eventMeshServerSecurityEnable) {
            acl.init(CommonConfiguration.eventMeshSecurityPluginType);
        }

        eventMeshHTTPServer = new EventMeshHTTPServer(this);
        eventMeshHTTPServer.init();
        connectorResource.init(CommonConfiguration.eventMeshConnectorPluginType);
        if (EventMeshTCPConfiguration.eventMeshTcpServerEnabled) {
            eventMeshTCPServer = new EventMeshTCPServer(this);
            eventMeshTCPServer.init();
        }

        if (CommonConfiguration.eventMeshServerRegistryEnable) {
            registry.init(CommonConfiguration.eventMeshRegistryPluginType);
        }

        String eventStore = System.getProperty(EventMeshConstants.EVENT_STORE_PROPERTIES, System.getenv(EventMeshConstants.EVENT_STORE_ENV));
        logger.info("eventStore : {}", eventStore);

        serviceState = ServiceState.INITED;
        logger.info("server state:{}", serviceState);
    }

    public void start() throws Exception {
        if (CommonConfiguration.eventMeshServerSecurityEnable) {
            acl.start();
        }
        if (CommonConfiguration.eventMeshServerRegistryEnable) {
            registry.start();
        }

        eventMeshHTTPServer.start();
        if (EventMeshTCPConfiguration.eventMeshTcpServerEnabled) {
            eventMeshTCPServer.start();
        }
        serviceState = ServiceState.RUNNING;
        logger.info("server state:{}", serviceState);
    }

    public void shutdown() throws Exception {
        serviceState = ServiceState.STOPING;
        logger.info("server state:{}", serviceState);
        eventMeshHTTPServer.shutdown();
        if (EventMeshTCPConfiguration.eventMeshTcpServerEnabled) {
            eventMeshTCPServer.shutdown();
        }

        if (CommonConfiguration.eventMeshServerRegistryEnable) {
            registry.shutdown();
        }
        connectorResource.release();

        if (CommonConfiguration.eventMeshServerSecurityEnable) {
            acl.shutdown();
        }
        serviceState = ServiceState.STOPED;
        logger.info("server state:{}", serviceState);
    }

    public EventMeshHTTPServer getEventMeshHTTPServer() {
        return eventMeshHTTPServer;
    }

    public EventMeshTCPServer getEventMeshTCPServer() {
        return eventMeshTCPServer;
    }

    public ServiceState getServiceState() {
        return serviceState;
    }
}
