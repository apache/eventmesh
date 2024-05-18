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
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcp2Client;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the HTTP requests of {@code /clientManage/rejectAllClient} endpoint, which is used to reject ALL client connections belonging to
 * the current EventMesh server node.
 * <p>
 * CAUTION: USE WITH CARE
 * <p>
 * It uses the {@link EventMeshTcp2Client#serverGoodby2Client} method to close the matching client connection.
 *
 * @see AbstractHttpHandler
 */

@Slf4j
@EventMeshHttpHandler(path = "/clientManage/rejectAllClient")
public class RejectAllClientHandler extends AbstractHttpHandler {

    private final transient EventMeshTCPServer eventMeshTCPServer;

    /**
     * Constructs a new instance with the provided server instance.
     *
     * @param eventMeshTCPServer the TCP server instance of EventMesh
     */
    public RejectAllClientHandler(final EventMeshTCPServer eventMeshTCPServer) {
        super();
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public void handle(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        // Retrieve the mapping between Sessions and their corresponding client address
        final ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();
        final ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
        final List<InetSocketAddress> successRemoteAddrs = new ArrayList<>();
        try {
            log.info("rejectAllClient in admin====================");
            if (!sessionMap.isEmpty()) {
                // Iterate through the sessionMap and close each client connection
                for (final Map.Entry<InetSocketAddress, Session> entry : sessionMap.entrySet()) {
                    final InetSocketAddress addr = EventMeshTcp2Client.serverGoodby2Client(
                        eventMeshTCPServer.getTcpThreadPoolGroup(), entry.getValue(), clientSessionGroupMapping);
                    // Add the remote client address to a list of successfully rejected addresses
                    if (addr != null) {
                        successRemoteAddrs.add(addr);
                    }
                }
            }
        } catch (Exception e) {
            log.error("clientManage rejectAllClient fail", e);
            writeText(ctx, String.format("rejectAllClient fail! sessionMap size {%d}, had reject {%s}, errorMsg : %s",
                    sessionMap.size(), NetUtils.addressToString(successRemoteAddrs), e.getMessage()));
            return;
        }
        writeText(ctx, String.format("rejectAllClient success! sessionMap size {%d}, had reject {%s}",
            sessionMap.size(), NetUtils.addressToString(successRemoteAddrs)));
    }
}
