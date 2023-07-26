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

package org.apache.eventmesh.trace.zipkin;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.trace.api.AbstractTraceService;
import org.apache.eventmesh.trace.api.common.EventMeshTraceConstants;
import org.apache.eventmesh.trace.api.exception.TraceException;
import org.apache.eventmesh.trace.zipkin.common.ZipkinConstants;
import org.apache.eventmesh.trace.zipkin.config.ZipkinConfiguration;

import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

import lombok.Getter;
import lombok.Setter;


/**
 * ZipkinTraceService
 */
@Config(field = "zipkinConfiguration")
@Config(field = "exporterConfiguration")
@Getter
@Setter
public class ZipkinTraceService extends AbstractTraceService {

    /**
     * Unified configuration class corresponding to zipkin.properties
     */
    private transient ZipkinConfiguration zipkinConfiguration;

    private transient ZipkinSpanExporter zipkinExporter;

    @Override
    public void init() {
        //zipkin's config
        final String eventMeshZipkinIP = zipkinConfiguration.getEventMeshZipkinIP();
        final int eventMeshZipkinPort = zipkinConfiguration.getEventMeshZipkinPort();

        //exporter's config
        final int eventMeshTraceExportInterval = exporterConfiguration.getEventMeshTraceExportInterval();
        final int eventMeshTraceExportTimeout = exporterConfiguration.getEventMeshTraceExportTimeout();
        final int eventMeshTraceMaxExportSize = exporterConfiguration.getEventMeshTraceMaxExportSize();
        final int eventMeshTraceMaxQueueSize = exporterConfiguration.getEventMeshTraceMaxQueueSize();

        final String httpUrl = String.format("http://%s:%s", eventMeshZipkinIP, eventMeshZipkinPort);
        zipkinExporter =
            ZipkinSpanExporter.builder().setEndpoint(httpUrl + ZipkinConstants.ENDPOINT_V2_SPANS).build();
        SpanProcessor spanProcessor = BatchSpanProcessor.builder(zipkinExporter)
            .setScheduleDelay(eventMeshTraceExportInterval, TimeUnit.SECONDS)
            .setExporterTimeout(eventMeshTraceExportTimeout, TimeUnit.SECONDS)
            .setMaxExportBatchSize(eventMeshTraceMaxExportSize)
            .setMaxQueueSize(eventMeshTraceMaxQueueSize)
            .build();

        //set the trace service's name
        final Resource serviceNameResource =
            Resource.create(Attributes.of(stringKey("service.name"), EventMeshTraceConstants.SERVICE_NAME));

        initVars(spanProcessor, serviceNameResource);
    }

    @Override
    public void shutdown() throws TraceException {

        Exception ex = null;

        try {
            if (sdkTracerProvider != null) {
                sdkTracerProvider.close();
            }
        } catch (Exception e) {
            ex = e;
        }

        try {
            if (zipkinExporter != null) {
                zipkinExporter.close();
            }
        } catch (Exception e) {
            ex = e;
        }

        if (ex != null) {
            throw new TraceException("trace close error", ex);
        }

        //todo: turn the value of useTrace in AbstractHTTPServer into false
    }

    public ZipkinConfiguration getClientConfiguration() {
        return this.zipkinConfiguration;
    }
}
