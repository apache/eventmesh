package org.apache.eventmesh.runtime.trace;

import io.cloudevents.CloudEvent;
import io.netty.channel.ChannelHandlerContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;

import org.apache.eventmesh.runtime.boot.EventMeshServer;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                .createSpan(spanName, SpanKind.SERVER, traceContext, false);
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
                        false);
            } else {
                span = EventMeshServer.getTrace()
                    .createSpan(spanName, SpanKind.SERVER, traceContext, false);
            }
        } catch (Throwable ex) {
            logger.warn("upload trace fail when prepareSpan", ex);
        }
        return span;
    }


    public static void finishSpan(Span span, CloudEvent event) {
        try {
            EventMeshServer.getTrace().addTraceInfoToSpan(span, event);
            EventMeshServer.getTrace().finishSpan(span, StatusCode.OK);
        } catch (Throwable ex) {
            logger.warn("upload trace fail when finishSpan", ex);
        }

    }

    public static void finishSpan(ChannelHandlerContext ctx, CloudEvent event) {
        try {
            EventMeshServer.getTrace().addTraceInfoToSpan(ctx, event);
            EventMeshServer.getTrace().finishSpan(ctx, StatusCode.OK);
        } catch (Throwable ex) {
            logger.warn("upload trace fail when finishSpan", ex);
        }

    }

    public static void finishSpanWithException(ChannelHandlerContext ctx, CloudEvent event,
                                               String errMsg, Throwable e) {
        try {
            EventMeshServer.getTrace().addTraceInfoToSpan(ctx, event);
            EventMeshServer.getTrace().finishSpan(ctx, StatusCode.ERROR, errMsg, e);
        } catch (Throwable ex) {
            logger.warn("upload trace fail when finishSpanWithException", ex);
        }
    }

    public static void finishSpanWithException(Span span, Map<String, Object> map, String errMsg,
                                               Throwable e) {
        try {
            EventMeshServer.getTrace().addTraceInfoToSpan(span, map);
            EventMeshServer.getTrace().finishSpan(span, StatusCode.ERROR, errMsg, e);
        } catch (Throwable ex) {
            logger.warn("upload trace fail when finishSpanWithException", ex);
        }
    }
}
