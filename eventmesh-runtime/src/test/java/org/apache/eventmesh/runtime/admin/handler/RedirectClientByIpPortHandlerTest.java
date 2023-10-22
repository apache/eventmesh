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

package org.apache.eventmesh.runtime.admin.handler;

import org.apache.eventmesh.runtime.admin.controller.HttpHandlerManager;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.sun.net.httpserver.HttpExchange;

public class RedirectClientByIpPortHandlerTest {

    private static transient RedirectClientByIpPortHandler redirectClientByIpPortHandler;

    @BeforeEach
    public void init() {
        EventMeshTCPServer mockServer = Mockito.mock(EventMeshTCPServer.class);
        HttpHandlerManager httpHandlerManager = new HttpHandlerManager();
        redirectClientByIpPortHandler = new RedirectClientByIpPortHandler(mockServer, httpHandlerManager);
    }

    @Test
    public void testHandleParamIllegal() throws IOException {
        OutputStream outputStream = new ByteArrayOutputStream();
        URI uri = URI.create("ip=127.0.0.1&port=1234&desteventMeshIp=127.0.0.1&desteventMeshPort=");

        HttpExchange mockExchange = Mockito.mock(HttpExchange.class);
        Mockito.when(mockExchange.getResponseBody()).thenReturn(outputStream);
        Mockito.when(mockExchange.getRequestURI()).thenReturn(uri);

        redirectClientByIpPortHandler.handle(mockExchange);

        String response = outputStream.toString();
        Assertions.assertEquals("params illegal!", response);

    }
}