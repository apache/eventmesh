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

package org.apache.eventmesh.runtime.trace;

import org.apache.eventmesh.trace.api.EventMeshTraceService;
import org.apache.eventmesh.trace.api.TracePluginFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;
import io.netty.channel.ChannelHandlerContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Trace {

    private static final Map<String, Trace> TRACE_CACHE = new HashMap<>(16);

    private final AtomicBoolean inited = new AtomicBoolean(false);

    private EventMeshTraceService eventMeshTraceService;

    private boolean useTrace;

    public static Trace getInstance(String tracePluginType, boolean useTrace) {
        return TRACE_CACHE.computeIfAbsent(tracePluginType, key -> traceBuilder(tracePluginType, useTrace));
    }

    private static Trace traceBuilder(String tracePluginType, boolean useTrace) {
        Trace trace = new Trace();
        trace.useTrace = useTrace;
        trace.eventMeshTraceService = TracePluginFactory.getEventMeshTraceService(tracePluginType);
        return trace;
    }

    private Trace() {

    }

    public void init() throws Exception {
        if (!inited.compareAndSet(false, true)) {
            return;
        }
        eventMeshTraceService.init();
    }

    public Span createSpan(String spanName, SpanKind spanKind, long startTime, TimeUnit timeUnit,
        Context context, boolean isSpanFinishInOtherThread) {
        if (!useTrace) {
            return Span.getInvalid();
        }
        return eventMeshTraceService.createSpan(spanName, spanKind, startTime, timeUnit, context,
            isSpanFinishInOtherThread);
    }

    public Span createSpan(String spanName, SpanKind spanKind, Context context,
        boolean isSpanFinishInOtherThread) {
        if (!useTrace) {
            return Span.getInvalid();
        }
        return eventMeshTraceService.createSpan(spanName, spanKind, context,
            isSpanFinishInOtherThread);
    }

    public Context extractFrom(Context context, Map<String, Object> map) {
        if (!useTrace) {
            return null;
        }
        if (map == null) {
            return context;
        }
        return eventMeshTraceService.extractFrom(context, map);
    }

    public void inject(Context context, Map<String, Object> map) {
        if (!useTrace) {
            return;
        }
        if (context == null || map == null) {
            return;
        }
        eventMeshTraceService.inject(context, map);
    }

    public Span addTraceInfoToSpan(ChannelHandlerContext ctx, CloudEvent cloudEvent) {
        if (!useTrace) {
            return null;
        }
        Context context = ctx.channel().attr(AttributeKeys.SERVER_CONTEXT).get();
        Span span = context != null ? context.get(SpanKey.SERVER_KEY) : null;

        if (span == null) {
            log.warn("span is null when finishSpan");
            return null;
        }

        //add trace info
        for (String entry : cloudEvent.getExtensionNames()) {
            span.setAttribute(entry, cloudEvent.getExtension(entry) == null ? "" : Objects.requireNonNull(cloudEvent.getExtension(entry)).toString());
        }
        return span;
    }

    public Span addTraceInfoToSpan(Span span, CloudEvent cloudEvent) {
        if (!useTrace) {
            return null;
        }

        if (span == null) {
            log.warn("span is null when finishSpan");
            return null;
        }

        if (cloudEvent == null) {
            return span;
        }

        // add trace info
        for (String entry : cloudEvent.getExtensionNames()) {
            span.setAttribute(entry, cloudEvent.getExtension(entry) != null ? cloudEvent.getExtension(entry).toString() : "");
        }
        return span;
    }

    public Span addTraceInfoToSpan(Span span, Map<String, Object> map) {
        if (!useTrace) {
            return null;
        }

        if (span == null) {
            log.warn("span is null when finishSpan");
            return null;
        }

        if (map == null || map.size() < 1) {
            return span;
        }

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            span.setAttribute(entry.getKey(), entry.getValue().toString());
        }
        return span;
    }

    public void finishSpan(ChannelHandlerContext ctx, StatusCode statusCode) {
        if (useTrace) {
            Context context = ctx.channel().attr(AttributeKeys.SERVER_CONTEXT).get();
            Span span = context != null ? context.get(SpanKey.SERVER_KEY) : null;
            finishSpan(span, statusCode);
        }
    }

    public void finishSpan(Span span, StatusCode statusCode) {
        try {
            if (useTrace) {
                if (span == null) {
                    log.warn("span is null when finishSpan");
                    return;
                }
                if (statusCode != null) {
                    span.setStatus(statusCode);
                }
                span.end();
            }
        } catch (Exception e) {
            log.error("finishSpan occur exception,", e);
        }
    }

    public void finishSpan(Span span, StatusCode statusCode, String errMsg, Throwable throwable) {
        try {
            if (useTrace) {
                if (span == null) {
                    log.warn("span is null when finishSpan");
                    return;
                }
                if (statusCode != null) {
                    span.setStatus(statusCode, errMsg);
                }
                if (throwable != null) {
                    span.recordException(throwable);
                }
                span.end();
            }
        } catch (Exception e) {
            log.error("finishSpan occur exception,", e);
        }
    }

    public void finishSpan(ChannelHandlerContext ctx, StatusCode statusCode, String errMsg, Throwable throwable) {
        if (useTrace) {
            Context context = ctx.channel().attr(AttributeKeys.SERVER_CONTEXT).get();
            Span span = context != null ? context.get(SpanKey.SERVER_KEY) : null;
            finishSpan(span, statusCode, errMsg, throwable);
        }
    }

    public void shutdown() throws Exception {
        if (useTrace && inited.compareAndSet(true, false)) {
            eventMeshTraceService.shutdown();
        }
    }
}
