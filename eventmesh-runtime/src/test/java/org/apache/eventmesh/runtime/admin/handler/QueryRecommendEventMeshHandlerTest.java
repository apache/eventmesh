/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.eventmesh.runtime.admin.handler;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.recommend.EventMeshRecommendImpl;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedConstruction;

import com.sun.net.httpserver.HttpExchange;

public class QueryRecommendEventMeshHandlerTest {

    @Test
    public void testHandle() throws Exception {
        HttpExchange httpExchange = mock(HttpExchange.class);
        URI uri = mock(URI.class);
        when(uri.getQuery()).thenReturn("group=group&purpose=purpose");
        when(httpExchange.getRequestURI()).thenReturn(uri);

        // mock eventMeshTCPServer
        EventMeshTCPServer eventMeshTCPServer = mock(EventMeshTCPServer.class);
        EventMeshTCPConfiguration tcpConfiguration = mock(EventMeshTCPConfiguration.class);
        doNothing().when(tcpConfiguration).init();
        tcpConfiguration.eventMeshServerRegistryEnable = true;
        when(eventMeshTCPServer.getEventMeshTCPConfiguration()).thenReturn(tcpConfiguration);

        OutputStream outputStream = new ByteArrayOutputStream();
        when(httpExchange.getResponseBody()).thenReturn(outputStream);

        QueryRecommendEventMeshHandler handler = new QueryRecommendEventMeshHandler(eventMeshTCPServer);
        try (MockedConstruction<EventMeshRecommendImpl> ignored = mockConstruction(EventMeshRecommendImpl.class,
            (mock, context) -> when(mock.calculateRecommendEventMesh(anyString(), anyString())).thenReturn("result"))) {
            handler.handle(httpExchange);
            String response = outputStream.toString();
            Assert.assertEquals("result", response);
        }
    }
}