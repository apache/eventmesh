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

import org.apache.eventmesh.admin.api.controller.AdminController;
import org.apache.eventmesh.runtime.admin.handler.ConfigurationHandler;
import org.apache.eventmesh.runtime.admin.handler.DeleteWebHookConfigHandler;
import org.apache.eventmesh.runtime.admin.handler.EventHandler;
import org.apache.eventmesh.runtime.admin.handler.GrpcClientHandler;
import org.apache.eventmesh.runtime.admin.handler.HTTPClientHandler;
import org.apache.eventmesh.runtime.admin.handler.InsertWebHookConfigHandler;
import org.apache.eventmesh.runtime.admin.handler.MetricsHandler;
import org.apache.eventmesh.runtime.admin.handler.QueryRecommendEventMeshHandler;
import org.apache.eventmesh.runtime.admin.handler.QueryWebHookConfigByIdHandler;
import org.apache.eventmesh.runtime.admin.handler.QueryWebHookConfigByManufacturerHandler;
import org.apache.eventmesh.runtime.admin.handler.RedirectClientByIpPortHandler;
import org.apache.eventmesh.runtime.admin.handler.RedirectClientByPathHandler;
import org.apache.eventmesh.runtime.admin.handler.RedirectClientBySubSystemHandler;
import org.apache.eventmesh.runtime.admin.handler.RegistryHandler;
import org.apache.eventmesh.runtime.admin.handler.RejectAllClientHandler;
import org.apache.eventmesh.runtime.admin.handler.RejectClientByIpPortHandler;
import org.apache.eventmesh.runtime.admin.handler.RejectClientBySubSystemHandler;
import org.apache.eventmesh.runtime.admin.handler.ShowClientBySystemHandler;
import org.apache.eventmesh.runtime.admin.handler.ShowClientHandler;
import org.apache.eventmesh.runtime.admin.handler.ShowListenClientByTopicHandler;
import org.apache.eventmesh.runtime.admin.handler.TCPClientHandler;
import org.apache.eventmesh.runtime.admin.handler.TopicHandler;
import org.apache.eventmesh.runtime.admin.handler.UpdateWebHookConfigHandler;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.registry.Registry;
import org.apache.eventmesh.webhook.admin.AdminWebHookConfigOperationManager;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;


import com.sun.net.httpserver.HttpServer;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * This class is responsible for managing the client connections
 * and initializing the client handlers.
 * <p>
 * It starts the AdminController for managing the event store or MQ.
 */

@SuppressWarnings("restriction")
@Slf4j
public class ClientManageController {

    private final EventMeshTCPServer eventMeshTCPServer;

    private final transient EventMeshHTTPServer eventMeshHTTPServer;

    private final transient EventMeshGrpcServer eventMeshGrpcServer;

    private final transient Registry eventMeshRegistry;

    @Setter
    private AdminWebHookConfigOperationManager adminWebHookConfigOperationManage;

    /**
     * Constructs a new ClientManageController with the given server instance.
     *
     * @param eventMeshTCPServer the TCP server instance of EventMesh
     * @param eventMeshHTTPServer the HTTP server instance of EventMesh
     * @param eventMeshGrpcServer the gRPC server instance of EventMesh
     * @param eventMeshRegistry the registry adaptor of EventMesh
     */
    public ClientManageController(EventMeshTCPServer eventMeshTCPServer,
        EventMeshHTTPServer eventMeshHTTPServer,
        EventMeshGrpcServer eventMeshGrpcServer,
        Registry eventMeshRegistry) {
        this.eventMeshTCPServer = eventMeshTCPServer;
        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.eventMeshRegistry = eventMeshRegistry;
    }

    /**
     * Invoke this method to start this controller on the specified port.
     *
     * @throws IOException if an I/O error occurs while starting the server
     */
    public void start() throws IOException {
        // Get the server's admin port.
        int port = eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshServerAdminPort();
        String pluginName = eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshStoragePluginType();
        // Create an HTTP server and bind it to the specified port.
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        HttpHandlerManager httpHandlerManager = new HttpHandlerManager();

        //TODO: Optimized for automatic injection

        // Initialize the client handler and register it with the HTTP handler manager.
        initClientHandler(eventMeshTCPServer, eventMeshHTTPServer,
            eventMeshGrpcServer, eventMeshRegistry, httpHandlerManager);

        // Register the handlers from the HTTP handler manager with the HTTP server.
        httpHandlerManager.registerHttpHandler(server);
        AdminController adminController = new AdminController(pluginName);
        adminController.run(server);

        server.start();
        log.info("ClientManageController start success, port:{}", port);
    }

    private void initClientHandler(EventMeshTCPServer eventMeshTCPServer,
        EventMeshHTTPServer eventMeshHTTPServer,
        EventMeshGrpcServer eventMeshGrpcServer,
        Registry eventMeshRegistry,
        HttpHandlerManager httpHandlerManager) {
        new ShowClientHandler(eventMeshTCPServer, httpHandlerManager);
        new ShowClientBySystemHandler(eventMeshTCPServer, httpHandlerManager);
        new RejectAllClientHandler(eventMeshTCPServer, httpHandlerManager);
        new RejectClientByIpPortHandler(eventMeshTCPServer, httpHandlerManager);
        new RejectClientBySubSystemHandler(eventMeshTCPServer, httpHandlerManager);
        new RedirectClientBySubSystemHandler(eventMeshTCPServer, httpHandlerManager);
        new RedirectClientByPathHandler(eventMeshTCPServer, httpHandlerManager);
        new RedirectClientByIpPortHandler(eventMeshTCPServer, httpHandlerManager);
        new ShowListenClientByTopicHandler(eventMeshTCPServer, httpHandlerManager);
        new QueryRecommendEventMeshHandler(eventMeshTCPServer, httpHandlerManager);
        new TCPClientHandler(eventMeshTCPServer, httpHandlerManager);
        new HTTPClientHandler(eventMeshHTTPServer, httpHandlerManager);
        new GrpcClientHandler(eventMeshGrpcServer, httpHandlerManager);
        new ConfigurationHandler(
            eventMeshTCPServer.getEventMeshTCPConfiguration(),
            eventMeshHTTPServer.getEventMeshHttpConfiguration(),
            eventMeshGrpcServer.getEventMeshGrpcConfiguration(), httpHandlerManager);
        new MetricsHandler(eventMeshHTTPServer, eventMeshTCPServer, httpHandlerManager);
        new TopicHandler(eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshStoragePluginType(), httpHandlerManager);
        new EventHandler(eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshStoragePluginType(), httpHandlerManager);
        new RegistryHandler(eventMeshRegistry, httpHandlerManager);

        if (Objects.nonNull(adminWebHookConfigOperationManage.getWebHookConfigOperation())) {
            WebHookConfigOperation webHookConfigOperation = adminWebHookConfigOperationManage.getWebHookConfigOperation();
            new InsertWebHookConfigHandler(webHookConfigOperation, httpHandlerManager);
            new UpdateWebHookConfigHandler(webHookConfigOperation, httpHandlerManager);
            new DeleteWebHookConfigHandler(webHookConfigOperation, httpHandlerManager);
            new QueryWebHookConfigByIdHandler(webHookConfigOperation, httpHandlerManager);
            new QueryWebHookConfigByManufacturerHandler(webHookConfigOperation, httpHandlerManager);
        }
    }


}
