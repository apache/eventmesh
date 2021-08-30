/*
 * Licensed to Apache Software Foundation (ASF) under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Apache Software Foundation (ASF) licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.eventmeth.server.http.handler;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.eventmesh.common.ThreadPoolFactory;
import org.apache.eventmesh.common.command.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.Header;
import org.apache.eventmeth.server.http.config.HttpProtocolConstants;
import org.apache.eventmeth.server.http.metrics.HTTPMetricsServer;
import org.apache.eventmeth.server.http.model.AsyncContext;
import org.apache.eventmeth.server.http.processor.HttpRequestProcessor;
import org.apache.eventmeth.server.http.utils.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

@ChannelHandler.Sharable
public class HttpChannelHandler extends SimpleChannelInboundHandler<HttpRequest> {

    public Logger httpLogger = LoggerFactory.getLogger("http");

    public DefaultHttpDataFactory defaultHttpDataFactory = new DefaultHttpDataFactory(false);

    protected Map<String/* request code */, Pair<HttpRequestProcessor, ThreadPoolExecutor>> processorTable =
            new ConcurrentHashMap<>(64);

    private ThreadPoolExecutor asyncContextCompleteHandler =
            ThreadPoolFactory.createThreadPoolExecutor(10, 10, "eventMesh-http-asyncContext-");

    public HTTPMetricsServer metrics;

    private AtomicBoolean started;

    public HttpChannelHandler(AtomicBoolean started, HTTPMetricsServer metrics) {
        this.started = started;
        this.metrics = metrics;
    }

    public void registerProcessor(String requestCode, HttpRequestProcessor httpRequestProcessor, ThreadPoolExecutor threadPoolExecutor) {
        processorTable.computeIfAbsent(requestCode, k -> Pair.of(httpRequestProcessor, threadPoolExecutor));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest httpRequest) {
        HttpPostRequestDecoder decoder = null;
        try {
            if (!httpRequest.decoderResult().isSuccess()) {
                sendError(ctx, HttpResponseStatus.BAD_REQUEST);
                return;
            }

            final HttpCommand requestCommand = new HttpCommand();

            httpRequest.headers().set(ProtocolKey.ClientInstanceKey.IP, HttpUtils.parseChannelRemoteAddr(ctx.channel()));

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

            HttpCommand responseCommand = null;

            if (!ProtocolVersion.contains(protocolVersion)) {
                responseCommand = requestCommand.createHttpCommandResponse(
                        EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg());
                sendResponse(ctx, responseCommand.httpResponse());
                return;
            }

            if (StringUtils.isBlank(requestCode)
                    || !StringUtils.isNumeric(requestCode)
                    || !RequestCode.contains(Integer.valueOf(requestCode))
                    || !processorTable.containsKey(requestCode)) {
                responseCommand = requestCommand.createHttpCommandResponse(
                        EventMeshRetCode.EVENTMESH_REQUESTCODE_INVALID.getRetCode(), EventMeshRetCode.EVENTMESH_REQUESTCODE_INVALID.getErrMsg());
                sendResponse(ctx, responseCommand.httpResponse());
                return;
            }

            if (!started.get()) {
                responseCommand = requestCommand.createHttpCommandResponse(
                        EventMeshRetCode.EVENTMESH_STOP.getRetCode(), EventMeshRetCode.EVENTMESH_STOP.getErrMsg());
                sendResponse(ctx, responseCommand.httpResponse());
                return;
            }

            try {
                requestCommand.setHeader(Header.buildHeader(requestCode, HttpUtils.parseHTTPHeader(httpRequest)));
                requestCommand.setBody(Body.buildBody(requestCode, bodyMap));
            } catch (Exception e) {
                responseCommand = requestCommand.createHttpCommandResponse(
                        EventMeshRetCode.EVENTMESH_RUNTIME_ERR.getRetCode(),
                        EventMeshRetCode.EVENTMESH_RUNTIME_ERR.getErrMsg() + e.getLocalizedMessage());
                sendResponse(ctx, responseCommand.httpResponse());
                return;
            }

            if (httpLogger.isDebugEnabled()) {
                httpLogger.debug("{}", requestCommand);
            }

            AsyncContext<HttpCommand> asyncContext = new AsyncContext<HttpCommand>(requestCommand, responseCommand, asyncContextCompleteHandler);
            processEventMeshRequest(ctx, asyncContext);
        } catch (Exception ex) {
            httpLogger.error("AbrstractHTTPServer.HTTPHandler.channelRead0 err", ex);
        } finally {
            try {
                decoder.destroy();
            } catch (Exception e) {
            }
        }
    }

    private void processEventMeshRequest(final ChannelHandlerContext ctx,
                                         final AsyncContext<HttpCommand> asyncContext) {
        Pair<HttpRequestProcessor, ThreadPoolExecutor> pair = processorTable.get(asyncContext.getRequest().getRequestCode());
        if (pair == null) {
            httpLogger.error("request: {} is not supported", asyncContext.getRequest().getRequestCode());
            return;
        }
        try {
            HttpRequestProcessor processor = pair.getKey();
            ThreadPoolExecutor executor = pair.getValue();
            executor.submit(() -> {
                try {
                    if (processor.rejectRequest()) {
                        HttpCommand responseCommand = asyncContext.getRequest().createHttpCommandResponse(
                                EventMeshRetCode.EVENTMESH_REJECT_BY_PROCESSOR_ERROR.getRetCode(),
                                EventMeshRetCode.EVENTMESH_REJECT_BY_PROCESSOR_ERROR.getErrMsg()
                        );
                        asyncContext.onComplete(responseCommand);
                        if (asyncContext.isComplete()) {
                            if (httpLogger.isDebugEnabled()) {
                                httpLogger.debug("{}", asyncContext.getResponse());
                            }
                            sendResponse(ctx, responseCommand.httpResponse());
                        }
                        return;
                    }

                    processor.processRequest(ctx, asyncContext);
                    if (!asyncContext.isComplete()) {
                        return;
                    }

                    metrics.summaryMetrics.recordHTTPReqResTimeCost(System.currentTimeMillis() - asyncContext.getRequest().getReqTime());

                    if (httpLogger.isDebugEnabled()) {
                        httpLogger.debug("{}", asyncContext.getResponse());
                    }

                    sendResponse(ctx, asyncContext.getResponse().httpResponse());
                } catch (Exception e) {
                    httpLogger.error("process error", e);
                }
            });
        } catch (RejectedExecutionException re) {
            HttpCommand responseCommand = asyncContext.getRequest().createHttpCommandResponse(
                    EventMeshRetCode.OVERLOAD.getRetCode(), EventMeshRetCode.OVERLOAD.getErrMsg());
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
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (null != cause) cause.printStackTrace();
        if (null != ctx) ctx.close();
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        response.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN + "; charset=" + HttpProtocolConstants.DEFAULT_CHARSET);
        response.headers().add(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendResponse(ChannelHandlerContext ctx,
                              DefaultFullHttpResponse response) {
        ctx.writeAndFlush(response).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                httpLogger.warn("send response to [{}] fail, will close this channel", HttpUtils.parseChannelRemoteAddr(f.channel()));
                f.channel().close();
            }
        });
    }
}
