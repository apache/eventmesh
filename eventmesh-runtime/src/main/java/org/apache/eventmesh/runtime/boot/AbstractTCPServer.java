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

import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.OPStatus;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.protocol.tcp.codec.Codec;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcp2Client;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.SessionState;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.task.GoodbyeTask;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.task.HeartBeatTask;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.task.HelloTask;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.task.ListenTask;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.task.MessageAckTask;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.task.MessageTransferTask;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.task.RecommendTask;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.task.SubscribeTask;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.task.UnSubscribeTask;
import org.apache.eventmesh.runtime.metrics.tcp.EventMeshTcpMonitor;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.apache.eventmesh.runtime.util.TraceUtils;
import org.apache.eventmesh.trace.api.common.EventMeshTraceConstants;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.opentelemetry.api.trace.Span;

import lombok.extern.slf4j.Slf4j;


@Slf4j
public class AbstractTCPServer extends AbstractRemotingServer {
    private EventMeshTCPServer eventMeshTCPServer;

    private final EventMeshTCPConfiguration eventMeshTCPConfiguration;
    private ClientSessionGroupMapping clientSessionGroupMapping;

    private EventMeshTcpMonitor eventMeshTcpMonitor;

    private transient GlobalTrafficShapingHandler globalTrafficShapingHandler;
    private TcpConnectionHandler tcpConnectionHandler;
    private TcpDispatcher tcpDispatcher;

    private final transient AtomicBoolean started = new AtomicBoolean(false);


    public AbstractTCPServer(EventMeshTCPConfiguration eventMeshTCPConfiguration) {
        this.eventMeshTCPConfiguration = eventMeshTCPConfiguration;
    }

    private void initSharableHandlers() {
        tcpConnectionHandler = new TcpConnectionHandler();
        tcpDispatcher = new TcpDispatcher();
    }


