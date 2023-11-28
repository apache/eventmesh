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

import static org.apache.eventmesh.common.Constants.TCP;
import static org.apache.eventmesh.metrics.prometheus.utils.PrometheusExporterConstants.METRICS_TCP_PREFIX;
import static org.apache.eventmesh.metrics.prometheus.utils.PrometheusExporterUtils.join;
import static org.apache.eventmesh.metrics.prometheus.utils.PrometheusExporterUtils.observeOfValue;

import org.apache.eventmesh.metrics.api.model.TcpSummaryMetrics;

import java.util.Map;
import java.util.function.Function;

import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.Meter;

import com.google.common.collect.ImmutableMap;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PrometheusTcpExporter {

    /**
     * Map structure : [metric name, description of name] -> the method of get corresponding metric.
     */
    private final Map<String[], Function<TcpSummaryMetrics, Number>> paramPairs = ImmutableMap
        .<String[], Function<TcpSummaryMetrics, Number>>builder()
        // retryQueueSize
        .put(join("retry.queue.size", "get size of retry queue."), TcpSummaryMetrics::getRetrySize)
        // client2eventMeshTPS
        .put(join("server.tps", "get tps of client to eventMesh."), TcpSummaryMetrics::getClient2eventMeshTPS)
        // eventMesh2mqTPS
        .put(join("mq.provider.tps", "get tps of eventMesh to mq."), TcpSummaryMetrics::getEventMesh2mqTPS)
        // mq2eventMeshTPS
        .put(join("mq.consumer.tps", "get tps of mq to eventMesh."), TcpSummaryMetrics::getMq2eventMeshTPS)
        // eventMesh2clientTPS
        .put(join("client.tps", "get tps of eventMesh to client."), TcpSummaryMetrics::getEventMesh2clientTPS)
        // allTPS
        .put(join("all.tps", "get all TPS."), TcpSummaryMetrics::getAllTPS)
        // EventMeshTcpConnectionHandler.connections
        .put(join("connection.num", "EventMeshTcpConnectionHandler.connections."), TcpSummaryMetrics::getAllConnections)
        // subTopicNum
        .put(join("sub.topic.num", "get sub topic num."), TcpSummaryMetrics::getSubTopicNum)
        .build();

    public void export(final String meterName, final TcpSummaryMetrics summaryMetrics) {
        final Meter meter = GlobalMeterProvider.getMeter(meterName);
        paramPairs.forEach(
            (metricInfo, getMetric) -> observeOfValue(meter, METRICS_TCP_PREFIX + metricInfo[0], metricInfo[1],
                TCP, summaryMetrics, getMetric, TcpSummaryMetrics.class));
    }
}
