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
import org.apache.eventmesh.runtime.admin.handler.AbstractHttpHandler;
import org.apache.eventmesh.runtime.admin.response.v1.GetMetricsResponse;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.common.EventMeshHttpHandler;
import org.apache.eventmesh.runtime.metrics.http.HttpMetrics;
import org.apache.eventmesh.runtime.metrics.tcp.TcpMetrics;

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

    private final HttpMetrics httpMetrics;
    private final TcpMetrics tcpMetrics;

    /**
     * Constructs a new instance with the provided EventMesh server instance.
     *
     * @param eventMeshHTTPServer the HTTP server instance of EventMesh
     * @param eventMeshTcpServer  the TCP server instance of EventMesh
     */
    public MetricsHandler(EventMeshHTTPServer eventMeshHTTPServer,
        EventMeshTCPServer eventMeshTcpServer) {
        super();
        this.httpMetrics = eventMeshHTTPServer.getEventMeshHttpMetricsManager().getHttpMetrics();
        this.tcpMetrics = eventMeshTcpServer.getEventMeshTcpMetricsManager().getTcpMetrics();
    }

    @Override
    protected void get(HttpRequest httpRequest, ChannelHandlerContext ctx) throws IOException {
        GetMetricsResponse getMetricsResponse = new GetMetricsResponse(
            httpMetrics.maxHTTPTPS(),
            httpMetrics.avgHTTPTPS(),
            httpMetrics.maxHTTPCost(),
            httpMetrics.avgHTTPCost(),
            httpMetrics.avgHTTPBodyDecodeCost(),
            httpMetrics.getHttpDiscard(),
            httpMetrics.maxSendBatchMsgTPS(),
            httpMetrics.avgSendBatchMsgTPS(),
            httpMetrics.getSendBatchMsgNumSum(),
            httpMetrics.getSendBatchMsgFailNumSum(),
            httpMetrics.getSendBatchMsgFailRate(),
            httpMetrics.getSendBatchMsgDiscardNumSum(),
            httpMetrics.maxSendMsgTPS(),
            httpMetrics.avgSendMsgTPS(),
            httpMetrics.getSendMsgNumSum(),
            httpMetrics.getSendMsgFailNumSum(),
            httpMetrics.getSendMsgFailRate(),
            httpMetrics.getReplyMsgNumSum(),
            httpMetrics.getReplyMsgFailNumSum(),
            httpMetrics.maxPushMsgTPS(),
            httpMetrics.avgPushMsgTPS(),
            httpMetrics.getHttpPushMsgNumSum(),
            httpMetrics.getHttpPushFailNumSum(),
            httpMetrics.getHttpPushMsgFailRate(),
            httpMetrics.maxHTTPPushLatency(),
            httpMetrics.avgHTTPPushLatency(),
            httpMetrics.getBatchMsgQueueSize(),
            httpMetrics.getSendMsgQueueSize(),
            httpMetrics.getPushMsgQueueSize(),
            httpMetrics.getHttpRetryQueueSize(),
            httpMetrics.avgBatchSendMsgCost(),
            httpMetrics.avgSendMsgCost(),
            httpMetrics.avgReplyMsgCost(),

            tcpMetrics.getRetrySize(),
            tcpMetrics.getClient2eventMeshTPS(),
            tcpMetrics.getEventMesh2mqTPS(),
            tcpMetrics.getMq2eventMeshTPS(),
            tcpMetrics.getEventMesh2clientTPS(),
            tcpMetrics.getAllTPS(),
            tcpMetrics.getAllConnections(),
            tcpMetrics.getSubTopicNum()
        );
        String result = JsonUtils.toJSONString(getMetricsResponse);
        writeJson(ctx, result);
    }
}
