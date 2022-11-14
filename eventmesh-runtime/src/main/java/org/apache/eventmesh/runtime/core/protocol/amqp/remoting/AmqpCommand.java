
package org.apache.eventmesh.runtime.core.protocol.amqp.remoting;

import org.apache.eventmesh.runtime.core.protocol.amqp.exception.UnexpectedFrameError;

import com.google.common.annotations.VisibleForTesting;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.impl.AMQContentHeader;
import com.rabbitmq.client.impl.AMQImpl;
import com.rabbitmq.client.impl.Method;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.Recycler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AmqpCommand implements Command {

    private static final ByteBuf EMPTY_BYTE_ARRAY = Unpooled.EMPTY_BUFFER;

    /** Current state, used to decide how to handle each incoming frame. */
    private enum CAState {
        EXPECTING_METHOD, EXPECTING_CONTENT_HEADER, EXPECTING_CONTENT_BODY, COMPLETE
    }

    private CAState state;

    /** The method for this command */
    private Method method;

    /** The content header for this command */
    private AMQContentHeader contentHeader;

    /** The fragments of this command's content body - a list of byte[] */
    private List<ByteBuf> bodyN;
    /** sum of the lengths of all fragments */
    private int bodyLength;

    /** No bytes of content body not yet accumulated */
    private long remainingBodyBytes;

    public static AmqpCommand get(Method method) {
        return AmqpCommand.get(method, null, null);
    }

    public static AmqpCommand get(Method method, AMQContentHeader contentHeader, ByteBuf body) {
        AmqpCommand command = RECYCLER.get();
        CAState state;
        int bodyLength = 0;
        long remainingBodyBytes = 0;

        if (command.bodyN == null) {
            command.bodyN = new ArrayList<>(2);
        } else {
            command.bodyN.clear();
        }

        if (body != null && body.readableBytes() > 0) {
            command.bodyN.add(body);
            bodyLength += body.readableBytes();
        }

        if (method == null) {
            state = CAState.EXPECTING_METHOD;
        } else if (contentHeader == null) {
            state = method.hasContent() ? CAState.EXPECTING_CONTENT_HEADER : CAState.COMPLETE;
        } else {
            remainingBodyBytes = contentHeader.getBodySize() - bodyLength;
            state = (remainingBodyBytes > 0) ? CAState.EXPECTING_CONTENT_BODY : CAState.COMPLETE;
        }

        command.method = method;
        command.contentHeader = contentHeader;
        command.bodyLength = bodyLength;
        command.remainingBodyBytes = remainingBodyBytes;
        command.state = state;
        return command;
    }

    @Override
    public Method getMethod() {
        return this.method;
    }

    @Override
    public AMQContentHeader getContentHeader() {
        return this.contentHeader;
    }

    /** @return true if the command is complete */
    public synchronized boolean isComplete() {
        return (this.state == CAState.COMPLETE);
    }

    /** Decides whether more body frames are expected */
    private void updateContentBodyState() {
        this.state = (this.remainingBodyBytes > 0) ? CAState.EXPECTING_CONTENT_BODY : CAState.COMPLETE;
    }

    private void consumeMethodFrame(AMQPFrame f) throws IOException {
        if (f.getType() == AMQP.FRAME_METHOD) {
            this.method = AMQImpl.readMethodFrom(f.getInputStream());
            this.state = this.method.hasContent() ? CAState.EXPECTING_CONTENT_HEADER : CAState.COMPLETE;
        } else {
            throw new UnexpectedFrameError(f, AMQP.FRAME_METHOD);
        }
    }

    private void consumeHeaderFrame(AMQPFrame f) throws IOException {
        if (f.getType() == AMQP.FRAME_HEADER) {
            this.contentHeader = AMQImpl.readContentHeaderFrom(f.getInputStream());
            this.remainingBodyBytes = this.contentHeader.getBodySize();
            updateContentBodyState();
        } else {
            throw new UnexpectedFrameError(f, AMQP.FRAME_HEADER);
        }
    }

    private void consumeBodyFrame(AMQPFrame f) {
        if (f.getType() == AMQP.FRAME_BODY) {
            ByteBuf fragment = Unpooled.copiedBuffer(f.getPayload());
            this.remainingBodyBytes -= fragment.readableBytes();
            updateContentBodyState();
            if (this.remainingBodyBytes < 0) {
                throw new UnsupportedOperationException("%%%%%% FIXME unimplemented");
            }
            appendBodyFragment(fragment);
        } else {
            throw new UnexpectedFrameError(f, AMQP.FRAME_BODY);
        }
    }

    /** Stitches together a fragmented content body into a single byte array */
    private ByteBuf coalesceContentBody() {
        if (this.bodyLength == 0) {
            return EMPTY_BYTE_ARRAY;
        }

        if (this.bodyN.size() == 1) {
            return this.bodyN.get(0);
        }

        ByteBuf body = Unpooled.buffer(bodyLength);
        for (ByteBuf fragment : this.bodyN) {
            body.writeBytes(fragment);
        }
        this.bodyN.clear();
        this.bodyN.add(body);
        return body;
    }

    @Override
    public synchronized ByteBuf getContentBody() {
        return coalesceContentBody();
    }

    @VisibleForTesting
    public List<ByteBuf> getBodyN() {
        return bodyN;
    }

    private void appendBodyFragment(ByteBuf fragment) {
        if (fragment == null || fragment.readableBytes() == 0) {
            return;
        }

        bodyN.add(fragment);
        bodyLength += fragment.readableBytes();
    }

    /**
     * @param f frame to be incorporated
     * @return true if command becomes complete
     * @throws IOException if error reading frame
     */
    public synchronized boolean handleFrame(AMQPFrame f) throws IOException {
        switch (this.state) {
            case EXPECTING_METHOD:
                consumeMethodFrame(f);
                break;
            case EXPECTING_CONTENT_HEADER:
                consumeHeaderFrame(f);
                break;
            case EXPECTING_CONTENT_BODY:
                consumeBodyFrame(f);
                break;

            default:
                throw new IllegalStateException("Bad Command State " + this.state);
        }
        return isComplete();
    }

    private final Recycler.Handle<AmqpCommand> recyclerHandle;

    private AmqpCommand(Recycler.Handle<AmqpCommand> recyclerHandle) {
        this.recyclerHandle = recyclerHandle;
    }

    private static final Recycler<AmqpCommand> RECYCLER = new Recycler<AmqpCommand>() {

        @Override
        protected AmqpCommand newObject(Handle<AmqpCommand> handle) {
            return new AmqpCommand(handle);
        }
    };

    public void recycle() {
        this.state = null;
        this.method = null;
        this.contentHeader = null;

        if (this.bodyN != null) {
            this.bodyN.clear();
        }

        this.bodyLength = -1;
        this.remainingBodyBytes = -1;

        if (recyclerHandle != null) {
            recyclerHandle.recycle(this);
        }

    }

}
