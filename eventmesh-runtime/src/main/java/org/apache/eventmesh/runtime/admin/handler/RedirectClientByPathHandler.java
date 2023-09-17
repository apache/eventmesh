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
import org.apache.eventmesh.common.utils.NetUtils;
import org.apache.eventmesh.runtime.admin.controller.HttpHandlerManager;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.common.EventHttpHandler;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcp2Client;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


import com.sun.net.httpserver.HttpExchange;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the HTTP requests of {@code /clientManage/redirectClientByPath} endpoint,
 * which is used to redirect matching clients to a target EventMesh server node
 * based on the provided client UserAgent path.
 * <p>
 * The request must specify the client's path and target EventMesh node's IP and port.
 * <p>
 * Parameters:
 * <ul>
 *     <li>client's path: {@code path} | Example: {@code /data/app/umg_proxy}</li>
 *     <li>target EventMesh node's IP: {@code desteventmeshIp}</li>
 *     <li>target EventMesh node's port: {@code desteventmeshport}</li>
 * </ul>
 * It uses the {@link EventMeshTcp2Client#redirectClient2NewEventMesh} method to redirect the matching client.
 *
 * @see AbstractHttpHandler
 */

@Slf4j
@EventHttpHandler(path = "/clientManage/redirectClientByPath")
public class RedirectClientByPathHandler extends AbstractHttpHandler {

    private final EventMeshTCPServer eventMeshTCPServer;

    /**
     * Constructs a new instance with the provided server instance and HTTP handler manager.
     *
     * @param eventMeshTCPServer  the TCP server instance of EventMesh
     * @param httpHandlerManager  Manages the registration of {@linkplain com.sun.net.httpserver.HttpHandler HttpHandler}
     *                            for an {@link com.sun.net.httpserver.HttpServer HttpServer}.
     */
    public RedirectClientByPathHandler(EventMeshTCPServer eventMeshTCPServer, HttpHandlerManager httpHandlerManager) {
        super(httpHandlerManager);
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    /**
     * Handles requests by redirecting matching clients to a target EventMesh server node.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String result = "";
        try (OutputStream out = httpExchange.getResponseBody()) {
            String queryString = httpExchange.getRequestURI().getQuery();
            Map<String, String> queryStringInfo = NetUtils.formData2Dic(queryString);
            // Extract parameters from the query string
            String path = queryStringInfo.get(EventMeshConstants.MANAGE_PATH);
            String destEventMeshIp = queryStringInfo.get(EventMeshConstants.MANAGE_DEST_IP);
            String destEventMeshPort = queryStringInfo.get(EventMeshConstants.MANAGE_DEST_PORT);

            // Check the validity of the parameters
            if (StringUtils.isBlank(path) || StringUtils.isBlank(destEventMeshIp)
                || StringUtils.isBlank(destEventMeshPort)
                || !StringUtils.isNumeric(destEventMeshPort)) {
                NetUtils.sendSuccessResponseHeaders(httpExchange);
                result = "params illegal!";
                out.write(result.getBytes(Constants.DEFAULT_CHARSET));
                return;
            }
            log.info("redirectClientByPath in admin,path:{},destIp:{},destPort:{}====================", path,
                destEventMeshIp, destEventMeshPort);
            // Retrieve the mapping between Sessions and their corresponding client address
            ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();
            ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
            StringBuilder redirectResult = new StringBuilder();
            try {
                if (!sessionMap.isEmpty()) {
                    // Iterate through the sessionMap to find matching sessions where the client's path matches the given param
                    for (Session session : sessionMap.values()) {
                        // For each matching session found, redirect the client
                        // to the new EventMesh node specified by given EventMesh IP and port.
                        if (session.getClient().getPath().contains(path)) {
                            redirectResult.append("|");
                            redirectResult.append(EventMeshTcp2Client.redirectClient2NewEventMesh(eventMeshTCPServer.getTcpThreadPoolGroup(),
                                destEventMeshIp, Integer.parseInt(destEventMeshPort),
                                session, clientSessionGroupMapping));
                        }
                    }
                }
            } catch (Exception e) {
                log.error("clientManage|redirectClientByPath|fail|path={}|destEventMeshIp"
                    +
                    "={}|destEventMeshPort={},errMsg={}", path, destEventMeshIp, destEventMeshPort, e);
                result = String.format("redirectClientByPath fail! sessionMap size {%d}, {path=%s "
                        +
                        "destEventMeshIp=%s destEventMeshPort=%s}, result {%s}, errorMsg : %s",
                    sessionMap.size(), path, destEventMeshIp, destEventMeshPort, redirectResult, e.getMessage());
                NetUtils.sendSuccessResponseHeaders(httpExchange);
                out.write(result.getBytes(Constants.DEFAULT_CHARSET));
                return;
            }
            result = String.format("redirectClientByPath success! sessionMap size {%d}, {path=%s "
                    +
                    "destEventMeshIp=%s destEventMeshPort=%s}, result {%s} ",
                sessionMap.size(), path, destEventMeshIp, destEventMeshPort, redirectResult);
            NetUtils.sendSuccessResponseHeaders(httpExchange);
            out.write(result.getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            log.error("redirectClientByPath fail...", e);
        }
    }
}
