package org.apache.eventmesh.protocol.api.exception;

/**
 * Protocol Handle exception, will be thrown in protocol handling.
 *
 * @since 1.3.0
 */
public class ProtocolHandleException extends Exception {

    public ProtocolHandleException(String message) {
        super(message);
    }

    public ProtocolHandleException(String message, Throwable cause) {
        super(message, cause);
    }

}
