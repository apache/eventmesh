

package org.apache.eventmesh.runtime.core.protocol.amqp.remoting;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ContentHeader;
import com.rabbitmq.client.Method;
import io.netty.buffer.ByteBuf;

/**
 * Interface to a container for an AMQP method-and-arguments, with optional content header and body.
 */
public interface Command {
    /**
     * Retrieves the {@link Method} held within this Command. Downcast to
     * concrete (implementation-specific!) subclasses as necessary.
     *
     * @return the command's method.
     */
    Method getMethod();

    /**
     * Retrieves the ContentHeader subclass instance held as part of this Command, if any.
     *
     * Downcast to one of the inner classes of AMQP,
     * for instance {@link AMQP.BasicProperties}, as appropriate.
     *
     * @return the Command's {@link ContentHeader}, or null if none
     */
    ContentHeader getContentHeader();

    /**
     * Retrieves the body byte array that travelled as part of this
     * Command, if any.
     *
     * @return the Command's content body, or null if none
     */
    ByteBuf getContentBody();
}
