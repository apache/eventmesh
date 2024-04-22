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

package org.apache.eventmesh.runtime.admin.handler.v1;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.metrics.api.model.HttpSummaryMetrics;
import org.apache.eventmesh.metrics.api.model.TcpSummaryMetrics;
import org.apache.eventmesh.runtime.admin.handler.AbstractHttpHandler;
import org.apache.eventmesh.runtime.admin.response.v1.GetMetricsResponse;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.common.EventMeshHttpHandler;

import java.io.IOException;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the {@code /metrics} endpoint, corresponding to the {@code eventmesh-dashboard} path {@code /metrics}.
 * <p>
 * This handler is responsible for retrieving summary information of metrics, including HTTP and TCP metrics.
 *
 * @see AbstractHttpHandler
 */

@Slf4j
@EventMeshHttpHandler(path = "/metrics")
public class MetricsHandler extends AbstractHttpHandler {

    private final HttpSummaryMetrics httpSummaryMetrics;
    private final TcpSummaryMetrics tcpSummaryMetrics;

    /**
     * Constructs a new instance with the provided EventMesh server instance.
     *
     * @param eventMeshHTTPServer the HTTP server instance of EventMesh
     * @param eventMeshTcpServer  the TCP server instance of EventMesh
     */
    public MetricsHandler(EventMeshHTTPServer eventMeshHTTPServer,
        EventMeshTCPServer eventMeshTcpServer) {
        super();
        this.httpSummaryMetrics = eventMeshHTTPServer.getMetrics().getSummaryMetrics();
        this.tcpSummaryMetrics = eventMeshTcpServer.getEventMeshTcpMonitor().getTcpSummaryMetrics();
    }

    @Override
    protected void get(HttpRequest httpRequest, ChannelHandlerContext ctx) throws IOException {
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
        writeJson(ctx, result);
    }
}
