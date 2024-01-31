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

import org.apache.eventmesh.runtime.admin.controller.ClientManageController;
import org.apache.eventmesh.runtime.admin.controller.HttpHandlerManager;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.util.HttpResponseUtils;
import org.apache.eventmesh.runtime.util.Utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpPrincipal;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshAdminServer extends AbstractHTTPServer {

    private HttpConnectionHandler httpConnectionHandler;

    HttpHandlerManager httpHandlerManager;

    private ClientManageController clientManageController;


    public EventMeshAdminServer(int port, boolean useTLS,
        EventMeshHTTPConfiguration eventMeshHttpConfiguration, ClientManageController clientManageController) {
        super(port, useTLS, eventMeshHttpConfiguration);
        this.clientManageController = clientManageController;
        this.httpHandlerManager = clientManageController.getHttpHandlerManager();
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
        }, "EventMesh-http-server");
        thread.setDaemon(true);
        thread.start();
        started.compareAndSet(false, true);
    }

    public void parseHttpRequest(ChannelHandlerContext ctx, HttpRequest httpRequest) {
        String uriStr = httpRequest.uri();
        URI uri = URI.create(uriStr);
        Optional<HttpHandler> httpHandlerOpt = httpHandlerManager.getHttpHandler(uri.getPath());
        if (httpHandlerOpt.isPresent()) {
            try {
                AdminHttpExchange adminHttpExchange = new AdminHttpExchange(ctx, httpRequest);
                httpHandlerOpt.get().handle(adminHttpExchange);
                adminHttpExchange.writeAndFlash();
                return;
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                ctx.writeAndFlush(HttpResponseUtils.createInternalServerError()).addListener(ChannelFutureListener.CLOSE);
            }
        } else {
            ctx.writeAndFlush(HttpResponseUtils.createNotFound()).addListener(ChannelFutureListener.CLOSE);
        }
    }

    class AdminHttpExchange extends HttpExchange {


        ChannelHandlerContext ctx;
        Optional<FullHttpRequest> httpRequest;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        Map<Integer, Long> responseCode = new HashMap<>();

        Headers responseHeader = new Headers();

        public AdminHttpExchange(ChannelHandlerContext ctx, HttpRequest httpRequest) {
            this.ctx = ctx;
            if (httpRequest instanceof FullHttpRequest) {
                this.httpRequest = Optional.ofNullable((FullHttpRequest) httpRequest);
            }
        }

        @Override
        public Headers getRequestHeaders() {
            Headers headers = new Headers();
            httpRequest.ifPresent(e -> {
                final Map<String, Object> headerMap = Utils.parseHttpHeader(e);
                headerMap.putAll(headerMap);
            });

            return headers;
        }

        @Override
        public Headers getResponseHeaders() {
            return responseHeader;
        }

        @Override
        public URI getRequestURI() {
            if (httpRequest.isPresent()) {
                return URI.create(httpRequest.get().uri());
            }
            return null;
        }

        @Override
        public String getRequestMethod() {
            if (httpRequest.isPresent()) {
                return httpRequest.get().method().name();
            }
            return null;
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {

        }

        @Override
        public InputStream getRequestBody() {
            if (httpRequest.isPresent()) {
                ByteBuf content = httpRequest.get().content();
                byte[] bytes = new byte[content.readableBytes()];
                try {
                    content.readBytes(bytes);
                } finally {
                    content.release();
                }
                return new ByteArrayInputStream(bytes);
            }
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public OutputStream getResponseBody() {
            return outputStream;
        }

        @Override
        public void sendResponseHeaders(int i, long l) throws IOException {
            responseCode.put(i, l);
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public int getResponseCode() {
            Set<Entry<Integer, Long>> entries = responseCode.entrySet();
            Optional<Entry<Integer, Long>> first = entries.stream().findFirst();
            return first.get().getKey();
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public String getProtocol() {
            return null;
        }

        @Override
        public Object getAttribute(String s) {
            return null;
        }

        @Override
        public void setAttribute(String s, Object o) {

        }

        @Override
        public void setStreams(InputStream inputStream, OutputStream outputStream) {

        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }

        public void writeAndFlash() {
            byte[] bytes = outputStream.toByteArray();
            Headers responseHeaders = getResponseHeaders();
            DefaultFullHttpResponse defaultFullHttpResponse =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(getResponseCode()),
                    Unpooled.copiedBuffer(bytes));
            responseHeaders.entrySet().stream().forEach(e -> {
                defaultFullHttpResponse.headers().add(e.getKey(), e.getValue());
            });
            ctx.writeAndFlush(defaultFullHttpResponse).addListener(ChannelFutureListener.CLOSE);
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
                    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest msg) throws Exception {
                        parseHttpRequest(ctx, msg);
                    }
                });
        }
    }

}
