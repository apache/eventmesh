package org.apache.eventmesh.runtime.core.protocol.amqp.exception;

public class AmqpException extends Exception {

    protected int errorCode;

    public AmqpException() {
    }

    public AmqpException(int errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public AmqpException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AmqpException(String message, Throwable cause, int errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
