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

package org.apache.eventmesh.runtime.admin.handler;

import org.apache.eventmesh.common.enums.HttpMethod;
import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.util.HttpResponseUtils;

import java.util.HashMap;
import java.util.Map;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;

import lombok.Data;

@Data
public abstract class AbstractHttpHandler implements org.apache.eventmesh.runtime.admin.handler.HttpHandler {

    protected void write(ChannelHandlerContext ctx, byte[] result, AsciiString headerValue) {
        ctx.writeAndFlush(HttpResponseUtils.getHttpResponse(result, ctx, headerValue)).addListener(ChannelFutureListener.CLOSE);
    }

    protected void write(ChannelHandlerContext ctx, HttpResponse response) {
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    protected void write(ChannelHandlerContext ctx, byte[] result, AsciiString headerValue, HttpResponseStatus httpResponseStatus) {
        ctx.writeAndFlush(HttpResponseUtils.getHttpResponse(result, ctx, headerValue, httpResponseStatus)).addListener(ChannelFutureListener.CLOSE);
    }

    protected void write401(ChannelHandlerContext ctx) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    protected void writeSuccess(ChannelHandlerContext ctx) {
        HttpHeaders responseHeaders = new DefaultHttpHeaders();
        responseHeaders.add(EventMeshConstants.HANDLER_ORIGIN, "*");
        DefaultFullHttpResponse response =
            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER, responseHeaders, responseHeaders);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    protected void writeSuccess(ChannelHandlerContext ctx, DefaultHttpHeaders responseHeaders) {
        DefaultFullHttpResponse response =
            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER, responseHeaders, responseHeaders);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    protected void preflight(ChannelHandlerContext ctx) {
        HttpHeaders responseHeaders = new DefaultHttpHeaders();
        responseHeaders.add(EventMeshConstants.HANDLER_ORIGIN, "*");
        responseHeaders.add(EventMeshConstants.HANDLER_METHODS, "*");
        responseHeaders.add(EventMeshConstants.HANDLER_HEADERS, "*");
        responseHeaders.add(EventMeshConstants.HANDLER_AGE, EventMeshConstants.MAX_AGE);
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER,
            responseHeaders, responseHeaders);
        write(ctx, response);
    }

    /**
     * Converts a query string to a map of key-value pairs.
     * <p>
     * This method takes a query string and parses it to create a map of key-value pairs, where each key and value are extracted from the query string
     * separated by '='.
     * <p>
     * If the query string is null, an empty map is returned.
     *
     * @param query the query string to convert to a map
     * @return a map containing the key-value pairs from the query string
     */
    protected Map<String, String> queryToMap(String query) {
        if (query == null) {
            return new HashMap<>();
        }
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }

    @Override
    public void handle(HttpCommand httpCommand, ChannelHandlerContext ctx) throws Exception {
        switch (HttpMethod.valueOf(httpCommand.getHttpMethod())) {
            case OPTIONS:
                preflight(ctx);
                break;
            case GET:
                get(httpCommand, ctx);
                break;
            case POST:
                post(httpCommand, ctx);
                break;
            case DELETE:
                delete(httpCommand, ctx);
                break;
            default: // do nothing
                break;
        }
    }

    protected void post(HttpCommand httpCommand, ChannelHandlerContext ctx) throws Exception{
    }

    protected void delete(HttpCommand httpCommand, ChannelHandlerContext ctx) throws Exception{
    }

    protected void get(HttpCommand httpCommand, ChannelHandlerContext ctx) throws Exception{
    }


}

