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


import org.apache.eventmesh.api.registry.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.metrics.api.MetricsPluginFactory;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumer.ConsumerManager;
import org.apache.eventmesh.runtime.core.consumer.SubscriptionManager;
import org.apache.eventmesh.runtime.core.producer.ProducerManager;
import org.apache.eventmesh.runtime.core.protocol.tcp.consumer.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.processor.GoodbyeProcessor;
import org.apache.eventmesh.runtime.core.protocol.tcp.processor.HeartBeatProcessor;
import org.apache.eventmesh.runtime.core.protocol.tcp.processor.HelloProcessor;
import org.apache.eventmesh.runtime.core.protocol.tcp.processor.ListenProcessor;
import org.apache.eventmesh.runtime.core.protocol.tcp.processor.MessageAckProcessor;
import org.apache.eventmesh.runtime.core.protocol.tcp.processor.RecommendProcessor;
import org.apache.eventmesh.runtime.core.protocol.tcp.processor.SendMessageProcessor;
import org.apache.eventmesh.runtime.core.protocol.tcp.processor.SubscribeProcessor;
import org.apache.eventmesh.runtime.core.protocol.tcp.processor.UnSubscribeProcessor;
import org.apache.eventmesh.runtime.core.protocol.tcp.rebalance.EventMeshRebalanceImpl;
import org.apache.eventmesh.runtime.core.protocol.tcp.rebalance.EventMeshRebalanceService;
import org.apache.eventmesh.runtime.core.protocol.tcp.retry.TcpRetryer;
import org.apache.eventmesh.runtime.metrics.tcp.EventMeshTcpMonitor;
import org.apache.eventmesh.runtime.registry.Registry;
import org.apache.eventmesh.webhook.admin.AdminWebHookConfigOperationManager;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.assertj.core.util.Lists;

