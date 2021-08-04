package org.apache.eventmesh.common;

public class EventMeshRuntimeException extends RuntimeException {

    public EventMeshRuntimeException(String message) {
        super(message);
    }

    public EventMeshRuntimeException(String message, Throwable t) {
        super(message, t);
    }
}
