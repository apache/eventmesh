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

package org.apache.eventmesh.runtime.core.protocol.http.processor;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.http.HttpEventWrapper;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.boot.HTTPTrace;
import org.apache.eventmesh.runtime.boot.HTTPTrace.TraceOperation;
import org.apache.eventmesh.runtime.common.EventMeshTrace;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.metrics.http.HTTPMetricsServer;
import org.apache.eventmesh.runtime.util.HttpResponseUtils;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.ReferenceCountUtil;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.Getter;
import lombok.Setter;

public class HandlerService {

    private Logger httpServerLogger = LoggerFactory.getLogger(this.getClass());

    private Logger httpLogger = LoggerFactory.getLogger("http");

    private Map<String, ProcessorWrapper> httpProcessorMap = new ConcurrentHashMap<>();

    @Setter
    private HTTPMetricsServer metrics;

    @Setter
    private HTTPTrace httpTrace;

    public DefaultHttpDataFactory defaultHttpDataFactory = new DefaultHttpDataFactory(false);


    public void init() {
        httpServerLogger.info("HandlerService start ");
    }

    public void register(HttpProcessor httpProcessor, ThreadPoolExecutor threadPoolExecutor) {
        for (String path : httpProcessor.paths()) {
            this.register(path, httpProcessor, threadPoolExecutor);
        }
    }

    public void register(String path, HttpProcessor httpProcessor, ThreadPoolExecutor threadPoolExecutor) {

        if (httpProcessorMap.containsKey(path)) {
            throw new RuntimeException(String.format("HandlerService path %s repeat, repeat processor is %s ",
                    path, httpProcessor.getClass().getSimpleName()));
        }
        ProcessorWrapper processorWrapper = new ProcessorWrapper();
        processorWrapper.threadPoolExecutor = threadPoolExecutor;
        if (httpProcessor instanceof AsyncHttpProcessor) {
            processorWrapper.async = (AsyncHttpProcessor) httpProcessor;
        }
        processorWrapper.httpProcessor = httpProcessor;
        processorWrapper.traceEnabled = httpProcessor.getClass().getAnnotation(EventMeshTrace.class).isEnable();
        httpProcessorMap.put(path, processorWrapper);
        httpServerLogger.info("path is {}  processor name is {}", path, httpProcessor.getClass().getSimpleName());
    }

    public boolean isProcessorWrapper(HttpRequest httpRequest) {
        return Objects.nonNull(this.getProcessorWrapper(httpRequest));
    }

