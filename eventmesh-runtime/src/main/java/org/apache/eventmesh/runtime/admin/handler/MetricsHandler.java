package org.apache.eventmesh.runtime.admin.handler;

import org.apache.eventmesh.metrics.api.MetricsPluginFactory;
import org.apache.eventmesh.metrics.api.MetricsRegistry;
import org.apache.eventmesh.metrics.api.model.HttpSummaryMetrics;
import org.apache.eventmesh.metrics.api.model.TcpSummaryMetrics;
import org.apache.eventmesh.runtime.admin.response.Error;
import org.apache.eventmesh.runtime.admin.response.GetConfigurationResponse;
import org.apache.eventmesh.runtime.admin.response.GetMetricsResponse;
import org.apache.eventmesh.runtime.admin.utils.JsonUtils;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import org.apache.eventmesh.runtime.metrics.http.HTTPMetricsServer;
import org.apache.eventmesh.runtime.metrics.tcp.EventMeshTcpMonitor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

import org.assertj.core.util.Lists;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class MetricsHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationHandler.class);
    private final HttpSummaryMetrics httpSummaryMetrics;
    private final TcpSummaryMetrics tcpSummaryMetrics;

    public MetricsHandler(EventMeshHTTPServer eventMeshHTTPServer, EventMeshTCPServer eventMeshTcpServer) {
        this.httpSummaryMetrics = eventMeshHTTPServer.metrics.getSummaryMetrics();
        this.tcpSummaryMetrics = eventMeshTcpServer.getEventMeshTcpMonitor().getTcpSummaryMetrics();
    }

    /**
     * OPTION /metrics
     */
    void preflight(HttpExchange httpExchange) throws IOException {
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Method", "*");
        httpExchange.getResponseHeaders().add("Access-Control-Max-Age", "86400");
        httpExchange.sendResponseHeaders(200, 0);
        OutputStream out = httpExchange.getResponseBody();
        out.close();
    }
    /**
     * GET /metrics
     * Return a response that contains a summary of metrics
     */
    void get(HttpExchange httpExchange) throws IOException {
        OutputStream out = httpExchange.getResponseBody();
        httpExchange.getResponseHeaders().add("Content-Type", "application/json");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        try{
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
            String result = JsonUtils.toJson(getMetricsResponse);
            httpExchange.sendResponseHeaders(200, result.getBytes().length);
            out.write(result.getBytes());
        } catch (Exception e) {
            Error error = new Error(e.toString());
            String result = JsonUtils.toJson(error);
            httpExchange.sendResponseHeaders(500, result.getBytes().length);
            out.write(result.getBytes());
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    logger.warn("out close failed...", e);
                }
            }
        }
    }


    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        if (httpExchange.getRequestMethod().equals("OPTION")) {
            preflight(httpExchange);
        }
        if (httpExchange.getRequestMethod().equals("GET")) {
            get(httpExchange);
        }
    }
}
