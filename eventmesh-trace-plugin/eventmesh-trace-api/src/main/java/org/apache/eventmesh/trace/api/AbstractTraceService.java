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

package org.apache.eventmesh.trace.api;

import org.apache.eventmesh.trace.api.common.EventMeshTraceConstants;
import org.apache.eventmesh.trace.api.config.ExporterConfiguration;
import org.apache.eventmesh.trace.api.exception.TraceException;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.SpanProcessor;

import lombok.Getter;
import lombok.Setter;

/**
 * The abstract base class for EventMeshTraceService's implementations
 */
@Getter
@Setter
public abstract class AbstractTraceService implements EventMeshTraceService {

    protected transient SdkTracerProvider sdkTracerProvider;

    protected transient Tracer tracer;

    protected transient TextMapPropagator textMapPropagator;

    /**
     * Unified configuration class corresponding to exporter.properties
     */
    protected transient ExporterConfiguration exporterConfiguration;

    protected transient Thread shutdownHook;

    @Override
    public Context extractFrom(final Context context, final Map<String, Object> carrier) throws TraceException {
        textMapPropagator.extract(context, carrier, new TextMapGetter<Map<String, Object>>() {

            @Override
            public Iterable<String> keys(@Nonnull final Map<String, Object> carrier) {
                return carrier.keySet();
            }

            @Nullable
            @Override
            public String get(final @Nonnull Map<String, Object> carrier, final String key) {
                return Optional.ofNullable(carrier.get(key)).map(Objects::toString).orElse(null);
            }
        });
        return context;
    }

    @Override
    public void inject(Context context, Map<String, Object> map) {
        textMapPropagator.inject(context, map, (carrier, key, value) -> map.put(key, value));
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
    public Span createSpan(String spanName, SpanKind spanKind, Context context,
        boolean isSpanFinishInOtherThread) throws TraceException {
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

    /**
     * Init the common fields
     *
     * @param spanProcessor
     * @param serviceNameResource
     */
    protected void initVars(SpanProcessor spanProcessor, Resource serviceNameResource) {
        SdkTracerProviderBuilder builder = SdkTracerProvider.builder()
            .addSpanProcessor(spanProcessor);
        if (serviceNameResource != null) {
            builder.setResource(Resource.getDefault().merge(serviceNameResource));
        }
        sdkTracerProvider = builder.build();

        final OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .setTracerProvider(sdkTracerProvider)
            .build();

        tracer = openTelemetry.getTracer(EventMeshTraceConstants.SERVICE_NAME);
        textMapPropagator = openTelemetry.getPropagators().getTextMapPropagator();
        shutdownHook = new Thread(sdkTracerProvider::close);
        shutdownHook.setDaemon(true);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }
}
