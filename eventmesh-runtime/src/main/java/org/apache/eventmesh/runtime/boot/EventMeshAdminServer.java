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

import org.apache.eventmesh.runtime.admin.handler.AdminHandlerManager;
import org.apache.eventmesh.runtime.admin.handler.HttpHandler;
import org.apache.eventmesh.runtime.util.HttpResponseUtils;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshAdminServer extends AbstractHTTPServer {

    private HttpConnectionHandler httpConnectionHandler = new HttpConnectionHandler();

    private AdminHandlerManager adminHandlerManager;

    public EventMeshAdminServer(EventMeshServer eventMeshServer) {
        super(eventMeshServer.getEventMeshTCPServer().getEventMeshTCPConfiguration().getEventMeshServerAdminPort(), false,
            eventMeshServer.getEventMeshHTTPServer().getEventMeshHttpConfiguration());
        adminHandlerManager = new AdminHandlerManager(eventMeshServer);
    }


    @Override
    public void init() throws Exception {
        super.init("eventMesh-admin");
        adminHandlerManager.registerHttpHandler();
    }

    @Override
    public void start() throws Exception {
        final Thread thread = new Thread(() -> {
            final ServerBootstrap bootstrap = new ServerBootstrap();
            try {
                bootstrap.group(this.getBossGroup(), this.getIoGroup())
                    .channel(useEpoll() ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
                    .childHandler(new AdminServerInitializer())
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
        }, "EventMesh-admin-server");
        thread.setDaemon(true);
        thread.start();
        started.compareAndSet(false, true);
    }

    public void parseHttpRequest(ChannelHandlerContext ctx, HttpRequest httpRequest) {
        String uriStr = httpRequest.uri();
        URI uri = URI.create(uriStr);
        Optional<HttpHandler> httpHandlerOpt = adminHandlerManager.getHttpHandler(uri.getPath());
        if (httpHandlerOpt.isPresent()) {
            try {
                httpHandlerOpt.get().handle(httpRequest, ctx);
            } catch (Exception e) {
                log.error("admin server channelRead error", e);
                ctx.writeAndFlush(HttpResponseUtils.buildHttpResponse(Objects.requireNonNull(e.getMessage()), ctx,
                    HttpHeaderValues.APPLICATION_JSON, HttpResponseStatus.INTERNAL_SERVER_ERROR)).addListener(ChannelFutureListener.CLOSE);
            }
        } else {
            ctx.writeAndFlush(HttpResponseUtils.createNotFound()).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private class AdminServerInitializer extends ChannelInitializer<SocketChannel> {

        @Override
        protected void initChannel(final SocketChannel channel) {
            final ChannelPipeline pipeline = channel.pipeline();

            pipeline.addLast(getWorkerGroup(),
                new HttpRequestDecoder(),
                new HttpResponseEncoder(),
                httpConnectionHandler,
                new HttpObjectAggregator(Integer.MAX_VALUE),
                new SimpleChannelInboundHandler<HttpRequest>() {

                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest msg) {
                        parseHttpRequest(ctx, msg);
                    }
                });
        }
    }
}
