package org.apache.eventmesh.connector.rocketmq.exception;

import org.apache.eventmesh.api.exception.ConnectorRuntimeException;

public class RMQMessageFormatException extends ConnectorRuntimeException {

    public RMQMessageFormatException(String message) {
        super(message);
    }

    public RMQMessageFormatException(Throwable throwable) {
        super(throwable);
    }

    public RMQMessageFormatException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
