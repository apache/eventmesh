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

import org.apache.eventmesh.runtime.admin.handler.v1.ConfigurationHandlerV1;
import org.apache.eventmesh.runtime.admin.handler.v1.EventHandler;
import org.apache.eventmesh.runtime.admin.handler.v1.GrpcClientHandler;
import org.apache.eventmesh.runtime.admin.handler.v1.HTTPClientHandler;
import org.apache.eventmesh.runtime.admin.handler.v1.MetaHandler;
import org.apache.eventmesh.runtime.admin.handler.v1.QueryRecommendEventMeshHandler;
import org.apache.eventmesh.runtime.admin.handler.v1.RedirectClientByIpPortHandler;
import org.apache.eventmesh.runtime.admin.handler.v1.RedirectClientByPathHandler;
import org.apache.eventmesh.runtime.admin.handler.v1.RedirectClientBySubSystemHandler;
import org.apache.eventmesh.runtime.admin.handler.v1.RejectAllClientHandler;
import org.apache.eventmesh.runtime.admin.handler.v1.RejectClientByIpPortHandler;
import org.apache.eventmesh.runtime.admin.handler.v1.RejectClientBySubSystemHandler;
import org.apache.eventmesh.runtime.admin.handler.v1.ShowClientBySystemHandler;
import org.apache.eventmesh.runtime.admin.handler.v1.ShowClientHandler;
import org.apache.eventmesh.runtime.admin.handler.v1.ShowListenClientByTopicHandler;
import org.apache.eventmesh.runtime.admin.handler.v1.TCPClientHandler;
import org.apache.eventmesh.runtime.admin.handler.v1.TopicHandler;
import org.apache.eventmesh.runtime.admin.handler.v2.ConfigurationHandler;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.boot.EventMeshServer;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.common.EventMeshHttpHandler;
import org.apache.eventmesh.runtime.meta.MetaStorage;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


public class AdminHandlerManager {

    private EventMeshServer eventMeshServer;

    private EventMeshTCPServer eventMeshTCPServer;

    private EventMeshHTTPServer eventMeshHTTPServer;

    private EventMeshGrpcServer eventMeshGrpcServer;

    private MetaStorage eventMeshMetaStorage;

    private final Map<String, HttpHandler> httpHandlerMap = new ConcurrentHashMap<>();

    public AdminHandlerManager(EventMeshServer eventMeshServer) {
        this.eventMeshServer = eventMeshServer;
        this.eventMeshTCPServer = eventMeshServer.getEventMeshTCPServer();
        this.eventMeshGrpcServer = eventMeshServer.getEventMeshGrpcServer();
        this.eventMeshHTTPServer = eventMeshServer.getEventMeshHTTPServer();
        this.eventMeshMetaStorage = eventMeshServer.getMetaStorage();
    }

    public void registerHttpHandler() {
        // v1 endpoints
        initHandler(new ShowClientHandler(eventMeshTCPServer));
        initHandler(new ShowClientBySystemHandler(eventMeshTCPServer));
        initHandler(new RejectAllClientHandler(eventMeshTCPServer));
        initHandler(new RejectClientByIpPortHandler(eventMeshTCPServer));
        initHandler(new RejectClientBySubSystemHandler(eventMeshTCPServer));
        initHandler(new RedirectClientBySubSystemHandler(eventMeshTCPServer));
        initHandler(new RedirectClientByPathHandler(eventMeshTCPServer));
        initHandler(new RedirectClientByIpPortHandler(eventMeshTCPServer));
        initHandler(new ShowListenClientByTopicHandler(eventMeshTCPServer));
        initHandler(new QueryRecommendEventMeshHandler(eventMeshTCPServer));
        initHandler(new TCPClientHandler(eventMeshTCPServer));
        initHandler(new HTTPClientHandler(eventMeshHTTPServer));
        initHandler(new GrpcClientHandler(eventMeshGrpcServer));
        initHandler(new ConfigurationHandlerV1(
            eventMeshTCPServer.getEventMeshTCPConfiguration(),
            eventMeshHTTPServer.getEventMeshHttpConfiguration(),
            eventMeshGrpcServer.getEventMeshGrpcConfiguration()));
        initHandler(new TopicHandler(eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshStoragePluginType()));
        initHandler(new EventHandler(eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshStoragePluginType()));
        initHandler(new MetaHandler(eventMeshMetaStorage));

        // v2 endpoints
        initHandler(new ConfigurationHandler(
            eventMeshServer.getConfiguration(),
            eventMeshTCPServer.getEventMeshTCPConfiguration(),
            eventMeshHTTPServer.getEventMeshHttpConfiguration(),
            eventMeshGrpcServer.getEventMeshGrpcConfiguration()));
    }

    private void initHandler(HttpHandler httpHandler) {
        EventMeshHttpHandler eventMeshHttpHandler = httpHandler.getClass().getAnnotation(EventMeshHttpHandler.class);
        httpHandlerMap.putIfAbsent(eventMeshHttpHandler.path(), httpHandler);
    }

    public Optional<HttpHandler> getHttpHandler(String path) {
        return Optional.ofNullable(httpHandlerMap.get(path));
    }
}
