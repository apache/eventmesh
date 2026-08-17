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

package org.apache.eventmesh.runtime.transport.tcp;

import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.Subscription;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.protocol.tcp.codec.Codec;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.subscription.DistributionMode;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.AttributeKey;

import lombok.extern.slf4j.Slf4j;

/**
 * Real netty TCP server for legacy TCP clients, reusing the wire {@link Codec} (frame encode/decode)
 * but routing decoded {@link Package}s into the new {@link UniIngressService} via
 * {@link PackageRouter} — none of the legacy session/group/rebalance code is on the path.
 *
 * <p>Pipeline: {@link Codec.Encoder} (outbound) + {@link Codec.Decoder} (inbound) +
 * {@link FrameHandler}. Ingress frames become core calls (publish/subscribe/ack); egress pushes go
 * out on the client's netty {@link Channel} via {@link NettyTcpPushChannel}. Old TCP clients speak
 * the same wire protocol, so they connect unchanged.</p>
 */
@Slf4j
public class UniTcpServer {

    private final UniIngressService ingress;
    private final TcpAckRegistry ackRegistry;
    private final PackageRouter router;
    private final CloudEventToPackageBody bodyMapper;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private final ConcurrentHashMap<String, Channel> clientChannels = new ConcurrentHashMap<>();

    public UniTcpServer(UniIngressService ingress, TcpAckRegistry ackRegistry, PackageRouter router,
        CloudEventToPackageBody bodyMapper) {
        this.ingress = ingress;
        this.ackRegistry = ackRegistry;
        this.router = router;
        this.bodyMapper = bodyMapper;
    }

