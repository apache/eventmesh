package org.apache.eventmesh.common.remote.exception;

public class RemoteRuntimeException extends RuntimeException{
    protected final int code;
    protected final String message;
    public RemoteRuntimeException(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
