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

import static org.apache.eventmesh.common.Constants.GRPC;
import static org.apache.eventmesh.metrics.prometheus.utils.PrometheusExporterConstants.METRICS_GRPC_PREFIX;
import static org.apache.eventmesh.metrics.prometheus.utils.PrometheusExporterUtils.join;
import static org.apache.eventmesh.metrics.prometheus.utils.PrometheusExporterUtils.observeOfValue;

import org.apache.eventmesh.metrics.api.model.GrpcSummaryMetrics;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.Meter;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PrometheusGrpcExporter {

    /**
     * Map structure : [metric name, description of name] -> the method of get corresponding metric.
     */
    private Map<String[], Function<GrpcSummaryMetrics, Number>> paramPairs;

    static {
        paramPairs = new HashMap<String[], Function<GrpcSummaryMetrics, Number>>() {

            {
                put(join("sub.topic.num", "get sub topic num."), GrpcSummaryMetrics::getSubscribeTopicNum);
                put(join("retry.queue.size", "get size of retry queue."), GrpcSummaryMetrics::getRetrySize);

                put(join("server.tps", "get size of retry queue."), GrpcSummaryMetrics::getClient2EventMeshTPS);
                put(join("client.tps", "get tps of eventMesh to mq."), GrpcSummaryMetrics::getEventMesh2ClientTPS);

                put(join("mq.provider.tps", "get tps of eventMesh to mq."), GrpcSummaryMetrics::getEventMesh2MqTPS);
                put(join("mq.consumer.tps", "get tps of eventMesh to mq."), GrpcSummaryMetrics::getMq2EventMeshTPS);
            }
        };
    }

    public static void export(final String meterName, final GrpcSummaryMetrics summaryMetrics) {
        final Meter meter = GlobalMeterProvider.getMeter(meterName);

        paramPairs.forEach(
            (metricInfo, getMetric) -> observeOfValue(meter, METRICS_GRPC_PREFIX + metricInfo[0], metricInfo[1], GRPC, summaryMetrics, getMetric));
    }
}
