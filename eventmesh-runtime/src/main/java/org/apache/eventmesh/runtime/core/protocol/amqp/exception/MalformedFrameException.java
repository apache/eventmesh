package org.apache.eventmesh.runtime.core.protocol.amqp.exception;

import java.io.IOException;

public class MalformedFrameException extends IOException {

    private static final long serialVersionUID = 1L;

    /**
     * Instantiate a MalformedFrameException.
     *
     * @param reason a string describing the exception
     */
    public MalformedFrameException(String reason) {
        super(reason);
    }

}
