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

package org.apache.eventmesh.client.trace;

import org.apache.eventmesh.client.trace.config.TraceConfig;
import org.apache.eventmesh.trace.api.EventMeshTraceService;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;

public class Trace {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private boolean useTrace = false;
    private String traceType;

    private EventMeshTraceService eventMeshTraceService;

    private static Trace trace = new Trace(TraceConfig.isUseTrace(), TraceConfig.getTraceType());

    private Trace(boolean useTrace, String traceType) {
        this.useTrace = useTrace;
        this.traceType = traceType;
    }

    public static Trace getInstance(){
        return trace;
    }

    public void init(){
        if (useTrace) {
            eventMeshTraceService = TraceFactory.createEventMeshTraceService(traceType);
            if (eventMeshTraceService == null) {
                throw new RuntimeException("eventMeshTraceService is null");
            }
            eventMeshTraceService.init();
        }
    }

    public Span createSpan(String spanName, SpanKind spanKind, Context context,
                           boolean isSpanFinishInOtherThread) {
        if (!useTrace) {
            return null;
        }
        return eventMeshTraceService.createSpan(spanName, spanKind, context,
            isSpanFinishInOtherThread);
    }

    public Context extractFrom(Context context, Map<String, Object> map) {
        if (!useTrace) {
            return null;
        }
        return eventMeshTraceService.extractFrom(context, map);
    }

    public void inject(Context context, Map map) {
        if (!useTrace) {
            return;
        }
        eventMeshTraceService.inject(context, map);
    }

    public Span addTraceInfoToSpan(Span span, Map<String, Object> map) {
        if (!useTrace) {
            return null;
        }

        if (span == null) {
            logger.warn("span is null when finishSpan");
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

    public void finishSpan(Span span, StatusCode statusCode) {
        try {
            if (useTrace) {
                if (span == null) {
                    logger.warn("span is null when finishSpan");
                    return;
                }
                if (statusCode != null) {
                    span.setStatus(statusCode);
                }
                span.end();
            }
        } catch (Exception e) {
            logger.warn("finishSpan occur exception,", e);
        }
    }

    public void finishSpan(Span span, StatusCode statusCode, String errMsg, Throwable throwable) {
        try {
            if (useTrace) {
                if (span == null) {
                    logger.warn("span is null when finishSpan");
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
            logger.warn("finishSpan occur exception,", e);
        }
    }

    public void shutdown() throws Exception {
        if (useTrace) {
            eventMeshTraceService.shutdown();
        }
    }
}
