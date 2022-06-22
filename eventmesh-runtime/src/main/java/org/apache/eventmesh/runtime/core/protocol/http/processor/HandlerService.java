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

import org.apache.eventmesh.runtime.boot.HTTPTrace;
import org.apache.eventmesh.runtime.boot.HTTPTrace.TraceOperation;
import org.apache.eventmesh.runtime.metrics.http.HTTPMetricsServer;
import org.apache.eventmesh.runtime.util.HttpResponseUtils;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import lombok.Setter;


public class HandlerService {

    private Logger httpServerLogger = LoggerFactory.getLogger(this.getClass());

    private Logger httpLogger = LoggerFactory.getLogger("http");

    private Map<String, ProcessorWrapper> httpProcessorMap = new ConcurrentHashMap<>();

    @Setter
    private HTTPMetricsServer metrics;

    @Setter
    private HTTPTrace httpTrace;


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
        processorWrapper.httpProcessor = httpProcessor;
        if (httpProcessor instanceof AsynHttpProcessor) {
            processorWrapper.asyn = (AsynHttpProcessor) httpProcessor;
        } else {
            processorWrapper.httpProcessor = httpProcessor;
        }
        httpProcessorMap.put(path, processorWrapper);
        httpServerLogger.info("path is {}  proocessor name is", path, httpProcessor.getClass().getSimpleName());
    }

    public boolean isProcessorWrapper(HttpRequest httpRequest) {
        return this.getProcessorWrapper(httpRequest) == null ? false : true;
    }

    private ProcessorWrapper getProcessorWrapper(HttpRequest httpRequest) {
        String uri = httpRequest.uri();
        for (Entry<String, ProcessorWrapper> e : httpProcessorMap.entrySet()) {
            if (e.getKey().startsWith(uri)) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * @param httpRequest
     */
    public void handler(ChannelHandlerContext ctx, HttpRequest httpRequest) {


        TraceOperation traceOperation = httpTrace.getTraceOperation(httpRequest, ctx.channel());

        ProcessorWrapper processorWrapper = getProcessorWrapper(httpRequest);
        if (Objects.isNull(processorWrapper)) {
            this.sendResponse(ctx, HttpResponseUtils.createNotFound());
        }
        try {
            HandlerSpecific handlerSpecific = new HandlerSpecific();
            handlerSpecific.httpRequest = httpRequest;
            handlerSpecific.ctx = ctx;
            handlerSpecific.traceOperation = traceOperation;
            processorWrapper.threadPoolExecutor.execute(handlerSpecific);
        } catch (Exception e) {
            httpServerLogger.error(e.getMessage(), e);
            this.sendResponse(ctx, HttpResponseUtils.createInternalServerError());
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpResponse response) {
        this.sendResponse(ctx, response, true);
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpResponse response, boolean isClose) {
        ctx.writeAndFlush(response).addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                httpLogger.warn("send response to [{}] fail, will close this channel",
                        RemotingHelper.parseChannelRemoteAddr(f.channel()));
                if (isClose) {
                    f.channel().close();
                }
            }
        });
    }

    class HandlerSpecific implements Runnable {

        private TraceOperation traceOperation;

        private ChannelHandlerContext ctx;

        private HttpRequest httpRequest;

        private HttpResponse response;

        private Throwable exception;

        long requestTime = System.currentTimeMillis();


        public void run() {
            ProcessorWrapper processorWrapper = HandlerService.this.httpProcessorMap.get(httpRequest.uri());
            try {
                this.postHandler();
                if (Objects.isNull(processorWrapper.httpProcessor)) {
                    processorWrapper.asyn.handler(this, httpRequest);
                    return;
                }
                response = processorWrapper.httpProcessor.handler(httpRequest);

                this.preHandler();
            } catch (Throwable e) {
                httpServerLogger.error("{},{}");
                httpServerLogger.error(e.getMessage(), e);
                exception = e;
                this.errer();
            }
        }

        private void preHandler() {
            metrics.getSummaryMetrics().recordHTTPRequest();
            if (httpLogger.isDebugEnabled()) {
                httpLogger.debug("{}", httpRequest);
            }
            if (Objects.isNull(response)) {
                this.response = HttpResponseUtils.createSuccess();
            }
            this.traceOperation.endTrace();
            this.sendResponse(this.response);
        }

        private void postHandler() {
            metrics.getSummaryMetrics().recordHTTPReqResTimeCost(System.currentTimeMillis() - requestTime);
            if (httpLogger.isDebugEnabled()) {
                httpLogger.debug("{}", response);
            }
        }

        private void errer() {
            this.traceOperation.exceptionTrace(this.exception);
            metrics.getSummaryMetrics().recordHTTPDiscard();
            metrics.getSummaryMetrics().recordHTTPReqResTimeCost(System.currentTimeMillis() - requestTime);
            this.sendResponse(HttpResponseUtils.createInternalServerError());
        }


        public void setResponseJsonBody(String body) {
            this.sendResponse(HttpResponseUtils.setResponseJsonBody(body, ctx));
        }

        public void setResponseTextBody(String body) {
            this.sendResponse(HttpResponseUtils.setResponseTextBody(body, ctx));
        }

        public void sendResponse(HttpResponse response) {
            this.response = response;
            this.preHandler();
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

        private AsynHttpProcessor asyn;
    }

}
