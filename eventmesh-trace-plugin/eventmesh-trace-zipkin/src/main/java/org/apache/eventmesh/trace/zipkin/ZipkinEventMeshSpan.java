package org.apache.eventmesh.trace.zipkin;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;

import org.apache.eventmesh.trace.api.EventMeshSpan;
import org.apache.eventmesh.trace.api.common.ProtocolType;
import org.apache.eventmesh.trace.api.propagation.EventMeshContextCarrier;

import java.util.concurrent.TimeUnit;

public class ZipkinEventMeshSpan implements EventMeshSpan {

    private Span span;

    public ZipkinEventMeshSpan(String spanName, Tracer tracer){
        //if the client injected span context,this will extract the context from httpRequest or it will be null

        span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

    }

    public ZipkinEventMeshSpan(String spanName,  EventMeshContextCarrier carrier, Tracer tracer){
        span = tracer.spanBuilder(spanName).startSpan();
        //TODO carrier
    }

    @Override
    public void start() {

    }

    @Override
    public void start(long timestamp) {

    }

    @Override
    public void finish() {
        span.end();
    }

    @Override
    public void finish(long timestamp) {
        span.end(timestamp, TimeUnit.MILLISECONDS);
    }

    @Override
    public void setOperationName(String operationName) {

    }

    @Override
    public void setRemoteAddress(String remoteAddress) {

    }

    @Override
    public void setProtocolType(ProtocolType type) {

    }

    @Override
    public void addTag(String key, String value) {
        span.setAttribute(key, value);
    }

    @Override
    public void addError(Throwable throwable) {
        span.recordException(throwable);
    }

    @Override
    public void setAsync() {

    }

    @Override
    public void setComponent(String componentName) {

    }

    @Override
    public boolean isAsync() {
        return false;
    }

    public Span getSpan() {
        return span;
    }
}
