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

package org.apache.eventmesh.runtime.http;

import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.push.ConnectionPushPump;
import org.apache.eventmesh.runtime.push.WsConnection;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.ssl.SslHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket push transport (§7.2 / §15.6 — default main push transport). A standalone netty server
 * (sibling to {@link UniHttpServer}, because the JDK {@code com.sun.net.httpserver} does not support
 * the WebSocket upgrade). Serves {@code ws://host:port/events/stream?clientId=...}: after the
 * handshake each connection owns a {@link WsConnection} and a {@link ConnectionPushPump} that drains
 * the subscriber's {@link org.apache.eventmesh.runtime.push.PushService} buffer onto it.
 *
 * <p>Inbound text frames are control messages: {@code {"type":"ack","deliveryId":"..."}} advances the
 * reliability layer; {@code {"type":"unsubscribe"}} is best-effort. Outbound push uses the same
 * buffered + ACK-tracked contract as long-polling and SSE, so retry/DLQ (§13.3) is shared.</p>
 */
@Slf4j
public class UniWsServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String WS_PATH = "/events/stream";
    private static final long PUMP_INTERVAL_MS = 50L;

    private final UniIngressService ingress;
    private SSLContext sslContext;
    private EventLoopGroup boss;
    private EventLoopGroup worker;
    private Channel serverChannel;

    public UniWsServer(UniIngressService ingress) {
        this.ingress = ingress;
    }

    /** Enable TLS (wss://) on the WebSocket port. */
    public UniWsServer withTls(SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    /**
     * Bind to {@code port} (0 = auto-select) and start serving WS handshakes.
     *
     * @return the actual bound port
     */
    public int start(int port) throws InterruptedException {
        // Daemon netty threads so a JVM that shuts down the server (tests / clean exit) isn't held
        // alive by the event-loop groups — the runtime's stop() still shuts them down gracefully.
        boss = new NioEventLoopGroup(1, new io.netty.util.concurrent.DefaultThreadFactory("uni-ws-boss", true));
        worker = new NioEventLoopGroup(new io.netty.util.concurrent.DefaultThreadFactory("uni-ws-worker", true));
        ServerBootstrap b = new ServerBootstrap();
        b.group(boss, worker)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline p = ch.pipeline();
                    if (sslContext != null) {
                        SSLEngine engine = sslContext.createSSLEngine();
                        engine.setUseClientMode(false);
                        engine.setNeedClientAuth(false);
                        p.addLast(new SslHandler(engine));
                    }
                    p.addLast(new HttpServerCodec());
                    p.addLast(new HttpObjectAggregator(65536));
                    p.addLast(new WebSocketServerProtocolHandler(
                        io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig.newBuilder()
                            .websocketPath(WS_PATH)
                            .checkStartsWith(true)
                            .build()));
                    p.addLast(new WsFrameHandler());
                }
            });
        serverChannel = b.bind(port).sync().channel();
        int bound = ((InetSocketAddress) serverChannel.localAddress()).getPort();
        log.info("uni WebSocket server started on port {} ({})", bound, sslContext != null ? "wss" : "ws");
        return bound;
    }

    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (boss != null) {
            boss.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
        if (worker != null) {
            worker.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
    }

    /**
     * Per-channel WS handler. Netty creates one instance per connection (handler is not sharable),
     * so the {@code clientId}/{@code connection}/{@code pump} fields are naturally per-connection.
     */
    private final class WsFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

        private String clientId;
        private WsConnection connection;
        private ConnectionPushPump pump;
        private io.netty.util.concurrent.ScheduledFuture<?> pumpTask;

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                WebSocketServerProtocolHandler.HandshakeComplete hs = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
                clientId = parseClientId(hs.requestUri());
                if (clientId == null) {
                    log.warn("ws handshake without clientId ({}), closing", hs.requestUri());
                    ctx.close();
                    return;
                }
                ingress.getPushService().register(clientId);
                connection = new WsConnection(ctx.channel());
                pump = new ConnectionPushPump(ingress.getPushService(), clientId, connection);
                pumpTask = ctx.channel().eventLoop().scheduleAtFixedRate(() -> {
                    try {
                        pump.pumpOnce(100);
                    } catch (Throwable t) {
                        log.debug("ws pump error for {}: {}", clientId, t.toString());
                    }
                }, PUMP_INTERVAL_MS, PUMP_INTERVAL_MS, TimeUnit.MILLISECONDS);
                log.info("ws subscriber connected: clientId={}", clientId);
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
            if (!(frame instanceof TextWebSocketFrame) || clientId == null) {
                return;
            }
            handleControl(((TextWebSocketFrame) frame).text());
        }

        private void handleControl(String text) {
            try {
                JsonNode node = MAPPER.readTree(text);
                String type = node.has("type") ? node.get("type").asText() : null;
                if ("ack".equals(type) && node.has("deliveryId")) {
                    ingress.ack(node.get("deliveryId").asText());
                } else if ("unsubscribe".equals(type)) {
                    log.debug("ws unsubscribe control frame from {}", clientId);
                }
            } catch (Exception e) {
                log.debug("ws control frame parse error from {}: {}", clientId, e.toString());
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (pumpTask != null) {
                pumpTask.cancel(false);
            }
            if (connection != null) {
                connection.close();
            }
            if (clientId != null) {
                log.info("ws subscriber disconnected: clientId={}", clientId);
            }
        }

        private String parseClientId(String requestUri) {
            int q = requestUri.indexOf('?');
            if (q < 0) {
                return null;
            }
            for (String pair : requestUri.substring(q + 1).split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0 && pair.substring(0, eq).equals("clientId")) {
                    return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                }
            }
            return null;
        }
    }
}
