package org.apache.eventmesh.runtime.core.protocol.amqp.exception;

public class AmqpFrameDecodingException extends Exception {

    public AmqpFrameDecodingException(String message) {
        super(message);
    }
}
