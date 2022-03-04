package org.apache.eventmesh.trace.api.exception;

public class TraceException extends RuntimeException {

    public TraceException(String message) {
        super(message);
    }

    public TraceException(String message, Throwable cause) {
        super(message, cause);
    }
}
