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

import com.google.common.util.concurrent.RateLimiter;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import org.apache.eventmesh.common.EventMeshException;
import org.apache.eventmesh.common.IPUtil;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.protocol.tcp.codec.Codec;
import org.apache.eventmesh.runtime.admin.controller.ClientManageController;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcpConnectionHandler;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcpExceptionHandler;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcpMessageDispatcher;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.push.retry.EventMeshTcpRetryer;
import org.apache.eventmesh.runtime.metrics.tcp.EventMeshTcpMonitor;
import org.apache.eventmesh.runtime.util.EventMeshThreadFactoryImpl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class EventMeshTCPServer extends AbstractRemotingServer {

    private ClientSessionGroupMapping clientSessionGroupMapping;

    private EventMeshTcpRetryer eventMeshTcpRetryer;

    private EventMeshTcpMonitor eventMeshTcpMonitor;

    private ClientManageController clientManageController;

    private EventMeshServer eventMeshServer;

    private EventMeshTCPConfiguration eventMeshTCPConfiguration;

    private GlobalTrafficShapingHandler globalTrafficShapingHandler;

    private ScheduledExecutorService scheduler;

    private ExecutorService taskHandleExecutorService;

    private ExecutorService broadcastMsgDownstreamExecutorService;

    private Registry registry;

    private EventMeshRebalanceService eventMeshRebalanceService;

    public void setClientSessionGroupMapping(ClientSessionGroupMapping clientSessionGroupMapping) {
        this.clientSessionGroupMapping = clientSessionGroupMapping;
    }

    public ClientManageController getClientManageController() {
        return clientManageController;
    }

    public void setClientManageController(ClientManageController clientManageController) {
        this.clientManageController = clientManageController;
    }

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    public ExecutorService getTaskHandleExecutorService() {
        return taskHandleExecutorService;
    }

    public ExecutorService getBroadcastMsgDownstreamExecutorService() {
        return broadcastMsgDownstreamExecutorService;
    }

    public void setTaskHandleExecutorService(ExecutorService taskHandleExecutorService) {
        this.taskHandleExecutorService = taskHandleExecutorService;
    }

    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    public void setRateLimiter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    private ScheduledFuture<?> tcpRegisterTask;

    private RateLimiter rateLimiter;

    public EventMeshTCPServer(EventMeshServer eventMeshServer, Registry registry) {
        super();
        this.eventMeshServer = eventMeshServer;
        this.eventMeshTCPConfiguration = eventMeshTCPConfiguration;
        this.registry = registry;
    }

    private void startServer() throws Exception {
        Runnable r = () -> {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, ioGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .childOption(ChannelOption.SO_KEEPALIVE, false)
                    .childOption(ChannelOption.SO_LINGER, 0)
                    .childOption(ChannelOption.SO_TIMEOUT, 600000)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_SNDBUF, 65535 * 4)
                    .childOption(ChannelOption.SO_RCVBUF, 65535 * 4)
                    .option(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator(2048, 4096, 65536))
                    .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .childHandler(new ChannelInitializer() {
                        @Override
                        public void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast(new Codec.Encoder())
                                    .addLast(new Codec.Decoder())
                                    .addLast("global-traffic-shaping", globalTrafficShapingHandler)
                                    .addLast("channel-traffic-shaping", newCTSHandler())
                                    .addLast(new EventMeshTcpConnectionHandler(EventMeshTCPServer.this))
                                    .addLast(workerGroup, new IdleStateHandler(EventMeshTCPConfiguration.eventMeshTcpIdleReadSeconds,
                                                    EventMeshTCPConfiguration.eventMeshTcpIdleWriteSeconds,
                                                    EventMeshTCPConfiguration.eventMeshTcpIdleAllSeconds),
                                            new EventMeshTcpMessageDispatcher(EventMeshTCPServer.this),
                                            new EventMeshTcpExceptionHandler(EventMeshTCPServer.this)
                                    );
                        }
                    });
            try {
                int port = EventMeshTCPConfiguration.eventMeshTcpServerPort;
                ChannelFuture f = bootstrap.bind(port).sync();
                logger.info("EventMeshTCPServer[port={}] started.....", port);
                f.channel().closeFuture().sync();
            } catch (Exception e) {
                logger.error("EventMeshTCPServer RemotingServer Start Err!", e);
                try {
                    shutdown();
                } catch (Exception e1) {
                    logger.error("EventMeshTCPServer RemotingServer shutdown Err!", e);
                }
                return;
            }
        };

        Thread t = new Thread(r, "eventMesh-tcp-server");
        t.start();
    }

    public void init() throws Exception {
        logger.info("==================EventMeshTCPServer Initialing==================");
        initThreadPool();

        rateLimiter = RateLimiter.create(EventMeshTCPConfiguration.eventMeshTcpMsgReqnumPerSecond);

        globalTrafficShapingHandler = newGTSHandler();

        clientManageController = new ClientManageController(this);

        clientSessionGroupMapping = new ClientSessionGroupMapping(this);
        clientSessionGroupMapping.init();

        eventMeshTcpRetryer = new EventMeshTcpRetryer(this);
        eventMeshTcpRetryer.init();

        eventMeshTcpMonitor = new EventMeshTcpMonitor(this);
        eventMeshTcpMonitor.init();

        if (CommonConfiguration.eventMeshServerRegistryEnable) {
            eventMeshRebalanceService = new EventMeshRebalanceService(this, new EventmeshRebalanceImpl(this));
            eventMeshRebalanceService.init();
        }

        logger.info("--------------------------EventMeshTCPServer Inited");
    }

    @Override
    public void start() throws Exception {
        startServer();

        clientSessionGroupMapping.start();

        eventMeshTcpRetryer.start();

        eventMeshTcpMonitor.start();

        clientManageController.start();

        if(CommonConfiguration.eventMeshServerRegistryEnable) {
            eventMeshRebalanceService.start();
            selfRegisterToRegistry();
        }

        logger.info("--------------------------EventMeshTCPServer Started");
    }

    @Override
    public void shutdown() throws Exception {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            logger.info("shutdown bossGroup, no client is allowed to connect access server");
        }

        if(CommonConfiguration.eventMeshServerRegistryEnable) {
            eventMeshRebalanceService.shutdown();

            selfUnRegisterToRegistry();
        }

        clientSessionGroupMapping.shutdown();
        try {
            Thread.sleep(40 * 1000);
        } catch (InterruptedException e) {
            logger.error("interruptedException occurred while sleeping", e);
        }

        globalTrafficShapingHandler.release();

        if (ioGroup != null) {
            ioGroup.shutdownGracefully();
            logger.info("shutdown ioGroup");
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            logger.info("shutdown workerGroup");
        }

        eventMeshTcpRetryer.shutdown();

        eventMeshTcpMonitor.shutdown();

        shutdownThreadPool();
        logger.info("--------------------------EventMeshTCPServer Shutdown");
    }

    private void initThreadPool() throws Exception {
        super.init("eventMesh-tcp");

        scheduler = ThreadPoolFactory.createScheduledExecutor(EventMeshTCPConfiguration.eventMeshTcpGlobalScheduler,
                new EventMeshThreadFactoryImpl("eventMesh-tcp-scheduler", true));

        taskHandleExecutorService = ThreadPoolFactory.createThreadPoolExecutor(EventMeshTCPConfiguration.eventMeshTcpTaskHandleExecutorPoolSize,
                EventMeshTCPConfiguration.eventMeshTcpTaskHandleExecutorPoolSize, new LinkedBlockingQueue<>(10000),
                new EventMeshThreadFactoryImpl("eventMesh-tcp-task-handle", true));

        broadcastMsgDownstreamExecutorService = ThreadPoolFactory.createThreadPoolExecutor(EventMeshTCPConfiguration.eventMeshTcpMsgDownStreamExecutorPoolSize,
                EventMeshTCPConfiguration.eventMeshTcpMsgDownStreamExecutorPoolSize, new LinkedBlockingQueue<>(10000),
                new EventMeshThreadFactoryImpl("eventMesh-tcp-msg-downstream", true));
    }

    private void shutdownThreadPool() {
        scheduler.shutdown();
        taskHandleExecutorService.shutdown();
    }

    private GlobalTrafficShapingHandler newGTSHandler() {
        GlobalTrafficShapingHandler handler = new GlobalTrafficShapingHandler(scheduler, 0, EventMeshTCPConfiguration.gtc.getReadLimit()) {
            @Override
            protected long calculateSize(Object msg) {
                return 1;
            }
        };
        handler.setMaxTimeWait(1000);
        return handler;
    }

    private ChannelTrafficShapingHandler newCTSHandler() {
        ChannelTrafficShapingHandler handler = new ChannelTrafficShapingHandler(0, EventMeshTCPConfiguration.ctc.getReadLimit()) {
            @Override
            protected long calculateSize(Object msg) {
                return 1;
            }
        };
        handler.setMaxTimeWait(3000);
        return handler;
    }

    private void selfRegisterToRegistry() throws Exception {

        boolean registerResult = registerToRegistry();
        if (!registerResult) {
            throw new EventMeshException("eventMesh fail to register");
        }

        tcpRegisterTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                boolean heartbeatResult = registerToRegistry();
                if (!heartbeatResult) {
                    logger.error("selfRegisterToRegistry fail");
                }
            } catch (Exception ex) {
                logger.error("selfRegisterToRegistry fail", ex);
            }
        }, eventMeshTCPConfiguration.eventMeshRegisterIntervalInMills, eventMeshTCPConfiguration.eventMeshRegisterIntervalInMills, TimeUnit.MILLISECONDS);
    }

    public boolean registerToRegistry() {
        boolean registerResult = false;
        try{
            String endPoints = IPUtil.getLocalAddress()
                    + EventMeshConstants.IP_PORT_SEPARATOR + eventMeshTCPConfiguration.eventMeshTcpServerPort;
            EventMeshRegisterInfo self = new EventMeshRegisterInfo();
            self.setEventMeshClusterName(eventMeshTCPConfiguration.eventMeshCluster);
            self.setEventMeshName(eventMeshTCPConfiguration.eventMeshName);
            self.setEndPoint(endPoints);
            self.setEventMeshInstanceNumMap(clientSessionGroupMapping.prepareProxyClientDistributionData());
            registerResult = registry.register(self);
        }catch (Exception e){
            logger.warn("eventMesh register to registry failed", e);
        }

        return registerResult;
    }

    private void selfUnRegisterToRegistry() throws Exception {
        EventMeshUnRegisterInfo eventMeshUnRegisterInfo = new EventMeshUnRegisterInfo();
        eventMeshUnRegisterInfo.setEventMeshClusterName(eventMeshTCPConfiguration.eventMeshCluster);
        eventMeshUnRegisterInfo.setEventMeshName(eventMeshTCPConfiguration.eventMeshName);
        boolean registerResult = registry.unRegister(eventMeshUnRegisterInfo);
        if (!registerResult) {
            throw new EventMeshException("eventMesh fail to unRegister");
        }

        //cancel task
        tcpRegisterTask.cancel(true);
    }

    public ClientSessionGroupMapping getClientSessionGroupMapping() {
        return clientSessionGroupMapping;
    }

    public EventMeshTcpRetryer getEventMeshTcpRetryer() {
        return eventMeshTcpRetryer;
    }

    public EventMeshTcpMonitor getEventMeshTcpMonitor() {
        return eventMeshTcpMonitor;
    }

    public EventMeshServer getEventMeshServer() {
        return eventMeshServer;
    }

}
