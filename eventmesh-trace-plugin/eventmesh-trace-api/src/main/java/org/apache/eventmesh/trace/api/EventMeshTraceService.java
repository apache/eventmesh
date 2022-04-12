package org.apache.eventmesh.trace.api;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;

import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;
import org.apache.eventmesh.trace.api.exception.TraceException;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@EventMeshSPI(isSingleton = true, eventMeshExtensionType = EventMeshExtensionType.TRACE)
public interface EventMeshTraceService {
    void init() throws TraceException;

    //extract attr from carrier to context
    Context extractFrom(Context context, Map<String, Object> carrier) throws TraceException;

    //inject attr from context to carrier
    void inject(Context context, Map<String, Object> carrier);

    Span createSpan(String spanName, SpanKind spanKind, long startTimestamp, TimeUnit timeUnit,
                    Context context, boolean isSpanFinishInOtherThread) throws TraceException;

    Span createSpan(String spanName, SpanKind spanKind, Context context,
                    boolean isSpanFinishInOtherThread) throws TraceException;

    void shutdown() throws TraceException;
}
