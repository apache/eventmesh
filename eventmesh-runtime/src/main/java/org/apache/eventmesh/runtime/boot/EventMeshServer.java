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
import static org.apache.eventmesh.common.Constants.HTTP;
import static org.apache.eventmesh.common.Constants.TCP;

import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.common.utils.AssertUtils;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.core.protocol.http.producer.ProducerTopicManager;
import org.apache.eventmesh.runtime.meta.MetaStorage;
import org.apache.eventmesh.runtime.storage.StorageResource;
import org.apache.eventmesh.runtime.trace.Trace;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshServer {

    private final Acl acl;

    private MetaStorage metaStorage;

    private static Trace trace;

    private final StorageResource storageResource;

    private ServiceState serviceState;

    private ProducerTopicManager producerTopicManager;

    private final CommonConfiguration configuration;

    //  private transient ClientManageController clientManageController;

    private static final List<EventMeshBootstrap> BOOTSTRAP_LIST = new CopyOnWriteArrayList<>();

    private static final String SERVER_STATE_MSG = "server state:{}";

    private static final ConfigService configService = ConfigService.getInstance();

    private EventMeshAdminBootstrap adminBootstrap;

    private EventMeshTCPServer eventMeshTCPServer = null;

    private EventMeshGrpcServer eventMeshGrpcServer = null;

    private EventMeshHTTPServer eventMeshHTTPServer = null;

    public EventMeshServer() {

        // Initialize configuration
        this.configuration = configService.buildConfigInstance(CommonConfiguration.class);
        AssertUtils.notNull(this.configuration, "configuration is null");

        // Initialize acl, registry, trace and storageResource
        this.acl = Acl.getInstance(this.configuration.getEventMeshSecurityPluginType());
        this.metaStorage = MetaStorage.getInstance(this.configuration.getEventMeshMetaStoragePluginType());
        trace = Trace.getInstance(this.configuration.getEventMeshTracePluginType(), this.configuration.isEventMeshServerTraceEnable());
        this.storageResource = StorageResource.getInstance(this.configuration.getEventMeshStoragePluginType());

        // Initialize BOOTSTRAP_LIST based on protocols provided in configuration
        final List<String> provideServerProtocols = configuration.getEventMeshProvideServerProtocols();
        for (String provideServerProtocol : provideServerProtocols) {
            switch (provideServerProtocol.toUpperCase()) {
                case HTTP:
                    BOOTSTRAP_LIST.add(new EventMeshHttpBootstrap(this));
                    break;
                case TCP:
                    BOOTSTRAP_LIST.add(new EventMeshTcpBootstrap(this));
                    break;
                case GRPC:
                    BOOTSTRAP_LIST.add(new EventMeshGrpcBootstrap(this));
                    break;
                default:
                    // nothing to do
            }
        }

        // If no protocols are provided, initialize BOOTSTRAP_LIST with default protocols
        if (BOOTSTRAP_LIST.isEmpty()) {
            BOOTSTRAP_LIST.add(new EventMeshTcpBootstrap(this));
        }
    }

    public void init() throws Exception {
        storageResource.init();
        if (configuration.isEventMeshServerSecurityEnable()) {
            acl.init();
        }
        if (configuration.isEventMeshServerMetaStorageEnable()) {
            metaStorage.init();
        }
        if (configuration.isEventMeshServerTraceEnable()) {
            trace.init();
        }

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

        if (Objects.nonNull(eventMeshTCPServer) && Objects.nonNull(eventMeshHTTPServer) && Objects.nonNull(eventMeshGrpcServer)) {
            adminBootstrap = new EventMeshAdminBootstrap(this);
            adminBootstrap.init();
        }

        producerTopicManager = new ProducerTopicManager(this);
        producerTopicManager.init();

        serviceState = ServiceState.INITED;
        log.info(SERVER_STATE_MSG, serviceState);
    }

    public void start() throws Exception {
        if (Objects.nonNull(configuration)) {
            if (configuration.isEventMeshServerSecurityEnable()) {
                acl.start();
            }
            // registry start
            if (configuration.isEventMeshServerMetaStorageEnable()) {
                metaStorage.start();
            }
        }
        // server start
        for (final EventMeshBootstrap eventMeshBootstrap : BOOTSTRAP_LIST) {
            eventMeshBootstrap.start();
        }

        if (Objects.nonNull(adminBootstrap)) {
            adminBootstrap.start();
        }
        producerTopicManager.start();
        serviceState = ServiceState.RUNNING;
        log.info(SERVER_STATE_MSG, serviceState);

    }

    public void shutdown() throws Exception {
        serviceState = ServiceState.STOPPING;
        log.info(SERVER_STATE_MSG, serviceState);

        for (final EventMeshBootstrap eventMeshBootstrap : BOOTSTRAP_LIST) {
            eventMeshBootstrap.shutdown();
        }

        if (configuration != null && configuration.isEventMeshServerMetaStorageEnable()) {
            metaStorage.shutdown();
        }

        storageResource.release();

        if (configuration != null && configuration.isEventMeshServerSecurityEnable()) {
            acl.shutdown();
        }

        if (configuration != null && configuration.isEventMeshServerTraceEnable()) {
            trace.shutdown();
        }
        producerTopicManager.shutdown();
        ConfigurationContextUtil.clear();
        serviceState = ServiceState.STOPPED;

        log.info(SERVER_STATE_MSG, serviceState);
    }

    public static Trace getTrace() {
        return trace;
    }

    public ServiceState getServiceState() {
        return serviceState;
    }

    public MetaStorage getMetaStorage() {
        return metaStorage;
    }

    public void setMetaStorage(final MetaStorage metaStorage) {
        this.metaStorage = metaStorage;
    }

    public Acl getAcl() {
        return acl;
    }

    public ProducerTopicManager getProducerTopicManager() {
        return producerTopicManager;
    }

    public CommonConfiguration getConfiguration() {
        return configuration;
    }

    public EventMeshTCPServer getEventMeshTCPServer() {
        return eventMeshTCPServer;
    }

    public EventMeshGrpcServer getEventMeshGrpcServer() {
        return eventMeshGrpcServer;
    }

    public EventMeshHTTPServer getEventMeshHTTPServer() {
        return eventMeshHTTPServer;
    }
}
