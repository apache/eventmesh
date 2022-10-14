package org.apache.eventmesh.runtime.core.protocol.amqp.exception;

public class AmqpNotFoundException extends Exception {

    public AmqpNotFoundException() {
    }

    public AmqpNotFoundException(String message) {
        super(message);
    }

    public AmqpNotFoundException(Throwable cause) {
        super(cause);
    }
}
