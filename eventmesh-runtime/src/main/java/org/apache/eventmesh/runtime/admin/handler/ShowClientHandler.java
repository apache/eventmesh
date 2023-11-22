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
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the HTTP requests of {@code /clientManage/showClient} endpoint.
 * <p>
 * It is used to query information about all clients connected to the current EventMesh server node
 * and to provide statistics on the number of clients in each subsystem.
 *
 * @see AbstractHttpHandler
 */

@Slf4j
@EventHttpHandler(path = "/clientManage/showClient")
public class ShowClientHandler extends AbstractHttpHandler {

    private final EventMeshTCPServer eventMeshTCPServer;

    /**
     * Constructs a new instance with the provided server instance and HTTP handler manager.
     *
     * @param eventMeshTCPServer  the TCP server instance of EventMesh
     * @param httpHandlerManager  Manages the registration of {@linkplain com.sun.net.httpserver.HttpHandler HttpHandler}
     *                            for an {@link com.sun.net.httpserver.HttpServer HttpServer}.
     */
    public ShowClientHandler(EventMeshTCPServer eventMeshTCPServer, HttpHandlerManager httpHandlerManager) {
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
            String newLine = System.getProperty("line.separator");
            log.info("showAllClient=================");
            ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();

            // Store the subsystem and the corresponding client count.
            HashMap<String, AtomicInteger> statMap = new HashMap<String, AtomicInteger>();

            Map<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
            if (!sessionMap.isEmpty()) {
                // Iterate through each Session to count the clients in each subsystem.
                for (Session session : sessionMap.values()) {
                    String key = session.getClient().getSubsystem();
                    if (!statMap.containsKey(key)) {
                        statMap.put(key, new AtomicInteger(1));
                    } else {
                        statMap.get(key).incrementAndGet();
                    }
                }

                // Generate the result with the number of clients for each subsystem.
                for (Map.Entry<String, AtomicInteger> entry : statMap.entrySet()) {
                    result.append(String.format("System=%s | ClientNum=%d", entry.getKey(), entry.getValue().intValue())).append(newLine);
                }
            }

            NetUtils.sendSuccessResponseHeaders(httpExchange);
            out.write(result.toString().getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            log.error("ShowClientHandler fail...", e);
        }
    }
}
