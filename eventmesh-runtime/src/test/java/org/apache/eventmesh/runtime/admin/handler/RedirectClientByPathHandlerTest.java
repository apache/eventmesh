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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.utils.NetUtils;
import org.apache.eventmesh.runtime.admin.controller.HttpHandlerManager;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.EventMeshTcp2Client;
import org.apache.eventmesh.runtime.core.protocol.tcp.consumer.SessionManager;
import org.apache.eventmesh.runtime.core.protocol.tcp.session.Session;

import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.sun.net.httpserver.HttpExchange;

public class RedirectClientByPathHandlerTest {

    @Mock
    private static transient EventMeshTCPServer eventMeshTCPServer;

    @Before
    public void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testHandle() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final HttpExchange mockExchange = mock(HttpExchange.class);

        SessionManager mapping = mock(SessionManager.class);
        when(eventMeshTCPServer.getClientSessionGroupMapping()).thenReturn(mapping);
        HttpHandlerManager httpHandlerManager = new HttpHandlerManager();
        RedirectClientByPathHandler redirectClientByPathHandler = new RedirectClientByPathHandler(eventMeshTCPServer, httpHandlerManager);

        // mock session map
        ConcurrentHashMap<InetSocketAddress, Session> sessionMap = new ConcurrentHashMap<>();
        Session session = mock(Session.class);
        UserAgent agent = mock(UserAgent.class);
        when(agent.getPath()).thenReturn("path");
        when(session.getClient()).thenReturn(agent);
        sessionMap.put(new InetSocketAddress(8080), session);
        when(mapping.getSessionMap()).thenReturn(sessionMap);

        // mock uri
        URI uri = mock(URI.class);
        when(uri.getQuery()).thenReturn("path=path&ip=127.0.0.1&port=1234&desteventMeshIp=127.0.0.1&desteventmeshport=8080");
        when(mockExchange.getRequestURI()).thenReturn(uri);

        try (MockedStatic<NetUtils> netUtilsMockedStatic = Mockito.mockStatic(NetUtils.class)) {
            Map<String, String> queryStringInfo = new HashMap<>();
            queryStringInfo.put(EventMeshConstants.MANAGE_PATH, EventMeshConstants.MANAGE_PATH);
            queryStringInfo.put(EventMeshConstants.MANAGE_DEST_IP, "localhost");
            queryStringInfo.put(EventMeshConstants.MANAGE_DEST_PORT, "8080");
            netUtilsMockedStatic.when(() -> NetUtils.formData2Dic(anyString())).thenReturn(queryStringInfo);

            // case 1: normal case
            when(mockExchange.getResponseBody()).thenReturn(outputStream);
            try (MockedStatic<EventMeshTcp2Client> clientMockedStatic = Mockito.mockStatic(EventMeshTcp2Client.class)) {
                clientMockedStatic.when(() -> EventMeshTcp2Client.redirectClient2NewEventMesh(any(), anyString(), anyInt(), any(),
                    any())).thenReturn("redirectResult");
                redirectClientByPathHandler.handle(mockExchange);
                String response = outputStream.toString(StandardCharsets.UTF_8.name());
                Assert.assertTrue(response.startsWith("redirectClientByPath success!"));
            }

            // case 2: params illegal
            outputStream = new ByteArrayOutputStream();
            when(mockExchange.getResponseBody()).thenReturn(outputStream);
            try (MockedStatic<StringUtils> dummyStatic = mockStatic(StringUtils.class)) {
                dummyStatic.when(() -> StringUtils.isBlank(any())).thenReturn(Boolean.TRUE);
                redirectClientByPathHandler.handle(mockExchange);
                String response = outputStream.toString(StandardCharsets.UTF_8.name());
                Assert.assertEquals("params illegal!", response);
            }

            // case 3: redirectClient2NewEventMesh fail
            outputStream = new ByteArrayOutputStream();
            when(mockExchange.getResponseBody()).thenReturn(outputStream);
            try (MockedStatic<EventMeshTcp2Client> clientMockedStatic = Mockito.mockStatic(EventMeshTcp2Client.class)) {
                clientMockedStatic.when(() -> EventMeshTcp2Client.redirectClient2NewEventMesh(any(), anyString(), anyInt(), any(),
                    any())).thenThrow(new RuntimeException());
                redirectClientByPathHandler.handle(mockExchange);
                String response = outputStream.toString(Constants.DEFAULT_CHARSET.name());
                Assert.assertTrue(response.startsWith("redirectClientByPath fail!"));
            }
        }
    }
}
