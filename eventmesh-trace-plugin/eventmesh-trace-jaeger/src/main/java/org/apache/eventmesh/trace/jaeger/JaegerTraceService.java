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

package org.apache.eventmesh.trace.jaeger;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.trace.api.EventMeshTraceService;
import org.apache.eventmesh.trace.api.config.ExporterConfiguration;
import org.apache.eventmesh.trace.api.exception.TraceException;
import org.apache.eventmesh.trace.jaeger.common.JaegerConstants;
import org.apache.eventmesh.trace.jaeger.config.JaegerConfiguration;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

import lombok.Data;

@Config(field = "jaegerConfiguration")
@Config(field = "exporterConfiguration")
@Data
public class JaegerTraceService implements EventMeshTraceService {

    private transient SdkTracerProvider sdkTracerProvider;

    private transient Tracer tracer;

    private transient TextMapPropagator textMapPropagator;

    /**
     * Unified configuration class corresponding to jaeger.properties
     */
    private transient JaegerConfiguration jaegerConfiguration;

    /**
     * Unified configuration class corresponding to exporter.properties
     */
    private transient ExporterConfiguration exporterConfiguration;

    private transient Thread shutdownHook;

    @Override
    public void init() throws TraceException {
        // jaeger's config
        final String eventMeshJaegerIp = jaegerConfiguration.getEventMeshJaegerIp();
        final int eventMeshJaegerPort = jaegerConfiguration.getEventMeshJaegerPort();
        // exporter's config
        final int eventMeshTraceExportInterval = exporterConfiguration.getEventMeshTraceExportInterval();
        final int eventMeshTraceExportTimeout = exporterConfiguration.getEventMeshTraceExportTimeout();
        final int eventMeshTraceMaxExportSize = exporterConfiguration.getEventMeshTraceMaxExportSize();
        final int eventMeshTraceMaxQueueSize = exporterConfiguration.getEventMeshTraceMaxQueueSize();

        final String httpEndpoint = String.format("http://%s:%s", eventMeshJaegerIp, eventMeshJaegerPort);
        final JaegerGrpcSpanExporter jaegerExporter = JaegerGrpcSpanExporter.builder()
                .setEndpoint(httpEndpoint)
                .build();

        final SpanProcessor spanProcessor = BatchSpanProcessor.builder(jaegerExporter)
                .setScheduleDelay(eventMeshTraceExportInterval, TimeUnit.SECONDS)
                .setExporterTimeout(eventMeshTraceExportTimeout, TimeUnit.SECONDS)
                .setMaxExportBatchSize(eventMeshTraceMaxExportSize)
                .setMaxQueueSize(eventMeshTraceMaxQueueSize)
                .build();

        final Resource serviceNameResource =
                Resource.create(Attributes.of(stringKey("service.name"), JaegerConstants.SERVICE_NAME));

        sdkTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(spanProcessor)
                .setResource(Resource.getDefault().merge(serviceNameResource))
                .build();

        final OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .setTracerProvider(sdkTracerProvider)
                .build();

        tracer = openTelemetry.getTracer(JaegerConstants.SERVICE_NAME);
        textMapPropagator = openTelemetry.getPropagators().getTextMapPropagator();

        shutdownHook = new Thread(sdkTracerProvider::close);
        shutdownHook.setDaemon(true);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    @Override
    public Context extractFrom(final Context context, final Map<String, Object> carrier) throws TraceException {
        textMapPropagator.extract(context, carrier, new TextMapGetter<Map<String, Object>>() {
            @Override
            public Iterable<String> keys(final Map<String, Object> carrier) {
                return carrier.keySet();
            }

            @Nullable
            @Override
            public String get(final @Nullable Map<String, Object> carrier, final String key) {
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
    public Span createSpan(final String spanName, final SpanKind spanKind, final long startTimestamp,
                           final TimeUnit timeUnit, final Context context,
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
        try {
            if (sdkTracerProvider != null) {
                sdkTracerProvider.close();
            }
        } catch (Exception e) {
            throw new TraceException("trace close error", e);
        }
    }

    public JaegerConfiguration getClientConfiguration() {
        return this.jaegerConfiguration;
    }

    public ExporterConfiguration getExporterConfiguration() {
        return this.exporterConfiguration;
    }
}