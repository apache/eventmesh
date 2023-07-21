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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


import com.sun.net.httpserver.HttpExchange;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the HTTP requests of {@code /clientManage/redirectClientBySubSystem} endpoint,
 * which is used to redirect matching clients to a target EventMesh server node
 * based on the provided client subsystem id in a Data Communication Network (DCN).
 * <p>
 * The request must specify the client's subsystem id and target EventMesh node's IP and port.
 * <p>
 * Parameters:
 * <ul>
 *     <li>client's subsystem id: {@code subsystem} | Example: {@code 5023}</li>
 *     <li>target EventMesh node's IP: {@code desteventmeshIp}</li>
 *     <li>target EventMesh node's port: {@code desteventmeshport}</li>
 * </ul>
 * It uses the {@link EventMeshTcp2Client#redirectClient2NewEventMesh} method to redirect the matching client.
 *
 * @see AbstractHttpHandler
 */

@Slf4j
@EventHttpHandler(path = "/clientManage/redirectClientBySubSystem")
public class RedirectClientBySubSystemHandler extends AbstractHttpHandler {

    private final transient EventMeshTCPServer eventMeshTCPServer;

    /**
     * Constructs a new instance with the provided server instance and HTTP handler manager.
     *
     * @param eventMeshTCPServer  the TCP server instance of EventMesh
     * @param httpHandlerManager  Manages the registration of {@linkplain com.sun.net.httpserver.HttpHandler HttpHandler}
     *                            for an {@link com.sun.net.httpserver.HttpServer HttpServer}.
     */
    public RedirectClientBySubSystemHandler(final EventMeshTCPServer eventMeshTCPServer,
        final HttpHandlerManager httpHandlerManager) {
        super(httpHandlerManager);
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    /**
     * Handles the HTTP requests by redirecting matching clients to a target EventMesh server node.
     * <p>
     * This method is an implementation of {@linkplain com.sun.net.httpserver.HttpHandler#handle(HttpExchange)  HttpHandler.handle()}.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    @Override
    public void handle(final HttpExchange httpExchange) throws IOException {
        Objects.requireNonNull(httpExchange, "httpExchange can not be null");

        try (OutputStream out = httpExchange.getResponseBody()) {
            // Retrieve the query string from the request URI and parses it into a key-value pair Map
            final Map<String, String> queryStringInfo = NetUtils.formData2Dic(httpExchange.getRequestURI().getQuery());
            // Extract parameters from the query string
            final String subSystem = queryStringInfo.get(EventMeshConstants.MANAGE_SUBSYSTEM);
            final String destEventMeshIp = queryStringInfo.get(EventMeshConstants.MANAGE_DEST_IP);
            final String destEventMeshPort = queryStringInfo.get(EventMeshConstants.MANAGE_DEST_PORT);

            // Check the validity of the parameters
            if (!StringUtils.isNumeric(subSystem)
                || StringUtils.isBlank(destEventMeshIp) || StringUtils.isBlank(destEventMeshPort)
                || !StringUtils.isNumeric(destEventMeshPort)) {
                NetUtils.sendSuccessResponseHeaders(httpExchange);
                out.write("params illegal!".getBytes(Constants.DEFAULT_CHARSET));
                return;
            }
            if (log.isInfoEnabled()) {
                log.info("redirectClientBySubSystem in admin,subsys:{},destIp:{},destPort:{}====================",
                    subSystem, destEventMeshIp, destEventMeshPort);
            }

            // Retrieve the mapping between Sessions and their corresponding client address
            final ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();
            final ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
            final StringBuilder redirectResult = new StringBuilder();
            try {
                if (!sessionMap.isEmpty()) {
                    // Iterate through the sessionMap to find matching sessions where the client's subsystem id matches the given param
                    for (final Session session : sessionMap.values()) {
                        // For each matching session found, it calls the redirectClient2NewEventMesh method to redirect the client
                        // to the new EventMesh node specified by destEventMeshIp and destEventMeshPort.
                        if (session.getClient().getSubsystem().equals(subSystem)) {
                            redirectResult.append('|')
                                .append(EventMeshTcp2Client.redirectClient2NewEventMesh(eventMeshTCPServer,
                                    destEventMeshIp, Integer.parseInt(destEventMeshPort),
                                    session, clientSessionGroupMapping));
                        }
                    }
                }
            } catch (Exception e) {
                log.error("clientManage|redirectClientBySubSystem|fail|subSystem={}|destEventMeshIp"
                    +
                    "={}|destEventMeshPort={},errMsg={}", subSystem, destEventMeshIp, destEventMeshPort, e);

                NetUtils.sendSuccessResponseHeaders(httpExchange);
                out.write(String.format("redirectClientBySubSystem fail! sessionMap size {%d}, {subSystem=%s "
                        +
                        "destEventMeshIp=%s destEventMeshPort=%s}, result {%s}, errorMsg : %s",
                    sessionMap.size(), subSystem, destEventMeshIp, destEventMeshPort, redirectResult, e
                        .getMessage()).getBytes(Constants.DEFAULT_CHARSET));
                return;
            }
            NetUtils.sendSuccessResponseHeaders(httpExchange);
            // Serialize the result of redirection and write it to the response output stream to be sent back to the client
            out.write(String.format("redirectClientBySubSystem success! sessionMap size {%d}, {subSystem=%s "
                        +
                        "destEventMeshIp=%s destEventMeshPort=%s}, result {%s} ",
                    sessionMap.size(), subSystem, destEventMeshIp, destEventMeshPort, redirectResult)
                .getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            log.error("redirectClientBySubSystem fail...", e);
        }
    }
}
