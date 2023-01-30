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

import org.apache.eventmesh.common.config.Config;
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

import lombok.Data;

/**
 * https://github.com/pinpoint-apm/pinpoint
 */
@Config(field = "pinpointConfiguration")
@Config(field = "exporterConfiguration")
@Data
public class PinpointTraceService implements EventMeshTraceService {

    private transient SdkTracerProvider sdkTracerProvider;

    private transient Tracer tracer;

    private transient TextMapPropagator textMapPropagator;

    /**
     * Unified configuration class corresponding to pinpoint.properties
     */
    private transient PinpointConfiguration pinpointConfiguration;

    /**
     * Unified configuration class corresponding to exporter.properties
     */
    private transient ExporterConfiguration exporterConfiguration;

    private transient SpanProcessor spanProcessor;

    private transient Thread shutdownHook;

    @Override
    public void init() throws TraceException {
        final long eventMeshTraceExportInterval = exporterConfiguration.getEventMeshTraceExportInterval();
        final long eventMeshTraceExportTimeout = exporterConfiguration.getEventMeshTraceExportTimeout();
        final int eventMeshTraceMaxExportSize = exporterConfiguration.getEventMeshTraceMaxExportSize();
        final int eventMeshTraceMaxQueueSize = exporterConfiguration.getEventMeshTraceMaxQueueSize();

        spanProcessor = BatchSpanProcessor.builder(
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

        sdkTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(spanProcessor)
                .build();

        final OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .setTracerProvider(sdkTracerProvider)
                .build();

        tracer = openTelemetry.getTracer(PinpointConstants.SERVICE_NAME);

        textMapPropagator = openTelemetry.getPropagators().getTextMapPropagator();

        shutdownHook = new Thread(sdkTracerProvider::close);
        shutdownHook.setDaemon(true);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    @Override
    public Context extractFrom(final Context context, final Map<String, Object> carrier) throws TraceException {
        textMapPropagator.extract(context, carrier, new TextMapGetter<Map<String, Object>>() {
            @Override
            public Iterable<String> keys(final @Nonnull Map<String, Object> carrier) {
                return carrier.keySet();
            }

            @Override
            public String get(final Map<String, Object> carrier, final @Nonnull String key) {
                return Optional.ofNullable(carrier.get(key)).map(Objects::toString).orElse(null);
            }
        });
        return context;

    }

    @Override
    public void inject(final Context context, final Map<String, Object> carrier) {
        textMapPropagator.inject(context, carrier, (cr, key, value) -> {
            if (cr != null) {
                cr.put(key, value);
            }
        });
    }

    @Override
    public Span createSpan(final String spanName,
                           final SpanKind spanKind,
                           final long startTimestamp,
                           final TimeUnit timeUnit,
                           final Context context,
                           final boolean isSpanFinishInOtherThread) throws TraceException {
        return tracer.spanBuilder(spanName)
                .setParent(context)
                .setSpanKind(spanKind)
                .setStartTimestamp(startTimestamp, timeUnit)
                .startSpan();
    }

    @Override
    public Span createSpan(final String spanName, final SpanKind spanKind, final Context context,
                           final boolean isSpanFinishInOtherThread) throws TraceException {
        return tracer.spanBuilder(spanName)
                .setParent(context)
                .setSpanKind(spanKind)
                .setStartTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .startSpan();
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
            if (spanProcessor != null) {
                spanProcessor.close();
            }
        } catch (Exception e) {
            ex = e;
        }

        if (ex != null) {
            throw new TraceException("trace close error", ex);
        }
    }

    public PinpointConfiguration getClientConfiguration() {
        return this.pinpointConfiguration;
    }

    public ExporterConfiguration getExporterConfiguration() {
        return this.exporterConfiguration;
    }
}
