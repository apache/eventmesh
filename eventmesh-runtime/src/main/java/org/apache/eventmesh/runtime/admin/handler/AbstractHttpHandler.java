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
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.util.HttpResponseUtils;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

import lombok.Data;

@Data
public abstract class AbstractHttpHandler implements HttpHandler {

    protected void writeText(ChannelHandlerContext ctx, String result) {
        HttpHeaders responseHeaders = new DefaultHttpHeaders();
        responseHeaders.add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_HTML);
        responseHeaders.add(EventMeshConstants.HANDLER_ORIGIN, "*");
        write(ctx, HttpResponseUtils.buildHttpResponse(result, ctx, responseHeaders, HttpResponseStatus.OK));
    }

    protected void writeJson(ChannelHandlerContext ctx, String result) {
        HttpHeaders responseHeaders = new DefaultHttpHeaders();
        responseHeaders.add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
        responseHeaders.add(EventMeshConstants.HANDLER_ORIGIN, "*");
        write(ctx, HttpResponseUtils.buildHttpResponse(result, ctx, responseHeaders, HttpResponseStatus.OK));
    }

    protected void writeUnauthorized(ChannelHandlerContext ctx, String result) {
        HttpHeaders responseHeaders = new DefaultHttpHeaders();
        responseHeaders.add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
        responseHeaders.add(EventMeshConstants.HANDLER_ORIGIN, "*");
        write(ctx, HttpResponseUtils.buildHttpResponse(result, ctx, responseHeaders, HttpResponseStatus.UNAUTHORIZED));
    }

    /**
     * Use {@link HttpResponseUtils#buildHttpResponse} to build {@link HttpResponse} param.
     */
    protected void write(ChannelHandlerContext ctx, HttpResponse response) {
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void handle(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        switch (HttpMethod.valueOf(httpRequest.method().name())) {
            case OPTIONS:
                preflight(ctx);
                break;
            case GET:
                get(httpRequest, ctx);
                break;
            case POST:
                post(httpRequest, ctx);
                break;
            case PUT:
                put(httpRequest, ctx);
                break;
            case DELETE:
                delete(httpRequest, ctx);
                break;
            default:
                // do nothing
                break;
        }
    }

    protected void preflight(ChannelHandlerContext ctx) {
        HttpHeaders responseHeaders = new DefaultHttpHeaders();
        responseHeaders.add(EventMeshConstants.HANDLER_ORIGIN, "*");
        responseHeaders.add(EventMeshConstants.HANDLER_METHODS, "*");
        responseHeaders.add(EventMeshConstants.HANDLER_HEADERS, "*");
        responseHeaders.add(EventMeshConstants.HANDLER_AGE, EventMeshConstants.MAX_AGE);
        write(ctx, HttpResponseUtils.buildHttpResponse("", ctx, responseHeaders, HttpResponseStatus.OK));
    }

    protected void get(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        // Override this method in subclass
    }

    /**
     * Add new resource.
     */
    protected void post(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        // Override this method in subclass
    }

    /**
     * Update resource, should be idempotent.
     */
    protected void put(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        // Override this method in subclass
    }

    protected void delete(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        // Override this method in subclass
    }
}

