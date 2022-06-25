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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.runtime.util.HttpTinyClient.HttpResult;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class HttpTinyClientTest {

    @Test
    public void testHttpGet() throws IOException {
        String content = "http mock response";
        HttpURLConnection conn = mock(HttpURLConnection.class);
        URL url = mock(URL.class);
        doNothing().when(conn).connect();
        when(url.openConnection()).thenReturn(conn);
        when(conn.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
        try (MockedStatic<IOTinyUtils> dummyStatic = Mockito.mockStatic(IOTinyUtils.class)) {
            dummyStatic.when(() -> IOTinyUtils.toString(any(), any())).thenReturn(content);
            String requestUrl = "https://eventmesh.apache.org";
            HttpResult result = HttpTinyClient.httpGet(requestUrl, null, null, "utf-8", 0);
            Assert.assertEquals(result.getContent(), content);
            Assert.assertEquals(result.getCode(), HttpURLConnection.HTTP_OK);
        }
    }

    @Test
    public void testHttpPost() throws IOException {
        String content = "http mock response";
        HttpURLConnection conn = mock(HttpURLConnection.class);
        URL url = mock(URL.class);
        doNothing().when(conn).connect();
        when(url.openConnection()).thenReturn(conn);
        when(conn.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);

        OutputStream outputStream = mock(OutputStream.class);
        doNothing().when(outputStream).write(new byte[0]);
        when(conn.getOutputStream()).thenReturn(outputStream);
        try (MockedStatic<IOTinyUtils> dummyStatic = Mockito.mockStatic(IOTinyUtils.class)) {
            dummyStatic.when(() -> IOTinyUtils.toString(any(), any())).thenReturn(content);
            String requestUrl = "https://eventmesh.apache.org";
            HttpResult result = HttpTinyClient.httpPost(requestUrl, anyList(), anyList(), "utf-8", 0);
            Assert.assertEquals(result.getContent(), content);
            Assert.assertEquals(result.getCode(), HttpURLConnection.HTTP_OK);
        }
    }
}