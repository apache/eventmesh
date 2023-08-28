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

package org.apache.eventmesh.runtime.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.admin.rocketmq.controller.AdminController;
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import org.apache.eventmesh.runtime.metrics.http.EventMeshHttpMetricsManager;
import org.apache.eventmesh.runtime.metrics.http.HttpMetrics;
import org.apache.eventmesh.runtime.metrics.tcp.EventMeshTcpMetricsManager;
import org.apache.eventmesh.runtime.metrics.tcp.TcpMetrics;
import org.apache.eventmesh.runtime.registry.Registry;
import org.apache.eventmesh.webhook.admin.AdminWebHookConfigOperationManager;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.sun.net.httpserver.HttpServer;

public class ClientManageControllerTest {

    @Test
    public void testStart() throws Exception {
        AdminController adminController = mock(AdminController.class);

        ConfigService configService = ConfigService.getInstance();
        configService.setRootConfig("classPath://configuration.properties");
        EventMeshTCPConfiguration tcpConfiguration = configService.buildConfigInstance(EventMeshTCPConfiguration.class);

        EventMeshTCPServer eventMeshTCPServer = mock(EventMeshTCPServer.class);
        when(eventMeshTCPServer.getEventMeshTCPConfiguration()).thenReturn(tcpConfiguration);

        HttpMetrics httpSummaryMetrics = mock(HttpMetrics.class);
        EventMeshHttpMetricsManager metrics = mock(EventMeshHttpMetricsManager.class);

        EventMeshHTTPServer eventMeshHTTPServer = mock(EventMeshHTTPServer.class);
        when(eventMeshHTTPServer.getEventMeshHttpMetricsManager()).thenReturn(metrics);
        when(eventMeshHTTPServer.getEventMeshHttpMetricsManager().getHttpMetrics()).thenReturn(httpSummaryMetrics);

        EventMeshTcpMetricsManager eventMeshTcpMonitor = mock(EventMeshTcpMetricsManager.class);
        TcpMetrics tcpMetrics = mock(org.apache.eventmesh.runtime.metrics.tcp.TcpMetrics.class);
        when(eventMeshTCPServer.getEventMeshTcpMetricsManager()).thenReturn(eventMeshTcpMonitor);
        when(eventMeshTCPServer.getEventMeshTcpMetricsManager().getTcpMetrics()).thenReturn(tcpMetrics);

        AdminWebHookConfigOperationManager adminWebHookConfigOperationManage = mock(AdminWebHookConfigOperationManager.class);
        WebHookConfigOperation webHookConfigOperation = mock(WebHookConfigOperation.class);
        when(adminWebHookConfigOperationManage.getWebHookConfigOperation()).thenReturn(webHookConfigOperation);

        EventMeshGrpcServer eventMeshGrpcServer = mock(EventMeshGrpcServer.class);
        Registry registry = mock(Registry.class);
        ClientManageController controller = new ClientManageController(eventMeshTCPServer,
            eventMeshHTTPServer, eventMeshGrpcServer, registry);
        controller.setAdminWebHookConfigOperationManage(adminWebHookConfigOperationManage);

        eventMeshTCPServer.getEventMeshTCPConfiguration().setEventMeshStoragePluginType("standalone");

        try (MockedStatic<HttpServer> dummyStatic = Mockito.mockStatic(HttpServer.class)) {
            HttpServer server = mock(HttpServer.class);
            dummyStatic.when(() -> HttpServer.create(any(), anyInt())).thenReturn(server);
            try {
                Mockito.doNothing().when(adminController).run(server);
                controller.start();
            } catch (IOException e) {
                Assert.fail(e.getMessage());
            }

        }
    }
}
