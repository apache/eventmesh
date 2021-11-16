package org.apache.eventmesh.connector.rocketmq.exception;

import org.apache.eventmesh.api.exception.ConnectorRuntimeException;

public class RMQTimeoutException extends ConnectorRuntimeException {

    public RMQTimeoutException(String message) {
        super(message);
    }

    public RMQTimeoutException(Throwable throwable) {
        super(throwable);
    }

    public RMQTimeoutException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
