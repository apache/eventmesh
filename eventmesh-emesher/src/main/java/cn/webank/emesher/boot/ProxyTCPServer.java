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

package cn.webank.emesher.boot;

import cn.webank.eventmesh.common.protocol.tcp.codec.Codec;
import cn.webank.emesher.admin.controller.ClientManageController;
import cn.webank.emesher.configuration.AccessConfiguration;
import cn.webank.emesher.core.protocol.tcp.client.ProxyTcpConnectionHandler;
import cn.webank.emesher.core.protocol.tcp.client.ProxyTcpExceptionHandler;
import cn.webank.emesher.core.protocol.tcp.client.ProxyTcpMessageDispatcher;
import cn.webank.emesher.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import cn.webank.emesher.core.protocol.tcp.client.session.push.retry.ProxyTcpRetryer;
import cn.webank.eventmesh.common.ThreadPoolFactory;
import cn.webank.emesher.metrics.tcp.ProxyTcpMonitor;
import cn.webank.emesher.threads.ProxyThreadFactoryImpl;
import cn.webank.emesher.threads.ThreadPoolHelper;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

public class ProxyTCPServer extends AbstractRemotingServer {

    private ClientSessionGroupMapping clientSessionGroupMapping;

    private ProxyTcpRetryer proxyTcpRetryer;

    private ProxyTcpMonitor proxyTcpMonitor;

    private ClientManageController clientManageController;

    private ProxyServer proxyServer;

    private AccessConfiguration accessConfiguration;

    private GlobalTrafficShapingHandler globalTrafficShapingHandler;

    public static ScheduledExecutorService scheduler;

    public static ExecutorService traceLogExecutor;

    public static ScheduledExecutorService configCenterUpdateScheduler;

    public static ExecutorService taskHandleExecutorService;

    public ScheduledFuture<?> tcpRegisterTask;

    public RateLimiter rateLimiter;

    public ProxyTCPServer(ProxyServer proxyServer,
                          AccessConfiguration accessConfiguration) {
        super();
        this.proxyServer = proxyServer;
        this.accessConfiguration = accessConfiguration;
    }