    private ProcessorWrapper getProcessorWrapper(HttpRequest httpRequest) {
        String uri = httpRequest.uri();
        for (Entry<String, ProcessorWrapper> e : httpProcessorMap.entrySet()) {
            if (uri.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * @param httpRequest
     */
    public void handler(ChannelHandlerContext ctx, HttpRequest httpRequest, ThreadPoolExecutor asyncContextCompleteHandler) {

        ProcessorWrapper processorWrapper = getProcessorWrapper(httpRequest);
        if (Objects.isNull(processorWrapper)) {
            this.sendResponse(ctx, httpRequest, HttpResponseUtils.createNotFound());
            return;
        }
        TraceOperation traceOperation = httpTrace.getTraceOperation(httpRequest, ctx.channel(), processorWrapper.traceEnabled);
        try {
            HandlerSpecific handlerSpecific = new HandlerSpecific();
            handlerSpecific.request = httpRequest;
            handlerSpecific.ctx = ctx;
            handlerSpecific.traceOperation = traceOperation;
            handlerSpecific.asyncContext = new AsyncContext<>(new HttpEventWrapper(), null, asyncContextCompleteHandler);
            processorWrapper.threadPoolExecutor.execute(handlerSpecific);
        } catch (Exception e) {
            httpServerLogger.error(e.getMessage(), e);
            this.sendResponse(ctx, httpRequest, HttpResponseUtils.createInternalServerError());
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpRequest request, HttpResponse response) {
        this.sendResponse(ctx, request, response, true);
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpRequest httpRequest, HttpResponse response, boolean isClose) {
        ReferenceCountUtil.release(httpRequest);
        ctx.writeAndFlush(response).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                httpLogger.warn("send response to [{}] fail, will close this channel",
                        RemotingHelper.parseChannelRemoteAddr(f.channel()));
            }
            if (isClose) {
            	f.channel().close();
            }
        });
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
                    JsonUtils.deserialize(new String(body, Constants.DEFAULT_CHARSET), new TypeReference<Map<String, Object>>() {
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

    @Getter
    @Setter
    class HandlerSpecific implements Runnable {

        private TraceOperation traceOperation;

        private ChannelHandlerContext ctx;

        private HttpRequest request;

        private HttpResponse response;

        private AsyncContext<HttpEventWrapper> asyncContext;

        private Throwable exception;

        long requestTime = System.currentTimeMillis();

        private Map<String, Object> traceMap;

        private CloudEvent ce;


        public void run() {
            String processorKey = "/";
            for (String eventProcessorKey : httpProcessorMap.keySet()) {
                if (request.uri().startsWith(eventProcessorKey)) {
                    processorKey = eventProcessorKey;
                    break;
                }
            }
            ProcessorWrapper processorWrapper = HandlerService.this.httpProcessorMap.get(processorKey);
            try {
                this.preHandler();
                if (processorWrapper.httpProcessor instanceof AsyncHttpProcessor) {
                    // set actual async request
                    HttpEventWrapper httpEventWrapper = parseHttpRequest(request);
                    this.asyncContext.setRequest(httpEventWrapper);
                    processorWrapper.async.handler(this, request);
                    return;
                }
                response = processorWrapper.httpProcessor.handler(request);

                this.postHandler();
            } catch (Throwable e) {
                exception = e;
                // todo: according exception to generate response
                this.response = HttpResponseUtils.createInternalServerError();
                this.error();
            }
        }

        private void postHandler() {
            metrics.getSummaryMetrics().recordHTTPRequest();
            if (httpLogger.isDebugEnabled()) {
                httpLogger.debug("{}", request);
            }
            if (Objects.isNull(response)) {
                this.response = HttpResponseUtils.createSuccess();
            }
            this.traceOperation.endTrace(ce);
            HandlerService.this.sendResponse(ctx, this.request, this.response);
        }

        private void preHandler() {
            metrics.getSummaryMetrics().recordHTTPReqResTimeCost(System.currentTimeMillis() - requestTime);
            if (httpLogger.isDebugEnabled()) {
                httpLogger.debug("{}", response);
            }
        }

        private void error() {
            httpServerLogger.error(this.exception.getMessage(), this.exception);
            this.traceOperation.exceptionTrace(this.exception, this.traceMap);
            metrics.getSummaryMetrics().recordHTTPDiscard();
            metrics.getSummaryMetrics().recordHTTPReqResTimeCost(System.currentTimeMillis() - requestTime);
            HandlerService.this.sendResponse(ctx, this.request, this.response);
        }


        public void setResponseJsonBody(String body) {
            this.sendResponse(HttpResponseUtils.setResponseJsonBody(body, ctx));
        }

        public void setResponseTextBody(String body) {
            this.sendResponse(HttpResponseUtils.setResponseTextBody(body, ctx));
        }

        public void sendResponse(HttpResponse response) {
            this.response = response;
            this.postHandler();
        }

        public void sendResponse(Map<String, Object> responseHeaderMap, Map<String, Object> responseBodyMap) {
            try {
                HttpEventWrapper responseWrapper = asyncContext.getRequest().createHttpResponse(responseHeaderMap, responseBodyMap);
                asyncContext.onComplete(responseWrapper);
                this.response = asyncContext.getResponse().httpResponse();
                this.postHandler();
            } catch (Exception e) {
                this.exception = e;
                // todo: according exception to generate response
                this.response = HttpResponseUtils.createInternalServerError();
                this.error();
            }

        }

        // for error response
        public void sendErrorResponse(EventMeshRetCode retCode, Map<String, Object> responseHeaderMap, Map<String, Object> responseBodyMap,
                                      Map<String, Object> traceMap) {
            this.traceMap = traceMap;
            try {
                responseBodyMap.put("retCode", retCode.getRetCode());
                responseBodyMap.put("retMsg", retCode.getErrMsg());
                HttpEventWrapper responseWrapper = asyncContext.getRequest().createHttpResponse(responseHeaderMap, responseBodyMap);
                asyncContext.onComplete(responseWrapper);
                this.exception = new RuntimeException(retCode.getErrMsg());
                this.response = asyncContext.getResponse().httpResponse();
                this.error();
            } catch (Exception e) {
                this.exception = e;
                // todo: according exception to generate response
                this.response = HttpResponseUtils.createInternalServerError();
                this.error();
            }
        }

        /**
         * @param count
         */
        public void recordSendBatchMsgFailed(int count) {
            metrics.getSummaryMetrics().recordSendBatchMsgFailed(1);
        }

    }


    private static class ProcessorWrapper {

        private ThreadPoolExecutor threadPoolExecutor;

        private HttpProcessor httpProcessor;

        private AsyncHttpProcessor async;

        private boolean traceEnabled;
    }

}
