package org.apache.eventmesh.trace.zipkin;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.TextMapPropagator;

import org.apache.eventmesh.trace.api.EventMeshSpan;
import org.apache.eventmesh.trace.api.EventMeshTracer;
import org.apache.eventmesh.trace.api.propagation.EventMeshContextCarrier;

public class ZipkinEventMeshTracer implements EventMeshTracer {
    private Tracer tracer;
    private TextMapPropagator textMapPropagator;

    public ZipkinEventMeshTracer(String serviceName, OpenTelemetry openTelemetry){
        tracer = openTelemetry.getTracer(serviceName);
        textMapPropagator = openTelemetry.getPropagators().getTextMapPropagator();
    }

    @Override
    public EventMeshSpan createSpan(String spanName) {
        return new ZipkinEventMeshSpan(spanName, tracer);
    }

    @Override
    public EventMeshSpan createSpan(String spanName, EventMeshContextCarrier carrier) {
        return new ZipkinEventMeshSpan(spanName, carrier, tracer);
    }
}
