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
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.admin.controller.ClientManageController;
import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.connector.ConnectorResource;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.registry.Registry;
import org.apache.eventmesh.runtime.trace.Trace;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventMeshServer {

    public static final Logger LOGGER = LoggerFactory.getLogger(EventMeshServer.class);

    private final Acl acl;

    private Registry registry;

    private static Trace trace;

    private final ConnectorResource connectorResource;

    private ServiceState serviceState;

    private final CommonConfiguration configuration;

    private transient ClientManageController clientManageController;

    private static final List<EventMeshBootstrap> BOOTSTRAP_LIST = new CopyOnWriteArrayList<>();

    private static final String SERVER_STATE_MSG = "server state:{}";

    public EventMeshServer() throws Exception {
        ConfigService configService = ConfigService.getInstance();
        this.configuration = configService.buildConfigInstance(CommonConfiguration.class);

        this.acl = new Acl();
        this.registry = new Registry();
        trace = new Trace(configuration.isEventMeshServerTraceEnable());
        this.connectorResource = new ConnectorResource();
        trace = new Trace(configuration.isEventMeshServerTraceEnable());

        final List<String> provideServerProtocols = configuration.getEventMeshProvideServerProtocols();
        for (final String provideServerProtocol : provideServerProtocols) {
            if (ConfigurationContextUtil.HTTP.equals(provideServerProtocol)) {
                BOOTSTRAP_LIST.add(new EventMeshHttpBootstrap(this, registry));
            }
            if (ConfigurationContextUtil.TCP.equals(provideServerProtocol)) {
                BOOTSTRAP_LIST.add(new EventMeshTcpBootstrap(this, registry));
            }
            if (ConfigurationContextUtil.GRPC.equals(provideServerProtocol)) {
                BOOTSTRAP_LIST.add(new EventMeshGrpcBootstrap(registry));
            }
        }

        init();
    }

    private void init() throws Exception {
        if (Objects.nonNull(configuration)) {
            connectorResource.init(configuration.getEventMeshConnectorPluginType());
            if (configuration.isEventMeshServerSecurityEnable()) {
                acl.init(configuration.getEventMeshSecurityPluginType());
            }
            if (configuration.isEventMeshServerRegistryEnable()) {
                registry.init(configuration.getEventMeshRegistryPluginType());
            }
            if (configuration.isEventMeshServerTraceEnable()) {
                trace.init(configuration.getEventMeshTracePluginType());
            }
        }

        EventMeshTCPServer eventMeshTCPServer = null;

        EventMeshGrpcServer eventMeshGrpcServer = null;

        EventMeshHTTPServer eventMeshHTTPServer = null;

        // server init
        for (final EventMeshBootstrap eventMeshBootstrap : BOOTSTRAP_LIST) {
            eventMeshBootstrap.init();
            if (eventMeshBootstrap instanceof EventMeshTcpBootstrap) {
                eventMeshTCPServer = ((EventMeshTcpBootstrap) eventMeshBootstrap).getEventMeshTcpServer();
            }
            if (eventMeshBootstrap instanceof EventMeshHttpBootstrap) {
                eventMeshHTTPServer = ((EventMeshHttpBootstrap) eventMeshBootstrap).getEventMeshHttpServer();
            }
            if (eventMeshBootstrap instanceof EventMeshGrpcBootstrap) {
                eventMeshGrpcServer = ((EventMeshGrpcBootstrap) eventMeshBootstrap).getEventMeshGrpcServer();
            }
        }

        if (Objects.nonNull(eventMeshTCPServer) && Objects.nonNull(eventMeshHTTPServer)
            && Objects.nonNull(eventMeshGrpcServer)) {
            clientManageController = new ClientManageController(eventMeshTCPServer,
                eventMeshHTTPServer, eventMeshGrpcServer, registry);

            clientManageController.setAdminWebHookConfigOperationManage(eventMeshTCPServer.getAdminWebHookConfigOperationManage());

        }

        final String eventStore = System
                .getProperty(EventMeshConstants.EVENT_STORE_PROPERTIES, System.getenv(EventMeshConstants.EVENT_STORE_ENV));

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("eventStore : {}", eventStore);
        }

        serviceState = ServiceState.INITED;

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(SERVER_STATE_MSG, serviceState);
        }
    }

    public void start() throws Exception {
        if (Objects.nonNull(configuration)) {
            if (configuration.isEventMeshServerSecurityEnable()) {
                acl.start();
            }
            // registry start
            if (configuration.isEventMeshServerRegistryEnable()) {
                registry.start();
            }
        }
        // server start
        for (final EventMeshBootstrap eventMeshBootstrap : BOOTSTRAP_LIST) {
            eventMeshBootstrap.start();
        }

        if (Objects.nonNull(clientManageController)) {
            clientManageController.start();
        }


        serviceState = ServiceState.RUNNING;
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(SERVER_STATE_MSG, serviceState);
        }

    }

    public void shutdown() throws Exception {
        serviceState = ServiceState.STOPING;
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(SERVER_STATE_MSG, serviceState);
        }

        for (final EventMeshBootstrap eventMeshBootstrap : BOOTSTRAP_LIST) {
            eventMeshBootstrap.shutdown();
        }

        if (configuration != null
                && configuration.isEventMeshServerRegistryEnable()) {
            registry.shutdown();
        }

        connectorResource.release();

        if (configuration != null && configuration.isEventMeshServerSecurityEnable()) {
            acl.shutdown();
        }

        if (configuration != null && configuration.isEventMeshServerTraceEnable()) {
            trace.shutdown();
        }

        ConfigurationContextUtil.clear();
        serviceState = ServiceState.STOPED;

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(SERVER_STATE_MSG, serviceState);
        }
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

    public void setRegistry(final Registry registry) {
        this.registry = registry;
    }
}
