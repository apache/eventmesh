/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.apache.eventmesh.trace.pinpoint;

import org.apache.eventmesh.trace.api.EventMeshTraceService;
import org.apache.eventmesh.trace.api.config.ExporterConfiguration;
import org.apache.eventmesh.trace.api.exception.TraceException;
import org.apache.eventmesh.trace.pinpoint.common.PinpointConstants;
import org.apache.eventmesh.trace.pinpoint.config.PinpointConfiguration;
import org.apache.eventmesh.trace.pinpoint.exporter.PinpointSpanExporter;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

/**
 * https://github.com/pinpoint-apm/pinpoint
 */
public class PinpointTraceService implements EventMeshTraceService {

    private SdkTracerProvider sdkTracerProvider;

    private Tracer tracer;

    private TextMapPropagator textMapPropagator;

    protected Thread shutdownHook;


    @Override
    public void init() throws TraceException {
        long eventMeshTraceExportInterval = ExporterConfiguration.getEventMeshTraceExportInterval();
        long eventMeshTraceExportTimeout = ExporterConfiguration.getEventMeshTraceExportTimeout();
        int eventMeshTraceMaxExportSize = ExporterConfiguration.getEventMeshTraceMaxExportSize();
        int eventMeshTraceMaxQueueSize = ExporterConfiguration.getEventMeshTraceMaxQueueSize();

        SpanProcessor spanProcessor = BatchSpanProcessor.builder(
                new PinpointSpanExporter(
                    PinpointConfiguration.getAgentId(),
                    PinpointConfiguration.getAgentName(),
                    PinpointConfiguration.getApplicationName(),
                    PinpointConfiguration.getGrpcTransportConfig()))
            .setScheduleDelay(eventMeshTraceExportInterval, TimeUnit.SECONDS)
            .setExporterTimeout(eventMeshTraceExportTimeout, TimeUnit.SECONDS)
            .setMaxExportBatchSize(eventMeshTraceMaxExportSize)
            .setMaxQueueSize(eventMeshTraceMaxQueueSize)
            .build();

        sdkTracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(spanProcessor)
            .build();

        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .setTracerProvider(sdkTracerProvider)
            .build();

        tracer = openTelemetry.getTracer(PinpointConstants.SERVICE_NAME);

        textMapPropagator = openTelemetry.getPropagators().getTextMapPropagator();

        shutdownHook = new Thread(sdkTracerProvider::close);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    @Override
    public Context extractFrom(Context context, Map<String, Object> carrier) throws TraceException {
        textMapPropagator.extract(context, carrier, new TextMapGetter<Map<String, Object>>() {
            @Override
            public Iterable<String> keys(@Nonnull Map<String, Object> carrier) {
                return carrier.keySet();
            }

            @Override
            public String get(Map<String, Object> carrier, @Nonnull String key) {
                return Optional.ofNullable(carrier.get(key)).map(Objects::toString).orElse(null);
            }
        });
        return context;

    }

    @Override
    public void inject(Context context, Map<String, Object> carrier) {
        textMapPropagator.inject(context, carrier, (cr, key, value) -> {
            if (cr != null) {
                cr.put(key, value);
            }
        });
    }

    @Override
    public Span createSpan(String spanName,
                           SpanKind spanKind,
                           long startTimestamp,
                           TimeUnit timeUnit,
                           Context context,
                           boolean isSpanFinishInOtherThread) throws TraceException {
        return tracer.spanBuilder(spanName)
            .setParent(context)
            .setSpanKind(spanKind)
            .setStartTimestamp(startTimestamp, timeUnit)
            .startSpan();
    }

    @Override
    public Span createSpan(String spanName, SpanKind spanKind, Context context, boolean isSpanFinishInOtherThread) throws TraceException {
        return tracer.spanBuilder(spanName)
            .setParent(context)
            .setSpanKind(spanKind)
            .setStartTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .startSpan();
    }

    @Override
    public void shutdown() throws TraceException {
        sdkTracerProvider.close();
    }
}
