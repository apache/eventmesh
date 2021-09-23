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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import com.google.common.base.Preconditions;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.DiskAttribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.command.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.Header;
import org.apache.eventmesh.runtime.common.Pair;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import org.apache.eventmesh.runtime.metrics.http.HTTPMetricsServer;
import org.apache.eventmesh.runtime.trace.AttributeKeys;
import org.apache.eventmesh.runtime.trace.OpenTelemetryTraceFactory;
import org.apache.eventmesh.runtime.trace.SpanKey;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractHTTPServer extends AbstractRemotingServer {

    public Logger httpServerLogger = LoggerFactory.getLogger(this.getClass());

    public Logger httpLogger = LoggerFactory.getLogger("http");

    public HTTPMetricsServer metrics;

    public DefaultHttpDataFactory defaultHttpDataFactory = new DefaultHttpDataFactory(false);

    private AtomicBoolean started = new AtomicBoolean(false);

    private boolean useTLS;

    public OpenTelemetryTraceFactory openTelemetryTraceFactory;

    public Tracer tracer;

    public TextMapPropagator textMapPropagator;

    public ThreadPoolExecutor asyncContextCompleteHandler =
            ThreadPoolFactory.createThreadPoolExecutor(10, 10, "eventMesh-http-asyncContext-");

    static {
        DiskAttribute.deleteOnExitTemporaryFile = false;
    }

    protected HashMap<Integer/* request code */, Pair<HttpRequestProcessor, ThreadPoolExecutor>> processorTable =
            new HashMap<Integer, Pair<HttpRequestProcessor, ThreadPoolExecutor>>(64);

    public AbstractHTTPServer(int port, boolean useTLS) {
        this.port = port;
        this.useTLS = useTLS;
    }

    public Map<String, Object> parseHTTPHeader(HttpRequest fullReq) {
        Map<String, Object> headerParam = new HashMap<>();
        for (String key : fullReq.headers().names()) {
            if (StringUtils.equalsIgnoreCase(HttpHeaderNames.CONTENT_TYPE.toString(), key)
                    || StringUtils.equalsIgnoreCase(HttpHeaderNames.ACCEPT_ENCODING.toString(), key)
                    || StringUtils.equalsIgnoreCase(HttpHeaderNames.CONTENT_LENGTH.toString(), key)) {
                continue;
            }
            headerParam.put(key, fullReq.headers().get(key));
        }
        return headerParam;
    }

    public void sendError(ChannelHandlerContext ctx,
                          HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                status);
        response.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN +
                "; charset=" + EventMeshConstants.DEFAULT_CHARSET);
        response.headers().add(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        //todo server span end with error, record status, we should get channel here to get span in channel's context in async call..
        Context context = ctx.channel().attr(AttributeKeys.SERVER_CONTEXT).get();
        Span span = context.get(SpanKey.SERVER_KEY);
        try (Scope ignored = context.makeCurrent()) {
            span.setStatus(StatusCode.ERROR);//set this span's status to ERROR
            span.end();// closing the scope does not end the span, this has to be done manually
        }
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    public void sendResponse(ChannelHandlerContext ctx,
                             DefaultFullHttpResponse response) {
        //todo end server span, we should get channel here to get span in channel's context in async call.
        Context context = ctx.channel().attr(AttributeKeys.SERVER_CONTEXT).get();
        Span span = context.get(SpanKey.SERVER_KEY);
        try (Scope ignored = context.makeCurrent()) {
            span.addEvent("Send Response");
            span.end();
        }

        ctx.writeAndFlush(response).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture f) throws Exception {
                if (!f.isSuccess()) {
                    httpLogger.warn("send response to [{}] fail, will close this channel", RemotingHelper.parseChannelRemoteAddr(f.channel()));
                    f.channel().close();
                    return;
                }
            }
        });
    }

    @Override
    public void start() throws Exception {
        super.start();
        Runnable r = () -> {
            ServerBootstrap b = new ServerBootstrap();
            SSLContext sslContext = useTLS ? SSLContextFactory.getSslContext() : null;
            b.group(this.bossGroup, this.workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new HttpsServerInitializer(sslContext))
                    .childOption(ChannelOption.SO_KEEPALIVE, Boolean.TRUE);
            try {
                httpServerLogger.info("HTTPServer[port={}] started......", this.port);
                ChannelFuture future = b.bind(this.port).sync();
                future.channel().closeFuture().sync();
            } catch (Exception e) {
                httpServerLogger.error("HTTPServer start Err!", e);
                try {
                    shutdown();
                } catch (Exception e1) {
                    httpServerLogger.error("HTTPServer shutdown Err!", e);
                }
                return;
            }
        };

        Thread t = new Thread(r, "eventMesh-http-server");
        t.start();
        started.compareAndSet(false, true);
    }

    @Override
    public void init(String threadPrefix) throws Exception {
        super.init(threadPrefix);
    }

    @Override
    public void shutdown() throws Exception {
        super.shutdown();
        started.compareAndSet(true, false);
    }

    public void registerProcessor(Integer requestCode, HttpRequestProcessor processor, ThreadPoolExecutor executor) {
        Preconditions.checkState(ObjectUtils.allNotNull(requestCode), "requestCode can't be null");
        Preconditions.checkState(ObjectUtils.allNotNull(processor), "processor can't be null");
        Preconditions.checkState(ObjectUtils.allNotNull(executor), "executor can't be null");
        Pair<HttpRequestProcessor, ThreadPoolExecutor> pair = new Pair<HttpRequestProcessor, ThreadPoolExecutor>(processor, executor);
        this.processorTable.put(requestCode, pair);
    }

    class HTTPHandler extends SimpleChannelInboundHandler<HttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, HttpRequest httpRequest) throws Exception {
            HttpPostRequestDecoder decoder = null;
            //todo start server span, we should get channel here to put span in channel's context in async call.
            //if the client injected span context,this will extract the context from httpRequest or it will be null
            Context context = textMapPropagator.extract(Context.current(), httpRequest, new TextMapGetter<HttpRequest>(){
                @Override
                public Iterable<String> keys(HttpRequest carrier) {
                    return carrier.headers().names();
                }

                @Override
                public String get(HttpRequest carrier, String key) {
                    return carrier.headers().get(key);
                }
            });

            Span span = null;

            try {
                if (!httpRequest.decoderResult().isSuccess()) {
                    sendError(ctx, HttpResponseStatus.BAD_REQUEST);
                    return;
                }

                final HttpCommand requestCommand = new HttpCommand();


                httpRequest.headers().set(ProtocolKey.ClientInstanceKey.IP, RemotingHelper.parseChannelRemoteAddr(ctx.channel()));

                String protocolVersion = StringUtils.deleteWhitespace(httpRequest.headers().get(ProtocolKey.VERSION));
                if (StringUtils.isBlank(protocolVersion)) {
                    protocolVersion = ProtocolVersion.V1.getVersion();
                    httpRequest.headers().set(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion());
                }

                metrics.summaryMetrics.recordHTTPRequest();

                long bodyDecodeStart = System.currentTimeMillis();

                Map<String, Object> bodyMap = new HashMap<>();

                if (httpRequest.method() == HttpMethod.GET) {
                    QueryStringDecoder getDecoder = new QueryStringDecoder(httpRequest.uri());
                    getDecoder.parameters().entrySet().forEach(entry -> {
                        bodyMap.put(entry.getKey(), entry.getValue().get(0));
                    });
                } else if (httpRequest.method() == HttpMethod.POST) {
                    decoder = new HttpPostRequestDecoder(defaultHttpDataFactory, httpRequest);
                    List<InterfaceHttpData> parmList = decoder.getBodyHttpDatas();
                    for (InterfaceHttpData parm : parmList) {
                        if (parm.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
                            Attribute data = (Attribute) parm;
                            bodyMap.put(data.getName(), data.getValue());
                        }
                    }
                } else {
                    sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
                    return;
                }

                metrics.summaryMetrics.recordDecodeTimeCost(System.currentTimeMillis() - bodyDecodeStart);
                String requestCode =
                        (httpRequest.method() == HttpMethod.POST) ? StringUtils.deleteWhitespace(httpRequest.headers().get(ProtocolKey.REQUEST_CODE))
                                : MapUtils.getString(bodyMap, StringUtils.lowerCase(ProtocolKey.REQUEST_CODE), "");

                requestCommand.setHttpMethod(httpRequest.method().name());
                requestCommand.setHttpVersion(httpRequest.protocolVersion().protocolName());
                requestCommand.setRequestCode(requestCode);

                //todo record command opaque in span.
                span = tracer.spanBuilder("HTTP"+requestCommand.httpMethod).setParent(context).setSpanKind(SpanKind.SERVER).startSpan();
                span.addEvent("Span Start");
                //attach the span to the server context
                context = context.with(SpanKey.SERVER_KEY,span);
                //put the context in channel
                ctx.channel().attr(AttributeKeys.SERVER_CONTEXT).set(context);
                //todo record command method, version and requestCode in span.
                span.setAttribute("HttpMethod",httpRequest.method().name());
                span.setAttribute("HttpVersion",httpRequest.protocolVersion().protocolName());
                span.setAttribute("RequestCode",requestCode);

                HttpCommand responseCommand = null;

                if (!ProtocolVersion.contains(protocolVersion)) {
                    responseCommand = requestCommand.createHttpCommandResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg());
                    sendResponse(ctx, responseCommand.httpResponse());
                    return;
                }

                if (StringUtils.isBlank(requestCode)
                        || !StringUtils.isNumeric(requestCode)
                        || !RequestCode.contains(Integer.valueOf(requestCode))
                        || !processorTable.containsKey(Integer.valueOf(requestCode))) {
                    responseCommand = requestCommand.createHttpCommandResponse(EventMeshRetCode.EVENTMESH_REQUESTCODE_INVALID.getRetCode(), EventMeshRetCode.EVENTMESH_REQUESTCODE_INVALID.getErrMsg());
                    sendResponse(ctx, responseCommand.httpResponse());
                    return;
                }

                if (!started.get()) {
                    responseCommand = requestCommand.createHttpCommandResponse(EventMeshRetCode.EVENTMESH_STOP.getRetCode(), EventMeshRetCode.EVENTMESH_STOP.getErrMsg());
                    sendResponse(ctx, responseCommand.httpResponse());
                    return;
                }

                try {
                    requestCommand.setHeader(Header.buildHeader(requestCode, parseHTTPHeader(httpRequest)));
                    requestCommand.setBody(Body.buildBody(requestCode, bodyMap));
                } catch (Exception e) {
                    responseCommand = requestCommand.createHttpCommandResponse(EventMeshRetCode.EVENTMESH_RUNTIME_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_RUNTIME_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 3));
                    sendResponse(ctx, responseCommand.httpResponse());
                    return;
                }

                if (httpLogger.isDebugEnabled()) {
                    httpLogger.debug("{}", requestCommand);
                }

                AsyncContext<HttpCommand> asyncContext = new AsyncContext<HttpCommand>(requestCommand, responseCommand, asyncContextCompleteHandler);
                processEventMeshRequest(ctx, asyncContext);
            } catch (Exception ex) {
                httpServerLogger.error("AbrstractHTTPServer.HTTPHandler.channelRead0 err", ex);
                //todo span end with exception.
                span.addEvent("End Exceptional");
                span.setStatus(StatusCode.ERROR,ex.getMessage());//set this span's status to ERROR
                span.recordException(ex);//record this exception
                span.end();// closing the scope does not end the span, this has to be done manually
            } finally {
                try {
                    decoder.destroy();
                } catch (Exception e) {
                }
            }
        }

        public void processEventMeshRequest(final ChannelHandlerContext ctx,
                                            final AsyncContext<HttpCommand> asyncContext) {
            final Pair<HttpRequestProcessor, ThreadPoolExecutor> choosed = processorTable.get(Integer.valueOf(asyncContext.getRequest().getRequestCode()));
            try {
                choosed.getObject2().submit(() -> {
                    try {
                        if (choosed.getObject1().rejectRequest()) {
                            HttpCommand responseCommand = asyncContext.getRequest().createHttpCommandResponse(EventMeshRetCode.EVENTMESH_REJECT_BY_PROCESSOR_ERROR.getRetCode(), EventMeshRetCode.EVENTMESH_REJECT_BY_PROCESSOR_ERROR.getErrMsg());
                            asyncContext.onComplete(responseCommand);
                            if (asyncContext.isComplete()) {
                                if (httpLogger.isDebugEnabled()) {
                                    httpLogger.debug("{}", asyncContext.getResponse());
                                }
                                sendResponse(ctx, responseCommand.httpResponse());
                            }
                            return;
                        }

                        choosed.getObject1().processRequest(ctx, asyncContext);
                        if (asyncContext == null || !asyncContext.isComplete()) {
                            return;
                        }

                        metrics.summaryMetrics.recordHTTPReqResTimeCost(System.currentTimeMillis() - asyncContext.getRequest().getReqTime());

                        if (httpLogger.isDebugEnabled()) {
                            httpLogger.debug("{}", asyncContext.getResponse());
                        }

                        sendResponse(ctx, asyncContext.getResponse().httpResponse());
                    } catch (Exception e) {
                        httpServerLogger.error("process error", e);
                    }
                });
            } catch (RejectedExecutionException re) {
                HttpCommand responseCommand = asyncContext.getRequest().createHttpCommandResponse(EventMeshRetCode.OVERLOAD.getRetCode(), EventMeshRetCode.OVERLOAD.getErrMsg());
                asyncContext.onComplete(responseCommand);
                metrics.summaryMetrics.recordHTTPDiscard();
                metrics.summaryMetrics.recordHTTPReqResTimeCost(System.currentTimeMillis() - responseCommand.getReqTime());
                try {
                    sendResponse(ctx, asyncContext.getResponse().httpResponse());
                } catch (Exception e) {
                }
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            super.channelReadComplete(ctx);
            ctx.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (null != cause) cause.printStackTrace();
            if (null != ctx) ctx.close();
        }
    }

    class HttpConnectionHandler extends ChannelDuplexHandler {
        public AtomicInteger connections = new AtomicInteger(0);

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            super.channelRegistered(ctx);
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            super.channelUnregistered(ctx);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            final String remoteAddress = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
            int c = connections.incrementAndGet();
            if (c > 20000) {
                httpServerLogger.warn("client|http|channelActive|remoteAddress={}|msg={}", remoteAddress, "too many client(20000) connect " +
                        "this eventMesh server");
                ctx.close();
                return;
            }

            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            connections.decrementAndGet();
            final String remoteAddress = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
            super.channelInactive(ctx);
        }


        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                if (event.state().equals(IdleState.ALL_IDLE)) {
                    final String remoteAddress = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
                    httpServerLogger.info("client|http|userEventTriggered|remoteAddress={}|msg={}", remoteAddress, evt.getClass()
                            .getName());
                    ctx.close();
                }
            }

            ctx.fireUserEventTriggered(evt);
        }
    }

    class HttpsServerInitializer extends ChannelInitializer<SocketChannel> {

        private SSLContext sslContext;

        public HttpsServerInitializer(SSLContext sslContext) {
            this.sslContext = sslContext;
        }

        @Override
        protected void initChannel(SocketChannel channel) throws Exception {
            ChannelPipeline pipeline = channel.pipeline();


            if (sslContext != null && useTLS) {
                SSLEngine sslEngine = sslContext.createSSLEngine();
                sslEngine.setUseClientMode(false);
                pipeline.addFirst("ssl", new SslHandler(sslEngine));
            }
            pipeline.addLast(new HttpRequestDecoder(),
                    new HttpResponseEncoder(),
                    new HttpConnectionHandler(),
                    new HttpObjectAggregator(Integer.MAX_VALUE),
                    new HTTPHandler());
        }
    }
}
