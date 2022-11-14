

package org.apache.eventmesh.runtime.core.protocol.amqp.exception;

import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.AMQPFrame;

/**
 * Thrown when the command parser hits an unexpected frame type.
 */
public class UnexpectedFrameError extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final AMQPFrame _frame;
    private final int _expectedFrameType;

    public UnexpectedFrameError(AMQPFrame frame, int expectedFrameType) {
        super("Received frame: " + frame + ", expected type " + expectedFrameType);
        _frame = frame;
        _expectedFrameType = expectedFrameType;
    }

    public static long getSerialVersionUID() {
        return serialVersionUID;
    }

    public AMQPFrame getReceivedFrame() {
        return _frame;
    }

    public int getExpectedFrameType() {
        return _expectedFrameType;
    }
}
