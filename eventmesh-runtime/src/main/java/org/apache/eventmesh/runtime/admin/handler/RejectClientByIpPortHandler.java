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
import org.apache.eventmesh.runtime.core.protocol.tcp.EventMeshTcp2Client;
import org.apache.eventmesh.runtime.core.protocol.tcp.session.ClientManager;
import org.apache.eventmesh.runtime.core.protocol.tcp.session.Session;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


import com.sun.net.httpserver.HttpExchange;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the HTTP requests of {@code /clientManage/rejectClientByIpPort} endpoint,
 * which is used to reject a client connection
 * that matches the provided IP address and port.
 * <p>
 * The request must specify the client's and target EventMesh node's IP and port.
 * <p>
 * Parameters:
 * <ul>
 *     <li>client's IP: {@code ip}</li>
 *     <li>client's port: {@code port}</li>
 *     <li>target EventMesh node's IP: {@code desteventmeshIp}</li>
 *     <li>target EventMesh node's port: {@code desteventmeshport}</li>
 * </ul>
 * It uses the {@link EventMeshTcp2Client#serverGoodby2Client} method to close the matching client connection.
 *
 * @see AbstractHttpHandler
 */

@Slf4j
@EventHttpHandler(path = "/clientManage/rejectClientByIpPort")
public class RejectClientByIpPortHandler extends AbstractHttpHandler {

    private final EventMeshTCPServer eventMeshTCPServer;

    /**
     * Constructs a new instance with the provided server instance and HTTP handler manager.
     *
     * @param eventMeshTCPServer  the TCP server instance of EventMesh
     * @param httpHandlerManager  Manages the registration of {@linkplain com.sun.net.httpserver.HttpHandler HttpHandler}
     *                            for an {@link com.sun.net.httpserver.HttpServer HttpServer}.
     */
    public RejectClientByIpPortHandler(EventMeshTCPServer eventMeshTCPServer, HttpHandlerManager httpHandlerManager) {
        super(httpHandlerManager);
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    /**
     * Handles requests by rejecting matching clients.
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
            String ip = queryStringInfo.get(EventMeshConstants.MANAGE_IP);
            String port = queryStringInfo.get(EventMeshConstants.MANAGE_PORT);

            // Check the validity of the parameters
            if (StringUtils.isBlank(ip) || StringUtils.isBlank(port)) {
                NetUtils.sendSuccessResponseHeaders(httpExchange);
                result = "params illegal!";
                out.write(result.getBytes(Constants.DEFAULT_CHARSET));
                return;
            }
            log.info("rejectClientByIpPort in admin,ip:{},port:{}====================", ip, port);
            // Retrieve the mapping between Sessions and their corresponding client address
            ClientManager clientManager = eventMeshTCPServer.getSessionManager();
            ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientManager.getSessionTable();
            final List<InetSocketAddress> successRemoteAddrs = new ArrayList<InetSocketAddress>();
            try {
                if (!sessionMap.isEmpty()) {
                    // Iterate through the sessionMap to find matching sessions where the remote client address matches the given IP and port
                    for (Map.Entry<InetSocketAddress, Session> entry : sessionMap.entrySet()) {
                        // Reject client connection for each matching session found
                        if (entry.getKey().getHostString().equals(ip) && String.valueOf(entry.getKey().getPort()).equals(port)) {
                            InetSocketAddress addr = EventMeshTcp2Client.serverGoodby2Client(eventMeshTCPServer.getTcpThreadPoolGroup(),
                                entry.getValue(), clientManager);
                            // Add the remote client address to a list of successfully rejected addresses
                            if (addr != null) {
                                successRemoteAddrs.add(addr);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("clientManage|rejectClientByIpPort|fail|ip={}|port={},errMsg={}", ip, port, e);
                result = String.format("rejectClientByIpPort fail! {ip=%s port=%s}, had reject {%s}, errorMsg : %s", ip,
                    port, NetUtils.addressToString(successRemoteAddrs), e.getMessage());
                NetUtils.sendSuccessResponseHeaders(httpExchange);
                out.write(result.getBytes(Constants.DEFAULT_CHARSET));
                return;
            }
            result = String.format("rejectClientByIpPort success! {ip=%s port=%s}, had reject {%s}", ip, port,
                NetUtils.addressToString(successRemoteAddrs));
            NetUtils.sendSuccessResponseHeaders(httpExchange);
            out.write(result.getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            log.error("rejectClientByIpPort fail...", e);
        }

    }
}
