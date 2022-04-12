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

import org.apache.eventmesh.metrics.api.model.TcpSummaryMetrics;

import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.common.Labels;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PrometheusTcpExporter {

    public static void export(final String meterName, final TcpSummaryMetrics summaryMetrics) {
        final Meter meter = GlobalMeterProvider.getMeter(meterName);
        //retryQueueSize
        meter.doubleValueObserverBuilder("eventmesh.tcp.retry.queue.size")
            .setDescription("get size of retry queue.")
            .setUnit("TCP")
            .setUpdater(result -> result.observe(summaryMetrics.getRetrySize(), Labels.empty()))
            .build();

        //client2eventMeshTPS
        meter.doubleValueObserverBuilder("eventmesh.tcp.server.tps")
            .setDescription("get tps of client to eventMesh.")
            .setUnit("TCP")
            .setUpdater(result -> result.observe(summaryMetrics.getClient2eventMeshTPS(), Labels.empty()))
            .build();

        //eventMesh2mqTPS
        meter.doubleValueObserverBuilder("eventmesh.tcp.mq.provider.tps")
            .setDescription("get tps of eventMesh to mq.")
            .setUnit("TCP")
            .setUpdater(result -> result.observe(summaryMetrics.getEventMesh2mqTPS(), Labels.empty()))
            .build();

        //mq2eventMeshTPS
        meter.doubleValueObserverBuilder("eventmesh.tcp.mq.consumer.tps")
            .setDescription("get tps of mq to eventMesh.")
            .setUnit("TCP")
            .setUpdater(result -> result.observe(summaryMetrics.getMq2eventMeshTPS(), Labels.empty()))
            .build();

        //eventMesh2clientTPS
        meter.doubleValueObserverBuilder("eventmesh.tcp.client.tps")
            .setDescription("get tps of eventMesh to client.")
            .setUnit("TCP")
            .setUpdater(result -> result.observe(summaryMetrics.getEventMesh2clientTPS(), Labels.empty()))
            .build();

        //allTPS
        meter.doubleValueObserverBuilder("eventmesh.tcp.all.tps")
            .setDescription("get all TPS.")
            .setUnit("TCP")
            .setUpdater(result -> result.observe(summaryMetrics.getAllTPS(), Labels.empty()))
            .build();

        //EventMeshTcpConnectionHandler.connections
        meter.doubleValueObserverBuilder("eventmesh.tcp.connection.num")
            .setDescription("EventMeshTcpConnectionHandler.connections.")
            .setUnit("TCP")
            .setUpdater(result -> result.observe(summaryMetrics.getAllConnections(), Labels.empty()))
            .build();

        //subTopicNum
        meter.doubleValueObserverBuilder("eventmesh.tcp.sub.topic.num")
            .setDescription("get sub topic num.")
            .setUnit("TCP")
            .setUpdater(result -> result.observe(summaryMetrics.getSubTopicNum(), Labels.empty()))
            .build();
    }
}
