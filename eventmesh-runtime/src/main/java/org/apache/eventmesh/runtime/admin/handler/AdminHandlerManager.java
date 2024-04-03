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

import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.boot.EventMeshServer;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.common.EventMeshHttpHandler;
import org.apache.eventmesh.runtime.meta.MetaStorage;
import org.apache.eventmesh.webhook.admin.AdminWebHookConfigOperationManager;
import org.apache.eventmesh.webhook.api.WebHookConfigOperation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


public class AdminHandlerManager {

    private EventMeshTCPServer eventMeshTCPServer;

    private EventMeshHTTPServer eventMeshHTTPServer;

    private EventMeshGrpcServer eventMeshGrpcServer;

    private MetaStorage eventMeshMetaStorage;

    private AdminWebHookConfigOperationManager adminWebHookConfigOperationManage;

    private final Map<String, HttpHandler> httpHandlerMap = new ConcurrentHashMap<>();

    public AdminHandlerManager(EventMeshServer eventMeshServer) {
        this.eventMeshGrpcServer = eventMeshServer.getEventMeshGrpcServer();
        this.eventMeshHTTPServer = eventMeshServer.getEventMeshHTTPServer();
        this.eventMeshTCPServer = eventMeshServer.getEventMeshTCPServer();
        this.eventMeshMetaStorage = eventMeshServer.getMetaStorage();
        this.adminWebHookConfigOperationManage = eventMeshTCPServer.getAdminWebHookConfigOperationManage();
    }

    public void registerHttpHandler() {
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
        initHandler(new ConfigurationHandler(
            eventMeshTCPServer.getEventMeshTCPConfiguration(),
            eventMeshHTTPServer.getEventMeshHttpConfiguration(),
            eventMeshGrpcServer.getEventMeshGrpcConfiguration()));
        initHandler(new MetricsHandler(eventMeshHTTPServer, eventMeshTCPServer));
        initHandler(new TopicHandler(eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshStoragePluginType()));
        initHandler(new EventHandler(eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshStoragePluginType()));
        initHandler(new MetaHandler(eventMeshMetaStorage));
        if (Objects.nonNull(adminWebHookConfigOperationManage.getWebHookConfigOperation())) {
            WebHookConfigOperation webHookConfigOperation = adminWebHookConfigOperationManage.getWebHookConfigOperation();
            initHandler(new InsertWebHookConfigHandler(webHookConfigOperation));
            initHandler(new UpdateWebHookConfigHandler(webHookConfigOperation));
            initHandler(new DeleteWebHookConfigHandler(webHookConfigOperation));
            initHandler(new QueryWebHookConfigByIdHandler(webHookConfigOperation));
            initHandler(new QueryWebHookConfigByManufacturerHandler(webHookConfigOperation));
        }
    }

    private void initHandler(HttpHandler httpHandler) {
        EventMeshHttpHandler eventMeshHttpHandler = httpHandler.getClass().getAnnotation(EventMeshHttpHandler.class);
        httpHandlerMap.putIfAbsent(eventMeshHttpHandler.path(), httpHandler);
    }

    public Optional<HttpHandler> getHttpHandler(String path) {
        return Optional.ofNullable(httpHandlerMap.get(path));
    }
}
