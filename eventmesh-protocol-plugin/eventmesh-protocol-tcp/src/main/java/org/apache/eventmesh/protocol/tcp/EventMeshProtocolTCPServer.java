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

package org.apache.eventmesh.protocol.tcp;

import com.google.common.util.concurrent.RateLimiter;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import org.apache.eventmesh.api.registry.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.EventMeshException;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.protocol.tcp.codec.Codec;
import org.apache.eventmesh.common.utils.IPUtil;
import org.apache.eventmesh.protocol.api.exception.EventMeshProtocolException;
import org.apache.eventmesh.protocol.api.model.ServiceState;
import org.apache.eventmesh.protocol.tcp.acl.Acl;
import org.apache.eventmesh.protocol.tcp.admin.controller.ClientManageController;
import org.apache.eventmesh.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.protocol.tcp.config.EventMeshTCPConfiguration;
import org.apache.eventmesh.protocol.tcp.config.TcpProtocolConstants;
import org.apache.eventmesh.protocol.tcp.handler.EventMeshTcpConnectionHandler;
import org.apache.eventmesh.protocol.tcp.handler.EventMeshTcpExceptionHandler;
import org.apache.eventmesh.protocol.tcp.handler.EventMeshTcpMessageDispatcher;
import org.apache.eventmesh.protocol.tcp.metrics.EventMeshTcpMonitor;
import org.apache.eventmesh.protocol.tcp.rebalance.EventMeshRebalanceService;
import org.apache.eventmesh.protocol.tcp.rebalance.EventmeshRebalanceImpl;
import org.apache.eventmesh.protocol.tcp.registry.Registry;
import org.apache.eventmesh.protocol.tcp.retry.EventMeshTcpRetryer;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class EventMeshProtocolTCPServer extends AbstractEventMeshProtocolTCPServer {

    private ClientSessionGroupMapping clientSessionGroupMapping;

    private EventMeshTcpRetryer eventMeshTcpRetryer;

    private EventMeshTcpMonitor eventMeshTcpMonitor;

    private ClientManageController clientManageController;

    private GlobalTrafficShapingHandler globalTrafficShapingHandler;

    private EventMeshRebalanceService eventMeshRebalanceService;

    private ScheduledFuture<?> tcpRegisterTask;

    private Registry registry;

    private Acl acl;

    private RateLimiter rateLimiter;

    private ServiceState serviceState;

    public EventMeshProtocolTCPServer() {
        super(EventMeshTCPConfiguration.eventMeshTcpServerPort);
    }

    public void init() throws EventMeshProtocolException {
        super.init();
        try {
            logger.info("==================EventMeshTCPServer Initialing==================");

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
                registry = new Registry();
                registry.init(CommonConfiguration.eventMeshRegistryPluginType);
                eventMeshRebalanceService = new EventMeshRebalanceService(this, new EventmeshRebalanceImpl(this));
                eventMeshRebalanceService.init();
            }
            if (CommonConfiguration.eventMeshServerSecurityEnable) {
                acl = new Acl();
                acl.init(CommonConfiguration.eventMeshSecurityPluginType);
            }

            serviceState = ServiceState.INITED;
            logger.info("--------------------------EventMeshTCPServer Inited");
        } catch (Exception ex) {
            throw new EventMeshProtocolException(ex);
        }
    }

    @Override
    public void start() throws EventMeshProtocolException {
        super.start();
        try {
            startServer();

            clientSessionGroupMapping.start();

            eventMeshTcpRetryer.start();

            eventMeshTcpMonitor.start();

            clientManageController.start();

            if (CommonConfiguration.eventMeshServerRegistryEnable) {
                registry.start();
                eventMeshRebalanceService.start();
                selfRegisterToRegistry();
            }
            if (CommonConfiguration.eventMeshServerSecurityEnable) {
                acl.start();
            }

            serviceState = ServiceState.RUNNING;
            logger.info("--------------------------EventMeshTCPServer Started");
        } catch (Exception ex) {
            serviceState = ServiceState.STOPED;
            throw new EventMeshProtocolException(ex);
        }
    }

    @Override
    public void shutdown() throws EventMeshProtocolException {
        super.shutdown();
        try {
            serviceState = ServiceState.STOPING;
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
                logger.info("shutdown bossGroup, no client is allowed to connect access server");
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
            if (CommonConfiguration.eventMeshServerSecurityEnable) {
                acl.shutdown();
            }

            eventMeshTcpRetryer.shutdown();

            eventMeshTcpMonitor.shutdown();

            if (CommonConfiguration.eventMeshServerRegistryEnable) {
                registry.shutdown();
            }

            serviceState = ServiceState.STOPING;
            logger.info("--------------------------EventMeshTCPServer Shutdown");
        } catch (Exception ex) {
            throw new EventMeshProtocolException(ex);
        }
    }

    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    private void startServer() {
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
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new Codec.Encoder())
                                    .addLast(new Codec.Decoder())
                                    .addLast("global-traffic-shaping", globalTrafficShapingHandler)
                                    .addLast("channel-traffic-shaping", newCTSHandler())
                                    .addLast(new EventMeshTcpConnectionHandler(EventMeshProtocolTCPServer.this))
                                    .addLast(workerGroup,
                                            new IdleStateHandler(
                                                    EventMeshTCPConfiguration.eventMeshTcpIdleReadSeconds,
                                                    EventMeshTCPConfiguration.eventMeshTcpIdleWriteSeconds,
                                                    EventMeshTCPConfiguration.eventMeshTcpIdleAllSeconds
                                            ),
                                            new EventMeshTcpMessageDispatcher(EventMeshProtocolTCPServer.this),
                                            new EventMeshTcpExceptionHandler(EventMeshProtocolTCPServer.this)
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
            }
        };

        Thread t = new Thread(r, "eventMesh-tcp-server");
        t.start();
    }

    private GlobalTrafficShapingHandler newGTSHandler() {
        GlobalTrafficShapingHandler handler = new GlobalTrafficShapingHandler(getScheduler(), 0, EventMeshTCPConfiguration.gtc.getReadLimit()) {
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

        tcpRegisterTask = getScheduler().scheduleAtFixedRate(() -> {
            try {
                boolean heartbeatResult = registerToRegistry();
                if (!heartbeatResult) {
                    logger.error("selfRegisterToRegistry fail");
                }
            } catch (Exception ex) {
                logger.error("selfRegisterToRegistry fail", ex);
            }
        }, CommonConfiguration.eventMeshRegisterIntervalInMills, CommonConfiguration.eventMeshRegisterIntervalInMills, TimeUnit.MILLISECONDS);
    }

    public boolean registerToRegistry() {
        boolean registerResult = false;
        try{
            String endPoints = IPUtil.getLocalAddress()
                    + TcpProtocolConstants.IP_PORT_SEPARATOR + EventMeshTCPConfiguration.eventMeshTcpServerPort;
            EventMeshRegisterInfo self = new EventMeshRegisterInfo();
            self.setEventMeshClusterName(CommonConfiguration.eventMeshCluster);
            self.setEventMeshName(CommonConfiguration.eventMeshName);
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
        eventMeshUnRegisterInfo.setEventMeshClusterName(CommonConfiguration.eventMeshCluster);
        eventMeshUnRegisterInfo.setEventMeshName(CommonConfiguration.eventMeshName);
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

    public ServiceState getServiceState() {
        return serviceState;
    }

    public EventMeshRebalanceService getEventMeshRebalanceService() {
        return eventMeshRebalanceService;
    }

    public Registry getRegistry() {
        return registry;
    }
}
