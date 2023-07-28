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

import org.apache.eventmesh.metrics.api.model.GrpcSummaryMetrics;


public class PrometheusGrpcExporter extends PrometheusExporter<GrpcSummaryMetrics> {

    public PrometheusGrpcExporter() {
        paramPairs.put(join("sub.topic.num", "get sub topic num."), GrpcSummaryMetrics::getSubscribeTopicNum);
        paramPairs.put(join("retry.queue.size", "get size of retry queue."), GrpcSummaryMetrics::getRetrySize);
        paramPairs.put(join("server.tps", "get size of retry queue."), GrpcSummaryMetrics::getClient2EventMeshTPS);
        paramPairs.put(join("client.tps", "get tps of eventMesh to mq."), GrpcSummaryMetrics::getEventMesh2ClientTPS);
        paramPairs.put(join("mq.provider.tps", "get tps of eventMesh to mq."), GrpcSummaryMetrics::getEventMesh2MqTPS);
        paramPairs.put(join("mq.consumer.tps", "get tps of eventMesh to mq."), GrpcSummaryMetrics::getMq2EventMeshTPS);
    }

    @Override
    public String getMetricName(String[] metricInfo) {
        return METRICS_GRPC_PREFIX + metricInfo[0];
    }

    @Override
    public String getProtocol() {
        return GRPC.getValue();
    }
}
