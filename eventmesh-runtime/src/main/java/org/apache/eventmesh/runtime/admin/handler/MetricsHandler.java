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

package org.apache.eventmesh.runtime.admin.handler;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.metrics.api.model.HttpSummaryMetrics;
import org.apache.eventmesh.metrics.api.model.TcpSummaryMetrics;
import org.apache.eventmesh.runtime.admin.controller.HttpHandlerManager;
import org.apache.eventmesh.runtime.admin.response.Error;
import org.apache.eventmesh.runtime.admin.response.GetMetricsResponse;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.common.EventHttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;


import com.sun.net.httpserver.HttpExchange;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@EventHttpHandler(path = "/metrics")
public class MetricsHandler extends AbstractHttpHandler {

    private final HttpSummaryMetrics httpSummaryMetrics;
    private final TcpSummaryMetrics tcpSummaryMetrics;

    public MetricsHandler(EventMeshHTTPServer eventMeshHTTPServer,
        EventMeshTCPServer eventMeshTcpServer,
        HttpHandlerManager httpHandlerManager) {
        super(httpHandlerManager);
        this.httpSummaryMetrics = eventMeshHTTPServer.getMetrics().getSummaryMetrics();
        this.tcpSummaryMetrics = eventMeshTcpServer.getEventMeshTcpMonitor().getTcpSummaryMetrics();
    }

    /**
     * OPTIONS /metrics
     */
    void preflight(HttpExchange httpExchange) throws IOException {
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Methods", "*");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
        httpExchange.getResponseHeaders().add("Access-Control-Max-Age", "86400");
        httpExchange.sendResponseHeaders(200, 0);
        OutputStream out = httpExchange.getResponseBody();
        out.close();
    }

    /**
     * GET /metrics Return a response that contains a summary of metrics
     */
    void get(HttpExchange httpExchange) throws IOException {
        try (OutputStream out = httpExchange.getResponseBody()) {
            httpExchange.getResponseHeaders().add("Content-Type", "application/json");
            httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            GetMetricsResponse getMetricsResponse = new GetMetricsResponse(
                httpSummaryMetrics.maxHTTPTPS(),
                httpSummaryMetrics.avgHTTPTPS(),
                httpSummaryMetrics.maxHTTPCost(),
                httpSummaryMetrics.avgHTTPCost(),
                httpSummaryMetrics.avgHTTPBodyDecodeCost(),
                httpSummaryMetrics.getHttpDiscard(),
                httpSummaryMetrics.maxSendBatchMsgTPS(),
                httpSummaryMetrics.avgSendBatchMsgTPS(),
                httpSummaryMetrics.getSendBatchMsgNumSum(),
                httpSummaryMetrics.getSendBatchMsgFailNumSum(),
                httpSummaryMetrics.getSendBatchMsgFailRate(),
                httpSummaryMetrics.getSendBatchMsgDiscardNumSum(),
                httpSummaryMetrics.maxSendMsgTPS(),
                httpSummaryMetrics.avgSendMsgTPS(),
                httpSummaryMetrics.getSendMsgNumSum(),
                httpSummaryMetrics.getSendMsgFailNumSum(),
                httpSummaryMetrics.getSendMsgFailRate(),
                httpSummaryMetrics.getReplyMsgNumSum(),
                httpSummaryMetrics.getReplyMsgFailNumSum(),
                httpSummaryMetrics.maxPushMsgTPS(),
                httpSummaryMetrics.avgPushMsgTPS(),
                httpSummaryMetrics.getHttpPushMsgNumSum(),
                httpSummaryMetrics.getHttpPushFailNumSum(),
                httpSummaryMetrics.getHttpPushMsgFailRate(),
                httpSummaryMetrics.maxHTTPPushLatency(),
                httpSummaryMetrics.avgHTTPPushLatency(),
                httpSummaryMetrics.getBatchMsgQueueSize(),
                httpSummaryMetrics.getSendMsgQueueSize(),
                httpSummaryMetrics.getPushMsgQueueSize(),
                httpSummaryMetrics.getHttpRetryQueueSize(),
                httpSummaryMetrics.avgBatchSendMsgCost(),
                httpSummaryMetrics.avgSendMsgCost(),
                httpSummaryMetrics.avgReplyMsgCost(),

                tcpSummaryMetrics.getRetrySize(),
                tcpSummaryMetrics.getClient2eventMeshTPS(),
                tcpSummaryMetrics.getEventMesh2mqTPS(),
                tcpSummaryMetrics.getMq2eventMeshTPS(),
                tcpSummaryMetrics.getEventMesh2clientTPS(),
                tcpSummaryMetrics.getAllTPS(),
                tcpSummaryMetrics.getAllConnections(),
                tcpSummaryMetrics.getSubTopicNum()
            );
            String result = JsonUtils.toJSONString(getMetricsResponse);
            byte[] bytes = result.getBytes(Constants.DEFAULT_CHARSET);
            httpExchange.sendResponseHeaders(200, bytes.length);
            out.write(bytes);
        } catch (Exception e) {
            StringWriter writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            e.printStackTrace(printWriter);
            printWriter.flush();
            String stackTrace = writer.toString();

            Error error = new Error(e.toString(), stackTrace);
            String result = JsonUtils.toJSONString(error);
            byte[] bytes = result.getBytes(Constants.DEFAULT_CHARSET);
            httpExchange.sendResponseHeaders(500, 0);
            log.error(result, e);
        }
    }


    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        if ("OPTIONS".equals(httpExchange.getRequestMethod())) {
            preflight(httpExchange);
        }
        if ("GET".equals(httpExchange.getRequestMethod())) {
            get(httpExchange);
        }
    }
}
