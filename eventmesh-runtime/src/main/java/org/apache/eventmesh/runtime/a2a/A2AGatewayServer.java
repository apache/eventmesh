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

package org.apache.eventmesh.runtime.a2a;

import org.apache.eventmesh.protocol.a2a.A2AProtocolConstants;
import org.apache.eventmesh.protocol.a2a.A2ATopicFactory;
import org.apache.eventmesh.protocol.a2a.AgentIdentity;
import org.apache.eventmesh.protocol.a2a.model.AgentCard;
import org.apache.eventmesh.protocol.a2a.model.AgentSkill;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import lombok.extern.slf4j.Slf4j;

/**
 * Standalone A2A Gateway Server.
 *
 * <p>Starts an embedded Netty HTTP server that exposes the A2A Gateway REST API.
 * Pre-registers a mock "weather-agent" that auto-responds to task requests.
 *
 * <p>Usage:
 * <pre>
 *   java org.apache.eventmesh.runtime.a2a.A2AGatewayServer [port]
 * </pre>
 *
 * <p>Then run the client demo (A2AGatewayDemo) to submit tasks via HTTP.
 */
@Slf4j
public class A2AGatewayServer {

    private static final String NAMESPACE = "global";
    private static final String GATEWAY_ID = "demo-gateway";
    private static final int DEFAULT_PORT = 10105;

    private final int port;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture serverChannel;

    private InMemoryA2AMessageTransport transport;
    private TaskRegistry taskRegistry;
    private A2APublishSubscribeService a2aService;
    private A2AGatewayService gatewayService;
    private A2AGatewayHttpHandler gatewayHandler;
    private A2ACardHttpHandler cardHandler;

    public A2AGatewayServer(int port) {
        this.port = port;
    }

