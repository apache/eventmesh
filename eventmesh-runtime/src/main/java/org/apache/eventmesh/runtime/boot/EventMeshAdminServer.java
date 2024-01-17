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

import org.apache.eventmesh.runtime.admin.controller.HttpHandlerManager;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshAdminServer extends AbstractHTTPServer {

    private HttpConnectionHandler httpConnectionHandler;

    HttpHandlerManager httpHandlerManager;

    public EventMeshAdminServer(int port, boolean useTLS,
        EventMeshHTTPConfiguration eventMeshHttpConfiguration, HttpHandlerManager httpHandlerManager) {
        super(port, useTLS, eventMeshHttpConfiguration);
        this.httpHandlerManager = httpHandlerManager;
    }

    @Override
    public void init() throws Exception {
        super.init("eventMesh-admin-http");
        httpConnectionHandler = new HttpConnectionHandler();
    }

    @Override
    public void start() throws Exception {
        final Thread thread = new Thread(() -> {
            final ServerBootstrap bootstrap = new ServerBootstrap();
            try {
                bootstrap.group(this.getBossGroup(), this.getIoGroup())
                    .channel(useEpoll() ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
                    .childHandler(new AdminServerInitializer(httpHandlerManager))
                    .childOption(ChannelOption.SO_KEEPALIVE, Boolean.TRUE);

                log.info("AdminHttpServer[port={}] started.", this.getPort());

                bootstrap.bind(this.getPort())
                    .channel()
                    .closeFuture()
                    .sync();
            } catch (Exception e) {
                log.error("AdminHttpServer start error!", e);
                try {
                    shutdown();
                } catch (Exception ex) {
                    log.error("AdminHttpServer shutdown error!", ex);
                }
                System.exit(-1);
            }
        }, "EventMesh-http-server");
        thread.setDaemon(true);
        thread.start();
        started.compareAndSet(false, true);
    }

    private class AdminServerInitializer extends ChannelInitializer<SocketChannel> {

        HttpHandlerManager httpHandlerManager;

        public AdminServerInitializer(HttpHandlerManager httpHandlerManager) {
            this.httpHandlerManager = httpHandlerManager;
        }

        @Override
        protected void initChannel(final SocketChannel channel) {
            final ChannelPipeline pipeline = channel.pipeline();

            pipeline.addLast(getWorkerGroup(),
                new HttpRequestDecoder(),
                new HttpResponseEncoder(),
                httpConnectionHandler,
                new HttpObjectAggregator(Integer.MAX_VALUE),
                new AdminServerHandler(httpHandlerManager));
        }
    }


    private class AdminServerHandler extends ChannelInboundHandlerAdapter {

        HttpHandlerManager httpHandlerManager;

        public AdminServerHandler(HttpHandlerManager httpHandlerManager) {
            this.httpHandlerManager = httpHandlerManager;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!(msg instanceof HttpRequest)) {
                return;
            }
            HttpRequest httpRequest = (HttpRequest) msg;
            httpHandlerManager.exec(ctx, httpRequest);

        }
    }


}