    private void startServer() throws Exception {
        Runnable r = () -> {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, ioGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.SO_KEEPALIVE, false)
                    .option(ChannelOption.SO_TIMEOUT, 600000)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_LINGER, 0)
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
                                    .addLast(new ProxyTcpConnectionHandler(ProxyTCPServer.this))
                                    .addLast(workerGroup, new IdleStateHandler(accessConfiguration.proxyTcpIdleReadSeconds,
                                                    accessConfiguration.proxyTcpIdleWriteSeconds,
                                                    accessConfiguration.proxyTcpIdleAllSeconds),
                                            new ProxyTcpMessageDispatcher(ProxyTCPServer.this),
                                            new ProxyTcpExceptionHandler(ProxyTCPServer.this)
                                    );
                        }
                    });
            try {
                int port = accessConfiguration.proxyTcpServerPort;
                ChannelFuture f = bootstrap.bind(port).sync();
                logger.info("ProxyTCPServer[port={}] started.....", port);
                f.channel().closeFuture().sync();
            } catch (Exception e) {
                logger.error("ProxyTCPServer RemotingServer Start Err!", e);
                try {
                    shutdown();
                } catch (Exception e1) {
                    logger.error("ProxyTCPServer RemotingServer shutdown Err!", e);
                }
                return;
            }
        };

        Thread t = new Thread(r, "proxy-tcp-server");
        t.start();
    }

    public void init() throws Exception {
        logger.info("==================ProxyTCPServer Initialing==================");
         initThreadPool();

        rateLimiter = RateLimiter.create(accessConfiguration.proxyTcpMsgReqnumPerSecond);

        globalTrafficShapingHandler = newGTSHandler();

        clientManageController = new ClientManageController(this);

        clientSessionGroupMapping = new ClientSessionGroupMapping(this);
        clientSessionGroupMapping.init();

        proxyTcpRetryer = new ProxyTcpRetryer(this);
        proxyTcpRetryer.init();

        proxyTcpMonitor = new ProxyTcpMonitor(this);
        proxyTcpMonitor.init();

        logger.info("--------------------------ProxyTCPServer Inited");
    }

    public void start() throws Exception {
        startServer();

        clientSessionGroupMapping.start();

        proxyTcpRetryer.start();

        proxyTcpMonitor.start();

        clientManageController.start();

        logger.info("--------------------------ProxyTCPServer Started");
    }

    public void shutdown() throws Exception {
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

        proxyTcpRetryer.shutdown();

        proxyTcpMonitor.shutdown();

        shutdownThreadPool();
        logger.info("--------------------------ProxyTCPServer Shutdown");
    }

    private void initThreadPool() throws Exception {
        super.init("proxy-tcp");

        scheduler = ThreadPoolFactory.createScheduledExecutor(accessConfiguration.proxyTcpGlobalScheduler, new ProxyThreadFactoryImpl("proxy-tcp-scheduler", true));

        traceLogExecutor = ThreadPoolFactory.createThreadPoolExecutor(accessConfiguration.proxyTcpTraceLogExecutorPoolSize, accessConfiguration.proxyTcpTraceLogExecutorPoolSize, new LinkedBlockingQueue<Runnable>(10000), new ProxyThreadFactoryImpl("proxy-tcp-trace", true));

        configCenterUpdateScheduler = ThreadPoolFactory.createScheduledExecutor(accessConfiguration.proxyTcpCcUpdateExecutorPoolSize, new ProxyThreadFactoryImpl("proxy-tcp-cc-update",true));

        taskHandleExecutorService = ThreadPoolFactory.createThreadPoolExecutor(accessConfiguration.proxyTcpTaskHandleExecutorPoolSize, accessConfiguration.proxyTcpTaskHandleExecutorPoolSize, new LinkedBlockingQueue<Runnable>(10000), new ProxyThreadFactoryImpl("proxy-tcp-task-handle", true));;
    }

    private void shutdownThreadPool(){
        traceLogExecutor.shutdown();
        configCenterUpdateScheduler.shutdown();
        scheduler.shutdown();
        taskHandleExecutorService.shutdown();

        ThreadPoolHelper.shutdownNettyClientSelector();
        ThreadPoolHelper.shutdownNettyClientWorkerThread();
        ThreadPoolHelper.shutdownMQClientInstanceExecutorService();
        ThreadPoolHelper.shutdownSharedScheduledExecutorService();
        ThreadPoolHelper.shutdownRebalanceImplExecutorService();
        ThreadPoolHelper.shutdownRebalanceServiceExecutorService();
        ThreadPoolHelper.shutdownPullMessageServiceExecutorService();
        ThreadPoolHelper.shutdownPullMessageRetryServiceExecutorService();
        ThreadPoolHelper.shutdownExecutorService();
        ThreadPoolHelper.shutdownConsumeMessageExecutor();
        ThreadPoolHelper.shutdownProducerCheckExecutorService();
    }

    private GlobalTrafficShapingHandler newGTSHandler() {
        GlobalTrafficShapingHandler handler = new GlobalTrafficShapingHandler(scheduler, 0, accessConfiguration.getGtc().getReadLimit()) {
            @Override
            protected long calculateSize(Object msg) {
                return 1;
            }
        };
        handler.setMaxTimeWait(1000);
        return handler;
    }

    private ChannelTrafficShapingHandler newCTSHandler() {
        ChannelTrafficShapingHandler handler = new ChannelTrafficShapingHandler(0, accessConfiguration.getCtc().getReadLimit()) {
            @Override
            protected long calculateSize(Object msg) {
                return 1;
            }
        };
        handler.setMaxTimeWait(3000);
        return handler;
    }

    public ClientSessionGroupMapping getClientSessionGroupMapping() {
        return clientSessionGroupMapping;
    }

    public ProxyTcpRetryer getProxyTcpRetryer() {
        return proxyTcpRetryer;
    }

    public ProxyTcpMonitor getProxyTcpMonitor() {
        return proxyTcpMonitor;
    }

    public ProxyServer getProxyServer() {
        return proxyServer;
    }

    public AccessConfiguration getAccessConfiguration() {
        return accessConfiguration;
    }
}
