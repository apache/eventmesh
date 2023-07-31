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
import org.apache.eventmesh.runtime.core.protocol.tcp.consumer.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.session.Session;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


import com.sun.net.httpserver.HttpExchange;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the HTTP requests of {@code /clientManage/showClientBySystem} endpoint,
 * which is used to display connected clients information
 * under a specific subsystem by subsystem id.
 * <p>
 * Parameters:
 * <ul>
 *     <li>client's subsystem id: {@code subsystem} | Example: {@code 5023}</li>
 * </ul>
 *
 * @see AbstractHttpHandler
 */

@Slf4j
@EventHttpHandler(path = "/clientManage/showClientBySystem")
public class ShowClientBySystemHandler extends AbstractHttpHandler {

    private final EventMeshTCPServer eventMeshTCPServer;

    /**
     * Constructs a new instance with the provided server instance and HTTP handler manager.
     *
     * @param eventMeshTCPServer  the TCP server instance of EventMesh
     * @param httpHandlerManager  Manages the registration of {@linkplain com.sun.net.httpserver.HttpHandler HttpHandler}
     *                            for an {@link com.sun.net.httpserver.HttpServer HttpServer}.
     */
    public ShowClientBySystemHandler(EventMeshTCPServer eventMeshTCPServer, HttpHandlerManager httpHandlerManager) {
        super(httpHandlerManager);
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    /**
     * Handles requests by displaying clients information.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        StringBuilder result = new StringBuilder();
        try (OutputStream out = httpExchange.getResponseBody()) {
            String queryString = httpExchange.getRequestURI().getQuery();
            Map<String, String> queryStringInfo = NetUtils.formData2Dic(queryString);
            // Extract parameter from the query string
            String subSystem = queryStringInfo.get(EventMeshConstants.MANAGE_SUBSYSTEM);

            String newLine = System.getProperty("line.separator");
            if (log.isInfoEnabled()) {
                log.info("showClientBySubsys,subsys:{}", subSystem);
            }
            // Retrieve the mapping between Sessions and their corresponding client address
            ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();
            ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
            if (sessionMap != null && !sessionMap.isEmpty()) {
                // Iterate through the sessionMap to find matching sessions where the client's subsystem id matches the given param
                for (Session session : sessionMap.values()) {
                    // For each matching session found, append the client's information to the result
                    if (session.getClient().getSubsystem().equals(subSystem)) {
                        UserAgent userAgent = session.getClient();
                        result.append(String.format("pid=%s | ip=%s | port=%s | path=%s | purpose=%s",
                                userAgent.getPid(), userAgent.getHost(), userAgent.getPort(),
                                userAgent.getPath(), userAgent.getPurpose()))
                            .append(newLine);
                    }
                }
            }
            NetUtils.sendSuccessResponseHeaders(httpExchange);
            out.write(result.toString().getBytes(Constants.DEFAULT_CHARSET));
        }
    }


}
