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

import static org.apache.eventmesh.common.Constants.TCP;

import org.apache.eventmesh.api.meta.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.meta.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.metrics.api.MetricsPluginFactory;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.processor.GoodbyeProcessor;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.processor.HeartBeatProcessor;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.processor.HelloProcessor;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.processor.ListenProcessor;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.processor.MessageAckProcessor;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.processor.MessageTransferProcessor;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.processor.RecommendProcessor;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.processor.SubscribeProcessor;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.processor.UnSubscribeProcessor;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.rebalance.EventMeshRebalanceImpl;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.rebalance.EventMeshRebalanceService;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.retry.TcpRetryer;
import org.apache.eventmesh.runtime.meta.MetaStorage;
import org.apache.eventmesh.runtime.metrics.tcp.EventMeshTcpMetricsManager;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.assertj.core.util.Lists;

import com.google.common.util.concurrent.RateLimiter;

import lombok.extern.slf4j.Slf4j;

/**
 * Add multiple managers to the underlying server
 */
@Slf4j
public class EventMeshTCPServer extends AbstractTCPServer {

    private final EventMeshServer eventMeshServer;
    private final EventMeshTCPConfiguration eventMeshTCPConfiguration;

    private final MetaStorage metaStorage;
    private final Acl acl;

    private ClientSessionGroupMapping clientSessionGroupMapping;

    private TcpRetryer tcpRetryer;

    private RateLimiter rateLimiter;
    private EventMeshRebalanceService eventMeshRebalanceService;

    public EventMeshTCPServer(final EventMeshServer eventMeshServer, final EventMeshTCPConfiguration eventMeshTCPConfiguration) {
        super(eventMeshTCPConfiguration);
        this.eventMeshServer = eventMeshServer;
        this.eventMeshTCPConfiguration = eventMeshTCPConfiguration;
        this.metaStorage = eventMeshServer.getMetaStorage();
        this.acl = eventMeshServer.getAcl();
    }

    public void init() throws Exception {
        log.info("==================EventMeshTCPServer Initialing==================");
        super.init();

        rateLimiter = RateLimiter.create(eventMeshTCPConfiguration.getEventMeshTcpMsgReqnumPerSecond());

        // The MetricsRegistry is singleton, so we can use factory method to get.
        final List<MetricsRegistry> metricsRegistries = Lists.newArrayList();
        Optional.ofNullable(eventMeshTCPConfiguration.getEventMeshMetricsPluginType()).ifPresent(
            metricsPlugins -> metricsPlugins.forEach(
                pluginType -> metricsRegistries.add(MetricsPluginFactory.getMetricsRegistry(pluginType))));

        tcpRetryer = new TcpRetryer(this);

        clientSessionGroupMapping = new ClientSessionGroupMapping(this);
        clientSessionGroupMapping.init();
        super.setClientSessionGroupMapping(clientSessionGroupMapping);

        super.setEventMeshTcpMetricsManager(new EventMeshTcpMetricsManager(this, metricsRegistries));

        if (eventMeshTCPConfiguration.isEventMeshServerMetaStorageEnable()) {
            eventMeshRebalanceService = new EventMeshRebalanceService(this, new EventMeshRebalanceImpl(this));
            eventMeshRebalanceService.init();
        }

        registerTCPRequestProcessor();

        log.info("--------------------------EventMeshTCPServer Inited");
    }

    @Override
    public void start() throws Exception {
        super.start();
        super.getEventMeshTcpMetricsManager().start();

        clientSessionGroupMapping.start();
        tcpRetryer.start();

        if (eventMeshTCPConfiguration.isEventMeshServerMetaStorageEnable()) {
            this.register();
            eventMeshRebalanceService.start();
        }

        log.info("--------------------------EventMeshTCPServer Started");
    }

    @Override
    public void shutdown() throws Exception {
        super.shutdown();

        super.getEventMeshTcpMetricsManager().shutdown();

        clientSessionGroupMapping.shutdown();
        ThreadUtils.sleep(40, TimeUnit.SECONDS);

        tcpRetryer.shutdown();

        if (eventMeshTCPConfiguration.isEventMeshServerMetaStorageEnable()) {
            eventMeshRebalanceService.shutdown();
            this.unRegister();
        }

        log.info("--------------------------EventMeshTCPServer Shutdown");
    }

    /**
     * Related to the registry module
     *
     * @return boolean
     */
    public boolean register() {
        boolean registerResult = false;
        try {
            String endPoints = IPUtils.getLocalAddress()
                + EventMeshConstants.IP_PORT_SEPARATOR + eventMeshTCPConfiguration.getEventMeshTcpServerPort();
            EventMeshRegisterInfo eventMeshRegisterInfo = new EventMeshRegisterInfo();
            eventMeshRegisterInfo.setEventMeshClusterName(eventMeshTCPConfiguration.getEventMeshCluster());
            eventMeshRegisterInfo.setEventMeshName(eventMeshTCPConfiguration.getEventMeshName() + "-" + TCP);
            eventMeshRegisterInfo.setEndPoint(endPoints);
            eventMeshRegisterInfo.setEventMeshInstanceNumMap(clientSessionGroupMapping.prepareProxyClientDistributionData());
            eventMeshRegisterInfo.setProtocolType(TCP);
            registerResult = metaStorage.register(eventMeshRegisterInfo);
        } catch (Exception e) {
            log.error("eventMesh register to registry failed", e);
        }

        return registerResult;
    }

