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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.ReplayingDecoder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Codec {

    private static final int FRAME_MAX_LENGTH = 1024 * 1024 * 4;
    private static final Charset DEFAULT_CHARSET = Charset.forName(Constants.DEFAULT_CHARSET);

    private static final byte[] CONSTANT_MAGIC_FLAG = serializeBytes("EventMesh");
    private static final byte[] VERSION = serializeBytes("0000");

    // todo: move to constants
    public static final String CLOUD_EVENTS_PROTOCOL_NAME = "cloudevents";
    public static final String EM_MESSAGE_PROTOCOL_NAME = "eventmeshmessage";
    public static final String OPEN_MESSAGE_PROTOCOL_NAME = "openmessage";

    // todo: use json util
    private static ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = new ObjectMapper();
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        OBJECT_MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        OBJECT_MAPPER.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        OBJECT_MAPPER.setTimeZone(TimeZone.getDefault());
    }

    public static class Encoder extends MessageToByteEncoder<Package> {
        @Override
        public void encode(ChannelHandlerContext ctx, Package pkg, ByteBuf out) throws Exception {
            Preconditions.checkNotNull(pkg, "TcpPackage cannot be null");
            final Header header = pkg.getHeader();
            Preconditions.checkNotNull(header, "TcpPackage header cannot be null", header);
            if (log.isDebugEnabled()) {
                log.debug("Encoder pkg={}", JsonUtils.serialize(pkg));
            }

            final byte[] headerData = serializeBytes(OBJECT_MAPPER.writeValueAsString(header));
            final byte[] bodyData;

            if (StringUtils.equals(CLOUD_EVENTS_PROTOCOL_NAME, header.getStringProperty(Constants.PROTOCOL_TYPE))) {
                bodyData = (byte[]) pkg.getBody();
            } else {
                bodyData = serializeBytes(OBJECT_MAPPER.writeValueAsString(pkg.getBody()));
            }

            int headerLength = ArrayUtils.getLength(headerData);
            int bodyLength = ArrayUtils.getLength(bodyData);

            int length = 4 + 4 + headerLength + bodyLength;

            if (length > FRAME_MAX_LENGTH) {
                throw new IllegalArgumentException("message size is exceed limit!");
            }

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
                final int bodyLength = length - 8 - headerLength;
                Header header = parseHeader(in, headerLength);
                Object body = parseBody(in, header, bodyLength);

                Package pkg = new Package(header, body);
                out.add(pkg);
            } catch (Exception e) {
                log.error("decode error| receive: {}.", deserializeBytes(in.array()));
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
            if (log.isDebugEnabled()) {
                log.debug("Decode headerJson={}", deserializeBytes(headerData));
            }
            return OBJECT_MAPPER.readValue(deserializeBytes(headerData), Header.class);
        }

        private Object parseBody(ByteBuf in, Header header, int bodyLength) throws JsonProcessingException {
            if (bodyLength <= 0 || header == null) {
                return null;
            }
            final byte[] bodyData = new byte[bodyLength];
            in.readBytes(bodyData);
            if (log.isDebugEnabled()) {
                log.debug("Decode bodyJson={}", deserializeBytes(bodyData));
            }
            return deserializeBody(deserializeBytes(bodyData), header);
        }

        private void validateFlag(byte[] flagBytes, byte[] versionBytes, ChannelHandlerContext ctx) {
            if (!Arrays.equals(flagBytes, CONSTANT_MAGIC_FLAG) || !Arrays.equals(versionBytes, VERSION)) {
                String errorMsg = String.format(
                        "invalid magic flag or version|flag=%s|version=%s|remoteAddress=%s",
                        deserializeBytes(flagBytes), deserializeBytes(versionBytes), ctx.channel().remoteAddress());
                throw new IllegalArgumentException(errorMsg);
            }
        }
    }

    private static Object deserializeBody(String bodyJsonString, Header header) throws JsonProcessingException {
        Command command = header.getCmd();
        switch (command) {
            case HELLO_REQUEST:
            case RECOMMEND_REQUEST:
                return OBJECT_MAPPER.readValue(bodyJsonString, UserAgent.class);
            case SUBSCRIBE_REQUEST:
            case UNSUBSCRIBE_REQUEST:
                return OBJECT_MAPPER.readValue(bodyJsonString, Subscription.class);
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
                // The message string will be deserialized by protocol plugin, if the event is cloudevents, the body is
                // just a string.
                return bodyJsonString;
            case REDIRECT_TO_CLIENT:
                return OBJECT_MAPPER.readValue(bodyJsonString, RedirectInfo.class);
            default:
                log.warn("Invalidate TCP command: {}", command);
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
        return new String(bytes, DEFAULT_CHARSET);
    }

    /**
     * Serialize String to bytes.
     *
     * @param str
     * @return
     */
    private static byte[] serializeBytes(String str) {
        if (str == null) {
            return null;
        }
        return str.getBytes(DEFAULT_CHARSET);
    }


}
