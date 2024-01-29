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

package org.apache.eventmesh.client.http.util;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.client.http.model.RequestParam;

import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.netty.handler.codec.http.HttpMethod;

public class HttpUtilsTest {

    @Test
    public void testPostPositive() throws IOException {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        String uri = "http://example.com";
        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        String expectedResult = "Success";
        when(client.execute(any(HttpPost.class), any(ResponseHandler.class))).thenReturn(expectedResult);
        String result = HttpUtils.post(client, uri, requestParam);
        Assertions.assertEquals(expectedResult, result);
    }

    @Test
    public void testPostNegative() throws IOException {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        String uri = "http://example.com";
        RequestParam requestParam = new RequestParam(HttpMethod.GET);
        String expectedResult = "Failure";
        when(client.execute(any(HttpPost.class), any(ResponseHandler.class))).thenReturn(expectedResult);
        Assertions.assertThrows(Exception.class, () -> HttpUtils.post(client, uri, requestParam));
    }

    @Test
    public void testGetPositive() throws IOException {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        String uri = "http://example.com";
        RequestParam requestParam = new RequestParam(HttpMethod.GET);
        String expectedResult = "Success";
        when(client.execute(any(HttpGet.class), any(ResponseHandler.class))).thenReturn(expectedResult);
        String result = HttpUtils.get(client, uri, requestParam);
        Assertions.assertEquals(expectedResult, result);
    }

    @Test
    public void testGetNegative() throws IOException {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        String uri = "http://example.com";
        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        String expectedResult = "Failure";
        when(client.execute(any(HttpGet.class), any(ResponseHandler.class))).thenReturn(expectedResult);
        Assertions.assertThrows(Exception.class, () -> HttpUtils.get(client, uri, requestParam));
    }
}
