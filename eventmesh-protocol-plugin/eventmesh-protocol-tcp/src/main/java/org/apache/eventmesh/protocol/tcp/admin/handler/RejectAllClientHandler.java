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

package org.apache.eventmesh.protocol.tcp.admin.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.eventmesh.protocol.tcp.EventMeshProtocolTCPServer;
import org.apache.eventmesh.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.protocol.tcp.client.session.Session;
import org.apache.eventmesh.protocol.tcp.utils.EventMeshTcp2Client;
import org.apache.eventmesh.protocol.tcp.utils.NetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RejectAllClientHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(RejectAllClientHandler.class);

    private final EventMeshProtocolTCPServer eventMeshTCPServer;

    public RejectAllClientHandler(EventMeshProtocolTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    /**
     * remove all clients accessed by eventMesh
     *
     * @param httpExchange
     * @throws IOException
     */
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String result = "";
        try (OutputStream out = httpExchange.getResponseBody()) {
            ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();
            ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
            final List<InetSocketAddress> successRemoteAddrs = new ArrayList<>();
            try {
                logger.info("rejectAllClient in admin====================");
                if (!sessionMap.isEmpty()) {
                    for (Map.Entry<InetSocketAddress, Session> entry : sessionMap.entrySet()) {
                        InetSocketAddress addr = EventMeshTcp2Client.serverGoodby2Client(eventMeshTCPServer, entry.getValue(), clientSessionGroupMapping);
                        if (addr != null) {
                            successRemoteAddrs.add(addr);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("clientManage|rejectAllClient|fail", e);
                result = String.format("rejectAllClient fail! sessionMap size {%d}, had reject {%s}, errorMsg : %s",
                        sessionMap.size(), NetUtils.addressToString(successRemoteAddrs), e.getMessage());
                httpExchange.sendResponseHeaders(200, 0);
                out.write(result.getBytes());
                return;
            }
            result = String.format("rejectAllClient success! sessionMap size {%d}, had reject {%s}", sessionMap.size(),
                    NetUtils.addressToString(successRemoteAddrs));
            httpExchange.sendResponseHeaders(200, 0);
            out.write(result.getBytes());
        } catch (Exception e) {
            logger.error("rejectAllClient fail...", e);
        }
    }
}
