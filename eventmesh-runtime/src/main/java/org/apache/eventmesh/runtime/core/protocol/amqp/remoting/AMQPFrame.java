package org.apache.eventmesh.runtime.core.protocol.amqp.remoting;


import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.MalformedFrameException;
import com.rabbitmq.client.impl.Frame;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.util.Recycler;

import java.io.DataInputStream;
import java.io.IOException;

public class AMQPFrame implements AMQData {

    public static final int NON_BODY_SIZE = 1 /* type */ + 2 /* channel */ + 4 /* payload size */ + 1 /* end character */;

    /** Frame type code */
    private int type;

    /** Frame channel number, 0-65535 */
    private int channel;

    private ByteBuf payload;

    public static AMQPFrame get(ByteBuf buf) throws IOException {
        int type = buf.readUnsignedByte();
        int channel = buf.readUnsignedShort();
        int payloadSize = buf.readInt();
        ByteBuf payload = buf.readSlice(payloadSize);
        int frameEndMarker = buf.readUnsignedByte();
        if (frameEndMarker != AMQP.FRAME_END) {
            throw new MalformedFrameException("Bad frame end marker: " + frameEndMarker);
        }
        AMQPFrame frame = RECYCLER.get();
        frame.type = type;
        frame.channel = channel;
        // increase ref cnt
        frame.payload = payload.retain();
        return frame;
    }

    public static AMQPFrame get(Frame frame) {
        AMQPFrame amqpFrame = RECYCLER.get();
        amqpFrame.type = frame.type;
        amqpFrame.channel = frame.channel;
        amqpFrame.payload = Unpooled.wrappedBuffer(frame.getPayload());
        return amqpFrame;
    }

    public static AMQPFrame get(int type, int channel) {
        AMQPFrame frame = RECYCLER.get();
        frame.type = type;
        frame.channel = channel;
        return frame;
    }

    public static AMQPFrame get(int type, int channel, ByteBuf payload) {
        AMQPFrame frame = RECYCLER.get();
        frame.type = type;
        frame.channel = channel;
        frame.payload = payload;
        return frame;
    }

    @Override
    public void encode(ByteBuf out) {
        out.writeByte(type);
        out.writeShort(channel);
        if (payload != null) {
            out.writeInt(payload.readableBytes());
            out.writeBytes(payload);
        } else {
            out.writeInt(0);
            out.writeBytes(new byte[0]);
        }

        out.writeByte(AMQP.FRAME_END);
    }

    public int getType() {
        return type;
    }

    public int getChannel() {
        return channel;
    }

    public byte[] getData() {

        if (payload == null) {
            return new byte[0];
        }

        if (payload.isDirect()) {
            byte[] data = new byte[payload.readableBytes()];
            payload.getBytes(payload.readerIndex(), data);
            return data;
        }

        if (payload.arrayOffset() == 0 && payload.capacity() == payload.array().length) {
            return payload.array();
        } else {
            // Need to copy into a smaller byte array
            byte[] data = new byte[payload.readableBytes()];
            payload.readBytes(data);
            return data;
        }
    }

    public ByteBuf getPayload() {
        return this.payload;
    }

    public DataInputStream getInputStream() {
        return new DataInputStream(new ByteBufInputStream(this.payload));
    }

    private final Recycler.Handle<AMQPFrame> recyclerHandle;

    private AMQPFrame(Recycler.Handle<AMQPFrame> recyclerHandle) {
        this.recyclerHandle = recyclerHandle;
    }

    private static final Recycler<AMQPFrame> RECYCLER = new Recycler<AMQPFrame>() {

        @Override
        protected AMQPFrame newObject(Handle<AMQPFrame> handle) {
            return new AMQPFrame(handle);
        }
    };

    @Override
    public void recycle() {
        this.type = -1;
        this.channel = -1;
        this.payload = null;
        if (recyclerHandle != null) {
            recyclerHandle.recycle(this);
        }

    }

    @Override
    public String toString() {
        return "AMQPFrame{" +
            "type=" + type +
            ", channel=" + channel +
            ", payload=" + payload +
            '}';
    }

}
