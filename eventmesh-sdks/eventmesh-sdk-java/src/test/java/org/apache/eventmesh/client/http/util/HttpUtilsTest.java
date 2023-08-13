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

import org.junit.Assert;
import org.junit.Test;

import io.netty.handler.codec.http.HttpMethod;

public class HttpUtilsTest {


    @Test
    public void testPostPositive() {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        String uri = "http://example.com";
        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        IOException exception = null;
        try {
            String expectedResult = "Success";
            when(client.execute(any(HttpPost.class), any(ResponseHandler.class))).thenReturn(expectedResult);
            String result = HttpUtils.post(client, uri, requestParam);
            Assert.assertEquals(expectedResult, result);
        } catch (IOException e) {
            exception = e;
        }
        Assert.assertNull(exception);
    }

    @Test
    public void testPostNegative() {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        String uri = "http://example.com";
        RequestParam requestParam = new RequestParam(HttpMethod.GET);
        String expectedResult = "Failure";
        try {
            when(client.execute(any(HttpPost.class), any(ResponseHandler.class))).thenReturn(expectedResult);
            String result = HttpUtils.post(client, uri, requestParam);
            Assert.assertNotEquals(expectedResult, result);
        } catch (Exception e) {
            Assert.assertNotNull(e);
        }
    }

    @Test
    public void testGetPositive() {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        String uri = "http://example.com";
        RequestParam requestParam = new RequestParam(HttpMethod.GET);
        String expectedResult = "Success";
        IOException exception = null;
        try {
            when(client.execute(any(HttpGet.class), any(ResponseHandler.class))).thenReturn(expectedResult);
            String result = HttpUtils.get(client, uri, requestParam);
            Assert.assertEquals(expectedResult, result);
        } catch (IOException e) {
            exception = e;
        }
        Assert.assertNull(exception);
    }

    @Test
    public void testGetNegative() {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        String uri = "http://example.com";
        RequestParam requestParam = new RequestParam(HttpMethod.POST);
        String expectedResult = "Failure";
        try {
            when(client.execute(any(HttpGet.class), any(ResponseHandler.class))).thenReturn(expectedResult);
            String result = HttpUtils.get(client, uri, requestParam);
            Assert.assertNotEquals(expectedResult, result);
        } catch (Exception e) {
            Assert.assertNotNull(e);
        }
    }
}
