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

import static org.apache.eventmesh.metrics.prometheus.utils.PrometheusExporterConstants.GRPC;
import static org.apache.eventmesh.metrics.prometheus.utils.PrometheusExporterConstants.METRICS_GRPC_PREFIX;
import static org.apache.eventmesh.metrics.prometheus.utils.PrometheusExporterUtils.join;
import static org.apache.eventmesh.metrics.prometheus.utils.PrometheusExporterUtils.observeOfValue;

import org.apache.eventmesh.metrics.api.model.GrpcSummaryMetrics;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Map;
import java.util.function.Function;

import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.Meter;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PrometheusGrpcExporter {

    private final Map<String, Function<GrpcSummaryMetrics, Number>> paramPairs = Stream.of(
            new Object[][] {
                    { "sub.topic.num", "get sub topic num.", GrpcSummaryMetrics::getSubscribeTopicNum },
                    { "retry.queue.size", "get size of retry queue.", GrpcSummaryMetrics::getRetrySize },
                    { "server.tps", "get size of retry queue.", GrpcSummaryMetrics::getClient2EventMeshTPS },
                    { "client.tps", "get tps of eventMesh to mq.", GrpcSummaryMetrics::getEventMesh2ClientTPS },
                    { "mq.provider.tps", "get tps of eventMesh to mq.", GrpcSummaryMetrics::getEventMesh2MqTPS },
                    { "mq.consumer.tps", "get tps of eventMesh to mq.", GrpcSummaryMetrics::getMq2EventMeshTPS }
            }
    ).collect(Collectors.toMap(
            data -> join((String)data[0], (String)data[1]),
            data -> (Function<GrpcSummaryMetrics, Number>)data[2]
    ));

    public static void export(final String meterName, final GrpcSummaryMetrics summaryMetrics) {
        final Meter meter = GlobalMeterProvider.getMeter(meterName);

        paramPairs.forEach((metricName, getMetric) ->
                observeOfValue(meter, METRICS_GRPC_PREFIX + metricName, "get " + metricName, GRPC, summaryMetrics, getMetric));
    }
}





