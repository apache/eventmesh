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

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;

import org.apache.eventmesh.runtime.admin.handler.RedirectClientByIpPortHandler;
import org.apache.eventmesh.runtime.admin.handler.RedirectClientByPathHandler;
import org.apache.eventmesh.runtime.admin.handler.RedirectClientBySubSystemHandler;
import org.apache.eventmesh.runtime.admin.handler.RejectAllClientHandler;
import org.apache.eventmesh.runtime.admin.handler.RejectClientByIpPortHandler;
import org.apache.eventmesh.runtime.admin.handler.RejectClientBySubSystemHandler;
import org.apache.eventmesh.runtime.admin.handler.ShowClientBySystemHandler;
import org.apache.eventmesh.runtime.admin.handler.ShowClientHandler;
import org.apache.eventmesh.runtime.admin.handler.ShowListenClientByTopicHandler;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientManageController {

    private static final Logger logger = LoggerFactory.getLogger(ClientManageController.class);

    private EventMeshTCPServer eventMeshTCPServer;

    public ClientManageController(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    public void start() throws IOException {
        int port = eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshServerAdminPort;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/clientManage/showClient", new ShowClientHandler(eventMeshTCPServer));
        server.createContext("/clientManage/showClientBySystemAndDcn", new ShowClientBySystemHandler(eventMeshTCPServer));
        server.createContext("/clientManage/rejectAllClient", new RejectAllClientHandler(eventMeshTCPServer));
        server.createContext("/clientManage/rejectClientByIpPort", new RejectClientByIpPortHandler(eventMeshTCPServer));
        server.createContext("/clientManage/rejectClientBySubSystem", new RejectClientBySubSystemHandler(eventMeshTCPServer));
        server.createContext("/clientManage/redirectClientBySubSystem", new RedirectClientBySubSystemHandler(eventMeshTCPServer));
        server.createContext("/clientManage/redirectClientByPath", new RedirectClientByPathHandler(eventMeshTCPServer));
        server.createContext("/clientManage/redirectClientByIpPort", new RedirectClientByIpPortHandler(eventMeshTCPServer));
        server.createContext("/clientManage/showListenClientByTopic", new ShowListenClientByTopicHandler(eventMeshTCPServer));

        server.start();
        logger.info("ClientManageController start success, port:{}", port);
    }
}
