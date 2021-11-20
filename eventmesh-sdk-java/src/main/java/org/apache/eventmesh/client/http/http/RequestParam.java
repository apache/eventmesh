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

package org.apache.eventmesh.client.http.http;

import org.apache.eventmesh.common.Constants;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.netty.handler.codec.http.HttpMethod;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RequestParam {

    private Map<String, String[]> queryParams;

    private final HttpMethod httpMethod;

    private Map<String, String> body;

    private Map<String, String> headers;

    private long timeout = Constants.DEFAULT_HTTP_TIME_OUT;

    public RequestParam(HttpMethod httpMethod) {
        this.httpMethod = httpMethod;
    }

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public RequestParam setHeaders(Map<String, String> headers) {
        this.headers = headers;
        return this;
    }

    public Map<String, String> getBody() {
        return body;
    }

    public RequestParam setBody(Map<String, String> body) {
        this.body = body;
        return this;
    }

    public Map<String, String[]> getQueryParamsMap() {
        return queryParams;
    }

    public String getQueryParams() {
        if (queryParams == null || queryParams.size() == 0) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        try {
            for (Map.Entry<String, String[]> query : queryParams.entrySet()) {
                for (String val : query.getValue()) {
                    stringBuilder.append("&")
                        .append(URLEncoder.encode(query.getKey(), "UTF-8"));

                    if (val != null && !val.isEmpty()) {
                        stringBuilder.append("=")
                            .append(URLEncoder.encode(val, "UTF-8"));
                    }
                }
            }
        } catch (UnsupportedEncodingException e) {
            log.error("get query params failed.", e);
            return "";
        }
        return stringBuilder.substring(1);
    }

    public RequestParam setQueryParams(Map<String, String[]> queryParams) {
        this.queryParams = queryParams;
        return this;
    }

    public RequestParam addQueryParam(String key, String value) {
        if (queryParams == null) {
            queryParams = new HashMap<>();
        }
        if (!queryParams.containsKey(key)) {
            queryParams.put(key, new String[] {value});
        } else {
            queryParams.put(key, (String[]) Arrays.asList(queryParams.get(key), value).toArray());
        }
        return this;
    }

    public RequestParam addHeader(String key, Object value) {
        if (headers == null) {
            headers = new HashMap<>();
        }
        headers.put(key, value.toString());
        return this;
    }

    public RequestParam addBody(String key, String value) {
        if (body == null) {
            body = new HashMap<>();
        }
        body.put(key, value);
        return this;
    }

    public long getTimeout() {
        return timeout;
    }

    public RequestParam setTimeout(long timeout) {
        this.timeout = timeout;
        return this;
    }
}
