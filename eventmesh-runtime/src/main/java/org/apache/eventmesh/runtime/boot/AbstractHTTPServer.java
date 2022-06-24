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

import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.HttpEventWrapper;
import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.Header;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.common.Pair;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.EventProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import org.apache.eventmesh.runtime.metrics.http.HTTPMetricsServer;
import org.apache.eventmesh.runtime.trace.TraceUtils;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.apache.eventmesh.trace.api.common.EventMeshTraceConstants;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
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
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Preconditions;

public abstract class AbstractHTTPServer extends AbstractRemotingServer {

    public Logger httpServerLogger = LoggerFactory.getLogger(this.getClass());

    public Logger httpLogger = LoggerFactory.getLogger("http");

    public HTTPMetricsServer metrics;

    public DefaultHttpDataFactory defaultHttpDataFactory = new DefaultHttpDataFactory(false);

    private AtomicBoolean started = new AtomicBoolean(false);

    private boolean useTLS;

    public Boolean useTrace = false; //Determine whether trace is enabled

    private EventMeshHTTPConfiguration eventMeshHttpConfiguration;

    public ThreadPoolExecutor asyncContextCompleteHandler =
        ThreadPoolFactory.createThreadPoolExecutor(10, 10, "EventMesh-http-asyncContext-");

    static {
        DiskAttribute.deleteOnExitTemporaryFile = false;
    }

    protected final Map<String/* request code */, Pair<HttpRequestProcessor, ThreadPoolExecutor>>
        processorTable = new HashMap<>(64);

    protected final Map<String/* request uri */, Pair<EventProcessor, ThreadPoolExecutor>>
        eventProcessorTable = new HashMap<>(64);

    public AbstractHTTPServer(int port, boolean useTLS, EventMeshHTTPConfiguration eventMeshHttpConfiguration) {
        this.port = port;
        this.useTLS = useTLS;
        this.eventMeshHttpConfiguration = eventMeshHttpConfiguration;
    }

    public void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        HttpHeaders responseHeaders = response.headers();
        responseHeaders.add(
            HttpHeaderNames.CONTENT_TYPE, String.format("text/plain; charset=%s", EventMeshConstants.DEFAULT_CHARSET)
        );
        responseHeaders.add(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        responseHeaders.add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    public void sendResponse(ChannelHandlerContext ctx, DefaultFullHttpResponse response) {
        ctx.writeAndFlush(response).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                httpLogger.warn("send response to [{}] fail, will close this channel",
                    RemotingHelper.parseChannelRemoteAddr(f.channel()));
                f.channel().close();
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
            }
        };

