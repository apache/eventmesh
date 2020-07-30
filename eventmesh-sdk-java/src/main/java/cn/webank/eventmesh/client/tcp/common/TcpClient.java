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

package cn.webank.eventmesh.client.tcp.common;

import cn.webank.eventmesh.common.protocol.tcp.codec.Codec;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cn.webank.eventmesh.common.protocol.tcp.Package;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.*;

public abstract class TcpClient implements Closeable {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public int clientNo = (new Random()).nextInt(1000);

    protected ConcurrentHashMap<Object, RequestContext> contexts = new ConcurrentHashMap<>();

    private final String host;
    private final int port;

    private Bootstrap bootstrap = new Bootstrap();

    private EventLoopGroup workers = new NioEventLoopGroup();

    private Channel channel;

    protected static final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(4, new WemqAccessThreadFactoryImpl("TCPClientScheduler", true));

    private ScheduledFuture<?> task;

    public TcpClient(String host, int port){
        this.host = host;
        this.port = port;
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
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new Codec.Encoder(), new Codec.Decoder())
                        .addLast(handler, newExceptionHandler());
            }
        });

        ChannelFuture f = bootstrap.connect(host, port).sync();
        InetSocketAddress localAddress = (InetSocketAddress) f.channel().localAddress();
        channel = f.channel();
        logger.info("connected|local={}:{}|server={}", localAddress.getAddress().getHostAddress(), localAddress.getPort(), host + ":" + port);
    }

    @Override
    public void close() throws IOException {
        try {
            channel.disconnect().sync();
        } catch (InterruptedException e) {
            logger.warn("close tcp client failed.|remote address={}", channel.remoteAddress(), e);
        }
        workers.shutdownGracefully();
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
                    logger.warn("send msg failed", future.isSuccess(), future.cause());
                }
            });
        } else {
            channel.writeAndFlush(msg).sync();
        }
    }

    protected Package io(Package msg, long timeout) throws Exception {
        Object key = RequestContext._key(msg);
        CountDownLatch latch = new CountDownLatch(1);
        RequestContext c = RequestContext._context(key, msg, latch);
        if (!contexts.contains(c)) {
            contexts.put(key, c);
        } else {
            logger.info("duplicate key : {}", key);
        }
        send(msg);
        if (!c.getLatch().await(timeout, TimeUnit.MILLISECONDS))
            throw new TimeoutException("operation timeout, context.key=" + c.getKey());
        return c.getResponse();
    }

    private ChannelDuplexHandler newExceptionHandler() {
        return new ChannelDuplexHandler() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                logger.info("exceptionCaught, close connection.|remote address={}",ctx.channel().remoteAddress(),cause);
                ctx.close();
            }
        };
    }
}
