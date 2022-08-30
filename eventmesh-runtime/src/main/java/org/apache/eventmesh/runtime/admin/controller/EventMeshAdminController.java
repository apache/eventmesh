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

import org.apache.eventmesh.runtime.admin.handler.ConfigurationHandler;
import org.apache.eventmesh.runtime.admin.handler.EventHandler;
import org.apache.eventmesh.runtime.admin.handler.GrpcClientHandler;
import org.apache.eventmesh.runtime.admin.handler.HTTPClientHandler;
import org.apache.eventmesh.runtime.admin.handler.MetricsHandler;
import org.apache.eventmesh.runtime.admin.handler.RegistryHandler;
import org.apache.eventmesh.runtime.admin.handler.TCPClientHandler;
import org.apache.eventmesh.runtime.admin.handler.TopicHandler;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.registry.Registry;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpServer;

public class EventMeshAdminController {

    private static final Logger logger = LoggerFactory.getLogger(EventMeshAdminController.class);

    private final EventMeshTCPServer eventMeshTCPServer;
    private final EventMeshHTTPServer eventMeshHTTPServer;
    private final EventMeshGrpcServer eventMeshGrpcServer;
    private final Registry eventMeshRegistry;

    public EventMeshAdminController(
        EventMeshTCPServer eventMeshTCPServer,
        EventMeshHTTPServer eventMeshHTTPServer,
        EventMeshGrpcServer eventMeshGrpcServer,
        Registry eventMeshRegistry
    ) {
        this.eventMeshTCPServer = eventMeshTCPServer;
        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.eventMeshRegistry = eventMeshRegistry;
    }

    public void start() throws IOException {
        int port = eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshServerAdminPort;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/client/tcp", new TCPClientHandler(eventMeshTCPServer));
        server.createContext("/client/http", new HTTPClientHandler(eventMeshHTTPServer));
        server.createContext("/client/grpc", new GrpcClientHandler(eventMeshGrpcServer));
        server.createContext("/configuration", new ConfigurationHandler(
            eventMeshTCPServer.getEventMeshTCPConfiguration(),
            eventMeshHTTPServer.getEventMeshHttpConfiguration(),
            eventMeshGrpcServer.getEventMeshGrpcConfiguration()
        ));
        server.createContext("/metrics", new MetricsHandler(eventMeshHTTPServer, eventMeshTCPServer));
        server.createContext("/topic", new TopicHandler(eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshConnectorPluginType));
        server.createContext("/event", new EventHandler(eventMeshTCPServer.getEventMeshTCPConfiguration().eventMeshConnectorPluginType));
        server.createContext("/registry", new RegistryHandler(eventMeshRegistry));
        server.start();
        logger.info("ClientManageController start success, port:{}", port);
    }
}
