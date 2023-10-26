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

package org.apache.eventmesh.common.protocol.tcp.codec;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.protocol.tcp.RedirectInfo;
import org.apache.eventmesh.common.protocol.tcp.Subscription;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.LogUtils;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.ReplayingDecoder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Codec {

    private static final int FRAME_MAX_LENGTH = 1024 * 1024 * 4;

    private static final byte[] CONSTANT_MAGIC_FLAG = serializeBytes("EventMesh");
    private static final byte[] VERSION = serializeBytes("0000");

    private static Object deserializeBody(String bodyJsonString, Header header) throws JsonProcessingException {
        Command command = header.getCmd();
        switch (command) {
            case HELLO_REQUEST:
            case RECOMMEND_REQUEST:
                return JsonUtils.parseObject(bodyJsonString, UserAgent.class);
            case SUBSCRIBE_REQUEST:
            case UNSUBSCRIBE_REQUEST:
                return JsonUtils.parseObject(bodyJsonString, Subscription.class);
            case REQUEST_TO_SERVER:
            case RESPONSE_TO_SERVER:
            case ASYNC_MESSAGE_TO_SERVER:
            case BROADCAST_MESSAGE_TO_SERVER:
            case REQUEST_TO_CLIENT:
            case RESPONSE_TO_CLIENT:
            case ASYNC_MESSAGE_TO_CLIENT:
            case BROADCAST_MESSAGE_TO_CLIENT:
            case REQUEST_TO_CLIENT_ACK:
            case RESPONSE_TO_CLIENT_ACK:
            case ASYNC_MESSAGE_TO_CLIENT_ACK:
            case BROADCAST_MESSAGE_TO_CLIENT_ACK:
            case HELLO_RESPONSE:
            case RECOMMEND_RESPONSE:
            case SUBSCRIBE_RESPONSE:
            case LISTEN_RESPONSE:
            case UNSUBSCRIBE_RESPONSE:
            case HEARTBEAT_RESPONSE:
            case ASYNC_MESSAGE_TO_SERVER_ACK:
            case BROADCAST_MESSAGE_TO_SERVER_ACK:
            case CLIENT_GOODBYE_REQUEST:
            case CLIENT_GOODBYE_RESPONSE:
            case SERVER_GOODBYE_REQUEST:
            case SERVER_GOODBYE_RESPONSE:
                // The message string will be deserialized by protocol plugin, if the event is cloudevents, the body is
                // just a string.
                return bodyJsonString;
            case REDIRECT_TO_CLIENT:
                return JsonUtils.parseObject(bodyJsonString, RedirectInfo.class);
            default:
                LogUtils.warn(log, "Invalidate TCP command: {}", command);
                return null;
        }
    }

    /**
     * Deserialize bytes to String.
     *
     * @param bytes
     * @return
     */
    private static String deserializeBytes(byte[] bytes) {
        return new String(bytes, Constants.DEFAULT_CHARSET);
    }

    /**
     * Serialize String to bytes.
     *
     * @param str
     * @return
     */
    private static byte[] serializeBytes(String str) {
        if (str == null) {
            return new byte[0];
        }
        return str.getBytes(Constants.DEFAULT_CHARSET);
    }

    public static class Encoder extends MessageToByteEncoder<Package> {

        @Override
        public void encode(ChannelHandlerContext ctx, Package pkg, ByteBuf out) throws Exception {
            Preconditions.checkNotNull(pkg, "TcpPackage cannot be null");
            final Header header = pkg.getHeader();
            Preconditions.checkNotNull(header, "TcpPackage header cannot be null", header);
            if (log.isDebugEnabled()) {
                log.debug("Encoder pkg={}", JsonUtils.toJSONString(pkg));
            }

            final byte[] headerData = JsonUtils.toJSONBytes(header);
            final byte[] bodyData;

            if (StringUtils.equals(Constants.CLOUD_EVENTS_PROTOCOL_NAME, header.getStringProperty(Constants.PROTOCOL_TYPE))) {
                bodyData = (byte[]) pkg.getBody();
            } else {
                bodyData = JsonUtils.toJSONBytes(pkg.getBody());
            }

            int headerLength = ArrayUtils.getLength(headerData);
            int bodyLength = ArrayUtils.getLength(bodyData);

            final int length = CONSTANT_MAGIC_FLAG.length + VERSION.length + headerLength + bodyLength;

            if (length > FRAME_MAX_LENGTH) {
                throw new IllegalArgumentException("message size is exceed limit!");
            }
            /**
             * Header + Body, Format:
             * <pre>
             * ┌───────────────┬─────────────┬──────────────────┬──────────────────┬──────────────────┬─────────────────┐
             * │   MAGIC_FLAG  │   VERSION   │ package length   │   Header length  │      Header      │      body       │
             * │    (9bytes)   │  (4bytes)   │    (4bytes)      │      (4bytes)    │   (header bytes) │   (body bytes)  │
             * └───────────────┴─────────────┴──────────────────┴──────────────────┴──────────────────┴─────────────────┘
             * </pre>
             */
            out.writeBytes(CONSTANT_MAGIC_FLAG);
            out.writeBytes(VERSION);
            out.writeInt(length);
            out.writeInt(headerLength);
            if (headerData != null) {
                out.writeBytes(headerData);
            }
            if (bodyData != null) {
                out.writeBytes(bodyData);
            }
        }
    }

    public static class Decoder extends ReplayingDecoder<Package> {

        @Override
        public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            try {
                if (null == in) {
                    return;
                }

                byte[] flagBytes = parseFlag(in);
                byte[] versionBytes = parseVersion(in);
                validateFlag(flagBytes, versionBytes, ctx);

                final int length = in.readInt();
                final int headerLength = in.readInt();
                final int bodyLength = length - CONSTANT_MAGIC_FLAG.length - VERSION.length - headerLength;
                Header header = parseHeader(in, headerLength);
                Object body = parseBody(in, header, bodyLength);

                Package pkg = new Package(header, body);
                out.add(pkg);
            } catch (Exception e) {
                log.error("decode error| received data: {}.", deserializeBytes(in.array()), e);
                throw e;
            }
        }

        private byte[] parseFlag(ByteBuf in) {
            final byte[] flagBytes = new byte[CONSTANT_MAGIC_FLAG.length];
            in.readBytes(flagBytes);
            return flagBytes;
        }

        private byte[] parseVersion(ByteBuf in) {
            final byte[] versionBytes = new byte[VERSION.length];
            in.readBytes(versionBytes);
            return versionBytes;
        }

        private Header parseHeader(ByteBuf in, int headerLength) throws JsonProcessingException {
            if (headerLength <= 0) {
                return null;
            }
            final byte[] headerData = new byte[headerLength];
            in.readBytes(headerData);
            LogUtils.debug(log, "Decode headerJson={}", deserializeBytes(headerData));
            return JsonUtils.parseObject(headerData, Header.class);
        }

        private Object parseBody(ByteBuf in, Header header, int bodyLength) throws JsonProcessingException {
            if (bodyLength <= 0 || header == null) {
                return null;
            }
            final byte[] bodyData = new byte[bodyLength];
            in.readBytes(bodyData);
            LogUtils.debug(log, "Decode bodyJson={}", deserializeBytes(bodyData));
            return deserializeBody(deserializeBytes(bodyData), header);
        }

        private void validateFlag(byte[] flagBytes, byte[] versionBytes, ChannelHandlerContext ctx) {
            if (!Arrays.equals(flagBytes, CONSTANT_MAGIC_FLAG) || !Arrays.equals(versionBytes, VERSION)) {
                String errorMsg = String.format("invalid magic flag or version|flag=%s|version=%s|remoteAddress=%s",
                    deserializeBytes(flagBytes), deserializeBytes(versionBytes), ctx.channel().remoteAddress());
                throw new IllegalArgumentException(errorMsg);
            }
        }
    }
}
