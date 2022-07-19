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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcp2Client;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.util.NetUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
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
    private EventMeshTCPServer eventMeshTCPServer;

    @Before
    public void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testHandle() throws IOException {
        final OutputStream outputStream = new ByteArrayOutputStream();

        ClientSessionGroupMapping mapping = mock(ClientSessionGroupMapping.class);
        when(eventMeshTCPServer.getClientSessionGroupMapping()).thenReturn(mapping);

        // mock session map
        ConcurrentHashMap<InetSocketAddress, Session> sessionMap = new ConcurrentHashMap<>();
        Session session = mock(Session.class);
        UserAgent agent = mock(UserAgent.class);
        when(agent.getPath()).thenReturn("path");
        when(session.getClient()).thenReturn(agent);
        sessionMap.put(new InetSocketAddress(8080), session);
        when(mapping.getSessionMap()).thenReturn(sessionMap);

        RedirectClientByPathHandler redirectClientByPathHandler = new RedirectClientByPathHandler(eventMeshTCPServer);

        HttpExchange mockExchange = mock(HttpExchange.class);
        when(mockExchange.getResponseBody()).thenReturn(outputStream);

        // mock uri
        URI uri = mock(URI.class);
        when(uri.getQuery()).thenReturn("path=path&ip=127.0.0.1&port=1234&desteventMeshIp=127.0.0.1&desteventmeshport=8080");
        when(mockExchange.getRequestURI()).thenReturn(uri);

        try (MockedStatic<NetUtils> netUtilsMockedStatic = Mockito.mockStatic(NetUtils.class)) {
            Map<String, String> queryStringInfo = new HashMap<>();
            queryStringInfo.put(EventMeshConstants.MANAGE_PATH, EventMeshConstants.MANAGE_PATH);
            queryStringInfo.put(EventMeshConstants.MANAGE_DEST_IP, "127.0.0.1");
            queryStringInfo.put(EventMeshConstants.MANAGE_DEST_PORT, "8080");
            netUtilsMockedStatic.when(() -> NetUtils.formData2Dic(anyString())).thenReturn(queryStringInfo);

            try (MockedStatic<EventMeshTcp2Client> clientMockedStatic = Mockito.mockStatic(EventMeshTcp2Client.class)) {
                clientMockedStatic.when(() -> EventMeshTcp2Client.redirectClient2NewEventMesh(any(), anyString(), anyInt(), any(),
                    any())).thenReturn("redirectResult");
                redirectClientByPathHandler.handle(mockExchange);

                String response = outputStream.toString();
                Assert.assertTrue(response.contains("redirectClientByPath success"));
            }
        }
    }
}