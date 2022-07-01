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

import org.apache.eventmesh.trace.api.EventMeshTraceService;
import org.apache.eventmesh.trace.api.config.ExporterConfiguration;
import org.apache.eventmesh.trace.api.exception.TraceException;
import org.apache.eventmesh.trace.zipkin.config.ZipkinConfiguration;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;


/**
 *
 */
public class ZipkinTraceService implements EventMeshTraceService {
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
    protected SdkTracerProvider sdkTracerProvider;

    protected OpenTelemetry openTelemetry;

    protected Thread shutdownHook;

    private Tracer tracer;
    private TextMapPropagator textMapPropagator;

    @Override
    public void init() {
        //zipkin's config
        eventMeshZipkinIP = ZipkinConfiguration.getEventMeshZipkinIP();
        eventMeshZipkinPort = ZipkinConfiguration.getEventMeshZipkinPort();
        //exporter's config
        eventMeshTraceExportInterval = ExporterConfiguration.getEventMeshTraceExportInterval();
        eventMeshTraceExportTimeout = ExporterConfiguration.getEventMeshTraceExportTimeout();
        eventMeshTraceMaxExportSize = ExporterConfiguration.getEventMeshTraceMaxExportSize();
        eventMeshTraceMaxQueueSize = ExporterConfiguration.getEventMeshTraceMaxQueueSize();

        String httpUrl = String.format("http://%s:%s", eventMeshZipkinIP, eventMeshZipkinPort);
        ZipkinSpanExporter zipkinExporter =
            ZipkinSpanExporter.builder().setEndpoint(httpUrl + ENDPOINT_V2_SPANS).build();

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

        //TODO serviceName???
        tracer = openTelemetry.getTracer(serviceName);
        textMapPropagator = openTelemetry.getPropagators().getTextMapPropagator();

        shutdownHook = new Thread(sdkTracerProvider::close);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    @Override
    public Context extractFrom(Context context, Map<String, Object> map) throws TraceException {
        textMapPropagator.extract(context, map, new TextMapGetter<Map<String, Object>>() {
            @Override
            public Iterable<String> keys(Map<String, Object> carrier) {
                return carrier.keySet();
            }

            @Override
            public String get(Map<String, Object> carrier, String key) {
                return carrier.get(key).toString();
            }
        });
        return context;
    }

    @Override
    public void inject(Context context, Map<String, Object> map) {
        textMapPropagator.inject(context, map, new TextMapSetter<Map<String, Object>>() {
            @Override
            public void set(@Nullable Map<String, Object> carrier, String key, String value) {
                map.put(key, value);
            }
        });
    }

    @Override
    public Span createSpan(String spanName, SpanKind spanKind, long startTime, TimeUnit timeUnit,
                           Context context, boolean isSpanFinishInOtherThread)
        throws TraceException {
        return tracer.spanBuilder(spanName)
            .setParent(context)
            .setSpanKind(spanKind)
            .setStartTimestamp(startTime, timeUnit)
            .startSpan();
    }

    @Override
    public Span createSpan(String spanName, SpanKind spanKind, Context context,
                           boolean isSpanFinishInOtherThread) throws TraceException {
        return tracer.spanBuilder(spanName)
            .setParent(context)
            .setSpanKind(spanKind)
            .setStartTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .startSpan();
    }

    @Override
    public void shutdown() {
        //todo: check the spanProcessor if it was already close

        sdkTracerProvider.close();

        //todo: turn the value of useTrace in AbstractHTTPServer into false
    }
}
