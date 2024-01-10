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
import org.apache.eventmesh.common.enums.HttpMethod;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.metrics.api.model.HttpSummaryMetrics;
import org.apache.eventmesh.metrics.api.model.TcpSummaryMetrics;
import org.apache.eventmesh.runtime.admin.controller.HttpHandlerManager;
import org.apache.eventmesh.runtime.admin.response.Error;
import org.apache.eventmesh.runtime.admin.response.GetMetricsResponse;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.common.EventHttpHandler;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

import com.sun.net.httpserver.HttpExchange;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the {@code /metrics} endpoint,
 * corresponding to the {@code eventmesh-dashboard} path {@code /metrics}.
 * <p>
 * This handler is responsible for retrieving summary information of metrics,
 * including HTTP and TCP metrics.
 *
 * @see AbstractHttpHandler
 */

@Slf4j
@EventHttpHandler(path = "/metrics")
public class MetricsHandler extends AbstractHttpHandler {

    private final HttpSummaryMetrics httpSummaryMetrics;
    private final TcpSummaryMetrics tcpSummaryMetrics;

    /**
     * Constructs a new instance with the provided EventMesh server instance and HTTP handler manager.
     *
     * @param eventMeshHTTPServer the HTTP server instance of EventMesh
     * @param eventMeshTcpServer the TCP server instance of EventMesh
     * @param httpHandlerManager Manages the registration of {@linkplain com.sun.net.httpserver.HttpHandler HttpHandler}
     *                           for an {@link com.sun.net.httpserver.HttpServer HttpServer}.
     */
    public MetricsHandler(EventMeshHTTPServer eventMeshHTTPServer,
        EventMeshTCPServer eventMeshTcpServer,
        HttpHandlerManager httpHandlerManager) {
        super(httpHandlerManager);
        this.httpSummaryMetrics = eventMeshHTTPServer.getMetrics().getSummaryMetrics();
        this.tcpSummaryMetrics = eventMeshTcpServer.getEventMeshTcpMonitor().getTcpSummaryMetrics();
    }

    /**
     * Handles the OPTIONS request first for {@code /metrics}.
     * <p>
     * This method adds CORS (Cross-Origin Resource Sharing) response headers to
     * the {@link HttpExchange} object and sends a 200 status code.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    void preflight(HttpExchange httpExchange) throws IOException {
        httpExchange.getResponseHeaders().add(EventMeshConstants.HANDLER_ORIGIN, "*");
        httpExchange.getResponseHeaders().add(EventMeshConstants.HANDLER_METHODS, "*");
        httpExchange.getResponseHeaders().add(EventMeshConstants.HANDLER_HEADERS, "*");
        httpExchange.getResponseHeaders().add(EventMeshConstants.HANDLER_AGE, EventMeshConstants.MAX_AGE);
        httpExchange.sendResponseHeaders(200, 0);
        OutputStream out = httpExchange.getResponseBody();
        out.close();
    }

    /**
     * Handles the GET request for {@code /metrics}.
     * <p>
     * This method retrieves the EventMesh metrics summary and returns it as a JSON response.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    void get(HttpExchange httpExchange) throws IOException {
        OutputStream out = httpExchange.getResponseBody();
        httpExchange.getResponseHeaders().add(EventMeshConstants.CONTENT_TYPE, EventMeshConstants.APPLICATION_JSON);
        httpExchange.getResponseHeaders().add(EventMeshConstants.HANDLER_ORIGIN, "*");

        try {
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
                tcpSummaryMetrics.getSubTopicNum());
            String result = JsonUtils.toJSONString(getMetricsResponse);
            byte[] bytes = Objects.requireNonNull(result).getBytes(Constants.DEFAULT_CHARSET);
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
            byte[] bytes = Objects.requireNonNull(result).getBytes(Constants.DEFAULT_CHARSET);
            httpExchange.sendResponseHeaders(500, bytes.length);
            out.write(bytes);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    log.warn("out close failed...", e);
                }
            }
        }
    }

    /**
     * Handles the HTTP requests for {@code /metrics}.
     * <p>
     * It delegates the handling to {@code preflight()} or {@code get()} methods
     * based on the request method type (OPTIONS or GET).
     * <p>
     * This method is an implementation of {@linkplain com.sun.net.httpserver.HttpHandler#handle(HttpExchange)  HttpHandler.handle()}
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        switch (HttpMethod.valueOf(httpExchange.getRequestMethod())) {
            case OPTIONS:
                preflight(httpExchange);
                break;
            case GET:
                get(httpExchange);
                break;
            default:
                break;
        }
    }
}
