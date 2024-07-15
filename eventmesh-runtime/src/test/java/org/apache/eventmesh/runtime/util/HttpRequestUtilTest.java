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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;

public class HttpRequestUtilTest {

    @Test
    public void testShouldParseHttpGETRequestBody() throws IOException {
        HttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/some-path?q1=xyz");
        Map<String, Object> expected = new HashMap<>();
        expected.put("q1", "xyz");
        Assertions.assertEquals(expected, HttpRequestUtil.parseHttpRequestBody(httpRequest));
    }

    @Test
    public void testShouldParseHttpPOSTRequestBody() throws IOException {
        HttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/some-path", Unpooled.copiedBuffer(("q1=xyz").getBytes()));
        Map<String, Object> expected = new HashMap<>();
        expected.put("q1", "xyz");
        Assertions.assertEquals(expected, HttpRequestUtil.parseHttpRequestBody(httpRequest));
    }

    @Test
    public void testQueryStringToMap() {
        Map<String, Object> expected = new HashMap<>();
        expected.put("q1", "xyz");
        expected.put("q2", "abc");
        Assertions.assertEquals(expected, HttpRequestUtil.queryStringToMap("q1=xyz&q2=abc"));
    }

    @Test
    public void testGetQueryParam() {
        HttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/some-path?q1=xyz");
        Assertions.assertEquals("xyz", HttpRequestUtil.getQueryParam(httpRequest, "q1", ""));
    }

    @Test
    public void testGetBodyParam() throws IOException {
        HttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/some-path", Unpooled.copiedBuffer(("q1=xyz").getBytes()));
        Assertions.assertEquals("xyz", HttpRequestUtil.getBodyParam(httpRequest, "q1"));
    }

}
