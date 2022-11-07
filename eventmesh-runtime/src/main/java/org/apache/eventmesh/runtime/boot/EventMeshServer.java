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
import org.apache.eventmesh.common.config.ConfigurationWrapper;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.connector.ConnectorResource;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.registry.Registry;
import org.apache.eventmesh.runtime.trace.Trace;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventMeshServer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Acl acl;

    private Registry registry;

    private static Trace trace;

    private final ConnectorResource connectorResource;

    private ServiceState serviceState;

    private final CommonConfiguration configuration;

    private static final List<EventMeshBootstrap> BOOTSTRAP_LIST = new CopyOnWriteArrayList<>();

    public EventMeshServer(ConfigurationWrapper configurationWrapper) {
        CommonConfiguration configuration = new CommonConfiguration(configurationWrapper);
        configuration.init();
        this.configuration = configuration;
        this.acl = new Acl();
        this.registry = new Registry();
        trace = new Trace(configuration.eventMeshServerTraceEnable);
        this.connectorResource = new ConnectorResource();

        List<String> provideServerProtocols = configuration.eventMeshProvideServerProtocols;
        for (String provideServerProtocol : provideServerProtocols) {
            if (ConfigurationContextUtil.HTTP.equals(provideServerProtocol)) {
                BOOTSTRAP_LIST.add(new EventMeshHttpBootstrap(this,
                        configurationWrapper, registry));
            }
            if (ConfigurationContextUtil.TCP.equals(provideServerProtocol)) {
                BOOTSTRAP_LIST.add(new EventMeshTcpBootstrap(this,
                        configurationWrapper, registry));
            }
            if (ConfigurationContextUtil.GRPC.equals(provideServerProtocol)) {
                BOOTSTRAP_LIST.add(new EventMeshGrpcBootstrap(configurationWrapper,
                        registry));
            }
        }
    }

    public void init() throws Exception {
        if (configuration != null && configuration.eventMeshServerSecurityEnable) {
            acl.init(configuration.eventMeshSecurityPluginType);
        }
        // registry init
        if (configuration != null && configuration.eventMeshServerRegistryEnable) {
            registry.init(configuration.eventMeshRegistryPluginType);
        }

        if (configuration != null && configuration.eventMeshServerTraceEnable) {
            trace.init(configuration.eventMeshTracePluginType);
        }

        if (configuration != null) {
            connectorResource.init(configuration.eventMeshConnectorPluginType);
        }

        // server init
        for (EventMeshBootstrap eventMeshBootstrap : BOOTSTRAP_LIST) {
            eventMeshBootstrap.init();
        }

        String eventStore = System
            .getProperty(EventMeshConstants.EVENT_STORE_PROPERTIES, System.getenv(EventMeshConstants.EVENT_STORE_ENV));
        logger.info("eventStore : {}", eventStore);

        serviceState = ServiceState.INITED;
        logger.info("server state:{}", serviceState);
    }

    public void start() throws Exception {
        if (configuration != null && configuration.eventMeshServerSecurityEnable) {
            acl.start();
        }
        // registry start
        if (configuration != null && configuration.eventMeshServerRegistryEnable) {
            registry.start();
        }

        // server start
        for (EventMeshBootstrap eventMeshBootstrap : BOOTSTRAP_LIST) {
            eventMeshBootstrap.start();
        }

        serviceState = ServiceState.RUNNING;
        logger.info("server state:{}", serviceState);
    }

    public void shutdown() throws Exception {
        serviceState = ServiceState.STOPING;
        logger.info("server state:{}", serviceState);

        for (EventMeshBootstrap eventMeshBootstrap : BOOTSTRAP_LIST) {
            eventMeshBootstrap.shutdown();
        }

        if (configuration != null
                && configuration.eventMeshServerRegistryEnable) {
            registry.shutdown();
        }

        connectorResource.release();

        if (configuration != null && configuration.eventMeshServerSecurityEnable) {
            acl.shutdown();
        }

        if (configuration != null && configuration.eventMeshServerTraceEnable) {
            trace.shutdown();
        }

        ConfigurationContextUtil.clear();
        serviceState = ServiceState.STOPED;
        logger.info("server state:{}", serviceState);
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
