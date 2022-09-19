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
package org.apache.eventmesh.runtime.core.protocol.amqp.remoting.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.runtime.core.protocol.amqp.exception.MalformedFrameException;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.AMQPFrame;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol.ProtocolFrame;
import org.apache.eventmesh.runtime.core.protocol.amqp.remoting.protocol.ProtocolVersion;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import java.io.IOException;
import java.util.List;

@Slf4j
@ChannelHandler.Sharable
public class AmqpCodeDecoder extends AbstractBatchDecoder {

    /**
     * the length of protocol code
     */
    protected int protocolVersionLength = 8;

    /**
     * only support 091
     */
    protected ProtocolVersion DEFAULT_PROTOCOL_VERSION = ProtocolVersion.v0_91;

    protected int maxFrameSize;

    public AmqpCodeDecoder(int maxFrameSize) {
        super();
        this.maxFrameSize = maxFrameSize;
    }

    private boolean firstRead = true;

    /**
     * decode the protocol code
     *
     * @param in input byte buf
     * @return an instance of ProtocolVersion
     */
    protected ProtocolVersion decodeProtocolVersion(ByteBuf in) {
        if (in.readableBytes() >= protocolVersionLength) {
            byte[] protocolCodeBytes = new byte[protocolVersionLength];
            in.readBytes(protocolCodeBytes);
            return ProtocolVersion.fromBytes(protocolCodeBytes);
        }
        return null;
    }

    /**
     * decode the protocol version
     *
     * @param in input byte buf
     * @return a byte to represent protocol version
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (firstRead) {
            if (in.readableBytes() >= protocolVersionLength) {
                in.markReaderIndex();
                ProtocolVersion protocolVersion = decodeProtocolVersion(in);

                if (checkProtocolVersion(protocolVersion)) {
                    in.resetReaderIndex();
                    decode0(ctx, in, out, true);
                    firstRead = false;
                } else {
                    log.error("protocol {} not support", protocolVersion);
                    ctx.writeAndFlush(new ProtocolFrame(DEFAULT_PROTOCOL_VERSION));
                    ctx.close();
                }
            }

        } else {
            decode0(ctx, in, out, false);

        }

    }

    private void decode0(ChannelHandlerContext ctx, ByteBuf in, List<Object> out, boolean isProtocolHeader) throws IOException {

        if (isProtocolHeader) {
            if (in.readableBytes() >= ProtocolFrame.PROTOCOL_FRAME_LENGTH) {
                out.add(ProtocolFrame.decode(in));

            }
        } else {
            if (in.readableBytes() >= AMQPFrame.NON_BODY_SIZE) {
                in.markReaderIndex();
                in.readUnsignedByte();
                in.readUnsignedShort();
                int payloadSize = in.readInt();
                in.resetReaderIndex();
                if (payloadSize > maxFrameSize) {
                    throw new MalformedFrameException("frame > maxFrameSize exception remoteAddress:" + RemotingHelper.parseChannelRemoteAddr(ctx.channel()));
                }

                if (in.readableBytes() >= (AMQPFrame.NON_BODY_SIZE + payloadSize)) {

                    out.add(AMQPFrame.get(in));
                }
            }

        }


    }


    private boolean checkProtocolVersion(ProtocolVersion pv) {
        return pv != null && pv.equals(DEFAULT_PROTOCOL_VERSION);
    }
}
