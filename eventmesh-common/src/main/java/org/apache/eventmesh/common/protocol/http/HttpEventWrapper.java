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
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.RequestURI;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;


public class HttpEventWrapper implements ProtocolTransportObject {


    private Map<String, Object> headerMap = new HashMap<>();

    private Map<String, Object> sysHeaderMap = new HashMap<>();

    private byte[] body;

    private String requestURI;

    public String httpMethod;

    public String httpVersion;

    //Command request time
    public long reqTime;

    //Command response time
    public long resTime;

    public HttpEventWrapper() {
        this(null, null, null);
    }

    public HttpEventWrapper(String httpMethod, String httpVersion, String requestURI) {
        this.httpMethod = httpMethod;
        this.httpVersion = httpVersion;
        this.reqTime = System.currentTimeMillis();
        this.requestURI = requestURI;
    }

    public HttpEventWrapper createHttpResponse(Map<String, Object> responseHeaderMap, Map<String, Object> responseBodyMap) {
        if (StringUtils.isBlank(requestURI)) {
            return null;
        }
        HttpEventWrapper response = new HttpEventWrapper(this.httpMethod, this.httpVersion, this.requestURI);
        response.setReqTime(this.reqTime);
        response.setHeaderMap(responseHeaderMap);
        response.setBody(JsonUtils.serialize(responseBodyMap).getBytes(StandardCharsets.UTF_8));
        response.setResTime(System.currentTimeMillis());
        return response;
    }

    public HttpEventWrapper createHttpResponse(EventMeshRetCode eventMeshRetCode) {
        if (StringUtils.isBlank(requestURI)) {
            return null;
        }
        HttpEventWrapper response = new HttpEventWrapper(this.httpMethod, this.httpVersion, this.requestURI);
        response.setReqTime(this.reqTime);
        Map<String, Object> responseHeaderMap = new HashMap<>();
        responseHeaderMap.put("requestURI", response.requestURI);
        response.setHeaderMap(responseHeaderMap);
        Map<String, Object> responseBodyMap = new HashMap<>();
        responseBodyMap.put("retCode", eventMeshRetCode.getRetCode());
        responseBodyMap.put("retMessage", eventMeshRetCode.getErrMsg());
        response.setBody(JsonUtils.serialize(responseBodyMap).getBytes(StandardCharsets.UTF_8));
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

    public String getRequestURI() {
        return requestURI;
    }

    public void setRequestURI(String requestURI) {
        this.requestURI = requestURI;
    }

    public Map<String, Object> getHeaderMap() {
        return headerMap;
    }

    public void setHeaderMap(Map<String, Object> headerMap) {
        this.headerMap = headerMap;
    }

    public Map<String, Object> getSysHeaderMap() {
        return sysHeaderMap;
    }

    public void setSysHeaderMap(Map<String, Object> sysHeaderMap) {
        this.sysHeaderMap = sysHeaderMap;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public DefaultFullHttpResponse httpResponse() throws Exception {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(this.body));
        HttpHeaders headers = response.headers();
        headers.add(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=" + Constants.DEFAULT_CHARSET);
        headers.add(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        headers.add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        Optional.of(this.headerMap).ifPresent(customerHeader -> customerHeader.forEach(headers::add));
        return response;
    }

    public void buildSysHeaderForClient() {
        // sys attributes
        sysHeaderMap.put(ProtocolKey.ClientInstanceKey.ENV, headerMap.getOrDefault(ProtocolKey.ClientInstanceKey.ENV, "env"));
        sysHeaderMap.put(ProtocolKey.ClientInstanceKey.IDC, headerMap.getOrDefault(ProtocolKey.ClientInstanceKey.IDC, "idc"));
        sysHeaderMap.put(ProtocolKey.ClientInstanceKey.IP, headerMap.getOrDefault(ProtocolKey.ClientInstanceKey.IP, IPUtils.getLocalAddress()));
        sysHeaderMap.put(ProtocolKey.ClientInstanceKey.PID, headerMap.getOrDefault(ProtocolKey.ClientInstanceKey.PID, ThreadUtils.getPID()));
        sysHeaderMap.put(ProtocolKey.ClientInstanceKey.SYS, headerMap.getOrDefault(ProtocolKey.ClientInstanceKey.SYS, "1234"));
        sysHeaderMap.put(ProtocolKey.ClientInstanceKey.USERNAME, headerMap.getOrDefault(ProtocolKey.ClientInstanceKey.USERNAME, "eventmesh"));
        sysHeaderMap.put(ProtocolKey.ClientInstanceKey.PASSWD, headerMap.getOrDefault(ProtocolKey.ClientInstanceKey.PASSWD, "pass"));
        sysHeaderMap.put(ProtocolKey.ClientInstanceKey.PRODUCERGROUP,
            headerMap.getOrDefault(ProtocolKey.ClientInstanceKey.PRODUCERGROUP, "em-http-producer"));
        sysHeaderMap.put(ProtocolKey.ClientInstanceKey.CONSUMERGROUP,
            headerMap.getOrDefault(ProtocolKey.ClientInstanceKey.CONSUMERGROUP, "em-http-consumer"));
        sysHeaderMap.put(ProtocolKey.PROTOCOL_TYPE, "http");
        sysHeaderMap.put(ProtocolKey.PROTOCOL_DESC, "http");
    }

    public void buildSysHeaderForCE() {
        // for cloudevents
        sysHeaderMap.put(ProtocolKey.CloudEventsKey.ID, UUID.randomUUID().toString());
        sysHeaderMap.put(ProtocolKey.CloudEventsKey.SOURCE, headerMap.getOrDefault("source", URI.create("/")));
        sysHeaderMap.put(ProtocolKey.CloudEventsKey.TYPE, headerMap.getOrDefault("type", "http_request"));

        String topic = headerMap.getOrDefault("subject", "").toString();

        if (requestURI.startsWith(RequestURI.PUBLISH.getRequestURI())) {
            topic = requestURI.substring(RequestURI.PUBLISH.getRequestURI().length() + 1);
        }

        if (StringUtils.isEmpty(topic)) {
            topic = "TEST-HTTP-TOPIC";
        }
        sysHeaderMap.put(ProtocolKey.CloudEventsKey.SUBJECT, topic);
    }

}