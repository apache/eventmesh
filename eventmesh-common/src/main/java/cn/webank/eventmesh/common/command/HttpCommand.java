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

package cn.webank.eventmesh.common.command;

import cn.webank.eventmesh.common.Constants;
import cn.webank.eventmesh.common.protocol.http.body.BaseResponseBody;
import cn.webank.eventmesh.common.protocol.http.body.Body;
import cn.webank.eventmesh.common.protocol.http.header.BaseResponseHeader;
import cn.webank.eventmesh.common.protocol.http.header.Header;
import com.alibaba.fastjson.JSON;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class HttpCommand {

    private static AtomicLong requestId = new AtomicLong(0);

    private long opaque;

    private String requestCode;

    public String httpMethod;

    public String httpVersion;

    public Header header;

    public Body body;

    //Command 请求时间
    public long reqTime;

    //Command 回复时间
    public long resTime;

    public CmdType cmdType = CmdType.REQ;

    public HttpCommand() {
        this.reqTime = System.currentTimeMillis();
        this.opaque = requestId.incrementAndGet();
    }

    public HttpCommand(String httpMethod, String httpVersion, String requestCode) {
        this.httpMethod = httpMethod;
        this.httpVersion = httpVersion;
        this.reqTime = System.currentTimeMillis();
        this.requestCode = requestCode;
        this.opaque = requestId.incrementAndGet();
    }

    public HttpCommand createHttpCommandResponse(Header header,
                                                 Body body) {
        if(StringUtils.isBlank(requestCode)) {
            return null;
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

    public HttpCommand createHttpCommandResponse(Integer retCode, String retMsg) {
        if(StringUtils.isBlank(requestCode)) {
            return null;
        }
        HttpCommand response = new HttpCommand(this.httpMethod, this.httpVersion, this.requestCode);
        response.setOpaque(this.opaque);
        response.setReqTime(this.reqTime);
        BaseResponseHeader baseResponseHeader = new BaseResponseHeader();
        baseResponseHeader.setCode(requestCode);
        response.setHeader(baseResponseHeader);
        BaseResponseBody baseResponseBody = new BaseResponseBody();
        baseResponseBody.setRetCode(retCode);
        baseResponseBody.setRetMsg(retMsg);
        response.setBody(baseResponseBody);
        response.setCmdType(CmdType.RES);
        response.setResTime(System.currentTimeMillis());
        return response;
    }

    public long getReqTime() {
        return reqTime;
    }

    public void setReqTime(long reqTime) {
        this.reqTime = reqTime;
    }

    public long getResTime() {
        return resTime;
    }

    public void setResTime(long resTime) {
        this.resTime = resTime;
    }

    public long getOpaque() {
        return opaque;
    }

    public void setOpaque(long opaque) {
        this.opaque = opaque;
    }

    public CmdType getCmdType() {
        return cmdType;
    }

    public void setCmdType(CmdType cmdType) {
        this.cmdType = cmdType;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public void setHttpVersion(String httpVersion) {
        this.httpVersion = httpVersion;
    }

    public String getRequestCode() {
        return requestCode;
    }

    public void setRequestCode(String requestCode) {
        this.requestCode = requestCode;
    }

    public Header getHeader() {
        return header;
    }

    public void setHeader(Header header) {
        this.header = header;
    }

    public Body getBody() {
        return body;
    }

    public void setBody(Body body) {
        this.body = body;
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

        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(JSON.toJSONString(this.getBody()).getBytes(Constants.DEFAULT_CHARSET)));
        response.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN +
                "; charset=" + Constants.DEFAULT_CHARSET);
        response.headers().add(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        Map<String, Object> customHeader = this.getHeader().toMap();
        if (MapUtils.isNotEmpty(customHeader)) {
            HttpHeaders heads = response.headers();
            for (String key : customHeader.keySet()) {
                heads.add(key, (customHeader.get(key) == null) ? "" : customHeader.get(key));
            }
        }
        return response;
    }

    public enum CmdType {
        REQ,
        RES
    }
}