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

package org.apache.eventmesh.client.http.model;

import org.apache.eventmesh.common.Constants;

import org.apache.commons.collections4.MapUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.netty.handler.codec.http.HttpMethod;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class RequestParam {

    private Map<String, String[]> queryParams;

    private final HttpMethod httpMethod;

    private Map<String, String> body;

    private Map<String, String> headers;

    private long timeout = Constants.DEFAULT_HTTP_TIME_OUT;

    public RequestParam(final HttpMethod httpMethod) {
        this.httpMethod = httpMethod;
    }

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public RequestParam setHeaders(final Map<String, String> headers) {
        this.headers = headers;
        return this;
    }

    public Map<String, String> getBody() {
        return body;
    }

    public RequestParam setBody(final Map<String, String> body) {
        this.body = body;
        return this;
    }

    public Map<String, String[]> getQueryParamsMap() {
        return queryParams;
    }

    public String getQueryParams() {
        if (MapUtils.isEmpty(queryParams)) {
            return "";
        }
        final StringBuilder stringBuilder = new StringBuilder();
        try {
            for (final Map.Entry<String, String[]> query : queryParams.entrySet()) {
                for (final String val : query.getValue()) {
                    stringBuilder.append(Constants.AND)
                            .append(URLEncoder.encode(query.getKey(), StandardCharsets.UTF_8.name()));

                    if (val != null && !val.isEmpty()) {
                        stringBuilder.append("=")
                                .append(URLEncoder.encode(val, StandardCharsets.UTF_8.name()));
                    }
                }
            }
        } catch (UnsupportedEncodingException e) {
            log.error("get query params failed.", e);
            return "";
        }
        return stringBuilder.substring(1);
    }

    public RequestParam setQueryParams(final Map<String, String[]> queryParams) {
        this.queryParams = queryParams;
        return this;
    }

    public RequestParam addQueryParam(final String key, final String value) {
        if (queryParams == null) {
            queryParams = new HashMap<>();
        }
        if (!queryParams.containsKey(key)) {
            queryParams.put(key, new String[]{value});
        } else {
            queryParams.put(key, (String[]) Arrays.asList(queryParams.get(key), value).toArray());
        }
        return this;
    }

    public RequestParam addHeader(final String key, final Object value) {
        if (headers == null) {
            headers = new HashMap<>();
        }
        headers.put(key, value.toString());
        return this;
    }

    public RequestParam addBody(final String key, final String value) {
        if (body == null) {
            body = new HashMap<>();
        }
        body.put(key, value);
        return this;
    }

    public long getTimeout() {
        return timeout;
    }

    public RequestParam setTimeout(final long timeout) {
        this.timeout = timeout;
        return this;
    }
}
