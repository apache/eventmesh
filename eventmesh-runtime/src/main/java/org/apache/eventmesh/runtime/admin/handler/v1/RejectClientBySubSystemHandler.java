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

package org.apache.eventmesh.runtime.admin.handler.v1;

import org.apache.eventmesh.common.utils.NetUtils;
import org.apache.eventmesh.runtime.admin.handler.AbstractHttpHandler;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.common.EventMeshHttpHandler;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcp2Client;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;

import org.apache.commons.lang3.StringUtils;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the HTTP requests of {@code /clientManage/rejectClientBySubSystem} endpoint, which is used to reject a client connection that
 * matches the provided client subsystem id in a Data Communication Network (DCN).
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

@EventMeshHttpHandler(path = "/clientManage/rejectClientBySubSystem")
@Slf4j
public class RejectClientBySubSystemHandler extends AbstractHttpHandler {

    private final transient EventMeshTCPServer eventMeshTCPServer;

    /**
     * Constructs a new instance with the provided server instance.
     *
     * @param eventMeshTCPServer the TCP server instance of EventMesh
     */
    public RejectClientBySubSystemHandler(EventMeshTCPServer eventMeshTCPServer) {
        super();
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

    @Override
    public void handle(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        String result;
        String queryString = URI.create(httpRequest.uri()).getQuery();
        Map<String, String> queryStringInfo = NetUtils.formData2Dic(queryString);
        // Extract parameter from the query string
        String subSystem = queryStringInfo.get(EventMeshConstants.MANAGE_SUBSYSTEM);

        // Check the validity of the parameters
        if (StringUtils.isBlank(subSystem)) {
            result = "params illegal!";
            writeText(ctx, result);
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
            writeText(ctx, result);
            return;
        }
        // Serialize the successfully rejected client addresses into output stream
        result = String.format("rejectClientBySubSystem success! sessionMap size {%d}, had reject {%s} , {"
            +
            "subSystemId=%s}", sessionMap.size(), printClients(successRemoteAddrs), subSystem);
        writeText(ctx, result);
    }
}
