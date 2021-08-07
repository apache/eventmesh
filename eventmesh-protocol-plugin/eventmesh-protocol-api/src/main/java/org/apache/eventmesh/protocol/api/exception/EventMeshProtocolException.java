package org.apache.eventmesh.protocol.api.exception;

public class EventMeshProtocolException extends RuntimeException {

    public EventMeshProtocolException(String message) {
        super(message);
    }

    public EventMeshProtocolException(Throwable throwable) {
        super(throwable);
    }
}
