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

package org.apache.eventmesh.runtime.client.common;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.ReplayingDecoder;

import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.RedirectInfo;
import org.apache.eventmesh.common.protocol.tcp.Subscription;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Codec {

    private final static Logger logger = LoggerFactory.getLogger(Codec.class);
    private static final int FRAME_MAX_LENGTH = 1024 * 1024 * 4;
    private static Charset UTF8 = Charset.forName(EventMeshConstants.DEFAULT_CHARSET);
    private static final byte[] CONSTANT_MAGIC_FLAG = "EventMesh".getBytes(UTF8);
    private static final byte[] VERSION = "0000".getBytes(UTF8);

    public static class Encoder extends MessageToByteEncoder<Package> {
        @Override
        public void encode(ChannelHandlerContext ctx, Package pkg, ByteBuf out) throws Exception {
            byte[] headerData;
            byte[] bodyData;

            final String headerJson = pkg != null ? ClientGlobal.jsonMapper.writeValueAsString(pkg.getHeader()) : null;
            final String bodyJson = pkg != null ? ClientGlobal.jsonMapper.writeValueAsString(pkg.getBody()) : null;

            headerData = headerJson == null ? null : headerJson.getBytes(UTF8);
            bodyData = bodyJson == null ? null : bodyJson.getBytes(UTF8);

            logger.debug("headerJson={}|bodyJson={}", headerJson, bodyJson);

            int headerLength = headerData == null ? 0 : headerData.length;
            int bodyLength = bodyData == null ? 0 : bodyData.length;

            int length = 4 + 4 + headerLength + bodyLength;

            if (length > FRAME_MAX_LENGTH) {
                throw new IllegalArgumentException("message size is exceed limit!");
            }

            out.writeBytes(CONSTANT_MAGIC_FLAG);
            out.writeBytes(VERSION);
            out.writeInt(length);
            out.writeInt(headerLength);
            if (headerData != null)
                out.writeBytes(headerData);
            if (bodyData != null)
                out.writeBytes(bodyData);
        }
    }

    public static class Decoder extends ReplayingDecoder {
        @Override
        public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            Header header = null;
            Object body = null;

            int length = 0;
            int headerLength = 0;
            int bodyLength = 0;

            try {
                if (null == in)
                    return;

                byte[] flagBytes = new byte[CONSTANT_MAGIC_FLAG.length];
                byte[] versionBytes = new byte[VERSION.length];

                in.readBytes(flagBytes);
                in.readBytes(versionBytes);
                if (!Arrays.equals(flagBytes, CONSTANT_MAGIC_FLAG) || !Arrays.equals(versionBytes, VERSION)) {
                    String errorMsg = String.format("invalid magic flag or " +
                            "version|flag=%s|version=%s|remoteAddress=%s", new String(flagBytes, UTF8), new String
                            (versionBytes, UTF8), ctx.channel().remoteAddress());
                    throw new Exception(errorMsg);
                }

                length = in.readInt();
                headerLength = in.readInt();
                bodyLength = length - 8 - headerLength;
                byte[] headerData = new byte[headerLength];
                byte[] bodyData = new byte[bodyLength];

                if (headerLength > 0) {
                    in.readBytes(headerData);
                    header = ClientGlobal.jsonMapper.readValue(new String(headerData, UTF8), Header.class);
                }

                if (bodyLength > 0 && header != null) {
                    in.readBytes(bodyData);
                    body = parseFromJson(header.getCommand(), new String(bodyData, UTF8));
                }

                logger.debug("headerJson={}|bodyJson={}", new String(headerData, UTF8), new String(bodyData, UTF8));

                Package pkg = new Package(header, body);
                out.add(pkg);
            } catch (Exception e) {
                logger.error("decode|length={}|headerLength={}|bodyLength={}|header={}|body={}.", length,
                        headerLength, bodyLength, header, body);
                throw e;
            }
        }
    }

    private static Object parseFromJson(Command cmd, String data) throws Exception {
        if (cmd == Command.HELLO_REQUEST || cmd == Command.RECOMMEND_REQUEST) {
            return ClientGlobal.jsonMapper.readValue(data, UserAgent.class);
        } else if (cmd == Command.SUBSCRIBE_REQUEST ||
                cmd == Command.UNSUBSCRIBE_REQUEST) {
            return ClientGlobal.jsonMapper.readValue(data, Subscription.class);
        } else if (cmd == Command.REQUEST_TO_SERVER || cmd == Command.REQUEST_TO_CLIENT || cmd == Command.RESPONSE_TO_SERVER || cmd ==
                Command.RESPONSE_TO_CLIENT || cmd == Command.ASYNC_MESSAGE_TO_SERVER || cmd == Command.ASYNC_MESSAGE_TO_CLIENT || cmd ==
                Command.BROADCAST_MESSAGE_TO_SERVER || cmd == Command.BROADCAST_MESSAGE_TO_CLIENT || cmd ==
                Command.BROADCAST_MESSAGE_TO_CLIENT_ACK || cmd == Command.ASYNC_MESSAGE_TO_CLIENT_ACK || cmd == Command.REQUEST_TO_CLIENT_ACK
                || cmd == Command.RESPONSE_TO_CLIENT_ACK) {
            return ClientGlobal.jsonMapper.readValue(data, EventMeshMessage.class);
        } else if (cmd == (Command.REDIRECT_TO_CLIENT)) {
            return ClientGlobal.jsonMapper.readValue(data, RedirectInfo.class);
        } else {
            return null;
        }
    }
}