    /**
     * Related to the registry module
     */
    private void unRegister() {
        String endPoints = IPUtils.getLocalAddress() + EventMeshConstants.IP_PORT_SEPARATOR + eventMeshTCPConfiguration.getEventMeshTcpServerPort();
        EventMeshUnRegisterInfo eventMeshUnRegisterInfo = new EventMeshUnRegisterInfo();
        eventMeshUnRegisterInfo.setEventMeshClusterName(eventMeshTCPConfiguration.getEventMeshCluster());
        eventMeshUnRegisterInfo.setEventMeshName(eventMeshTCPConfiguration.getEventMeshName());
        eventMeshUnRegisterInfo.setEndPoint(endPoints);
        eventMeshUnRegisterInfo.setProtocolType(TCP);
        boolean registerResult = metaStorage.unRegister(eventMeshUnRegisterInfo);
        if (!registerResult) {
            throw new EventMeshException("eventMesh fail to unRegister");
        }
    }

    private void registerTCPRequestProcessor() {
        ThreadPoolExecutor taskHandleExecutorService = super.getTcpThreadPoolGroup().getTaskHandleExecutorService();

        HelloProcessor helloProcessor = new HelloProcessor(this);
        registerProcessor(Command.HELLO_REQUEST, helloProcessor, taskHandleExecutorService);

        RecommendProcessor recommendProcessor = new RecommendProcessor(this);
        registerProcessor(Command.RECOMMEND_REQUEST, recommendProcessor, taskHandleExecutorService);

        HeartBeatProcessor heartBeatProcessor = new HeartBeatProcessor(this);
        registerProcessor(Command.HEARTBEAT_REQUEST, heartBeatProcessor, taskHandleExecutorService);

        GoodbyeProcessor goodbyeProcessor = new GoodbyeProcessor(this);
        registerProcessor(Command.CLIENT_GOODBYE_REQUEST, goodbyeProcessor, taskHandleExecutorService);
        registerProcessor(Command.SERVER_GOODBYE_RESPONSE, goodbyeProcessor, taskHandleExecutorService);

        SubscribeProcessor subscribeProcessor = new SubscribeProcessor(this);
        registerProcessor(Command.SUBSCRIBE_REQUEST, subscribeProcessor, taskHandleExecutorService);

        UnSubscribeProcessor unSubscribeProcessor = new UnSubscribeProcessor(this);
        registerProcessor(Command.UNSUBSCRIBE_REQUEST, unSubscribeProcessor, taskHandleExecutorService);

        ListenProcessor listenProcessor = new ListenProcessor(this);
        registerProcessor(Command.LISTEN_REQUEST, listenProcessor, taskHandleExecutorService);

        ThreadPoolExecutor sendExecutorService = super.getTcpThreadPoolGroup().getSendExecutorService();
        MessageTransferProcessor messageTransferProcessor = new MessageTransferProcessor(this);
        registerProcessor(Command.REQUEST_TO_SERVER, messageTransferProcessor, sendExecutorService);
        registerProcessor(Command.ASYNC_MESSAGE_TO_SERVER, messageTransferProcessor, sendExecutorService);
        registerProcessor(Command.BROADCAST_MESSAGE_TO_SERVER, messageTransferProcessor, sendExecutorService);

        ThreadPoolExecutor replyExecutorService = super.getTcpThreadPoolGroup().getReplyExecutorService();
        registerProcessor(Command.RESPONSE_TO_SERVER, messageTransferProcessor, replyExecutorService);

        ThreadPoolExecutor ackExecutorService = super.getTcpThreadPoolGroup().getAckExecutorService();
        MessageAckProcessor messageAckProcessor = new MessageAckProcessor(this);
        registerProcessor(Command.RESPONSE_TO_CLIENT_ACK, messageAckProcessor, ackExecutorService);
        registerProcessor(Command.ASYNC_MESSAGE_TO_CLIENT_ACK, messageAckProcessor, ackExecutorService);
        registerProcessor(Command.BROADCAST_MESSAGE_TO_CLIENT_ACK, messageAckProcessor, ackExecutorService);
        registerProcessor(Command.REQUEST_TO_CLIENT_ACK, messageAckProcessor, ackExecutorService);
    }

    public EventMeshServer getEventMeshServer() {
        return eventMeshServer;
    }

    public EventMeshTCPConfiguration getEventMeshTCPConfiguration() {
        return eventMeshTCPConfiguration;
    }

    public MetaStorage getMetaStorage() {
        return metaStorage;
    }

    public EventMeshRebalanceService getEventMeshRebalanceService() {
        return eventMeshRebalanceService;
    }

    public Acl getAcl() {
        return acl;
    }

    public ClientSessionGroupMapping getClientSessionGroupMapping() {
        return clientSessionGroupMapping;
    }

    public void setClientSessionGroupMapping(ClientSessionGroupMapping clientSessionGroupMapping) {
        this.clientSessionGroupMapping = clientSessionGroupMapping;
    }

    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    public void setRateLimiter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    public TcpRetryer getTcpRetryer() {
        return tcpRetryer;
    }


}