    @Override
    public void start() throws Exception {
        initSharableHandlers();

        Runnable runnable = () -> {
            final ServerBootstrap bootstrap = new ServerBootstrap();

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
                    .childHandler(new TcpServerInitializer());

            try {
                int port = eventMeshTCPConfiguration.getEventMeshTcpServerPort();
                ChannelFuture f = bootstrap.bind(port).sync();
                log.info("EventMeshTCPServer[port={}] started.....", port);
                f.channel().closeFuture().sync();
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

        started.compareAndSet(false, true);

    }


    @Override
    public void shutdown() throws Exception {
        super.shutdown();
        globalTrafficShapingHandler.release();
        started.compareAndSet(true, false);
    }

    public void setEventMeshTCPServer(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }


    private class TcpServerInitializer extends ChannelInitializer<SocketChannel> {

        @Override
        protected void initChannel(SocketChannel ch) {
            globalTrafficShapingHandler = newGTSHandler(eventMeshTCPServer.getScheduler(), eventMeshTCPConfiguration.getCtc().getReadLimit());
            ch.pipeline()
                    .addLast(getWorkerGroup(), new Codec.Encoder())
                    .addLast(getWorkerGroup(), new Codec.Decoder())
                    .addLast(getWorkerGroup(), "global-traffic-shaping", globalTrafficShapingHandler)
                    .addLast(getWorkerGroup(), "channel-traffic-shaping", newCTSHandler(eventMeshTCPConfiguration.getCtc().getReadLimit()))
                    .addLast(getWorkerGroup(), tcpConnectionHandler)
                    .addLast(getWorkerGroup(),
                            new IdleStateHandler(
                                    eventMeshTCPConfiguration.getEventMeshTcpIdleReadSeconds(),
                                    eventMeshTCPConfiguration.getEventMeshTcpIdleWriteSeconds(),
                                    eventMeshTCPConfiguration.getEventMeshTcpIdleAllSeconds()),
                            new TcpDispatcher());
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

    }

    @Sharable
    private class TcpDispatcher extends SimpleChannelInboundHandler<Package> {

        private final Logger messageLogger = LoggerFactory.getLogger(EventMeshConstants.MESSAGE);

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Package pkg) throws Exception {
            long startTime = System.currentTimeMillis();
            validateMsg(pkg);

            eventMeshTcpMonitor.getTcpSummaryMetrics().getClient2eventMeshMsgNum().incrementAndGet();

            Command cmd = pkg.getHeader().getCmd();
            try {
                Runnable task;

                if (isNeedTrace(cmd)) {
                    pkg.getHeader().getProperties()
                            .put(EventMeshConstants.REQ_C2EVENTMESH_TIMESTAMP, startTime);
                    pkg.getHeader().getProperties().put(EventMeshConstants.REQ_SEND_EVENTMESH_IP,
                            eventMeshTCPConfiguration.getEventMeshServerIp());
                    Session session = clientSessionGroupMapping.getSession(ctx);

                    pkg.getHeader().getProperties().put(EventMeshConstants.REQ_SYS, session.getClient().getSubsystem());
                    pkg.getHeader().getProperties().put(EventMeshConstants.REQ_IP, session.getClient().getHost());
                    pkg.getHeader().getProperties().put(EventMeshConstants.REQ_IDC, session.getClient().getIdc());
                    pkg.getHeader().getProperties().put(EventMeshConstants.REQ_GROUP, session.getClient().getGroup());
                }

                // todo failover?
                if (Command.RECOMMEND_REQUEST == cmd) {
                    if (messageLogger.isInfoEnabled()) {
                        messageLogger.info("pkg|c2eventMesh|cmd={}|pkg={}", cmd, pkg);
                    }
                    task = new RecommendTask(pkg, ctx, startTime, eventMeshTCPServer);
                    eventMeshTCPServer.getTaskHandleExecutorService().submit(task);
                    return;
                }

                if (Command.HELLO_REQUEST == cmd) {
                    if (messageLogger.isInfoEnabled()) {
                        messageLogger.info("pkg|c2eventMesh|cmd={}|pkg={}", cmd, pkg);
                    }
                    task = new HelloTask(pkg, ctx, startTime, eventMeshTCPServer);
                    eventMeshTCPServer.getTaskHandleExecutorService().submit(task);
                    return;
                }

                if (eventMeshTCPServer.getClientSessionGroupMapping().getSession(ctx) == null) {
                    if (messageLogger.isInfoEnabled()) {
                        messageLogger.info("pkg|c2eventMesh|cmd={}|pkg={},no session is found", cmd, pkg);
                    }
                    throw new Exception("no session is found");
                }

                logMessageFlow(ctx, pkg, cmd);

                if (eventMeshTCPServer.getClientSessionGroupMapping().getSession(ctx)
                        .getSessionState() == SessionState.CLOSED) {
                    throw new Exception(
                            "this eventMesh tcp session will be closed, may be reboot or version change!");
                }

                dispatch(ctx, pkg, startTime, cmd);
            } catch (Exception e) {
                log.error("exception occurred while pkg|cmd={}|pkg={}", cmd, pkg, e);

                if (isNeedTrace(cmd)) {
                    Span span = TraceUtils.prepareServerSpan(pkg.getHeader().getProperties(),
                            EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_SERVER_SPAN, startTime,
                            TimeUnit.MILLISECONDS, false);
                    TraceUtils.finishSpanWithException(span, pkg.getHeader().getProperties(),
                            "exception occurred while dispatch pkg", e);
                }

                writeToClient(cmd, pkg, ctx, e);
            }
        }

        private boolean isNeedTrace(Command cmd) {
            if (eventMeshTCPConfiguration.isEventMeshServerTraceEnable()
                    && cmd != null && (Command.REQUEST_TO_SERVER == cmd
                    || Command.ASYNC_MESSAGE_TO_SERVER == cmd
                    || Command.BROADCAST_MESSAGE_TO_SERVER == cmd)) {
                return true;
            }
            return false;
        }

        private void writeToClient(Command cmd, Package pkg, ChannelHandlerContext ctx, Exception e) {
            try {
                Package res = new Package();
                res.setHeader(new Header(getReplyCommand(cmd), OPStatus.FAIL.getCode(), e.toString(),
                        pkg.getHeader().getSeq()));
                ctx.writeAndFlush(res);
            } catch (Exception ex) {
                log.warn("writeToClient failed", ex);
            }
        }

        private Command getReplyCommand(Command cmd) {
            switch (cmd) {
                case HELLO_REQUEST:
                    return Command.HELLO_RESPONSE;
                case RECOMMEND_REQUEST:
                    return Command.RECOMMEND_RESPONSE;
                case HEARTBEAT_REQUEST:
                    return Command.HEARTBEAT_RESPONSE;
                case SUBSCRIBE_REQUEST:
                    return Command.SUBSCRIBE_RESPONSE;
                case UNSUBSCRIBE_REQUEST:
                    return Command.UNSUBSCRIBE_RESPONSE;
                case LISTEN_REQUEST:
                    return Command.LISTEN_RESPONSE;
                case CLIENT_GOODBYE_REQUEST:
                    return Command.CLIENT_GOODBYE_RESPONSE;
                case REQUEST_TO_SERVER:
                    return Command.RESPONSE_TO_CLIENT;
                case ASYNC_MESSAGE_TO_SERVER:
                    return Command.ASYNC_MESSAGE_TO_SERVER_ACK;
                case BROADCAST_MESSAGE_TO_SERVER:
                    return Command.BROADCAST_MESSAGE_TO_SERVER_ACK;
                default:
                    return cmd;
            }
        }

        private void logMessageFlow(ChannelHandlerContext ctx, Package pkg, Command cmd) {
            if (!messageLogger.isInfoEnabled()) {
                return;
            }

            if (pkg.getBody() instanceof EventMeshMessage) {
                messageLogger.info("pkg|c2eventMesh|cmd={}|Msg={}|user={}", cmd,
                        EventMeshUtil.printMqMessage((EventMeshMessage) pkg.getBody()),
                        clientSessionGroupMapping.getSession(ctx).getClient());
            } else {
                messageLogger.info("pkg|c2eventMesh|cmd={}|pkg={}|user={}", cmd, pkg,
                        clientSessionGroupMapping.getSession(ctx).getClient());
            }
        }

        private void validateMsg(Package pkg) throws Exception {
            if (pkg == null) {
                throw new Exception("the incoming message is empty.");
            }
            if (pkg.getHeader() == null) {
                log.error("the incoming message does not have a header|pkg={}", pkg);
                throw new Exception("the incoming message does not have a header.");
            }
            if (pkg.getHeader().getCmd() == null) {
                log.error("the incoming message does not have a command type|pkg={}", pkg);
                throw new Exception("the incoming message does not have a command type.");
            }
        }

        private void dispatch(ChannelHandlerContext ctx, Package pkg, long startTime, Command cmd)
                throws Exception {
            Runnable task;
            switch (cmd) {
                case HEARTBEAT_REQUEST:
                    task = new HeartBeatTask(pkg, ctx, startTime, eventMeshTCPServer);
                    break;
                case CLIENT_GOODBYE_REQUEST:
                case SERVER_GOODBYE_RESPONSE:
                    task = new GoodbyeTask(pkg, ctx, startTime, eventMeshTCPServer);
                    break;
                case SUBSCRIBE_REQUEST:
                    task = new SubscribeTask(pkg, ctx, startTime, eventMeshTCPServer);
                    break;
                case UNSUBSCRIBE_REQUEST:
                    task = new UnSubscribeTask(pkg, ctx, startTime, eventMeshTCPServer);
                    break;
                case LISTEN_REQUEST:
                    task = new ListenTask(pkg, ctx, startTime, eventMeshTCPServer);
                    break;
                case REQUEST_TO_SERVER:
                case RESPONSE_TO_SERVER:
                case ASYNC_MESSAGE_TO_SERVER:
                case BROADCAST_MESSAGE_TO_SERVER:
                    task = new MessageTransferTask(pkg, ctx, startTime, eventMeshTCPServer);
                    break;
                case RESPONSE_TO_CLIENT_ACK:
                case ASYNC_MESSAGE_TO_CLIENT_ACK:
                case BROADCAST_MESSAGE_TO_CLIENT_ACK:
                case REQUEST_TO_CLIENT_ACK:
                    task = new MessageAckTask(pkg, ctx, startTime, eventMeshTCPServer);
                    break;
                default:
                    throw new Exception("unknown cmd");
            }
            eventMeshTCPServer.getTaskHandleExecutorService().submit(task);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            Session session = clientSessionGroupMapping.getSession(ctx);
            UserAgent client = session == null ? null : session.getClient();
            log.error("exceptionCaught, push goodbye to client|user={},errMsg={}", client, cause.fillInStackTrace());
            String errMsg;
            if (cause.toString().contains("value not one of declared Enum instance names")) {
                errMsg = "Unknown Command type";
            } else {
                errMsg = cause.toString();
            }

            if (session != null) {
                EventMeshTcp2Client.goodBye2Client(eventMeshTCPServer, session, errMsg, OPStatus.FAIL.getCode(),
                        clientSessionGroupMapping);
            } else {
                EventMeshTcp2Client.goodBye2Client(ctx, errMsg, clientSessionGroupMapping, eventMeshTcpMonitor);
            }
        }

    }

    @Sharable
    public class TcpConnectionHandler extends ChannelDuplexHandler {

        private final AtomicInteger connections = new AtomicInteger(0);


        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
            log.info("client|tcp|channelRegistered|remoteAddress={}|msg={}", remoteAddress, "");
            super.channelRegistered(ctx);
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
            log.info("client|tcp|channelUnregistered|remoteAddress={}|msg={}", remoteAddress, "");
            super.channelUnregistered(ctx);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
            log.info("client|tcp|channelActive|remoteAddress={}|msg={}", remoteAddress, "");

            if (connections.incrementAndGet() > eventMeshTCPConfiguration.getEventMeshTcpClientMaxNum()) {
                log.warn("client|tcp|channelActive|remoteAddress={}|msg={}", remoteAddress, "too many client connect this eventMesh server");
                ctx.close();
                return;
            }

            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            connections.decrementAndGet();
            final String remoteAddress = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
            log.info("client|tcp|channelInactive|remoteAddress={}|msg={}", remoteAddress, "");
            clientSessionGroupMapping.closeSession(ctx);
            super.channelInactive(ctx);
        }


        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                if (event.state().equals(IdleState.ALL_IDLE)) {
                    final String remoteAddress = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
                    log.info("client|tcp|userEventTriggered|remoteAddress={}|msg={}", remoteAddress, evt.getClass().getName());
                    clientSessionGroupMapping.closeSession(ctx);
                }
            }

            ctx.fireUserEventTriggered(evt);
        }

        public int getConnectionCount() {
            return this.connections.get();
        }
    }

    public TcpConnectionHandler getTcpConnectionHandler() {
        return tcpConnectionHandler;
    }

    public EventMeshTcpMonitor getEventMeshTcpMonitor() {
        return eventMeshTcpMonitor;
    }

    public void setEventMeshTcpMonitor(EventMeshTcpMonitor eventMeshTcpMonitor) {
        this.eventMeshTcpMonitor = eventMeshTcpMonitor;
    }

    public ClientSessionGroupMapping getClientSessionGroupMapping() {
        return clientSessionGroupMapping;
    }

    public void setClientSessionGroupMapping(ClientSessionGroupMapping clientSessionGroupMapping) {
        this.clientSessionGroupMapping = clientSessionGroupMapping;
    }
}