    /**
     * Bind to {@code port} (0 = auto-select).
     *
     * @return the actual bound port
     */
    public int start(int port) throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {

                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline()
                        .addLast(new Codec.Encoder())
                        .addLast(new Codec.Decoder())
                        .addLast(new FrameHandler(ingress, ackRegistry, router, clientChannels, bodyMapper));
                }
            });
        serverChannel = bootstrap.bind(port).sync().channel();
        int bound = ((java.net.InetSocketAddress) serverChannel.localAddress()).getPort();
        log.info("uni TCP server (legacy compat) started on port {}", bound);
        return bound;
    }

    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    /**
     * Decoded-frame handler. Protocol-management commands (HELLO/HEARTBEAT/LISTEN/SUBSCRIBE/
     * UNSUBSCRIBE/GOODBYE) are handled here directly because they need channel context (the
     * subscriber's clientId comes from the HELLO {@link UserAgent#getGroup()}, not the SUBSCRIBE
     * body). Message commands (ASYNC_MESSAGE_TO_SERVER / ASYNC_MESSAGE_TO_CLIENT_ACK) go through the
     * {@link PackageRouter} for CloudEvents translation. Static + package-private so it can be
     * exercised directly via netty {@code EmbeddedChannel} in tests.
     */
    static final class FrameHandler extends SimpleChannelInboundHandler<Package> {

        /** Channel attribute holding the clientId (= HELLO UserAgent.group) for a connected client. */
        private static final AttributeKey<String> CLIENT_ID = AttributeKey.valueOf("em-tcp-client-id");

        private final UniIngressService ingress;
        private final TcpAckRegistry ackRegistry;
        private final PackageRouter router;
        private final ConcurrentHashMap<String, Channel> clientChannels;
        private final CloudEventToPackageBody bodyMapper;

        FrameHandler(UniIngressService ingress, TcpAckRegistry ackRegistry, PackageRouter router,
            ConcurrentHashMap<String, Channel> clientChannels, CloudEventToPackageBody bodyMapper) {
            this.ingress = ingress;
            this.ackRegistry = ackRegistry;
            this.router = router;
            this.clientChannels = clientChannels;
            this.bodyMapper = bodyMapper;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Package pkg) {
            Command cmd = pkg.getHeader() != null ? pkg.getHeader().getCommand() : null;
            if (cmd == null) {
                return;
            }
            switch (cmd) {
                case HELLO_REQUEST:
                    // Legacy clientId is the HELLO UserAgent.group; stash it on the channel so
                    // SUBSCRIBE/UNSUBSCRIBE (whose body carries only topics) can address the client.
                    String clientId = clientIdFromHello(pkg);
                    if (clientId != null) {
                        ctx.channel().attr(CLIENT_ID).set(clientId);
                    }
                    respond(ctx, pkg, true);
                    return;
                case HEARTBEAT_REQUEST:
                case LISTEN_REQUEST:
                case CLIENT_GOODBYE_REQUEST:
                    // No-op protocol pings — ACK so the client's io() future completes.
                    respond(ctx, pkg, true);
                    return;
                case SUBSCRIBE_REQUEST:
                    handleSubscribe(ctx, pkg);
                    return;
                case UNSUBSCRIBE_REQUEST:
                    handleUnsubscribe(ctx, pkg);
                    return;
                default:
                    // Message commands → router (publish / push-ACK).
                    break;
            }
            TcpRequest req;
            try {
                req = router.route(pkg);
            } catch (RuntimeException e) {
                log.warn("tcp route failed: {}", e.toString());
                return;
            }
            if (req == null) {
                return;
            }
            switch (req.getKind()) {
                case PUBLISH:
                    ingress.publish(req.getTopic(), req.getEvent())
                        .whenComplete((v, ex) -> respond(ctx, pkg, ex == null));
                    break;
                case ACK:
                    ackRegistry.onClientAck(req.getDeliveryId());
                    break;
                default:
                    break;
            }
        }

        private void handleSubscribe(ChannelHandlerContext ctx, Package pkg) {
            String clientId = ctx.channel().attr(CLIENT_ID).get();
            if (clientId == null) {
                log.warn("SUBSCRIBE before HELLO on channel {}", ctx.channel().remoteAddress());
                respond(ctx, pkg, false);
                return;
            }
            if (!(pkg.getBody() instanceof Subscription)) {
                log.warn("SUBSCRIBE body not a Subscription: {}", pkg.getBody() == null ? "null" : pkg.getBody().getClass());
                respond(ctx, pkg, false);
                return;
            }
            Subscription sub = (Subscription) pkg.getBody();
            List<SubscriptionItem> items = sub.getTopicList();
            if (items == null || items.isEmpty()) {
                respond(ctx, pkg, false);
                return;
            }
            clientChannels.put(clientId, ctx.channel());
            // Register the egress push channel so the dispatcher can deliver to this TCP client.
            ingress.registerChannel(clientId, new NettyTcpPushChannel(ctx.channel(), ackRegistry));
            for (SubscriptionItem item : items) {
                ingress.subscribe(item.getTopic(), clientId, toDistributionMode(item.getMode()), null);
            }
            respond(ctx, pkg, true);
        }

        private void handleUnsubscribe(ChannelHandlerContext ctx, Package pkg) {
            String clientId = ctx.channel().attr(CLIENT_ID).get();
            if (clientId == null) {
                respond(ctx, pkg, false);
                return;
            }
            ingress.getSubscriptionManager().unsubscribeByClient(clientId);
            clientChannels.remove(clientId);
            respond(ctx, pkg, true);
        }

        private static String clientIdFromHello(Package pkg) {
            if (pkg.getBody() instanceof UserAgent) {
                return ((UserAgent) pkg.getBody()).getGroup();
            }
            return null;
        }

        private static DistributionMode toDistributionMode(SubscriptionMode mode) {
            return mode == SubscriptionMode.CLUSTERING
                ? DistributionMode.LOAD_BALANCE
                : DistributionMode.BROADCAST;
        }

        private void respond(ChannelHandlerContext ctx, Package request, boolean ok) {
            Command reqCmd = request.getHeader() != null ? request.getHeader().getCommand() : null;
            Command ackCmd = ackCommandFor(reqCmd);
            if (ackCmd == null) {
                return;
            }
            Package resp = new Package(new Header(ackCmd, ok ? 0 : 1, ok ? "ok" : "error",
                request.getHeader() != null ? request.getHeader().getSeq() : null));
            ctx.writeAndFlush(resp);
        }

        private static Command ackCommandFor(Command cmd) {
            if (cmd == null) {
                return null;
            }
            switch (cmd) {
                case ASYNC_MESSAGE_TO_SERVER:
                    return Command.ASYNC_MESSAGE_TO_SERVER_ACK;
                case BROADCAST_MESSAGE_TO_SERVER:
                    return Command.BROADCAST_MESSAGE_TO_SERVER_ACK;
                case SUBSCRIBE_REQUEST:
                    return Command.SUBSCRIBE_RESPONSE;
                case UNSUBSCRIBE_REQUEST:
                    return Command.UNSUBSCRIBE_RESPONSE;
                case HELLO_REQUEST:
                    return Command.HELLO_RESPONSE;
                case HEARTBEAT_REQUEST:
                    return Command.HEARTBEAT_RESPONSE;
                case LISTEN_REQUEST:
                    return Command.LISTEN_RESPONSE;
                case CLIENT_GOODBYE_REQUEST:
                    return Command.CLIENT_GOODBYE_RESPONSE;
                default:
                    return null;
            }
        }
    }
}
