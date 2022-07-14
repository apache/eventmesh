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

package org.apache.eventmesh.runtime.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.admin.rocketmq.controller.AdminController;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;

import java.io.IOException;

import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.sun.net.httpserver.HttpServer;

public class ClientManageControllerTest {

    @Test
    public void testStart() throws IOException {
        EventMeshTCPServer eventMeshTCPServer = mock(EventMeshTCPServer.class);
        AdminController adminController = mock(AdminController.class);
        EventMeshTCPConfiguration tcpConfiguration = mock(EventMeshTCPConfiguration.class);
        doNothing().when(tcpConfiguration).init();
        when(eventMeshTCPServer.getEventMeshTCPConfiguration()).thenReturn(tcpConfiguration);
        ClientManageController controller = new ClientManageController(eventMeshTCPServer);
        try (MockedStatic<HttpServer> dummyStatic = Mockito.mockStatic(HttpServer.class)) {
            HttpServer server = mock(HttpServer.class);
            dummyStatic.when(() -> HttpServer.create(any(), anyInt())).thenReturn(server);
            Mockito.doNothing().when(adminController).run(server);
            controller.start();
        }
    }
}