        Thread t = new Thread(r, "EventMesh-http-server");
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
        Pair<HttpRequestProcessor, ThreadPoolExecutor> pair = new Pair<>(processor, executor);
        this.processorTable.put(requestCode.toString(), pair);
    }

    public void registerProcessor(String requestURI, EventProcessor processor, ThreadPoolExecutor executor) {
        Preconditions.checkState(ObjectUtils.allNotNull(requestURI), "requestURI can't be null");
        Preconditions.checkState(ObjectUtils.allNotNull(processor), "processor can't be null");
        Preconditions.checkState(ObjectUtils.allNotNull(executor), "executor can't be null");
        Pair<EventProcessor, ThreadPoolExecutor> pair = new Pair<>(processor, executor);
        this.eventProcessorTable.put(requestURI, pair);
    }

    private Map<String, Object> parseHttpHeader(HttpRequest fullReq) {
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

    /**
     * Validate request, return error status.
     *
     * @param httpRequest
     * @return if request is validated return null else return error status
     */
    private HttpResponseStatus validateHttpRequest(HttpRequest httpRequest) {
        if (!started.get()) {
            return HttpResponseStatus.SERVICE_UNAVAILABLE;
        }
        if (!httpRequest.decoderResult().isSuccess()) {
            return HttpResponseStatus.BAD_REQUEST;
        }
        if (!HttpMethod.GET.equals(httpRequest.method()) && !HttpMethod.POST.equals(httpRequest.method())) {
            return HttpResponseStatus.METHOD_NOT_ALLOWED;
        }
        final String protocolVersion = httpRequest.headers().get(ProtocolKey.VERSION);
        if (!ProtocolVersion.contains(protocolVersion)) {
            return HttpResponseStatus.BAD_REQUEST;
        }
        return null;
    }

    /**
     * Inject ip and protocol version, if the protocol version is empty, set default to {@link ProtocolVersion#V1}.
     *
     * @param ctx
     * @param httpRequest
     */
    private void preProcessHttpRequestHeader(ChannelHandlerContext ctx, HttpRequest httpRequest) {
        long startTime = System.currentTimeMillis();
        HttpHeaders requestHeaders = httpRequest.headers();
        requestHeaders.set(ProtocolKey.ClientInstanceKey.IP,
            RemotingHelper.parseChannelRemoteAddr(ctx.channel()));

        String protocolVersion = httpRequest.headers().get(ProtocolKey.VERSION);
        if (StringUtils.isBlank(protocolVersion)) {
            requestHeaders.set(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion());
        }
        requestHeaders.set(EventMeshConstants.REQ_C2EVENTMESH_TIMESTAMP, startTime);
        requestHeaders.set(EventMeshConstants.REQ_SEND_EVENTMESH_IP, eventMeshHttpConfiguration.eventMeshServerIp);
    }

    /**
     * Parse request body to map
     *
     * @param httpRequest
     * @return
     */
    private Map<String, Object> parseHttpRequestBody(HttpRequest httpRequest) throws IOException {
        final long bodyDecodeStart = System.currentTimeMillis();
        Map<String, Object> httpRequestBody = new HashMap<>();

        if (HttpMethod.GET.equals(httpRequest.method())) {
            QueryStringDecoder getDecoder = new QueryStringDecoder(httpRequest.uri());
            getDecoder.parameters().forEach((key, value) -> httpRequestBody.put(key, value.get(0)));
        } else if (HttpMethod.POST.equals(httpRequest.method())) {
            HttpPostRequestDecoder decoder =
                new HttpPostRequestDecoder(defaultHttpDataFactory, httpRequest);
            for (InterfaceHttpData parm : decoder.getBodyHttpDatas()) {
                if (parm.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
                    Attribute data = (Attribute) parm;
                    httpRequestBody.put(data.getName(), data.getValue());
                }
            }
            decoder.destroy();
        }
        metrics.getSummaryMetrics().recordDecodeTimeCost(System.currentTimeMillis() - bodyDecodeStart);
        return httpRequestBody;
    }

    class HTTPHandler extends SimpleChannelInboundHandler<HttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, HttpRequest httpRequest) {
            //            Context context = null;
            //            Span span = null;
            //            if (useTrace) {
            //
            //                //if the client injected span context,this will extract the context from httpRequest or it will be null
            //                context = textMapPropagator.extract(Context.current(), httpRequest, new TextMapGetter<HttpRequest>() {
            //                    @Override
            //                    public Iterable<String> keys(HttpRequest carrier) {
            //                        return carrier.headers().names();
            //                    }
            //
            //                    @Override
            //                    public String get(HttpRequest carrier, String key) {
            //                        return carrier.headers().get(key);
            //                    }
            //                });
            //
            //                span = tracer.spanBuilder("HTTP " + httpRequest.method())
            //                        .setParent(context)
            //                        .setSpanKind(SpanKind.SERVER)
            //                        .startSpan();
            //                //attach the span to the server context
            //                context = context.with(SpanKey.SERVER_KEY, span);
            //                //put the context in channel
            //                ctx.channel().attr(AttributeKeys.SERVER_CONTEXT).set(context);
            //            }



            Span span = null;
            //            Context context = null;
            //            context = EventMeshServer.getTrace().extractFrom();
            //            span = EventMeshServer.getTrace().createSpan("", context);
            //            //attach the span to the server context
            //            context = context.with(SpanKey.SERVER_KEY, span);
            //            //put the context in channel
            //            ctx.channel().attr(AttributeKeys.SERVER_CONTEXT).set(context);

            try {
                preProcessHttpRequestHeader(ctx, httpRequest);

                final Map<String, Object> headerMap = parseHttpHeader(httpRequest);


                final HttpResponseStatus errorStatus = validateHttpRequest(httpRequest);
                if (errorStatus != null) {
                    sendError(ctx, errorStatus);

                    span = TraceUtils.prepareServerSpan(headerMap, EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_SERVER_SPAN, false);
                    TraceUtils.finishSpanWithException(span, headerMap, errorStatus.reasonPhrase(), null);
                    return;
                }
                metrics.getSummaryMetrics().recordHTTPRequest();

                boolean useRequestURI = false;
                for (String processURI : eventProcessorTable.keySet()) {
                    if (httpRequest.uri().startsWith(processURI)) {
                        useRequestURI = true;
                        break;
                    }
                }
                if (useRequestURI) {
                    if (useTrace) {
                        span.setAttribute(SemanticAttributes.HTTP_METHOD, httpRequest.method().name());
                        span.setAttribute(SemanticAttributes.HTTP_FLAVOR, httpRequest.protocolVersion().protocolName());
                        span.setAttribute(SemanticAttributes.HTTP_URL, httpRequest.uri());
                    }
                    HttpEventWrapper httpEventWrapper = parseHttpRequest(httpRequest);

                    AsyncContext<HttpEventWrapper> asyncContext =
                        new AsyncContext<>(httpEventWrapper, null, asyncContextCompleteHandler);
                    processHttpRequest(ctx, asyncContext);

                } else {
                    final HttpCommand requestCommand = new HttpCommand();

                    final Map<String, Object> bodyMap = parseHttpRequestBody(httpRequest);

                    String requestCode =
                        (httpRequest.method() == HttpMethod.POST)
                            ? httpRequest.headers().get(ProtocolKey.REQUEST_CODE)
                            : MapUtils.getString(bodyMap, StringUtils.lowerCase(ProtocolKey.REQUEST_CODE), "");

                    requestCommand.setHttpMethod(httpRequest.method().name());
                    requestCommand.setHttpVersion(httpRequest.protocolVersion().protocolName());
                    requestCommand.setRequestCode(requestCode);

                    //                if (useTrace) {
                    //                    span.setAttribute(SemanticAttributes.HTTP_METHOD, httpRequest.method().name());
                    //                    span.setAttribute(SemanticAttributes.HTTP_FLAVOR, httpRequest.protocolVersion().protocolName());
                    //                    span.setAttribute(String.valueOf(SemanticAttributes.HTTP_STATUS_CODE), requestCode);
                    //                }

                    HttpCommand responseCommand = null;

                    if (StringUtils.isBlank(requestCode)
                        || !processorTable.containsKey(requestCode)
                        || !RequestCode.contains(Integer.valueOf(requestCode))) {
                        responseCommand =
                            requestCommand.createHttpCommandResponse(EventMeshRetCode.EVENTMESH_REQUESTCODE_INVALID);
                        sendResponse(ctx, responseCommand.httpResponse());

                        span = TraceUtils.prepareServerSpan(headerMap, EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_SERVER_SPAN, false);
                        TraceUtils.finishSpanWithException(span, headerMap, EventMeshRetCode.EVENTMESH_REQUESTCODE_INVALID.getErrMsg(), null);
                        return;
                    }

                    try {
                        requestCommand.setHeader(Header.buildHeader(requestCode, headerMap));
                        requestCommand.setBody(Body.buildBody(requestCode, bodyMap));
                    } catch (Exception e) {
                        responseCommand = requestCommand.createHttpCommandResponse(EventMeshRetCode.EVENTMESH_RUNTIME_ERR);
                        sendResponse(ctx, responseCommand.httpResponse());

                        span = TraceUtils.prepareServerSpan(headerMap, EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_SERVER_SPAN, false);
                        TraceUtils.finishSpanWithException(span, headerMap, EventMeshRetCode.EVENTMESH_RUNTIME_ERR.getErrMsg(), e);
                        return;
                    }

                    if (httpLogger.isDebugEnabled()) {
                        httpLogger.debug("{}", requestCommand);
                    }

                    AsyncContext<HttpCommand> asyncContext =
                        new AsyncContext<>(requestCommand, responseCommand, asyncContextCompleteHandler);
                    processEventMeshRequest(ctx, asyncContext);
                }


            } catch (Exception ex) {
                httpServerLogger.error("AbrstractHTTPServer.HTTPHandler.channelRead0 err", ex);
            }
        }

        public void processHttpRequest(final ChannelHandlerContext ctx,
                                       final AsyncContext<HttpEventWrapper> asyncContext) {
            final HttpEventWrapper requestWrapper = asyncContext.getRequest();
            String requestURI = requestWrapper.getRequestURI();
            String processorKey = "/";
            for (String eventProcessorKey : eventProcessorTable.keySet()) {
                if (requestURI.startsWith(eventProcessorKey)) {
                    processorKey = eventProcessorKey;
                    break;
                }
            }
            final Pair<EventProcessor, ThreadPoolExecutor> choosed = eventProcessorTable.get(processorKey);
            try {
                choosed.getObject2().submit(() -> {
                    try {
                        EventProcessor processor = choosed.getObject1();
                        if (processor.rejectRequest()) {

                            HttpEventWrapper responseWrapper =
                                requestWrapper.createHttpResponse(EventMeshRetCode.EVENTMESH_REJECT_BY_PROCESSOR_ERROR);

                            asyncContext.onComplete(responseWrapper);
                            if (asyncContext.isComplete()) {
                                if (httpLogger.isDebugEnabled()) {
                                    httpLogger.debug("{}", asyncContext.getResponse());
                                }
                                sendResponse(ctx, asyncContext.getResponse().httpResponse());
                            }
                            return;
                        }

                        processor.processRequest(ctx, asyncContext);
                        if (!asyncContext.isComplete()) {
                            return;
                        }

                        metrics.getSummaryMetrics()
                            .recordHTTPReqResTimeCost(System.currentTimeMillis() - requestWrapper.getReqTime());

                        if (httpLogger.isDebugEnabled()) {
                            httpLogger.debug("{}", asyncContext.getResponse());
                        }

                        sendResponse(ctx, asyncContext.getResponse().httpResponse());
                    } catch (Exception e) {
                        httpServerLogger.error("process error", e);
                    }
                });
            } catch (RejectedExecutionException re) {
                HttpEventWrapper responseWrapper = requestWrapper.createHttpResponse(EventMeshRetCode.OVERLOAD);
                asyncContext.onComplete(responseWrapper);
                metrics.getSummaryMetrics().recordHTTPDiscard();
                metrics.getSummaryMetrics().recordHTTPReqResTimeCost(System.currentTimeMillis() - requestWrapper.getReqTime());
                try {
                    sendResponse(ctx, asyncContext.getResponse().httpResponse());
                } catch (Exception e) {
                    // ignore
                }
            }

        }

        public void processEventMeshRequest(final ChannelHandlerContext ctx,
                                            final AsyncContext<HttpCommand> asyncContext) {
            final HttpCommand request = asyncContext.getRequest();
            final Pair<HttpRequestProcessor, ThreadPoolExecutor> choosed = processorTable.get(request.getRequestCode());
            try {
                choosed.getObject2().submit(() -> {
                    try {
                        HttpRequestProcessor processor = choosed.getObject1();
                        if (processor.rejectRequest()) {
                            HttpCommand responseCommand =
                                request.createHttpCommandResponse(EventMeshRetCode.EVENTMESH_REJECT_BY_PROCESSOR_ERROR);
                            asyncContext.onComplete(responseCommand);
                            if (asyncContext.isComplete()) {
                                if (httpLogger.isDebugEnabled()) {
                                    httpLogger.debug("{}", asyncContext.getResponse());
                                }
                                sendResponse(ctx, responseCommand.httpResponse());

                                Map<String, Object> traceMap = asyncContext.getRequest().getHeader().toMap();
                                Span span = TraceUtils.prepareServerSpan(traceMap, EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_SERVER_SPAN,
                                    false);
                                TraceUtils.finishSpanWithException(span, traceMap,
                                    EventMeshRetCode.EVENTMESH_REJECT_BY_PROCESSOR_ERROR.getErrMsg(), null);
                            }
                            return;
                        }

                        processor.processRequest(ctx, asyncContext);
                        if (!asyncContext.isComplete()) {
                            return;
                        }

                        metrics.getSummaryMetrics()
                            .recordHTTPReqResTimeCost(System.currentTimeMillis() - request.getReqTime());

                        if (httpLogger.isDebugEnabled()) {
                            httpLogger.debug("{}", asyncContext.getResponse());
                        }

                        sendResponse(ctx, asyncContext.getResponse().httpResponse());

                    } catch (Exception e) {
                        httpServerLogger.error("process error", e);
                    }
                });
            } catch (RejectedExecutionException re) {
                HttpCommand responseCommand = request.createHttpCommandResponse(EventMeshRetCode.OVERLOAD);
                asyncContext.onComplete(responseCommand);
                metrics.getSummaryMetrics().recordHTTPDiscard();
                metrics.getSummaryMetrics().recordHTTPReqResTimeCost(System.currentTimeMillis() - request.getReqTime());
                try {
                    sendResponse(ctx, asyncContext.getResponse().httpResponse());

                    Map<String, Object> traceMap = asyncContext.getRequest().getHeader().toMap();
                    Span span = TraceUtils.prepareServerSpan(traceMap, EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_SERVER_SPAN, false);
                    TraceUtils.finishSpanWithException(span, traceMap, EventMeshRetCode.EVENTMESH_RUNTIME_ERR.getErrMsg(), re);
                } catch (Exception e) {
                    httpServerLogger.error("processEventMeshRequest fail", re);
                }
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            super.channelReadComplete(ctx);
            ctx.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (null != cause) {
                logger.error("", cause);
            }
            if (null != ctx) {
                ctx.close();
            }
        }

        Map<String, String> extractFromRequest(HttpRequest httpRequest) {
            return null;
        }
    }

    private HttpEventWrapper parseHttpRequest(HttpRequest httpRequest) throws IOException {
        HttpEventWrapper httpEventWrapper = new HttpEventWrapper();
        httpEventWrapper.setHttpMethod(httpRequest.method().name());
        httpEventWrapper.setHttpVersion(httpRequest.protocolVersion().protocolName());
        httpEventWrapper.setRequestURI(httpRequest.uri());

        //parse http header
        for (String key : httpRequest.headers().names()) {
            httpEventWrapper.getHeaderMap().put(key, httpRequest.headers().get(key));
        }

        final long bodyDecodeStart = System.currentTimeMillis();
        //parse http body
        FullHttpRequest fullHttpRequest = (FullHttpRequest) httpRequest;
        final Map<String, Object> bodyMap = new HashMap<>();
        if (HttpMethod.GET == fullHttpRequest.method()) {
            QueryStringDecoder getDecoder = new QueryStringDecoder(fullHttpRequest.uri());
            getDecoder.parameters().forEach((key, value) -> bodyMap.put(key, value.get(0)));
        } else if (HttpMethod.POST == fullHttpRequest.method()) {

            if (StringUtils.contains(httpRequest.headers().get("Content-Type"), ContentType.APPLICATION_JSON.getMimeType())) {
                int length = fullHttpRequest.content().readableBytes();
                if (length > 0) {
                    byte[] body = new byte[length];
                    fullHttpRequest.content().readBytes(body);
                    JsonUtils.deserialize(new String(body), new TypeReference<Map<String, Object>>() {
                    }).forEach(bodyMap::put);
                }
            } else {
                HttpPostRequestDecoder decoder =
                    new HttpPostRequestDecoder(defaultHttpDataFactory, httpRequest);
                for (InterfaceHttpData parm : decoder.getBodyHttpDatas()) {
                    if (parm.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
                        Attribute data = (Attribute) parm;
                        bodyMap.put(data.getName(), data.getValue());
                    }
                }
                decoder.destroy();
            }

        } else {
            throw new RuntimeException("UnSupported Method " + fullHttpRequest.method());
        }

        byte[] requestBody = JsonUtils.serialize(bodyMap).getBytes(StandardCharsets.UTF_8);
        httpEventWrapper.setBody(requestBody);

        metrics.getSummaryMetrics().recordDecodeTimeCost(System.currentTimeMillis() - bodyDecodeStart);

        return httpEventWrapper;
    }

    class HttpConnectionHandler extends ChannelDuplexHandler {
        public final AtomicInteger connections = new AtomicInteger(0);

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
                httpServerLogger
                    .warn("client|http|channelActive|remoteAddress={}|msg={}", remoteAddress,
                        "too many client(20000) connect this eventMesh server");
                ctx.close();
                return;
            }

            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            connections.decrementAndGet();
            super.channelInactive(ctx);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                if (event.state().equals(IdleState.ALL_IDLE)) {
                    final String remoteAddress = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
                    httpServerLogger.info("client|http|userEventTriggered|remoteAddress={}|msg={}",
                        remoteAddress, evt.getClass().getName());
                    ctx.close();
                }
            }

            ctx.fireUserEventTriggered(evt);
        }
    }

    class HttpsServerInitializer extends ChannelInitializer<SocketChannel> {

        private final SSLContext sslContext;

        public HttpsServerInitializer(SSLContext sslContext) {
            this.sslContext = sslContext;
        }

        @Override
        protected void initChannel(SocketChannel channel) {
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
