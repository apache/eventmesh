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

import org.apache.eventmesh.admin.rocketmq.controller.AdminController;
import org.apache.eventmesh.runtime.admin.handler.AbstractHttpHandler;
import org.apache.eventmesh.runtime.admin.handler.DeleteWebHookConfigHandler;
import org.apache.eventmesh.runtime.admin.handler.InsertWebHookConfigHandler;
import org.apache.eventmesh.runtime.admin.handler.QueryRecommendEventMeshHandler;
import org.apache.eventmesh.runtime.admin.handler.QueryWebHookConfigByIdHandler;
import org.apache.eventmesh.runtime.admin.handler.QueryWebHookConfigByManufacturerHandler;
import org.apache.eventmesh.runtime.admin.handler.RedirectClientByIpPortHandler;
import org.apache.eventmesh.runtime.admin.handler.RedirectClientByPathHandler;
import org.apache.eventmesh.runtime.admin.handler.RedirectClientBySubSystemHandler;
import org.apache.eventmesh.runtime.admin.handler.RejectAllClientHandler;
import org.apache.eventmesh.runtime.admin.handler.RejectClientByIpPortHandler;
import org.apache.eventmesh.runtime.admin.handler.RejectClientBySubSystemHandler;
import org.apache.eventmesh.runtime.admin.handler.ShowClientBySystemHandler;
import org.apache.eventmesh.runtime.admin.handler.ShowListenClientByTopicHandler;
import org.apache.eventmesh.runtime.admin.handler.UpdateWebHookConfigHandler;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.common.EventHttpHandler;
import org.apache.eventmesh.runtime.common.EventHttpHandlerConfigEnum;
import org.apache.eventmesh.webhook.admin.AdminWebHookConfigOperationManage;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpServer;

import lombok.Setter;

@SuppressWarnings("restriction")
public class ClientManageController {

    private static final Logger logger = LoggerFactory.getLogger(ClientManageController.class);

    private EventMeshTCPServer eventMeshTCPServer;

    private AdminController adminController;

    private HttpServer server;

    private final Map<EventHttpHandlerConfigEnum, Object> config = new HashMap<>();

    @Setter
    private AdminWebHookConfigOperationManage adminWebHookConfigOperationManage;

    public ClientManageController(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
        config.put(EventHttpHandlerConfigEnum.EVENT_MESH_TCP_SERVER, eventMeshTCPServer);
    }


    public void start() throws IOException {
        int port = eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshServerAdminPort;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server = server;
        registerHandler(new DeleteWebHookConfigHandler());
        registerHandler(new ShowClientBySystemHandler());

        registerHandler(new RejectAllClientHandler());
        registerHandler(new ShowClientBySystemHandler());
        registerHandler(new ShowClientBySystemHandler());
        
//        server.createContext("/clientManage/showClient", new ShowClientHandler(eventMeshTCPServer));
//        server.createContext("/clientManage/showClientBySystem", new ShowClientBySystemHandler(eventMeshTCPServer));
        server.createContext("/clientManage/rejectAllClient", new RejectAllClientHandler(eventMeshTCPServer));
        server.createContext("/clientManage/rejectClientByIpPort", new RejectClientByIpPortHandler(eventMeshTCPServer));
        server.createContext("/clientManage/rejectClientBySubSystem", new RejectClientBySubSystemHandler(eventMeshTCPServer));
        server.createContext("/clientManage/redirectClientBySubSystem", new RedirectClientBySubSystemHandler(eventMeshTCPServer));
        server.createContext("/clientManage/redirectClientByPath", new RedirectClientByPathHandler(eventMeshTCPServer));
        server.createContext("/clientManage/redirectClientByIpPort", new RedirectClientByIpPortHandler(eventMeshTCPServer));
        server.createContext("/clientManage/showListenClientByTopic", new ShowListenClientByTopicHandler(eventMeshTCPServer));
        server.createContext("/eventMesh/recommend", new QueryRecommendEventMeshHandler(eventMeshTCPServer));

        if (Objects.nonNull(adminWebHookConfigOperationManage.getWebHookConfigOperation())) {
            WebHookConfigOperation webHookConfigOperation = adminWebHookConfigOperationManage.getWebHookConfigOperation();
            config.put(EventHttpHandlerConfigEnum.ADMIN_WEB_HOOK_CONFIG_OPERATION_MANAGE,webHookConfigOperation)
            server.createContext("/webhook/insertWebHookConfig", new InsertWebHookConfigHandler(webHookConfigOperation));
            server.createContext("/webhook/updateWebHookConfig", new UpdateWebHookConfigHandler(webHookConfigOperation));
//            server.createContext("/webhook/deleteWebHookConfig", new DeleteWebHookConfigHandler(webHookConfigOperation));
            server.createContext("/webhook/queryWebHookConfigById", new QueryWebHookConfigByIdHandler(webHookConfigOperation));
            server.createContext("/webhook/queryWebHookConfigByManufacturer", new QueryWebHookConfigByManufacturerHandler(webHookConfigOperation));
        }

        adminController = new AdminController();
        adminController.run(server);

        server.start();
        logger.info("ClientManageController start success, port:{}", port);
    }

    public void registerHandler(AbstractHttpHandler httpHandler) {
        EventHttpHandler annotation = httpHandler.getClass().getAnnotation(EventHttpHandler.class);
        httpHandler.setConfig(config.get(annotation.config()));
        this.server.createContext(annotation.path(), httpHandler);
        
    }
}