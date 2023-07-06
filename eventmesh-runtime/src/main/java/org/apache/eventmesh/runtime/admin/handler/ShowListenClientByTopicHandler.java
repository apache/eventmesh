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

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.utils.NetUtils;
import org.apache.eventmesh.runtime.admin.controller.HttpHandlerManager;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.common.EventHttpHandler;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientGroupWrapper;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


import com.sun.net.httpserver.HttpExchange;

import lombok.extern.slf4j.Slf4j;

/**
 * query client subscription by topic
 */
@Slf4j
@EventHttpHandler(path = "/clientManage/showListenClientByTopic")
public class ShowListenClientByTopicHandler extends AbstractHttpHandler {

    private final EventMeshTCPServer eventMeshTCPServer;

    public ShowListenClientByTopicHandler(EventMeshTCPServer eventMeshTCPServer, HttpHandlerManager httpHandlerManager) {
        super(httpHandlerManager);
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        StringBuilder result = new StringBuilder();
        try (OutputStream out = httpExchange.getResponseBody()) {
            String queryString = httpExchange.getRequestURI().getQuery();
            Map<String, String> queryStringInfo = NetUtils.formData2Dic(queryString);
            String topic = queryStringInfo.get(EventMeshConstants.MANAGE_TOPIC);

            String newLine = System.getProperty("line.separator");
            log.info("showListeningClientByTopic,topic:{}=================", topic);
            ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();
            ConcurrentHashMap<String, ClientGroupWrapper> clientGroupMap = clientSessionGroupMapping.getClientGroupMap();
            if (!clientGroupMap.isEmpty()) {
                for (ClientGroupWrapper cgw : clientGroupMap.values()) {
                    Map<String, Session> listenSessions = cgw.getTopic2sessionInGroupMapping().get(topic);
                    if (listenSessions != null && !listenSessions.isEmpty()) {
                        result.append(String.format("group:%s", cgw.getGroup())).append(newLine);
                        for (Session session : listenSessions.values()) {
                            UserAgent userAgent = session.getClient();
                            result.append(String.format("pid=%s | ip=%s | port=%s | path=%s | version=%s", userAgent.getPid(), userAgent
                                    .getHost(), userAgent.getPort(), userAgent.getPath(), userAgent.getVersion()))
                                .append(newLine);
                        }
                    }
                }
            }
            NetUtils.sendSuccessResponseHeaders(httpExchange);
            out.write(result.toString().getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            log.error("ShowListenClientByTopicHandler fail...", e);
        }
    }
}
