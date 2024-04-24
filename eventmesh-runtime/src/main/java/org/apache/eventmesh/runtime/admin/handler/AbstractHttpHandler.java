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
import org.apache.eventmesh.runtime.admin.response.Result;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.util.HttpResponseUtils;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

import lombok.Data;

@Data
public abstract class AbstractHttpHandler implements HttpHandler {

    protected void writeText(ChannelHandlerContext ctx, String text) {
        HttpHeaders responseHeaders = HttpResponseUtils.buildDefaultHttpHeaders(HttpHeaderValues.TEXT_HTML);
        write(ctx, HttpResponseUtils.buildHttpResponse(text, ctx, responseHeaders, HttpResponseStatus.OK));
    }

    /**
     * Return given JSON String with given {@link HttpResponseStatus}.
     */
    protected void writeJson(ChannelHandlerContext ctx, String json, HttpResponseStatus status) {
        HttpHeaders responseHeaders = HttpResponseUtils.buildDefaultHttpHeaders(HttpHeaderValues.APPLICATION_JSON);
        write(ctx, HttpResponseUtils.buildHttpResponse(json, ctx, responseHeaders, status));
    }

    /**
     * Return given JSON String with status {@link HttpResponseStatus#OK}.
     */
    protected void writeJson(ChannelHandlerContext ctx, String json) {
        writeJson(ctx, json, HttpResponseStatus.OK);
    }

    /**
     * Serialize given data into the JSON String of {@link Result} and return with status {@link HttpResponseStatus#OK}.
     */
    protected void writeSuccess(ChannelHandlerContext ctx, Object data) {
        Result<Object> result = Result.success(data);
        String json = JSON.toJSONString(result, JSONWriter.Feature.WriteNulls);
        writeJson(ctx, json);
    }

    /**
     * Wrap given message to {@link Result} and return with status {@link HttpResponseStatus#BAD_REQUEST}.
     */
    protected void writeBadRequest(ChannelHandlerContext ctx, String message) {
        Result<String> result = new Result<>(message);
        String json = JSON.toJSONString(result, JSONWriter.Feature.WriteNulls);
        writeJson(ctx, json, HttpResponseStatus.BAD_REQUEST);
    }

    protected void writeUnauthorized(ChannelHandlerContext ctx, String message) {
        Result<String> result = new Result<>(message);
        String json = JSON.toJSONString(result, JSONWriter.Feature.WriteNulls);
        writeJson(ctx, json, HttpResponseStatus.UNAUTHORIZED);
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

