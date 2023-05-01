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

import static org.apache.eventmesh.common.Constants.DEFAULT_CHARSET;
import static org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey.PROTOCOL_DESC;
import static org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey.PROTOCOL_TYPE;
import static org.apache.eventmesh.common.protocol.http.common.ProtocolKey.ClientInstanceKey.CONSUMERGROUP;
import static org.apache.eventmesh.common.protocol.http.common.ProtocolKey.ClientInstanceKey.ENV;
import static org.apache.eventmesh.common.protocol.http.common.ProtocolKey.ClientInstanceKey.IDC;
import static org.apache.eventmesh.common.protocol.http.common.ProtocolKey.ClientInstanceKey.IP;
import static org.apache.eventmesh.common.protocol.http.common.ProtocolKey.ClientInstanceKey.PASSWD;
import static org.apache.eventmesh.common.protocol.http.common.ProtocolKey.ClientInstanceKey.PID;
import static org.apache.eventmesh.common.protocol.http.common.ProtocolKey.ClientInstanceKey.PRODUCERGROUP;
import static org.apache.eventmesh.common.protocol.http.common.ProtocolKey.ClientInstanceKey.SYS;
import static org.apache.eventmesh.common.protocol.http.common.ProtocolKey.ClientInstanceKey.USERNAME;
import static org.apache.eventmesh.common.protocol.http.common.ProtocolKey.CloudEventsKey.ID;
import static org.apache.eventmesh.common.protocol.http.common.ProtocolKey.CloudEventsKey.SOURCE;
import static org.apache.eventmesh.common.protocol.http.common.ProtocolKey.CloudEventsKey.SUBJECT;
import static org.apache.eventmesh.common.protocol.http.common.ProtocolKey.CloudEventsKey.TYPE;
import static org.apache.eventmesh.common.protocol.http.common.RequestURI.PUBLISH;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.util.Maps;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;


import lombok.Getter;
import lombok.Setter;

public class HttpEventWrapper implements ProtocolTransportObject {

    public static final long serialVersionUID = -8547334421415366981L;

    @Getter
    @Setter
    private Map<String, Object> headerMap = new HashMap<>();

    @Getter
    @Setter
    private Map<String, Object> sysHeaderMap = new HashMap<>();

    private byte[] body;

    @Getter
    @Setter
    private String requestURI;

    @Getter
    @Setter
    public String httpMethod;

    @Getter
    @Setter
    public String httpVersion;

    //Command request time
    @Getter
    @Setter
    public long reqTime = System.currentTimeMillis();

    //Command response time
    @Getter
    @Setter
    public long resTime;

    public HttpEventWrapper() {
        this(null, null, null);
    }

    public HttpEventWrapper(String httpMethod, String httpVersion, String requestURI) {
        this.httpMethod = httpMethod;
        this.httpVersion = httpVersion;
        this.requestURI = requestURI;
    }

    public HttpEventWrapper createHttpResponse(Map<String, Object> responseHeaderMap, Map<String, Object> responseBodyMap) {
        if (StringUtils.isBlank(requestURI)) {
            return null;
        }
        HttpEventWrapper response = new HttpEventWrapper(this.httpMethod, this.httpVersion, this.requestURI);
        response.setReqTime(this.reqTime);
        response.setHeaderMap(responseHeaderMap);
        response.setBody(Objects.requireNonNull(JsonUtils.toJSONString(responseBodyMap)).getBytes(DEFAULT_CHARSET));
        response.setResTime(System.currentTimeMillis());
        return response;
    }

    public HttpEventWrapper createHttpResponse(EventMeshRetCode eventMeshRetCode) {
        if (StringUtils.isBlank(requestURI)) {
            return null;
        }
        HttpEventWrapper response = new HttpEventWrapper(this.httpMethod, this.httpVersion, this.requestURI);
        response.setReqTime(this.reqTime);
        Map<String, Object> responseHeaderMap = Maps.newHashMap("requestURI", response.requestURI);
        response.setHeaderMap(responseHeaderMap);
        Map<String, Object> responseBodyMap = new HashMap<>();
        responseBodyMap.put("retCode", eventMeshRetCode.getRetCode());
        responseBodyMap.put("retMessage", eventMeshRetCode.getErrMsg());
        response.setBody(Objects.requireNonNull(JsonUtils.toJSONString(responseBodyMap)).getBytes(DEFAULT_CHARSET));
        response.setResTime(System.currentTimeMillis());
        return response;
    }

    public byte[] getBody() {
        int len = body.length;
        byte[] b = new byte[len];
        System.arraycopy(body, 0, b, 0, len);
        return b;
    }

    public void setBody(byte[] newBody) {
        if (newBody == null || newBody.length == 0) {
            return;
        }

        int len = newBody.length;
        this.body = new byte[len];
        System.arraycopy(newBody, 0, this.body, 0, len);
    }

    public DefaultFullHttpResponse httpResponse() throws Exception {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer(this.body));
        HttpHeaders headers = response.headers();
        headers.add(CONTENT_TYPE, "text/plain; charset=" + DEFAULT_CHARSET);
        headers.add(CONTENT_LENGTH, response.content().readableBytes());
        headers.add(CONNECTION, KEEP_ALIVE);
        Optional.of(this.headerMap).ifPresent(customerHeader -> customerHeader.forEach(headers::add));
        return response;
    }

    public void buildSysHeaderForClient() {
        // sys attributes

        sysHeaderMap.put(ENV, headerMap.getOrDefault(ENV, "env"));
        sysHeaderMap.put(IDC, headerMap.getOrDefault(IDC, "idc"));
        sysHeaderMap.put(IP, headerMap.getOrDefault(IP, IPUtils.getLocalAddress()));
        sysHeaderMap.put(PID, headerMap.getOrDefault(PID, ThreadUtils.getPID()));
        sysHeaderMap.put(SYS, headerMap.getOrDefault(SYS, "1234"));
        sysHeaderMap.put(USERNAME, headerMap.getOrDefault(USERNAME, "eventmesh"));
        sysHeaderMap.put(PASSWD, headerMap.getOrDefault(PASSWD, "pass"));
        sysHeaderMap.put(PRODUCERGROUP, headerMap.getOrDefault(PRODUCERGROUP, "em-http-producer"));
        sysHeaderMap.put(CONSUMERGROUP, headerMap.getOrDefault(CONSUMERGROUP, "em-http-consumer"));
        sysHeaderMap.put(PROTOCOL_TYPE, "http");
        sysHeaderMap.put(PROTOCOL_DESC, "http");
    }

    public void buildSysHeaderForCE() {
        // for cloudevents
        sysHeaderMap.put(ID, UUID.randomUUID().toString());
        sysHeaderMap.put(SOURCE, headerMap.getOrDefault("source", URI.create("/")));
        sysHeaderMap.put(TYPE, headerMap.getOrDefault("type", "http_request"));

        String topic = headerMap.getOrDefault("subject", "").toString();

        if (requestURI.startsWith(PUBLISH.getRequestURI())) {
            topic = requestURI.substring(PUBLISH.getRequestURI().length() + 1);
        }

        if (StringUtils.isEmpty(topic)) {
            topic = "TEST-HTTP-TOPIC";
        }
        sysHeaderMap.put(SUBJECT, topic);
    }

}