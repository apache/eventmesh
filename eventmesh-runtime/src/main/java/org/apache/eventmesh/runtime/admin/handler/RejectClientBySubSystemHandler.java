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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpExchange;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the HTTP requests of {@code /clientManage/rejectClientBySubSystem} endpoint,
 * which is used to reject a client connection
 * that matches the provided client subsystem id in a Data Communication Network (DCN).
 * <p>
 * The request must specify the client's subsystem id.
 * <p>
 * Parameters:
 * <ul>
 *     <li>client's subsystem id: {@code subsystem} | Example: {@code 5023}</li>
 * </ul>
 * It uses the {@link EventMeshTcp2Client#serverGoodby2Client} method to close the matching client connection.
 *
 * @see AbstractHttpHandler
 */

@EventHttpHandler(path = "/clientManage/rejectClientBySubSystem")
@Slf4j
public class RejectClientBySubSystemHandler extends AbstractHttpHandler {

    private final transient EventMeshTCPServer eventMeshTCPServer;

    /**
     * Constructs a new instance with the provided server instance and HTTP handler manager.
     *
     * @param eventMeshTCPServer  the TCP server instance of EventMesh
     * @param httpHandlerManager  Manages the registration of {@linkplain com.sun.net.httpserver.HttpHandler HttpHandler}
     *                            for an {@link com.sun.net.httpserver.HttpServer HttpServer}.
     */
    public RejectClientBySubSystemHandler(EventMeshTCPServer eventMeshTCPServer, HttpHandlerManager httpHandlerManager) {
        super(httpHandlerManager);
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    private String printClients(Collection<InetSocketAddress> clients) {
        if (clients == null || clients.isEmpty()) {
            return "no session had been closed";
        }
        StringBuilder sb = new StringBuilder();
        for (InetSocketAddress addr : clients) {
            sb.append(addr).append("|");
        }
        return sb.toString();
    }

    /**
     * Handles requests by rejecting matching clients.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String result;
        try (OutputStream out = httpExchange.getResponseBody()) {
            String queryString = httpExchange.getRequestURI().getQuery();
            Map<String, String> queryStringInfo = NetUtils.formData2Dic(queryString);
            // Extract parameter from the query string
            String subSystem = queryStringInfo.get(EventMeshConstants.MANAGE_SUBSYSTEM);

            // Check the validity of the parameters
            if (StringUtils.isBlank(subSystem)) {
                NetUtils.sendSuccessResponseHeaders(httpExchange);
                result = "params illegal!";
                out.write(result.getBytes(Constants.DEFAULT_CHARSET));
                return;
            }

            log.info("rejectClientBySubSystem in admin,subsys:{}====================", subSystem);
            // Retrieve the mapping between Sessions and their corresponding client address
            ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();
            ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
            final List<InetSocketAddress> successRemoteAddrs = new ArrayList<>();
            try {
                if (!sessionMap.isEmpty()) {
                    // Iterate through the sessionMap to find matching sessions where the client's subsystem id matches the given param
                    for (Session session : sessionMap.values()) {
                        // Reject client connection for each matching session found
                        if (session.getClient().getSubsystem().equals(subSystem)) {
                            InetSocketAddress addr = EventMeshTcp2Client.serverGoodby2Client(eventMeshTCPServer.getTcpThreadPoolGroup(), session,
                                clientSessionGroupMapping);
                            // Add the remote client address to a list of successfully rejected addresses
                            if (addr != null) {
                                successRemoteAddrs.add(addr);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("clientManage|rejectClientBySubSystem|fail|subSystemId={}", subSystem, e);
                result = String.format("rejectClientBySubSystem fail! sessionMap size {%d}, had reject {%s} , {"
                    +
                    "subSystemId=%s}, errorMsg : %s", sessionMap.size(), printClients(successRemoteAddrs),
                    subSystem, e.getMessage());
                NetUtils.sendSuccessResponseHeaders(httpExchange);
                out.write(result.getBytes(Constants.DEFAULT_CHARSET));
                return;
            }
            // Serialize the successfully rejected client addresses into output stream
            result = String.format("rejectClientBySubSystem success! sessionMap size {%d}, had reject {%s} , {"
                +
                "subSystemId=%s}", sessionMap.size(), printClients(successRemoteAddrs), subSystem);
            NetUtils.sendSuccessResponseHeaders(httpExchange);
            out.write(result.getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            log.error("rejectClientBySubSystem fail...", e);
        }

    }
}
