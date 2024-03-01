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
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.producer.ProducerTopicManager;
import org.apache.eventmesh.runtime.lifecircle.EventMeshSwitchableComponent;
import org.apache.eventmesh.runtime.meta.MetaStorage;
import org.apache.eventmesh.runtime.storage.StorageResource;
import org.apache.eventmesh.runtime.trace.Trace;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshServer extends EventMeshSwitchableComponent {

    private static final ConfigService configService = ConfigService.getInstance();

    private ServiceState serviceState;

    private static final String SERVER_STATE_MSG = "server state:{}";
    private final Acl acl;

    private MetaStorage metaStorage;

    private static Trace trace;

    private final StorageResource storageResource;

    private ProducerTopicManager producerTopicManager;

    //  private transient ClientManageController clientManageController;

    private static final List<EventMeshBootstrap> BOOTSTRAP_LIST = new CopyOnWriteArrayList<>();

    private EventMeshAdminBootstrap adminBootstrap;

    private EventMeshTCPServer eventMeshTCPServer = null;

    private EventMeshGrpcServer eventMeshGrpcServer = null;

    private EventMeshHTTPServer eventMeshHTTPServer = null;

    public EventMeshServer() {
        // Initialize configuration
        this.configuration = configService.buildConfigInstance(CommonConfiguration.class);
        /*EventMeshSwitchableComponent.setCommonConfiguration(configuration);*/
        AssertUtils.notNull(this.configuration, "configuration is null");

        // Initialize acl, registry, trace and storageResource
        this.acl = Acl.getInstance(this.configuration.getEventMeshSecurityPluginType());
        bindLifeCycle(this.acl);

        this.metaStorage = MetaStorage.getInstance(this.configuration.getEventMeshMetaStoragePluginType());
        bindLifeCycle(this.metaStorage);

        trace = Trace.getInstance(this.configuration.getEventMeshTracePluginType(), this.configuration.isEventMeshServerTraceEnable());
        bindLifeCycle(this.trace);

        this.storageResource = StorageResource.getInstance(this.configuration.getEventMeshStoragePluginType());
        bindLifeCycle(this.storageResource);

        // Initialize BOOTSTRAP_LIST based on protocols provided in configuration
        final List<String> provideServerProtocols = configuration.getEventMeshProvideServerProtocols();
        for (String provideServerProtocol : provideServerProtocols) {
            switch (provideServerProtocol.toUpperCase()) {
                case HTTP:
                    EventMeshHttpBootstrap httpBootstrap = new EventMeshHttpBootstrap(this);
                    BOOTSTRAP_LIST.add(httpBootstrap);
                    bindLifeCycle(httpBootstrap);
                    break;
                case TCP:
                    EventMeshTcpBootstrap tcpBootstrap = new EventMeshTcpBootstrap(this);
                    BOOTSTRAP_LIST.add(tcpBootstrap);
                    bindLifeCycle(tcpBootstrap);
                    break;
                case GRPC:
                    EventMeshGrpcBootstrap grpcBootstrap = new EventMeshGrpcBootstrap(this);
                    BOOTSTRAP_LIST.add(grpcBootstrap);
                    bindLifeCycle(grpcBootstrap);
                    break;
                default:
                    // nothing to do
            }
        }

        // If no protocols are provided, initialize BOOTSTRAP_LIST with default protocols
        if (BOOTSTRAP_LIST.isEmpty()) {
            EventMeshTcpBootstrap tcpBootstrap = new EventMeshTcpBootstrap(this);
            BOOTSTRAP_LIST.add(tcpBootstrap);
            bindLifeCycle(tcpBootstrap);
        }

        producerTopicManager = new ProducerTopicManager(this);
        bindLifeCycle(producerTopicManager);
    }

    @Override
    protected boolean shouldTurn(CommonConfiguration configuration) {
        return true;
    }

    public void componentInit() throws Exception {
    }

    @Override
    protected void postBindInited() throws Exception {
        for (final EventMeshBootstrap eventMeshBootstrap : BOOTSTRAP_LIST) {
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

        // adminBootstrap Depend on http\grpc\tcp Server
        adminBootstrap = new EventMeshAdminBootstrap(this);
        bindLifeCycle(adminBootstrap);

        final String eventStore = System.getProperty(EventMeshConstants.EVENT_STORE_PROPERTIES, System.getenv(EventMeshConstants.EVENT_STORE_ENV));
        log.info("eventStore : {}", eventStore);

        serviceState = ServiceState.INITED;
        log.info(SERVER_STATE_MSG, serviceState);
    }


    public void componentStart() throws Exception {
    }

    protected void postBindStarted() throws Exception {
        serviceState = ServiceState.RUNNING;
        log.info(SERVER_STATE_MSG, serviceState);
    }

    public void componentStop() throws Exception {
        serviceState = ServiceState.STOPPING;
        log.info(SERVER_STATE_MSG, serviceState);
    }

    @Override
    protected void postBindStopped() {
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
