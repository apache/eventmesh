package org.apache.eventmesh.common.remote.exception;

public class PayloadFormatException extends RemoteRuntimeException {
    public PayloadFormatException(int code, String desc) {
        super(code, desc);
    }
}
