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

package org.apache.eventmesh.metrics.prometheus.metrics;

import static org.apache.eventmesh.metrics.prometheus.utils.PrometheusExporterConstants.METRICS_TCP_PREFIX;
import static org.apache.eventmesh.metrics.prometheus.utils.PrometheusExporterConstants.TCP;
import static org.apache.eventmesh.metrics.prometheus.utils.PrometheusExporterUtils.join;

import org.apache.eventmesh.metrics.api.model.TcpSummaryMetrics;

public class PrometheusTcpExporter extends PrometheusExporter<TcpSummaryMetrics> {

    public PrometheusTcpExporter() {
        //retryQueueSize
        paramPairs.put(join("retry.queue.size", "get size of retry queue."),
            TcpSummaryMetrics::getRetrySize);
        //client2eventMeshTPS
        paramPairs.put(join("server.tps", "get tps of client to eventMesh."),
            TcpSummaryMetrics::getClient2eventMeshTPS);
        //eventMesh2mqTPS
        paramPairs.put(join("mq.provider.tps", "get tps of eventMesh to mq."),
            TcpSummaryMetrics::getEventMesh2mqTPS);
        //mq2eventMeshTPS
        paramPairs.put(join("mq.consumer.tps", "get tps of mq to eventMesh."),
            TcpSummaryMetrics::getMq2eventMeshTPS);
        //eventMesh2clientTPS
        paramPairs.put(join("client.tps", "get tps of eventMesh to client."),
            TcpSummaryMetrics::getEventMesh2clientTPS);
        //allTPS
        paramPairs.put(join("all.tps", "get all TPS."), TcpSummaryMetrics::getAllTPS);
        //EventMeshTcpConnectionHandler.connections
        paramPairs.put(join("connection.num", "EventMeshTcpConnectionHandler.connections."),
            TcpSummaryMetrics::getAllConnections);
        //subTopicNum
        paramPairs.put(join("sub.topic.num", "get sub topic num."), TcpSummaryMetrics::getSubTopicNum);
    }

    @Override
    protected String getMetricName(String[] metricInfo) {
        return METRICS_TCP_PREFIX + metricInfo[0];
    }

    @Override
    protected String getMetricDescription(String[] metricInfo) {
        return metricInfo[1];
    }

    @Override
    protected String getProtocol() {
        return TCP.getValue();
    }

}
