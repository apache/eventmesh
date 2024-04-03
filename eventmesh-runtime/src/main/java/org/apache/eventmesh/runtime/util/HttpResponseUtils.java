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

package org.apache.eventmesh.runtime.util;

import org.apache.eventmesh.common.Constants;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;

public class HttpResponseUtils {

    public static HttpResponse createSuccess() {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    }

    public static HttpResponse createNotFound() {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
    }

    public static HttpResponse createInternalServerError() {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }

    private static ByteBuf createByteBuf(ChannelHandlerContext ctx, String body) {
        byte[] bytes = body.getBytes(Constants.DEFAULT_CHARSET);
        return createByteBuf(ctx, bytes);
    }

    private static ByteBuf createByteBuf(ChannelHandlerContext ctx, byte[] bytes) {
        ByteBuf byteBuf = ctx.alloc().buffer(bytes.length);
        byteBuf.writeBytes(bytes);
        return byteBuf;
    }

    public static HttpResponse setResponseJsonBody(String body, ChannelHandlerContext ctx) {
        return getHttpResponse(body, ctx, HttpHeaderValues.APPLICATION_JSON);

    }

    public static HttpResponse setResponseTextBody(String body, ChannelHandlerContext ctx) {
        return getHttpResponse(body, ctx, HttpHeaderValues.TEXT_HTML);
    }

    public static HttpResponse getHttpResponse(String body, ChannelHandlerContext ctx, AsciiString headerValue) {
        HttpHeaders responseHeaders = new DefaultHttpHeaders();
        responseHeaders.add(HttpHeaderNames.CONTENT_TYPE, headerValue);
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, createByteBuf(ctx, body), responseHeaders, responseHeaders);
    }

    public static HttpResponse getHttpResponse(byte[] bytes, ChannelHandlerContext ctx, AsciiString headerValue) {
        return getHttpResponse(bytes, ctx, headerValue, HttpResponseStatus.OK);
    }

    public static HttpResponse getHttpResponse(byte[] bytes, ChannelHandlerContext ctx, AsciiString headerValue, HttpResponseStatus status) {
        HttpHeaders responseHeaders = new DefaultHttpHeaders();
        responseHeaders.add(HttpHeaderNames.CONTENT_TYPE, headerValue);
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, createByteBuf(ctx, bytes), responseHeaders, responseHeaders);
    }

    public static HttpResponse getHttpResponse(byte[] bytes, ChannelHandlerContext ctx, HttpHeaders responseHeaders, HttpResponseStatus status) {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, createByteBuf(ctx, bytes), responseHeaders, responseHeaders);
    }
}