    public void start() throws Exception {
        // 1. Initialize components
        transport = new InMemoryA2AMessageTransport();
        taskRegistry = new TaskRegistry();
        a2aService = new A2APublishSubscribeService(null);
        a2aService.init();
        a2aService.start();

        gatewayService = new A2AGatewayService(NAMESPACE, GATEWAY_ID, transport, taskRegistry, a2aService);
        gatewayService.start();

        gatewayHandler = new A2AGatewayHttpHandler(gatewayService, a2aService);
        cardHandler = new A2ACardHttpHandler(a2aService);

        // 2. Pre-register a mock weather agent
        registerMockWeatherAgent();

        // 3. Start Netty HTTP server
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline()
                        .addLast(new HttpRequestDecoder())
                        .addLast(new HttpObjectAggregator(65536))
                        .addLast(new HttpResponseEncoder())
                        .addLast(new A2AGatewayRequestHandler(gatewayHandler, cardHandler));
                }
            })
            .option(ChannelOption.SO_BACKLOG, 128)
            .childOption(ChannelOption.SO_KEEPALIVE, true);

        serverChannel = bootstrap.bind(port).sync();
        log.info("=== A2A Gateway Server started on port {} ===", port);
        log.info("Gateway ID: {}, Namespace: {}", GATEWAY_ID, NAMESPACE);
        log.info("Endpoints:");
        log.info("  POST   /a2a/tasks              - submit task (sync/async)");
        log.info("  GET    /a2a/tasks               - list tasks (?state=COMPLETED&limit=100&offset=0)");
        log.info("  GET    /a2a/tasks/{{taskId}}       - get task status");
        log.info("  DELETE /a2a/tasks/{{taskId}}       - cancel task");
        log.info("  GET    /a2a/tasks/{{taskId}}/wait  - wait for result (long-poll)");
        log.info("  GET    /a2a/tasks/{{taskId}}/stream - SSE stream of task status updates");
        log.info("  GET    /a2a/agents               - list registered agents");
        log.info("  POST   /a2a/heartbeat            - agent heartbeat");
        log.info("  GET    /a2a/health               - health check");
        log.info("  GET    /a2a/cards/list           - list agent cards");
        log.info("  POST   /a2a/cards/card/{{org}}/{{unit}}/{{agent}} - register card");
    }

    /**
     * Registers a mock weather agent that auto-responds to task requests.
     */
    private void registerMockWeatherAgent() throws Exception {
        AgentCard weatherCard = AgentCard.builder()
            .name("weather-agent")
            .description("A weather agent that can tell you the weather")
            .version("1.0.0")
            .supportedInterfaces(Arrays.asList(
                org.apache.eventmesh.protocol.a2a.model.AgentInterface.builder()
                    .url("http://localhost:" + port + "/a2a")
                    .protocolBinding("JSONRPC")
                    .protocolVersion("0.3")
                    .build()
            ))
            .capabilities(org.apache.eventmesh.protocol.a2a.model.AgentCapabilities.builder()
                .streaming(false)
                .pushNotifications(false)
                .build())
            .skills(Arrays.asList(
                AgentSkill.builder()
                    .id("get-weather")
                    .name("Get Weather")
                    .description("Get the current weather for a city")
                    .tags(Arrays.asList("weather", "test"))
                    .build()
            ))
            .defaultInputModes(Arrays.asList("text/plain"))
            .defaultOutputModes(Arrays.asList("text/plain"))
            .build();

        AgentIdentity identity = AgentIdentity.builder()
            .orgId("default").unitId("default").agentId("weather-agent").build();
        A2APublishSubscribeService.RegistrationResult regResult =
            a2aService.registerCard(identity, weatherCard);
        if (!regResult.isSuccess()) {
            log.warn("Failed to register weather-agent card: {}", regResult.getErrorMessage());
        }

        // Subscribe to agent request topic and auto-respond
        String requestTopic = A2ATopicFactory.agentRequestTopic(NAMESPACE, "weather-agent");
        transport.subscribe(requestTopic, (topic, event) -> {
            String taskId = event.getId();
            String message = event.getData() != null
                ? new String(event.getData().toBytes(), StandardCharsets.UTF_8) : "";

            log.info("[Weather Agent] Received request: taskId={}, message={}", taskId, message);

            // Simulate processing delay
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Publish response
            String gwId = extractGatewayId(event);
            String responseTopic = A2ATopicFactory.gatewayResponseTopic(NAMESPACE, gwId, taskId);
            String result = "The weather in " + message + " is sunny, 25\u00b0C";
            CloudEvent responseEvent = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(A2AProtocolConstants.CE_TYPE_PREFIX + "task.response")
                .withSource(java.net.URI.create("agent/weather-agent"))
                .withDataContentType("application/json")
                .withData(result.getBytes(StandardCharsets.UTF_8))
                .withExtension(A2AProtocolConstants.CE_EXTENSION_A2A_METHOD, A2AProtocolConstants.OP_SEND_MESSAGE)
                .build();

            try {
                transport.publish(responseTopic, responseEvent);
                log.info("[Weather Agent] Response published: taskId={}", taskId);
            } catch (Exception e) {
                log.error("[Weather Agent] Failed to publish response", e);
            }
        });

        log.info("Mock weather-agent registered and listening for requests.");
    }

    private String extractGatewayId(CloudEvent event) {
        if (event.getSource() == null) {
            return "default-gateway";
        }
        String source = event.getSource().toString();
        if (source.startsWith("gateway/")) {
            return source.substring("gateway/".length());
        }
        return "default-gateway";
    }

    /**
     * Returns the actual bound port. Useful when port 0 was specified to let the OS pick a port.
     */
    public int getBoundPort() {
        if (serverChannel != null) {
            return ((io.netty.channel.socket.ServerSocketChannel) serverChannel.channel()).localAddress().getPort();
        }
        return port;
    }

    public void shutdown() throws Exception {
        if (serverChannel != null) {
            serverChannel.channel().close().sync();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (gatewayService != null) {
            gatewayService.shutdown();
        }
        if (a2aService != null) {
            a2aService.shutdown();
        }
        log.info("A2A Gateway Server shut down.");
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        A2AGatewayServer server = new A2AGatewayServer(port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.shutdown();
            } catch (Exception e) {
                log.error("Error during shutdown", e);
            }
        }));

        server.start();

        // Keep running until interrupted
        Thread.currentThread().join();
    }

    // =========================================================================
    // Netty Channel Handler
    // =========================================================================

    private static class A2AGatewayRequestHandler extends io.netty.channel.SimpleChannelInboundHandler<FullHttpRequest> {

        private final A2AGatewayHttpHandler gatewayHandler;
        private final A2ACardHttpHandler cardHandler;

        A2AGatewayRequestHandler(A2AGatewayHttpHandler gatewayHandler, A2ACardHttpHandler cardHandler) {
            this.gatewayHandler = gatewayHandler;
            this.cardHandler = cardHandler;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            String uri = request.uri();
            try {
                FullHttpResponse response;
                if (uri.startsWith("/a2a/cards")) {
                    response = cardHandler.handle(request, ctx);
                } else if (uri.startsWith("/a2a/")) {
                    response = gatewayHandler.handle(request, ctx);
                } else {
                    response = jsonError(HttpResponseStatus.NOT_FOUND, "Not found: " + uri);
                }

                // null means the handler wrote the response directly (e.g. SSE streaming)
                if (response != null) {
                    ctx.writeAndFlush(response);
                }
            } catch (Exception e) {
                log.error("Error handling request: {} - {}", uri, e.getMessage(), e);
                FullHttpResponse errorResponse = jsonError(HttpResponseStatus.INTERNAL_SERVER_ERROR, e.getMessage());
                ctx.writeAndFlush(errorResponse);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("Channel exception: {}", cause.getMessage(), cause);
            ctx.close();
        }

        private FullHttpResponse jsonError(HttpResponseStatus status, String message) {
            String body = "{\"error\":\"" + message + "\"}";
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status, Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            response.headers().set("Access-Control-Allow-Origin", "*");
            response.headers().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
            response.headers().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            return response;
        }
    }
}
