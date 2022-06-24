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

import org.apache.eventmesh.runtime.boot.EventMeshServer;

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

public class TraceUtils {
    private static Logger logger = LoggerFactory.getLogger(TraceUtils.class);

    public static Span prepareClientSpan(Map map, String spanName,
                                         boolean isSpanFinishInOtherThread) {
        Span span = null;
        try {
            span = EventMeshServer.getTrace().createSpan(
                spanName, SpanKind.CLIENT, Context.current(), isSpanFinishInOtherThread);
            EventMeshServer.getTrace().inject(Context.current(), map);
        } catch (Throwable ex) {
            logger.warn("upload trace fail when prepareSpan", ex);
        }
        return span;
    }

    public static Span prepareServerSpan(Map<String, Object> map, String spanName,
                                         boolean isSpanFinishInOtherThread) {
        Span span = null;
        try {
            Context traceContext = EventMeshServer.getTrace().extractFrom(Context.current(), map);
            span = EventMeshServer.getTrace()
                .createSpan(spanName, SpanKind.SERVER, traceContext, isSpanFinishInOtherThread);
        } catch (Throwable ex) {
            logger.warn("upload trace fail when prepareSpan", ex);
        }
        return span;
    }

    public static Span prepareServerSpan(Map<String, Object> map, String spanName, long startTime,
                                         TimeUnit timeUnit, boolean isSpanFinishInOtherThread) {
        Span span = null;
        try {
            Context traceContext = EventMeshServer.getTrace().extractFrom(Context.current(), map);
            if (startTime > 0) {
                span = EventMeshServer.getTrace()
                    .createSpan(spanName, SpanKind.SERVER, startTime, timeUnit, traceContext,
                        isSpanFinishInOtherThread);
            } else {
                span = EventMeshServer.getTrace()
                    .createSpan(spanName, SpanKind.SERVER, traceContext, isSpanFinishInOtherThread);
            }
        } catch (Throwable ex) {
            logger.warn("upload trace fail when prepareSpan", ex);
        }
        return span;
    }


    public static void finishSpan(Span span, CloudEvent event) {
        try {
            logger.debug("finishSpan with event:{}", event);
            EventMeshServer.getTrace().addTraceInfoToSpan(span, event);
            EventMeshServer.getTrace().finishSpan(span, StatusCode.OK);
        } catch (Throwable ex) {
            logger.warn("upload trace fail when finishSpan", ex);
        }

    }

    public static void finishSpan(ChannelHandlerContext ctx, CloudEvent event) {
        try {
            logger.debug("finishSpan with event:{}", event);
            EventMeshServer.getTrace().addTraceInfoToSpan(ctx, event);
            EventMeshServer.getTrace().finishSpan(ctx, StatusCode.OK);
        } catch (Throwable ex) {
            logger.warn("upload trace fail when finishSpan", ex);
        }

    }

    public static void finishSpanWithException(ChannelHandlerContext ctx, CloudEvent event,
                                               String errMsg, Throwable e) {
        try {
            logger.debug("finishSpanWithException with event:{}", event);
            EventMeshServer.getTrace().addTraceInfoToSpan(ctx, event);
            EventMeshServer.getTrace().finishSpan(ctx, StatusCode.ERROR, errMsg, e);
        } catch (Throwable ex) {
            logger.warn("upload trace fail when finishSpanWithException", ex);
        }
    }

    public static void finishSpanWithException(Span span, Map<String, Object> map, String errMsg,
                                               Throwable e) {
        try {
            logger.debug("finishSpanWithException with map:{}", map);
            EventMeshServer.getTrace().addTraceInfoToSpan(span, map);
            EventMeshServer.getTrace().finishSpan(span, StatusCode.ERROR, errMsg, e);
        } catch (Throwable ex) {
            logger.warn("upload trace fail when finishSpanWithException", ex);
        }
    }
}
