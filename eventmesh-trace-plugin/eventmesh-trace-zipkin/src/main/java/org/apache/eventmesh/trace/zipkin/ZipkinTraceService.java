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

import org.apache.eventmesh.trace.api.TraceService;
import org.apache.eventmesh.trace.api.config.ExporterConfiguration;
import org.apache.eventmesh.trace.zipkin.config.ZipkinConfiguration;

import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
public class ZipkinTraceService implements TraceService {
    // Zipkin API Endpoints for uploading spans
    private static final String ENDPOINT_V2_SPANS = "/api/v2/spans";
    // Name of the service(using the instrumentationName)
    private final String serviceName = "eventmesh_trace";
    private String eventMeshZipkinIP;
    private int eventMeshZipkinPort;
    private int eventMeshTraceExportInterval;
    private int eventMeshTraceExportTimeout;
    private int eventMeshTraceMaxExportSize;
    private int eventMeshTraceMaxQueueSize;
    private SdkTracerProvider sdkTracerProvider;

    private OpenTelemetry openTelemetry;

    @Override
    public void init() {
        //zipkin's config
        eventMeshZipkinIP = ZipkinConfiguration.getEventMeshZipkinIP();
        eventMeshZipkinPort = ZipkinConfiguration.getEventMeshZipkinPort();
        String httpUrl = String.format("http://%s:%s", eventMeshZipkinIP, eventMeshZipkinPort);
        ZipkinSpanExporter zipkinExporter =
            ZipkinSpanExporter.builder().setEndpoint(httpUrl + ENDPOINT_V2_SPANS).build();

        //exporter's config
        eventMeshTraceExportInterval = ExporterConfiguration.getEventMeshTraceExportInterval();
        eventMeshTraceExportTimeout = ExporterConfiguration.getEventMeshTraceExportTimeout();
        eventMeshTraceMaxExportSize = ExporterConfiguration.getEventMeshTraceMaxExportSize();
        eventMeshTraceMaxQueueSize = ExporterConfiguration.getEventMeshTraceMaxQueueSize();
        SpanProcessor spanProcessor = BatchSpanProcessor.builder(zipkinExporter)
            .setScheduleDelay(eventMeshTraceExportInterval, TimeUnit.SECONDS)
            .setExporterTimeout(eventMeshTraceExportTimeout, TimeUnit.SECONDS)
            .setMaxExportBatchSize(eventMeshTraceMaxExportSize)
            .setMaxQueueSize(eventMeshTraceMaxQueueSize)
            .build();

        //set the trace service's name
        Resource serviceNameResource =
            Resource.create(Attributes.of(stringKey("service.name"), serviceName));

        sdkTracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(spanProcessor)
            .setResource(Resource.getDefault().merge(serviceNameResource))
            .build();

        openTelemetry = OpenTelemetrySdk.builder()
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .setTracerProvider(sdkTracerProvider)
            .build();

        Runtime.getRuntime().addShutdownHook(new Thread(sdkTracerProvider::close));
    }

    @Override
    public void shutdown() {
        //todo: check the spanProcessor if it was already close

        sdkTracerProvider.close();

        //todo: turn the value of useTrace in AbstractHTTPServer into false
    }

    //Gets or creates a named tracer instance
    @Override
    public Tracer getTracer(String instrumentationName) {
        return openTelemetry.getTracer(instrumentationName);
    }

    //to inject or extract span context
    @Override
    public TextMapPropagator getTextMapPropagator() {
        return openTelemetry.getPropagators().getTextMapPropagator();
    }
}
