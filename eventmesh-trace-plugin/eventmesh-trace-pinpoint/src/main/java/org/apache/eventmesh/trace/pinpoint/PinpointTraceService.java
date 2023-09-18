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

package org.apache.eventmesh.trace.pinpoint;

import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.trace.api.AbstractTraceService;
import org.apache.eventmesh.trace.api.exception.TraceException;
import org.apache.eventmesh.trace.pinpoint.config.PinpointConfiguration;
import org.apache.eventmesh.trace.pinpoint.exporter.PinpointSpanExporter;

import java.util.concurrent.TimeUnit;

import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

import lombok.Getter;
import lombok.Setter;

/**
 * https://github.com/pinpoint-apm/pinpoint
 */
@Config(field = "pinpointConfiguration")
@Config(field = "exporterConfiguration")
public class PinpointTraceService extends AbstractTraceService {

    /**
     * Unified configuration class corresponding to pinpoint.properties
     */
    @Getter
    @Setter
    private transient PinpointConfiguration pinpointConfiguration;

    @Override
    public void init() throws TraceException {
        final long eventMeshTraceExportInterval = exporterConfiguration.getEventMeshTraceExportInterval();
        final long eventMeshTraceExportTimeout = exporterConfiguration.getEventMeshTraceExportTimeout();
        final int eventMeshTraceMaxExportSize = exporterConfiguration.getEventMeshTraceMaxExportSize();
        final int eventMeshTraceMaxQueueSize = exporterConfiguration.getEventMeshTraceMaxQueueSize();

        SpanProcessor spanProcessor = BatchSpanProcessor.builder(
                new PinpointSpanExporter(
                        pinpointConfiguration.getAgentId(),
                        pinpointConfiguration.getAgentName(),
                        pinpointConfiguration.getApplicationName(),
                        pinpointConfiguration.getGrpcTransportConfig()))
                .setScheduleDelay(eventMeshTraceExportInterval, TimeUnit.SECONDS)
                .setExporterTimeout(eventMeshTraceExportTimeout, TimeUnit.SECONDS)
                .setMaxExportBatchSize(eventMeshTraceMaxExportSize)
                .setMaxQueueSize(eventMeshTraceMaxQueueSize)
                .build();

        initVars(spanProcessor, null);
    }

    public PinpointConfiguration getClientConfiguration() {
        return this.pinpointConfiguration;
    }

}
