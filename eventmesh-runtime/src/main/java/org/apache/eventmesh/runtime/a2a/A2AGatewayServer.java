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

import org.apache.eventmesh.protocol.a2a.A2AMessageTransport;
import org.apache.eventmesh.runtime.state.TaskStore;

import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

import lombok.extern.slf4j.Slf4j;

/**
 * A2A Gateway HTTP server.
 *
 * <p>Boots an embedded Netty HTTP server that exposes the A2A Gateway REST + SSE API on
 * the given port. The gateway is constructed with a {@link TaskStore} (Sub-PR A/C), an
 * {@link AgentCardRegistry} (in-memory D1; Meta-backed D2), and a
 * {@link A2AMessageTransport} &mdash; production wiring is
 * {@link EventMeshA2ATransport} (Runtime-bridged), tests pass an in-process transport.</p>
 *
 * <p><b>Issue #5302 D1 scope:</b> this class is the new home of the gateway that
 * <a href="https://github.com/apache/eventmesh/pull/5260">PR #5260</a> originally added
 * (then deleted in the uni-architecture redesign). The weather-agent demo from PR #5260
 * has been removed &mdash; the gateway no longer pre-registers a mock agent; the demo
 * client ({@code A2AGatewayDemo}) will be ported in D2 alongside AgentCard Meta-ization.</p>
 */
@Slf4j
public class A2AGatewayServer {

    private final int port;
    private final A2AMessageTransport transport;
    private final TaskStore taskStore;
    private final AgentCardRegistry agentCardRegistry;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    private A2AGatewayService gatewayService;
    private A2AGatewayHttpHandler gatewayHandler;

    public A2AGatewayServer(int port, A2AMessageTransport transport, TaskStore taskStore,
                              AgentCardRegistry agentCardRegistry) {
        this.port = port;
        this.transport = transport;
        this.taskStore = taskStore;
        this.agentCardRegistry = agentCardRegistry;
    }

    public void start() throws Exception {
        // 1. Initialize components
        gatewayService = new A2AGatewayService(
            "global", "gateway-" + port, transport, taskStore, agentCardRegistry);
        gatewayService.start();

        gatewayHandler = new A2AGatewayHttpHandler(gatewayService);

        // 2. Start Netty HTTP server
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
                        .addLast(gatewayHandler);
                }
            })
            .option(ChannelOption.SO_BACKLOG, 128)
            .childOption(ChannelOption.SO_KEEPALIVE, true);

        serverChannel = bootstrap.bind(port).sync().channel();
        log.info("=== A2A Gateway Server started on port {} ===", port);
        log.info("Gateway ID: {}, backed by: {}",
            gatewayService.getGatewayId(), taskStore.getClass().getSimpleName());
    }

    public void shutdown() throws Exception {
        if (serverChannel != null) {
            serverChannel.close().sync();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS);
        }
        if (gatewayService != null) {
            gatewayService.shutdown();
        }
    }

    public int getPort() {
        return port;
    }

    public A2AGatewayService getGatewayService() {
        return gatewayService;
    }
}