import com.google.common.util.concurrent.RateLimiter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshTCPServer extends AbstractTCPServer {
    private final EventMeshServer eventMeshServer;
    private final EventMeshTCPConfiguration eventMeshTCPConfiguration;

    private final Registry registry;
    private final Acl acl;

    private ConsumerManager consumerManager;
    private ProducerManager producerManager;
    private SubscriptionManager subscriptionManager;
    private ClientSessionGroupMapping clientSessionGroupMapping;
    private TcpRetryer tcpRetryer;


    private ScheduledExecutorService scheduler;
    private ThreadPoolExecutor taskHandleExecutorService;
    private ThreadPoolExecutor broadcastMsgDownstreamExecutorService;

    private AdminWebHookConfigOperationManager adminWebHookConfigOperationManage;

    private RateLimiter msgRateLimiter;
    private EventMeshRebalanceService eventMeshRebalanceService;


    public EventMeshTCPServer(final EventMeshServer eventMeshServer, final EventMeshTCPConfiguration eventMeshTCPConfiguration) {
        super(eventMeshTCPConfiguration);
        super.setEventMeshTCPServer(this);

        this.eventMeshServer = eventMeshServer;
        this.eventMeshTCPConfiguration = eventMeshTCPConfiguration;
        this.registry = eventMeshServer.getRegistry();
        this.acl = eventMeshServer.getAcl();
    }

    public void init() throws Exception {
        if (log.isInfoEnabled()) {
            log.info("==================EventMeshTCPServer Initialing==================");
        }
        super.init("eventMesh-tcp");

        initThreadPool();

        msgRateLimiter = RateLimiter.create(eventMeshTCPConfiguration.getEventMeshTcpMsgReqnumPerSecond());


        // The MetricsRegistry is singleton, so we can use factory method to get.
        final List<MetricsRegistry> metricsRegistries = Lists.newArrayList();
        Optional.ofNullable(eventMeshTCPConfiguration.getEventMeshMetricsPluginType()).ifPresent(
                metricsPlugins -> metricsPlugins.forEach(
                        pluginType -> metricsRegistries.add(MetricsPluginFactory.getMetricsRegistry(pluginType))
                )
        );

        tcpRetryer = new TcpRetryer(this);
        tcpRetryer.init();


        clientSessionGroupMapping = new ClientSessionGroupMapping(this);
        super.setClientSessionGroupMapping(clientSessionGroupMapping);
        clientSessionGroupMapping.init();

        producerManager = new ProducerManager(eventMeshTCPConfiguration);
        producerManager.init();

        super.setEventMeshTcpMonitor(new EventMeshTcpMonitor(this, metricsRegistries));
        super.getEventMeshTcpMonitor().init();

        if (eventMeshTCPConfiguration.isEventMeshServerRegistryEnable()) {
            eventMeshRebalanceService = new EventMeshRebalanceService(this, new EventMeshRebalanceImpl(this));
            eventMeshRebalanceService.init();
        }

        adminWebHookConfigOperationManage = new AdminWebHookConfigOperationManager();
        adminWebHookConfigOperationManage.init();

        registerTCPRequestProcessor();

        if (log.isInfoEnabled()) {
            log.info("--------------------------EventMeshTCPServer Inited");
        }
    }

    @Override
    public void start() throws Exception {
        super.start();
        super.getEventMeshTcpMonitor().start();

        clientSessionGroupMapping.start();
        tcpRetryer.start();

        if (eventMeshTCPConfiguration.isEventMeshServerRegistryEnable()) {
            this.register();
            eventMeshRebalanceService.start();
        }

        if (log.isInfoEnabled()) {
            log.info("--------------------------EventMeshTCPServer Started");
        }
    }

    @Override
    public void shutdown() throws Exception {
        super.shutdown();

        super.getEventMeshTcpMonitor().shutdown();

        clientSessionGroupMapping.shutdown();
        ThreadUtils.sleep(40, TimeUnit.SECONDS);


        shutdownThreadPool();

        tcpRetryer.shutdown();

        if (eventMeshTCPConfiguration.isEventMeshServerRegistryEnable()) {
            eventMeshRebalanceService.shutdown();
            this.unRegister();
        }

        if (log.isInfoEnabled()) {
            log.info("--------------------------EventMeshTCPServer Shutdown");
        }
    }

    public boolean register() {
        boolean registerResult = false;
        try {
            String endPoints = IPUtils.getLocalAddress()
                    + EventMeshConstants.IP_PORT_SEPARATOR + eventMeshTCPConfiguration.getEventMeshTcpServerPort();
            EventMeshRegisterInfo eventMeshRegisterInfo = new EventMeshRegisterInfo();
            eventMeshRegisterInfo.setEventMeshClusterName(eventMeshTCPConfiguration.getEventMeshCluster());
            eventMeshRegisterInfo.setEventMeshName(eventMeshTCPConfiguration.getEventMeshName() + "-" + ConfigurationContextUtil.TCP);
            eventMeshRegisterInfo.setEndPoint(endPoints);
            eventMeshRegisterInfo.setEventMeshInstanceNumMap(clientSessionGroupMapping.prepareProxyClientDistributionData());
            eventMeshRegisterInfo.setProtocolType(ConfigurationContextUtil.TCP);
            registerResult = registry.register(eventMeshRegisterInfo);
        } catch (Exception e) {
            log.error("eventMesh register to registry failed", e);
        }

        return registerResult;
    }

    private void unRegister() {
        String endPoints = IPUtils.getLocalAddress() + EventMeshConstants.IP_PORT_SEPARATOR + eventMeshTCPConfiguration.getEventMeshTcpServerPort();
        EventMeshUnRegisterInfo eventMeshUnRegisterInfo = new EventMeshUnRegisterInfo();
        eventMeshUnRegisterInfo.setEventMeshClusterName(eventMeshTCPConfiguration.getEventMeshCluster());
        eventMeshUnRegisterInfo.setEventMeshName(eventMeshTCPConfiguration.getEventMeshName());
        eventMeshUnRegisterInfo.setEndPoint(endPoints);
        eventMeshUnRegisterInfo.setProtocolType(ConfigurationContextUtil.TCP);
        boolean registerResult = registry.unRegister(eventMeshUnRegisterInfo);
        if (!registerResult) {
            throw new EventMeshException("eventMesh fail to unRegister");
        }
    }

    private void registerTCPRequestProcessor() {
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

        SendMessageProcessor sendMessageProcessor = new SendMessageProcessor(this);
        registerProcessor(Command.REQUEST_TO_SERVER, sendMessageProcessor, taskHandleExecutorService);
        registerProcessor(Command.RESPONSE_TO_SERVER, sendMessageProcessor, taskHandleExecutorService);
        registerProcessor(Command.ASYNC_MESSAGE_TO_SERVER, sendMessageProcessor, taskHandleExecutorService);
        registerProcessor(Command.BROADCAST_MESSAGE_TO_SERVER, sendMessageProcessor, taskHandleExecutorService);

        MessageAckProcessor messageAckProcessor = new MessageAckProcessor(this);
        registerProcessor(Command.RESPONSE_TO_CLIENT_ACK, messageAckProcessor, taskHandleExecutorService);
        registerProcessor(Command.ASYNC_MESSAGE_TO_CLIENT_ACK, messageAckProcessor, taskHandleExecutorService);
        registerProcessor(Command.BROADCAST_MESSAGE_TO_CLIENT_ACK, messageAckProcessor, taskHandleExecutorService);
        registerProcessor(Command.REQUEST_TO_CLIENT_ACK, messageAckProcessor, taskHandleExecutorService);
    }


    private void initThreadPool() {

        scheduler = ThreadPoolFactory.createScheduledExecutor(eventMeshTCPConfiguration.getEventMeshTcpGlobalScheduler(),
                new EventMeshThreadFactory("eventMesh-tcp-scheduler", true));

        taskHandleExecutorService = ThreadPoolFactory.createThreadPoolExecutor(
                eventMeshTCPConfiguration.getEventMeshTcpTaskHandleExecutorPoolSize(),
                eventMeshTCPConfiguration.getEventMeshTcpTaskHandleExecutorPoolSize(),
                new LinkedBlockingQueue<>(10_000),
                new EventMeshThreadFactory("eventMesh-tcp-task-handle", true));

        broadcastMsgDownstreamExecutorService = ThreadPoolFactory.createThreadPoolExecutor(
                eventMeshTCPConfiguration.getEventMeshTcpMsgDownStreamExecutorPoolSize(),
                eventMeshTCPConfiguration.getEventMeshTcpMsgDownStreamExecutorPoolSize(),
                new LinkedBlockingQueue<>(10_000),
                new EventMeshThreadFactory("eventMesh-tcp-msg-downstream", true));
    }

    private void shutdownThreadPool() {
        scheduler.shutdown();
        taskHandleExecutorService.shutdown();
        broadcastMsgDownstreamExecutorService.shutdown();
    }

    public ClientSessionGroupMapping getClientSessionGroupMapping() {
        return clientSessionGroupMapping;
    }

    public TcpRetryer getEventMeshTcpRetryer() {
        return tcpRetryer;
    }


    public EventMeshServer getEventMeshServer() {
        return eventMeshServer;
    }

    public EventMeshTCPConfiguration getEventMeshTCPConfiguration() {
        return eventMeshTCPConfiguration;
    }

    public Registry getRegistry() {
        return registry;
    }

    public EventMeshRebalanceService getEventMeshRebalanceService() {
        return eventMeshRebalanceService;
    }

    public AdminWebHookConfigOperationManager getAdminWebHookConfigOperationManage() {
        return adminWebHookConfigOperationManage;
    }

    public void setAdminWebHookConfigOperationManage(AdminWebHookConfigOperationManager adminWebHookConfigOperationManage) {
        this.adminWebHookConfigOperationManage = adminWebHookConfigOperationManage;
    }

    public Acl getAcl() {
        return acl;
    }

    public void setClientSessionGroupMapping(final ClientSessionGroupMapping clientSessionGroupMapping) {
        this.clientSessionGroupMapping = clientSessionGroupMapping;
    }

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public void setScheduler(final ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    public ExecutorService getTaskHandleExecutorService() {
        return taskHandleExecutorService;
    }

    public ExecutorService getBroadcastMsgDownstreamExecutorService() {
        return broadcastMsgDownstreamExecutorService;
    }

    public void setTaskHandleExecutorService(final ThreadPoolExecutor taskHandleExecutorService) {
        this.taskHandleExecutorService = taskHandleExecutorService;
    }

    public RateLimiter getMsgRateLimiter() {
        return msgRateLimiter;
    }

    public void setMsgRateLimiter(final RateLimiter msgRateLimiter) {
        this.msgRateLimiter = msgRateLimiter;
    }

    public ProducerManager getProducerManager() {
        return producerManager;
    }

}
