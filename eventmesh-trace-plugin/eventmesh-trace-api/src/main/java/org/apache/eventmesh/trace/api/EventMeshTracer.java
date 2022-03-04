package org.apache.eventmesh.trace.api;


import org.apache.eventmesh.trace.api.propagation.EventMeshContextCarrier;


public interface EventMeshTracer {
    EventMeshSpan createSpan(String spanName);
    EventMeshSpan createSpan(String spanName, EventMeshContextCarrier carrier);
}
