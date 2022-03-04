package org.apache.eventmesh.trace.api;

public class EventMeshTraceContext {
    private EventMeshSpan span;

    public EventMeshSpan getSpan() {
        return span;
    }

    public void setSpan(EventMeshSpan span) {
        this.span = span;
    }
}
