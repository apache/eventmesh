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

package org.apache.eventmesh.client.tcp.common;

import java.util.Objects;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.protocol.tcp.codec.Codec;
import org.apache.eventmesh.common.utils.LogUtils;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class TcpClient implements Closeable {

    protected static transient int CLIENTNO = 0;

    static {
        try {
            CLIENTNO = SecureRandom.getInstanceStrong().nextInt(1000);
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate a random number!", e);
        }
    }

    protected final transient ConcurrentHashMap<Object, RequestContext> contexts = new ConcurrentHashMap<>();

    protected final transient String host;
    protected final transient int port;
    protected final transient UserAgent userAgent;

    private final transient Bootstrap bootstrap = new Bootstrap();

    private final transient EventLoopGroup workers = new NioEventLoopGroup();

    private transient Channel channel;

    private transient ScheduledFuture<?> heartTask;

    protected static final ScheduledExecutorService scheduler = ThreadPoolFactory.createScheduledExecutor(Runtime.getRuntime().availableProcessors(),
        new EventMeshThreadFactory("TCPClientScheduler", true));

    public TcpClient(EventMeshTCPClientConfig eventMeshTcpClientConfig) {
        Preconditions.checkNotNull(eventMeshTcpClientConfig, "EventMeshTcpClientConfig cannot be null");
        Preconditions.checkNotNull(eventMeshTcpClientConfig.getHost(), "Host cannot be null");
        Preconditions.checkState(eventMeshTcpClientConfig.getPort() > 0, "port is not validated");
        this.host = eventMeshTcpClientConfig.getHost();
        this.port = eventMeshTcpClientConfig.getPort();
        this.userAgent = eventMeshTcpClientConfig.getUserAgent();
    }

    protected synchronized void open(SimpleChannelInboundHandler<Package> handler) throws Exception {
        bootstrap.group(workers);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1_000)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.SO_SNDBUF, 64 * 1024)
            .option(ChannelOption.SO_RCVBUF, 64 * 1024)
            .option(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator(1024, 8192, 65536))
            .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {

            @Override
            public void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new Codec())
                    .addLast(handler, newExceptionHandler());
            }
        });

        ChannelFuture f = bootstrap.connect(host, port).sync();
        InetSocketAddress localAddress = (InetSocketAddress) f.channel().localAddress();
        channel = f.channel();
        LogUtils.info(log, "connected|local={}:{}|server={}", localAddress.getAddress().getHostAddress(),
            localAddress.getPort(), host + ":" + port);
    }

    @Override
    public void close() {
        try {
            channel.disconnect().sync();
            workers.shutdownGracefully();
            if (heartTask != null) {
                heartTask.cancel(false);
            }
            goodbye();
        } catch (Exception e) {
            Thread.currentThread().interrupt();

            LogUtils.warn(log, "close tcp client failed.|remote address={}", channel.remoteAddress(), e);
        }
    }

    protected void heartbeat() {
        if (heartTask == null) {
            synchronized (TcpClient.class) {
                heartTask = scheduler.scheduleAtFixedRate(() -> {
                    try {
                        if (!isActive()) {
                            reconnect();
                        }
                        Package msg = MessageUtils.heartBeat();
                        io(msg, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
                        LogUtils.debug(log, "heart beat start {}", msg);
                    } catch (Exception e) {
                        // ignore
                    }
                }, EventMeshCommon.HEARTBEAT, EventMeshCommon.HEARTBEAT, TimeUnit.MILLISECONDS);
            }
        }
    }

    protected synchronized void reconnect() throws Exception {
        ChannelFuture f = bootstrap.connect(host, port).sync();
        channel = f.channel();
    }

    protected boolean isActive() {
        return (channel != null) && (channel.isActive());
    }

    protected void send(Package msg) throws Exception {
        if (channel.isWritable()) {
            channel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    LogUtils.warn(log, "send msg failed", future.cause());
                }
            });
        } else {
            channel.writeAndFlush(msg).sync();
        }
    }

    protected Package io(Package msg, long timeout) throws Exception {
        Object key = RequestContext.key(msg);
        RequestContext context = RequestContext.context(key, msg);
        RequestContext previousContext = contexts.putIfAbsent(key, context);
        if (null != previousContext) {
            LogUtils.info(log, "duplicate key : {}", key);
        }
        send(msg);
        Supplier<Package> supplier = () -> {
            try {
                return context.getResponse(timeout);
            } catch (ExecutionException | InterruptedException | TimeoutException exception) {
                throw new RuntimeException(exception);
            }
        };
        return CompletableFuture.supplyAsync(supplier).get();
    }

    // todo: remove hello
    protected void hello() throws Exception {
        Package msg = MessageUtils.hello(userAgent);
        this.io(msg, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
    }

    // todo: remove goodbye
    protected void goodbye() throws Exception {
        Package msg = MessageUtils.goodbye();
        this.io(msg, EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
    }

    private ChannelDuplexHandler newExceptionHandler() {
        return new ChannelDuplexHandler() {

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                LogUtils.info(log, "exceptionCaught, close connection.|remote address={}",
                    ctx.channel().remoteAddress(), cause);
                ctx.close();
            }
        };
    }
}
