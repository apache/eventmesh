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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.netty.channel.ChannelHandlerContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;

public class Trace {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private boolean useTrace = false;
    private EventMeshTraceService eventMeshTraceService;

    public Trace(boolean useTrace) {
        this.useTrace = useTrace;
    }

    public void init(String tracePluginType) throws Exception {
        if (useTrace) {
            eventMeshTraceService = TracePluginFactory.getEventMeshTraceService(tracePluginType);
            eventMeshTraceService.init();
        }
    }

    public Span createSpan(String spanName, SpanKind spanKind, long startTime, TimeUnit timeUnit,
                           Context context, boolean isSpanFinishInOtherThread) {
        if (!useTrace) {
            return null;
        }
        return eventMeshTraceService.createSpan(spanName, spanKind, startTime, timeUnit, context,
            isSpanFinishInOtherThread);
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
            logger.warn("span is null when finishSpan");
            return null;
        }

        //add trace info
        for (String entry : cloudEvent.getExtensionNames()) {
            span.setAttribute(entry, cloudEvent.getExtension(entry).toString());
        }
        return span;
    }

    public Span addTraceInfoToSpan(Span span, CloudEvent cloudEvent) {
        if (!useTrace) {
            return null;
        }

        if (span == null) {
            logger.warn("span is null when finishSpan");
            return null;
        }

        if (cloudEvent == null) {
            return span;
        }

        for (String entry : cloudEvent.getExtensionNames()) {
            span.setAttribute(entry, cloudEvent.getExtension(entry).toString());
        }
        return span;
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

    public void finishSpan(ChannelHandlerContext ctx, StatusCode statusCode) {
        try {
            if (useTrace) {
                Context context = ctx.channel().attr(AttributeKeys.SERVER_CONTEXT).get();
                Span span = context != null ? context.get(SpanKey.SERVER_KEY) : null;

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

    public void finishSpan(ChannelHandlerContext ctx, StatusCode statusCode, String errMsg,
                           Throwable throwable) {
        try {
            if (useTrace) {
                Context context = ctx.channel().attr(AttributeKeys.SERVER_CONTEXT).get();
                Span span = context != null ? context.get(SpanKey.SERVER_KEY) : null;

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
