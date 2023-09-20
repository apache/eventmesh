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

package org.apache.eventmesh.common.utils;

import org.apache.eventmesh.common.enums.HttpMethod;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.sun.net.httpserver.HttpExchange;

public class NetUtilsTest {

    @Test
    public void testFormData2Dic() {
        String formData = "";
        Map<String, String> result = NetUtils.formData2Dic(formData);
        Assert.assertTrue(result.isEmpty());

        formData = "item_id=10081&item_name=test item name";
        result = NetUtils.formData2Dic(formData);
        Assert.assertEquals("10081", result.get("item_id"));
    }

    @Test
    public void testAddressToString() {
        List<InetSocketAddress> clients = new ArrayList<>();
        String result = NetUtils.addressToString(clients);
        Assert.assertEquals("no session had been closed", result);

        InetSocketAddress localAddress = new InetSocketAddress(80);
        clients.add(localAddress);
        result = NetUtils.addressToString(clients);
        Assert.assertEquals(localAddress + "|", result);
    }

    @Test
    public void testParsePostBody() throws Exception {

        HttpExchange exchange = Mockito.mock(HttpExchange.class);
        String expected = "mxsm";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8));
        Mockito.when(exchange.getRequestMethod()).thenReturn(HttpMethod.POST.name());
        Mockito.when(exchange.getRequestBody()).thenReturn(inputStream);

        String actual = NetUtils.parsePostBody(exchange);
        Assert.assertEquals(expected, actual);

    }

    @Test
    public void testSendSuccessResponseHeaders() throws IOException {
        HttpExchange exchange = Mockito.mock(HttpExchange.class);
        NetUtils.sendSuccessResponseHeaders(exchange);
        Mockito.verify(exchange, Mockito.times(1))
            .sendResponseHeaders(Mockito.anyInt(), Mockito.anyLong());
    }
}
