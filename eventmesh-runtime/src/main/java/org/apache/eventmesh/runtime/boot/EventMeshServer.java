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

import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import org.apache.eventmesh.runtime.connector.ConnectorResource;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.registry.Registry;
import org.apache.eventmesh.runtime.trace.Trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventMeshServer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public EventMeshHTTPServer eventMeshHTTPServer;

    private EventMeshTCPServer eventMeshTCPServer;

    private EventMeshGrpcServer eventMeshGrpcServer;

    private EventMeshGrpcConfiguration eventMeshGrpcConfiguration;

    private EventMeshHTTPConfiguration eventMeshHttpConfiguration;

    private EventMeshTCPConfiguration eventMeshTcpConfiguration;

    private Acl acl;

    private Registry registry;

    private static Trace trace;

    private ConnectorResource connectorResource;

    private ServiceState serviceState;

    public EventMeshServer(EventMeshHTTPConfiguration eventMeshHttpConfiguration,
                           EventMeshTCPConfiguration eventMeshTcpConfiguration,
                           EventMeshGrpcConfiguration eventMeshGrpcConfiguration) {
        this.eventMeshHttpConfiguration = eventMeshHttpConfiguration;
        this.eventMeshTcpConfiguration = eventMeshTcpConfiguration;
        this.eventMeshGrpcConfiguration = eventMeshGrpcConfiguration;
        this.acl = new Acl();
        this.registry = new Registry();
        this.trace = new Trace(eventMeshHttpConfiguration.eventMeshServerTraceEnable);
        this.connectorResource = new ConnectorResource();

        ConfigurationContextUtil.putIfAbsent(ConfigurationContextUtil.TCP, eventMeshTcpConfiguration);
        ConfigurationContextUtil.putIfAbsent(ConfigurationContextUtil.GRPC, eventMeshGrpcConfiguration);
        ConfigurationContextUtil.putIfAbsent(ConfigurationContextUtil.HTTP, eventMeshHttpConfiguration);
    }

    public void init() throws Exception {
        if (eventMeshHttpConfiguration != null && eventMeshHttpConfiguration.eventMeshServerSecurityEnable) {
            acl.init(eventMeshHttpConfiguration.eventMeshSecurityPluginType);
        }

        // registry init
        if (eventMeshTcpConfiguration != null
            && eventMeshTcpConfiguration.eventMeshTcpServerEnabled
            && eventMeshTcpConfiguration.eventMeshServerRegistryEnable) {
            registry.init(eventMeshTcpConfiguration.eventMeshRegistryPluginType);
        }

        if (eventMeshGrpcConfiguration != null && eventMeshGrpcConfiguration.eventMeshServerRegistryEnable) {
            registry.init(eventMeshGrpcConfiguration.eventMeshRegistryPluginType);
        }

        if (eventMeshHttpConfiguration != null && eventMeshHttpConfiguration.eventMeshServerRegistryEnable) {
            registry.init(eventMeshHttpConfiguration.eventMeshRegistryPluginType);
        }

        if (eventMeshHttpConfiguration != null && eventMeshHttpConfiguration.eventMeshServerTraceEnable) {
            trace.init(eventMeshHttpConfiguration.eventMeshTracePluginType);
        }

        connectorResource.init(eventMeshHttpConfiguration.eventMeshConnectorPluginType);

        // server init
        if (eventMeshGrpcConfiguration != null) {
            eventMeshGrpcServer = new EventMeshGrpcServer(eventMeshGrpcConfiguration, registry);
            eventMeshGrpcServer.init();
        }

        if (eventMeshHttpConfiguration != null) {
            eventMeshHTTPServer = new EventMeshHTTPServer(this, eventMeshHttpConfiguration);
            eventMeshHTTPServer.init();
        }

        if (eventMeshTcpConfiguration != null) {
            eventMeshTCPServer = new EventMeshTCPServer(this, eventMeshTcpConfiguration, registry);
            if (eventMeshTcpConfiguration.eventMeshTcpServerEnabled) {
                eventMeshTCPServer.init();
            }
        }

        String eventStore = System
            .getProperty(EventMeshConstants.EVENT_STORE_PROPERTIES, System.getenv(EventMeshConstants.EVENT_STORE_ENV));
        logger.info("eventStore : {}", eventStore);

        serviceState = ServiceState.INITED;
        logger.info("server state:{}", serviceState);
    }

    public void start() throws Exception {
        if (eventMeshHttpConfiguration != null && eventMeshHttpConfiguration.eventMeshServerSecurityEnable) {
            acl.start();
        }
        // registry start
        if (eventMeshTcpConfiguration != null
            && eventMeshTcpConfiguration.eventMeshTcpServerEnabled
            && eventMeshTcpConfiguration.eventMeshServerRegistryEnable) {
            registry.start();
        }
        if (eventMeshHttpConfiguration != null && eventMeshHttpConfiguration.eventMeshServerRegistryEnable) {
            registry.start();
        }
        if (eventMeshGrpcConfiguration != null && eventMeshGrpcConfiguration.eventMeshServerRegistryEnable) {
            registry.start();
        }

        // server start
        if (eventMeshGrpcConfiguration != null) {
            eventMeshGrpcServer.start();
        }
        if (eventMeshHttpConfiguration != null) {
            eventMeshHTTPServer.start();
        }
        if (eventMeshTcpConfiguration != null && eventMeshTcpConfiguration.eventMeshTcpServerEnabled) {
            eventMeshTCPServer.start();
        }
        serviceState = ServiceState.RUNNING;
        logger.info("server state:{}", serviceState);
    }

    public void shutdown() throws Exception {
        serviceState = ServiceState.STOPING;
        logger.info("server state:{}", serviceState);
        eventMeshHTTPServer.shutdown();
        if (eventMeshTcpConfiguration != null && eventMeshTcpConfiguration.eventMeshTcpServerEnabled) {
            eventMeshTCPServer.shutdown();
        }

        connectorResource.release();

        if (eventMeshGrpcConfiguration != null) {
            eventMeshGrpcServer.shutdown();
        }

        if (eventMeshHttpConfiguration != null && eventMeshHttpConfiguration.eventMeshServerSecurityEnable) {
            acl.shutdown();
        }

        if (eventMeshHttpConfiguration != null && eventMeshHttpConfiguration.eventMeshServerTraceEnable) {
            trace.shutdown();
        }


        ConfigurationContextUtil.clear();
        serviceState = ServiceState.STOPED;
        logger.info("server state:{}", serviceState);
    }

    public EventMeshGrpcServer getEventMeshGrpcServer() {
        return eventMeshGrpcServer;
    }

    public EventMeshHTTPServer getEventMeshHTTPServer() {
        return eventMeshHTTPServer;
    }

    public EventMeshTCPServer getEventMeshTCPServer() {
        return eventMeshTCPServer;
    }

    public static Trace getTrace() {
        return trace;
    }

    public ServiceState getServiceState() {
        return serviceState;
    }

    public Registry getRegistry() {
        return registry;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }
}
