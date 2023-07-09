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
import org.apache.eventmesh.common.protocol.tcp.codec.Codec;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.metrics.api.MetricsPluginFactory;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcpConnectionHandler;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcpExceptionHandler;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcpMessageDispatcher;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.rebalance.EventMeshRebalanceImpl;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.rebalance.EventMeshRebalanceService;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.retry.EventMeshTcpRetryer;
import org.apache.eventmesh.runtime.metrics.tcp.EventMeshTcpMetricsManager;
import org.apache.eventmesh.runtime.registry.Registry;
import org.apache.eventmesh.webhook.admin.AdminWebHookConfigOperationManager;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.assertj.core.util.Lists;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;


import com.google.common.util.concurrent.RateLimiter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshTCPServer extends AbstractRemotingServer {

    private ClientSessionGroupMapping clientSessionGroupMapping;

    private transient EventMeshTcpRetryer eventMeshTcpRetryer;

    private EventMeshTcpMetricsManager eventMeshTcpMetricsManager;

    private final transient EventMeshServer eventMeshServer;

    private final transient EventMeshTCPConfiguration eventMeshTCPConfiguration;

    private transient GlobalTrafficShapingHandler globalTrafficShapingHandler;

    private EventMeshTcpConnectionHandler eventMeshTcpConnectionHandler;

    private transient ScheduledExecutorService scheduler;

    private transient ExecutorService taskHandleExecutorService;

    private transient ExecutorService broadcastMsgDownstreamExecutorService;

    private final transient Registry registry;

    private final Acl acl;

    private transient EventMeshRebalanceService eventMeshRebalanceService;

    private transient AdminWebHookConfigOperationManager adminWebHookConfigOperationManage;

    private transient RateLimiter rateLimiter;

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

    public void setTaskHandleExecutorService(final ExecutorService taskHandleExecutorService) {
        this.taskHandleExecutorService = taskHandleExecutorService;
    }

    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    public void setRateLimiter(final RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }


    public EventMeshTCPServer(final EventMeshServer eventMeshServer, final EventMeshTCPConfiguration eventMeshTCPConfiguration) {
        super();
        this.eventMeshServer = eventMeshServer;
        this.eventMeshTCPConfiguration = eventMeshTCPConfiguration;
        this.registry = eventMeshServer.getRegistry();
        this.acl = eventMeshServer.getAcl();
    }

    private void startServer() {
        Runnable runnable = () -> {
            ServerBootstrap bootstrap = new ServerBootstrap();
            ChannelInitializer channelInitializer = new ChannelInitializer() {
                @Override
                public void initChannel(final Channel ch) throws Exception {
                    ch.pipeline()
                        .addLast(getWorkerGroup(), new Codec.Encoder())
                        .addLast(getWorkerGroup(), new Codec.Decoder())
                        .addLast(getWorkerGroup(), "global-traffic-shaping", globalTrafficShapingHandler)
                        .addLast(getWorkerGroup(), "channel-traffic-shaping", newCTSHandler(eventMeshTCPConfiguration.getCtc().getReadLimit()))
                        .addLast(getWorkerGroup(), eventMeshTcpConnectionHandler)
                        .addLast(getWorkerGroup(),
                            new IdleStateHandler(
                                eventMeshTCPConfiguration.getEventMeshTcpIdleReadSeconds(),
                                eventMeshTCPConfiguration.getEventMeshTcpIdleWriteSeconds(),
                                eventMeshTCPConfiguration.getEventMeshTcpIdleAllSeconds()),
                            new EventMeshTcpMessageDispatcher(EventMeshTCPServer.this),
                            new EventMeshTcpExceptionHandler(EventMeshTCPServer.this)
                        );
                }
            };

            bootstrap.group(this.getBossGroup(), this.getIoGroup())
                .channel(useEpoll() ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .childOption(ChannelOption.SO_KEEPALIVE, false)
                .childOption(ChannelOption.SO_LINGER, 0)
                .childOption(ChannelOption.SO_TIMEOUT, 600_000)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_SNDBUF, 65_535 * 4)
                .childOption(ChannelOption.SO_RCVBUF, 65_535 * 4)
                .option(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator(2_048, 4_096, 65_536))
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childHandler(channelInitializer);

            try {
                int port = eventMeshTCPConfiguration.getEventMeshTcpServerPort();
                ChannelFuture cf = bootstrap.bind(port).sync();
                log.info("EventMeshTCPServer[port={}] started.....", port);
                cf.channel().closeFuture().sync();
            } catch (Exception e) {
                log.error("EventMeshTCPServer RemotingServer Start Err!", e);
                try {
                    shutdown();
                } catch (Exception ex) {
                    log.error("EventMeshTCPServer RemotingServer shutdown Err!", ex);
                }
                System.exit(-1);
            }
        };

        Thread thread = new Thread(runnable, "eventMesh-tcp-server");
        thread.start();
    }

    public void init() throws Exception {
        if (log.isInfoEnabled()) {
            log.info("==================EventMeshTCPServer Initialing==================");
        }
        initThreadPool();

        rateLimiter = RateLimiter.create(eventMeshTCPConfiguration.getEventMeshTcpMsgReqnumPerSecond());

        globalTrafficShapingHandler = newGTSHandler(scheduler, eventMeshTCPConfiguration.getGtc().getReadLimit());

        eventMeshTcpConnectionHandler = new EventMeshTcpConnectionHandler(this);

        adminWebHookConfigOperationManage = new AdminWebHookConfigOperationManager();
        adminWebHookConfigOperationManage.init();

        clientSessionGroupMapping = new ClientSessionGroupMapping(this);
        clientSessionGroupMapping.init();

        eventMeshTcpRetryer = new EventMeshTcpRetryer(this);
        eventMeshTcpRetryer.init();

        // The MetricsRegistry is singleton, so we can use factory method to get.
        final List<MetricsRegistry> metricsRegistries = Lists.newArrayList();
        Optional.ofNullable(eventMeshTCPConfiguration.getEventMeshMetricsPluginType()).ifPresent(
            metricsPlugins -> metricsPlugins.forEach(pluginType -> metricsRegistries.add(MetricsPluginFactory.getMetricsRegistry(pluginType))));
        eventMeshTcpMetricsManager = new EventMeshTcpMetricsManager(this, metricsRegistries);

        if (eventMeshTCPConfiguration.isEventMeshServerRegistryEnable()) {
            eventMeshRebalanceService = new EventMeshRebalanceService(this,
                new EventMeshRebalanceImpl(this));
            eventMeshRebalanceService.init();
        }

        if (log.isInfoEnabled()) {
            log.info("--------------------------EventMeshTCPServer Inited");
        }
    }

    @Override
    public void start() throws Exception {
        startServer();

        clientSessionGroupMapping.start();

        eventMeshTcpRetryer.start();

        //eventMeshTcpMetricsManager.start();

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
        if (this.getBossGroup() != null) {
            this.getBossGroup().shutdownGracefully();
            log.info("shutdown bossGroup, no client is allowed to connect access server");
        }

        if (eventMeshTCPConfiguration.isEventMeshServerRegistryEnable()) {
            eventMeshRebalanceService.shutdown();

            this.unRegister();
        }

        clientSessionGroupMapping.shutdown();
        ThreadUtils.sleep(40, TimeUnit.SECONDS);
        globalTrafficShapingHandler.release();

        if (this.getIoGroup() != null) {
            this.getIoGroup().shutdownGracefully();
            log.info("shutdown ioGroup");
        }
        if (this.getWorkerGroup() != null) {
            this.getWorkerGroup().shutdownGracefully();
            log.info("shutdown workerGroup");
        }

        eventMeshTcpRetryer.shutdown();

        shutdownThreadPool();
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

    private void unRegister() throws Exception {
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

    private void initThreadPool() throws Exception {
        super.init("eventMesh-tcp");

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
    }

    private GlobalTrafficShapingHandler newGTSHandler(final ScheduledExecutorService executor, final long readLimit) {
        GlobalTrafficShapingHandler handler = new GlobalTrafficShapingHandler(executor, 0, readLimit) {
            @Override
            protected long calculateSize(final Object msg) {
                return 1;
            }
        };
        handler.setMaxTimeWait(1_000);
        return handler;
    }

    private ChannelTrafficShapingHandler newCTSHandler(final long readLimit) {
        ChannelTrafficShapingHandler handler = new ChannelTrafficShapingHandler(0, readLimit) {
            @Override
            protected long calculateSize(final Object msg) {
                return 1;
            }
        };
        handler.setMaxTimeWait(3_000);
        return handler;
    }

    public ClientSessionGroupMapping getClientSessionGroupMapping() {
        return clientSessionGroupMapping;
    }

    public EventMeshTcpRetryer getEventMeshTcpRetryer() {
        return eventMeshTcpRetryer;
    }

    public EventMeshTcpMetricsManager getEventMeshTcpMetricsManager() {
        return eventMeshTcpMetricsManager;
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

    public EventMeshTcpConnectionHandler getEventMeshTcpConnectionHandler() {
        return eventMeshTcpConnectionHandler;
    }
}
