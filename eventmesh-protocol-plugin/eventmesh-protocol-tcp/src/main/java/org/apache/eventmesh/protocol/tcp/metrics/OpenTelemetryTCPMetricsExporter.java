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

package org.apache.eventmesh.protocol.tcp.metrics;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.metrics.common.Labels;
import org.apache.eventmesh.protocol.api.common.OpenTelemetryExporterConfiguration;
import org.apache.eventmesh.protocol.tcp.handler.EventMeshTcpConnectionHandler;

public class OpenTelemetryTCPMetricsExporter {
    private Meter meter;

    private EventMeshTcpMonitor eventMeshTcpMonitor;

    public OpenTelemetryTCPMetricsExporter(EventMeshTcpMonitor eventMeshTcpMonitor){
        this.eventMeshTcpMonitor = eventMeshTcpMonitor;

        // it is important to initialize the OpenTelemetry SDK as early as possible in your process.
        MeterProvider meterProvider = OpenTelemetryExporterConfiguration.INSTANCE.initializeOpenTelemetry();
        meter = meterProvider.get("OpenTelemetryTCPExporter", "0.13.1");
    }

    public void start(){
        if (meter==null){
            return;
        }
        //retryQueueSize
        meter
                .doubleValueObserverBuilder("eventmesh.tcp.retry.queue.size")
                .setDescription("get size of retry queue")
                .setUnit("TCP")
                .setUpdater(result -> result.observe(eventMeshTcpMonitor.getEventMeshTCPServer().getEventMeshTcpRetryer().getRetrySize(), Labels.empty()))
                .build();

        //client2eventMeshTPS
        meter
                .doubleValueObserverBuilder("eventmesh.tcp.client2.tps")
                .setDescription("get tps of client to eventMesh")
                .setUnit("TCP")
                .setUpdater(result -> result.observe(eventMeshTcpMonitor.getClient2eventMeshTPS(), Labels.empty()))
                .build();

        //eventMesh2mqTPS
        meter
                .doubleValueObserverBuilder("eventmesh.tcp.2mq.tps")
                .setDescription("get tps of eventMesh to mq")
                .setUnit("TCP")
                .setUpdater(result -> result.observe(eventMeshTcpMonitor.getEventMesh2mqTPS(), Labels.empty()))
                .build();

        //mq2eventMeshTPS
        meter
                .doubleValueObserverBuilder("eventmesh.tcp.mq2.tps")
                .setDescription("get tps of mq to eventMesh")
                .setUnit("TCP")
                .setUpdater(result -> result.observe(eventMeshTcpMonitor.getMq2eventMeshTPS(), Labels.empty()))
                .build();

        //eventMesh2clientTPS
        meter
                .doubleValueObserverBuilder("eventmesh.tcp.2client.tps")
                .setDescription("get tps of eventMesh to client")
                .setUnit("TCP")
                .setUpdater(result -> result.observe(eventMeshTcpMonitor.getEventMesh2clientTPS(), Labels.empty()))
                .build();

        //allTPS
        meter
                .doubleValueObserverBuilder("eventmesh.tcp.all.tps")
                .setDescription("get all TPS")
                .setUnit("TCP")
                .setUpdater(result -> result.observe(eventMeshTcpMonitor.getAllTPS(), Labels.empty()))
                .build();

        //EventMeshTcpConnectionHandler.connections
        meter
                .doubleValueObserverBuilder("eventmesh.tcp.connection.handler.connections")
                .setDescription("EventMeshTcpConnectionHandler.connections")
                .setUnit("TCP")
                .setUpdater(result -> result.observe(EventMeshTcpConnectionHandler.connections.doubleValue(), Labels.empty()))
                .build();

        //subTopicNum
        meter
                .doubleValueObserverBuilder("eventmesh.tcp.sub.topic.num")
                .setDescription("get sub topic num")
                .setUnit("TCP")
                .setUpdater(result -> result.observe(eventMeshTcpMonitor.getSubTopicNum(), Labels.empty()))
                .build();
    }
    public void shutdown(){
        OpenTelemetryExporterConfiguration.INSTANCE.shutdownPrometheusEndpoint();
    }
}
