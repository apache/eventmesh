package org.apache.eventmesh.runtime.boot;

import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.Header;
import org.apache.eventmesh.common.utils.AssertUtils;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.processor.HandlerService;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import org.apache.eventmesh.runtime.metrics.http.EventMeshHttpMetricsManager;
import org.apache.eventmesh.runtime.util.HttpRequestUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.apache.eventmesh.runtime.util.TraceUtils;
import org.apache.eventmesh.runtime.util.Utils;
import org.apache.eventmesh.trace.api.common.EventMeshTraceConstants;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
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
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.DiskAttribute;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import io.opentelemetry.api.trace.Span;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractMCPServer extends AbstractRemotingServer{
    private final transient EventMeshHTTPConfiguration eventMeshHttpConfiguration;

    @Getter
    @Setter
    private EventMeshHttpMetricsManager eventMeshHttpMetricsManager;

    private static final DefaultHttpDataFactory DEFAULT_HTTP_DATA_FACTORY = new DefaultHttpDataFactory(false);

    static {
        DiskAttribute.deleteOnExitTemporaryFile = false;
    }

    protected final transient AtomicBoolean started = new AtomicBoolean(false);

    @Getter
    private final transient boolean useTLS;

    @Getter
    @Setter
    private Boolean useTrace = false; // Determine whether trace is enabled

    private static final int MAX_CONNECTIONS = 20_000;

    /**
     * key: request type
     */
    protected final transient Map<String, HttpRequestProcessor> mcpRequestProcessorTable =
            new ConcurrentHashMap<>(64);

    private MCPConnectionHandler mcpConnectionHandler;
    private MCPDispatcher mcpDispatcher;

    @Setter
    @Getter
    private HandlerService handlerService;

    private final transient ThreadPoolExecutor asyncContextCompleteHandler =
            ThreadPoolFactory.createThreadPoolExecutor(10, 10, "MCP-asyncContext");

    @Getter
    private final HTTPThreadPoolGroup mcpThreadPoolGroup;

    public AbstractMCPServer(final int port, final boolean useTLS,
                             final EventMeshHTTPConfiguration eventMeshHttpConfiguration) {
        super();
        this.setPort(port);
        this.useTLS = useTLS;
        this.eventMeshHttpConfiguration = eventMeshHttpConfiguration;
        this.mcpThreadPoolGroup = new HTTPThreadPoolGroup(eventMeshHttpConfiguration);
    }

    protected void initSharableHandlers() {
        mcpConnectionHandler = new MCPConnectionHandler();
        mcpDispatcher = new MCPDispatcher();
    }

    public void init() throws Exception {
        super.init("mcp-server");
        initProducerManager();
        mcpThreadPoolGroup.initThreadPool();
    }

    @Override
    public CommonConfiguration getConfiguration() {
        return eventMeshHttpConfiguration;
    }

    @Override
    public void start() throws Exception {

        initSharableHandlers();

        final Thread thread = new Thread(() -> {
            final ServerBootstrap bootstrap = new ServerBootstrap();
            try {
                bootstrap.group(this.getBossGroup(), this.getIoGroup())
                        .channel(useEpoll() ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
                        .childHandler(new MCPServerInitializer(useTLS ? SSLContextFactory.getSslContext(eventMeshHttpConfiguration) : null))
                        .childOption(ChannelOption.SO_KEEPALIVE, Boolean.TRUE);

                log.info("MCPServer[port={}] started.", this.getPort());

                bootstrap.bind(this.getPort())
                        .channel()
                        .closeFuture()
                        .sync();
            } catch (Exception e) {
                log.error("MCPServer start error!", e);
                try {
                    shutdown();
                } catch (Exception ex) {
                    log.error("MCPServer shutdown error!", ex);
                }
                System.exit(-1);
            }
        }, "MCP-server");
        thread.setDaemon(true);
        thread.start();
        started.compareAndSet(false, true);
    }

    @Override
    public void shutdown() throws Exception {
        super.shutdown();
        mcpThreadPoolGroup.shutdownThreadPool();
        started.compareAndSet(true, false);
    }

    /**
     * Registers the processors required by the runtime module
     */
    public void registerProcessor(final Integer requestCode, final HttpRequestProcessor processor) {
        AssertUtils.notNull(requestCode, "requestCode can't be null");
        AssertUtils.notNull(processor, "processor can't be null");
        this.mcpRequestProcessorTable.putIfAbsent(requestCode.toString(), processor);
    }

    /**
     * Validate request, return error status.
     *
     * @param httpRequest
     * @return if request is validated return null else return error status
     */
    private HttpResponseStatus validateMCPRequest(final HttpRequest httpRequest) {
        if (!started.get()) {
            return HttpResponseStatus.SERVICE_UNAVAILABLE;
        }

        if (!httpRequest.decoderResult().isSuccess()) {
            return HttpResponseStatus.BAD_REQUEST;
        }

        if (!HttpMethod.POST.equals(httpRequest.method())) {
            return HttpResponseStatus.METHOD_NOT_ALLOWED;
        }

        // MCP协议检查Content-Type
        String contentType = httpRequest.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (StringUtils.isBlank(contentType) || !contentType.contains("application/json")) {
            return HttpResponseStatus.BAD_REQUEST;
        }

        return null;
    }

    public void sendError(final ChannelHandlerContext ctx, final HttpResponseStatus status) {
        final FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        final HttpHeaders responseHeaders = response.headers();
        responseHeaders.add(
                HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        responseHeaders.add(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        responseHeaders.add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        ctx.channel().eventLoop().execute(() -> {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        });
    }

    public void sendResponse(final ChannelHandlerContext ctx, final DefaultFullHttpResponse response) {
        ctx.channel().eventLoop().execute(() -> {
            ctx.writeAndFlush(response).addListener((ChannelFutureListener) f -> {
                if (!f.isSuccess()) {
                    log.warn("send response to [{}] fail, will close this channel", RemotingHelper.parseChannelRemoteAddr(f.channel()));
                    f.channel().close();
                }
            });
        });
    }

    /**
     * Parse MCP request body to map
     *
     * @param httpRequest
     * @return
     */
    private Map<String, Object> parseMCPRequestBody(final HttpRequest httpRequest) throws IOException {
        return HttpRequestUtil.parseHttpRequestBody(httpRequest, () -> System.currentTimeMillis(),
                (startTime) -> eventMeshHttpMetricsManager.getHttpMetrics().recordDecodeTimeCost(System.currentTimeMillis() - startTime));
    }

    @Sharable
    private class MCPDispatcher extends ChannelInboundHandlerAdapter {

        /**
         * Is called for each message of type {@link HttpRequest}.
         *
         * @param ctx the {@link ChannelHandlerContext} which this {@link ChannelInboundHandlerAdapter} belongs to
         * @param msg the message to handle
         */
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof HttpRequest)) {
                return;
            }

            HttpRequest httpRequest = (HttpRequest) msg;

            if (Objects.nonNull(handlerService) && handlerService.isProcessorWrapper(httpRequest)) {
                handlerService.handler(ctx, httpRequest, asyncContextCompleteHandler);
                return;
            }

            try {

                Span span = null;
                injectMCPRequestHeader(ctx, httpRequest);

                final Map<String, Object> headerMap = Utils.parseHttpHeader(httpRequest);
                final HttpResponseStatus errorStatus = validateMCPRequest(httpRequest);
                if (errorStatus != null) {
                    sendError(ctx, errorStatus);
                    span = TraceUtils.prepareServerSpan(headerMap,
                            EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_SERVER_SPAN, false);
                    TraceUtils.finishSpanWithException(span, headerMap, errorStatus.reasonPhrase(), null);
                    return;
                }
                eventMeshHttpMetricsManager.getHttpMetrics().recordHTTPRequest();

                // process MCP request
                final HttpCommand requestCommand = new HttpCommand();
                final Map<String, Object> bodyMap = parseMCPRequestBody(httpRequest);

                final String requestCode = HttpMethod.POST.equals(httpRequest.method())
                        ? httpRequest.headers().get(ProtocolKey.REQUEST_CODE)
                        : MapUtils.getString(bodyMap, StringUtils.lowerCase(ProtocolKey.REQUEST_CODE), "");

                requestCommand.setHttpMethod(httpRequest.method().name());
                requestCommand.setHttpVersion(httpRequest.protocolVersion() == null ? ""
                        : httpRequest.protocolVersion().protocolName());
                requestCommand.setRequestCode(requestCode);

                HttpCommand responseCommand = null;

                if (StringUtils.isBlank(requestCode)
                        || !mcpRequestProcessorTable.containsKey(requestCode)
                        || !RequestCode.contains(Integer.valueOf(requestCode))) {
                    responseCommand =
                            requestCommand.createHttpCommandResponse(EventMeshRetCode.EVENTMESH_REQUESTCODE_INVALID);
                    sendResponse(ctx, responseCommand.httpResponse(HttpResponseStatus.BAD_REQUEST));

                    span = TraceUtils.prepareServerSpan(headerMap,
                            EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_SERVER_SPAN, false);
                    TraceUtils.finishSpanWithException(span, headerMap,
                            EventMeshRetCode.EVENTMESH_REQUESTCODE_INVALID.getErrMsg(), null);
                    return;
                }

                try {
                    requestCommand.setHeader(Header.buildHeader(requestCode, headerMap));
                    requestCommand.setBody(Body.buildBody(requestCode, bodyMap));
                } catch (Exception e) {
                    responseCommand = requestCommand.createHttpCommandResponse(EventMeshRetCode.EVENTMESH_RUNTIME_ERR);
                    sendResponse(ctx, responseCommand.httpResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR));

                    span = TraceUtils.prepareServerSpan(headerMap,
                            EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_SERVER_SPAN, false);
                    TraceUtils.finishSpanWithException(span, headerMap,
                            EventMeshRetCode.EVENTMESH_RUNTIME_ERR.getErrMsg(), e);
                    return;
                }

                log.debug("{}", requestCommand);

                final AsyncContext<HttpCommand> asyncContext =
                        new AsyncContext<>(requestCommand, responseCommand, asyncContextCompleteHandler);
                processMCPCommandRequest(ctx, asyncContext);

            } catch (Exception ex) {
                log.error("AbstractMCPServer.MCPDispatcher.channelRead error", ex);
                sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            } finally {
                ReferenceCountUtil.release(httpRequest);
            }
        }

        /**
         * Inject ip and protocol version for MCP.
         *
         * @param ctx
         * @param httpRequest
         */
        private void injectMCPRequestHeader(final ChannelHandlerContext ctx, final HttpRequest httpRequest) {
            final long startTime = System.currentTimeMillis();
            final HttpHeaders requestHeaders = httpRequest.headers();

            requestHeaders.set(EventMeshConstants.REQ_C2EVENTMESH_TIMESTAMP, startTime);
            if (StringUtils.isBlank(requestHeaders.get(ProtocolKey.VERSION))) {
                requestHeaders.set(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion());
            }

            requestHeaders.set(ProtocolKey.ClientInstanceKey.IP.getKey(),
                    RemotingHelper.parseChannelRemoteAddr(ctx.channel()));
            requestHeaders.set(EventMeshConstants.REQ_SEND_EVENTMESH_IP, eventMeshHttpConfiguration.getEventMeshServerIp());
        }

        private void processMCPCommandRequest(final ChannelHandlerContext ctx, final AsyncContext<HttpCommand> asyncContext) {
            final HttpCommand request = asyncContext.getRequest();
            final HttpRequestProcessor choosed = mcpRequestProcessorTable.get(request.getRequestCode());
            Runnable runnable = () -> {
                try {
                    final HttpRequestProcessor processor = choosed;
                    if (processor.rejectRequest()) {
                        final HttpCommand responseCommand =
                                request.createHttpCommandResponse(EventMeshRetCode.EVENTMESH_REJECT_BY_PROCESSOR_ERROR);
                        asyncContext.onComplete(responseCommand);

                        if (asyncContext.isComplete()) {
                            sendResponse(ctx, responseCommand.httpResponse());
                            log.debug("{}", asyncContext.getResponse());
                            final Map<String, Object> traceMap = asyncContext.getRequest().getHeader().toMap();
                            TraceUtils.finishSpanWithException(TraceUtils.prepareServerSpan(traceMap,

                                            EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_SERVER_SPAN,
                                            false),
                                    traceMap,
                                    EventMeshRetCode.EVENTMESH_REJECT_BY_PROCESSOR_ERROR.getErrMsg(), null);
                        }

                        return;
                    }

                    processor.processRequest(ctx, asyncContext);
                    if (!asyncContext.isComplete()) {
                        return;
                    }

                    log.debug("{}", asyncContext.getResponse());
                    eventMeshHttpMetricsManager.getHttpMetrics().recordHTTPReqResTimeCost(System.currentTimeMillis() - request.getReqTime());
                    sendResponse(ctx, asyncContext.getResponse().httpResponse());

                } catch (Exception e) {
                    log.error("process error", e);
                }
            };

            try {
                if (Objects.nonNull(choosed.executor())) {
                    choosed.executor().execute(() -> {
                        runnable.run();
                    });
                } else {
                    runnable.run();
                }

            } catch (RejectedExecutionException re) {
                asyncContext.onComplete(request.createHttpCommandResponse(EventMeshRetCode.OVERLOAD));
                eventMeshHttpMetricsManager.getHttpMetrics().recordHTTPDiscard();
                eventMeshHttpMetricsManager.getHttpMetrics().recordHTTPReqResTimeCost(System.currentTimeMillis() - request.getReqTime());
                try {
                    sendResponse(ctx, asyncContext.getResponse().httpResponse());

                    final Map<String, Object> traceMap = asyncContext.getRequest().getHeader().toMap();

                    TraceUtils.finishSpanWithException(
                            TraceUtils.prepareServerSpan(traceMap,
                                    EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_SERVER_SPAN,
                                    false),
                            traceMap,
                            EventMeshRetCode.EVENTMESH_RUNTIME_ERR.getErrMsg(),
                            re);
                } catch (Exception e) {
                    log.error("processEventMeshRequest fail", re);
                }
            }
        }

        @Override
        public void channelReadComplete(final ChannelHandlerContext ctx) throws Exception {
            super.channelReadComplete(ctx);
            ctx.flush();
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
            if (cause != null) {
                log.error("", cause);
            }

            if (ctx != null) {
                ctx.close();
            }
        }
    }

    @Sharable
    protected class MCPConnectionHandler extends ChannelDuplexHandler {

        public final transient AtomicInteger connections = new AtomicInteger(0);

        @Override
        public void channelActive(final ChannelHandlerContext ctx) throws Exception {
            if (connections.incrementAndGet() > MAX_CONNECTIONS) {
                log.warn("client|mcp|channelActive|remoteAddress={}|msg=too many client({}) connect this MCP server",
                        RemotingHelper.parseChannelRemoteAddr(ctx.channel()), MAX_CONNECTIONS);
                ctx.close();
                return;
            }
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
            connections.decrementAndGet();
            super.channelInactive(ctx);
        }

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
            if (evt instanceof IdleStateEvent) {
                final IdleStateEvent event = (IdleStateEvent) evt;
                if (event.state().equals(IdleState.ALL_IDLE)) {
                    final String remoteAddress = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
                    log.info("client|mcp|userEventTriggered|remoteAddress={}|msg={}", remoteAddress, evt.getClass().getName());
                    ctx.close();
                }
            }

            ctx.fireUserEventTriggered(evt);
        }
    }

    private class MCPServerInitializer extends ChannelInitializer<SocketChannel> {

        private final transient SSLContext sslContext;

        public MCPServerInitializer(final SSLContext sslContext) {
            this.sslContext = sslContext;
        }

        @Override
        protected void initChannel(final SocketChannel channel) {
            final ChannelPipeline pipeline = channel.pipeline();

            if (sslContext != null && useTLS) {
                final SSLEngine sslEngine = sslContext.createSSLEngine();
                sslEngine.setUseClientMode(false);
                pipeline.addFirst(getWorkerGroup(), "ssl", new SslHandler(sslEngine));
            }

            pipeline.addLast(getWorkerGroup(),
                    new HttpRequestDecoder(),
                    new HttpResponseEncoder(),
                    mcpConnectionHandler,
                    new HttpObjectAggregator(Integer.MAX_VALUE),
                    mcpDispatcher);
        }
    }
}
