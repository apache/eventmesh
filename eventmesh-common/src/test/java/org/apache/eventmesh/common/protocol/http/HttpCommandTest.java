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

import static org.mockito.Mockito.when;

import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.header.Header;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;

@ExtendWith(MockitoExtension.class)
public class HttpCommandTest {

    @Mock
    private Header header;

    @Mock
    private Body body;

    private HttpCommand httpCommand;

    @BeforeEach
    public void before() {
        httpCommand = new HttpCommand("POST", "1.1", "200");
    }

    @Test
    public void testCreateHttpCommandResponseWithHeaderAndBody() {
        HttpCommand command = httpCommand.createHttpCommandResponse(header, body);
        Map<String, Object> headerMap = new HashMap<>();
        headerMap.put("key1", "value1");
        when(header.toMap()).thenReturn(headerMap);
        Assertions.assertEquals("1.1", command.getHttpVersion());
        Assertions.assertEquals("POST", command.getHttpMethod());
        Assertions.assertEquals("200", command.getRequestCode());
        Assertions.assertEquals("value1", command.getHeader().toMap().get("key1"));
    }

    @Test
    public void testAbstractDesc() {
        HttpCommand command = httpCommand.createHttpCommandResponse(header, body);
        String desc = command.abstractDesc();
        Assertions.assertTrue(desc.startsWith("httpCommand"));
    }

    @Test
    public void testSimpleDesc() {
        HttpCommand command = httpCommand.createHttpCommandResponse(header, body);
        String desc = command.simpleDesc();
        Assertions.assertTrue(desc.startsWith("httpCommand"));
    }

    @Test
    public void testHttpResponse() throws Exception {
        HttpCommand command = httpCommand.createHttpCommandResponse(header, body);
        DefaultFullHttpResponse response = command.httpResponse();
        Assertions.assertEquals("keep-alive", response.headers().get(HttpHeaderNames.CONNECTION));
    }

    @Test
    public void testHttpResponseWithREQCmdType() throws Exception {
        DefaultFullHttpResponse response = httpCommand.httpResponse();
        Assertions.assertNull(response);
    }

    @Test
    public void testCreateHttpCommandResponse() {
        HttpCommand command = new HttpCommand();
        HttpCommand response = command.createHttpCommandResponse(EventMeshRetCode.SUCCESS);
        Assertions.assertNotNull(response);
        Assertions.assertEquals("0", response.getRequestCode());

    }
}
