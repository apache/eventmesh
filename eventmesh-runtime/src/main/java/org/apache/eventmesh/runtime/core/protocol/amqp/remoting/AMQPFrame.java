/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.runtime.core.protocol.amqp.remoting;


import org.apache.eventmesh.runtime.core.protocol.amqp.exception.MalformedFrameException;

import java.io.DataInputStream;
import java.io.IOException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.impl.Frame;

public class AMQPFrame implements AMQData {

    public static final int NON_BODY_SIZE = 1 /* type */ + 2 /* channel */ + 4 /* payload size */ + 1 /* end character */;

    /**
     * Frame type code
     */
    private final int type;

    /**
     * Frame channel number, 0-65535
     */
    private final int channel;

    private final ByteBuf payload;


    public AMQPFrame(int type, int channel, ByteBuf payload) {
        this.type = type;
        this.channel = channel;
        this.payload = payload;
    }

    public static AMQPFrame get(ByteBuf buf) throws IOException {
        int type = buf.readUnsignedByte();
        int channel = buf.readUnsignedShort();
        int payloadSize = buf.readInt();
        ByteBuf payload = buf.readSlice(payloadSize);
        int frameEndMarker = buf.readUnsignedByte();
        if (frameEndMarker != AMQP.FRAME_END) {
            throw new MalformedFrameException("Bad frame end marker: " + frameEndMarker);
        }

        return new AMQPFrame(type, channel, payload.retain());
    }

    public static AMQPFrame get(Frame frame) {
        return new AMQPFrame(frame.type, frame.channel, Unpooled.wrappedBuffer(frame.getPayload()));
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


    @Override
    public String toString() {
        return "AMQPFrame{"
            + "type=" + type
            + ", channel=" + channel
            + ", payload=" + payload
            + '}';
    }

}
