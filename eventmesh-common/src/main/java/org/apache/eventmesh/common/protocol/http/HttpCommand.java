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

package org.apache.eventmesh.common.protocol.http;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.http.body.BaseResponseBody;
import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.BaseResponseHeader;
import org.apache.eventmesh.common.protocol.http.header.Header;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import lombok.Data;

@Data
public class HttpCommand implements ProtocolTransportObject {

    private static final long serialVersionUID = -8763824685105888009L;

    private static final AtomicLong requestId = new AtomicLong(0);

    private long opaque;

    private String requestCode;

    public String httpMethod;

    public String httpVersion;

    private transient Header header;

    private transient Body body;

    // Command request time
    public long reqTime;

    // Command response time
    public long resTime;

    public CmdType cmdType = CmdType.REQ;

    public HttpCommand() {
        this(null, null, null);
    }

    public HttpCommand(String httpMethod, String httpVersion, String requestCode) {
        this.httpMethod = httpMethod;
        this.httpVersion = httpVersion;
        this.reqTime = System.currentTimeMillis();
        this.requestCode = requestCode;
        this.opaque = requestId.incrementAndGet();
    }

    public HttpCommand createHttpCommandResponse(Header header, Body body) {
        if (this.requestCode == null) {
            this.requestCode = RequestCode.UNKNOWN.getRequestCode().toString();
        }
        HttpCommand response = new HttpCommand(this.httpMethod, this.httpVersion, this.requestCode);
        response.setOpaque(this.opaque);
        response.setReqTime(this.reqTime);
        response.setHeader(header);
        response.setBody(body);
        response.setCmdType(CmdType.RES);
        response.setResTime(System.currentTimeMillis());
        return response;
    }

    public HttpCommand createHttpCommandResponse(EventMeshRetCode eventMeshRetCode) {
        if (this.requestCode == null) {
            this.requestCode = RequestCode.UNKNOWN.getRequestCode().toString();
        }
        HttpCommand response = new HttpCommand(this.httpMethod, this.httpVersion, this.requestCode);
        response.setOpaque(this.opaque);
        response.setReqTime(this.reqTime);
        BaseResponseHeader baseResponseHeader = new BaseResponseHeader();
        baseResponseHeader.setCode(requestCode);
        response.setHeader(baseResponseHeader);
        BaseResponseBody baseResponseBody = new BaseResponseBody();
        baseResponseBody.setRetCode(eventMeshRetCode.getRetCode());
        baseResponseBody.setRetMsg(eventMeshRetCode.getErrMsg());
        response.setBody(baseResponseBody);
        response.setCmdType(CmdType.RES);
        response.setResTime(System.currentTimeMillis());
        return response;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("httpCommand={")
            .append(cmdType).append(",")
            .append(httpMethod).append("/").append(httpVersion).append(",")
            .append("requestCode=").append(requestCode).append(",")
            .append("opaque=").append(opaque).append(",");

        if (cmdType == CmdType.RES) {
            sb.append("cost=").append(resTime - reqTime).append(",");
        }

        sb.append("header=").append(header).append(",")
            .append("body=").append(body)
            .append("}");

        return sb.toString();
    }

    public String abstractDesc() {
        StringBuilder sb = new StringBuilder();
        sb.append("httpCommand={")
            .append(cmdType).append(",")
            .append(httpMethod).append("/").append(httpVersion).append(",")
            .append("requestCode=").append(requestCode).append(",")
            .append("opaque=").append(opaque).append(",");

        if (cmdType == CmdType.RES) {
            sb.append("cost=").append(resTime - reqTime).append(",");
        }

        sb.append("header=").append(header).append(",")
            .append("bodySize=").append(body.toString().length()).append("}");

        return sb.toString();
    }

    public String simpleDesc() {
        StringBuilder sb = new StringBuilder();
        sb.append("httpCommand={")
            .append(cmdType).append(",")
            .append(httpMethod).append("/").append(httpVersion).append(",")
            .append("requestCode=").append(requestCode).append("}");

        return sb.toString();
    }

    public DefaultFullHttpResponse httpResponse() throws Exception {
        if (cmdType == CmdType.REQ) {
            return null;
        }
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
            Unpooled.wrappedBuffer(Objects.requireNonNull(JsonUtils.toJSONString(this.getBody())).getBytes(Constants.DEFAULT_CHARSET)));
        HttpHeaders headers = response.headers();
        headers.add(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=" + Constants.DEFAULT_CHARSET);
        headers.add(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        headers.add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        Optional.of(this.getHeader().toMap()).ifPresent(customerHeader -> customerHeader.forEach(headers::add));
        return response;
    }

    public DefaultFullHttpResponse httpResponse(HttpResponseStatus httpResponseStatus) throws Exception {
        if (cmdType == CmdType.REQ) {
            return null;
        }
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, httpResponseStatus,
            Unpooled.wrappedBuffer(Objects.requireNonNull(JsonUtils.toJSONString(this.getBody())).getBytes(Constants.DEFAULT_CHARSET)));
        HttpHeaders headers = response.headers();
        headers.add(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=" + Constants.DEFAULT_CHARSET);
        headers.add(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        headers.add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        Optional.of(this.getHeader().toMap()).ifPresent(customerHeader -> customerHeader.forEach(headers::add));
        return response;
    }

    public enum CmdType {
        REQ,
        RES
    }
}
