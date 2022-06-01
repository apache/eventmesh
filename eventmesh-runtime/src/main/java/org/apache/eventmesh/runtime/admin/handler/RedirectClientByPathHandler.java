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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcp2Client;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.util.NetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * redirect subsystem for path
 */
public class RedirectClientByPathHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(RedirectClientByPathHandler.class);

    private EventMeshTCPServer eventMeshTCPServer;

    public RedirectClientByPathHandler(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String result = "";
        OutputStream out = httpExchange.getResponseBody();
        try {
            String queryString = httpExchange.getRequestURI().getQuery();
            Map<String, String> queryStringInfo = NetUtils.formData2Dic(queryString);
            String path = queryStringInfo.get(EventMeshConstants.MANAGE_PATH);
            String destEventMeshIp = queryStringInfo.get(EventMeshConstants.MANAGE_DEST_IP);
            String destEventMeshPort = queryStringInfo.get(EventMeshConstants.MANAGE_DEST_PORT);

            if (StringUtils.isBlank(path) || StringUtils.isBlank(destEventMeshIp) || StringUtils.isBlank(destEventMeshPort) ||
                    !StringUtils.isNumeric(destEventMeshPort)) {
                httpExchange.sendResponseHeaders(200, 0);
                result = "params illegal!";
                out.write(result.getBytes());
                return;
            }
            logger.info("redirectClientByPath in admin,path:{},destIp:{},destPort:{}====================", path, destEventMeshIp, destEventMeshPort);
            ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();
            ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
            String redirectResult = "";
            try {
                if (!sessionMap.isEmpty()) {
                    for (Session session : sessionMap.values()) {
                        if (session.getClient().getPath().contains(path)) {
                            redirectResult += "|";
                            redirectResult += EventMeshTcp2Client.redirectClient2NewEventMesh(eventMeshTCPServer, destEventMeshIp, Integer.parseInt(destEventMeshPort),
                                    session, clientSessionGroupMapping);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("clientManage|redirectClientByPath|fail|path={}|destEventMeshIp" +
                        "={}|destEventMeshPort={},errMsg={}", path, destEventMeshIp, destEventMeshPort, e);
                result = String.format("redirectClientByPath fail! sessionMap size {%d}, {path=%s " +
                                "destEventMeshIp=%s destEventMeshPort=%s}, result {%s}, errorMsg : %s",
                        sessionMap.size(), path, destEventMeshIp, destEventMeshPort, redirectResult, e
                                .getMessage());
                httpExchange.sendResponseHeaders(200, 0);
                out.write(result.getBytes());
                return;
            }
            result = String.format("redirectClientByPath success! sessionMap size {%d}, {path=%s " +
                            "destEventMeshIp=%s destEventMeshPort=%s}, result {%s} ",
                    sessionMap.size(), path, destEventMeshIp, destEventMeshPort, redirectResult);
            httpExchange.sendResponseHeaders(200, 0);
            out.write(result.getBytes());
        } catch (Exception e) {
            logger.error("redirectClientByPath fail...", e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    logger.warn("out close failed...", e);
                }
            }
        }

    }
}