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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the HTTP requests of {@code /clientManage/redirectClientBySubSystem} endpoint, which is used to redirect matching clients to a
 * target EventMesh server node based on the provided client subsystem id in a Data Communication Network (DCN).
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
@EventMeshHttpHandler(path = "/clientManage/redirectClientBySubSystem")
public class RedirectClientBySubSystemHandler extends AbstractHttpHandler {

    private final transient EventMeshTCPServer eventMeshTCPServer;

    /**
     * Constructs a new instance with the provided server instance.
     *
     * @param eventMeshTCPServer the TCP server instance of EventMesh
     */
    public RedirectClientBySubSystemHandler(final EventMeshTCPServer eventMeshTCPServer) {
        super();
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public void handle(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        final Map<String, String> queryStringInfo = NetUtils.formData2Dic(URI.create(httpRequest.uri()).getQuery());
        // Extract parameters from the query string
        final String subSystem = queryStringInfo.get(EventMeshConstants.MANAGE_SUBSYSTEM);
        final String destEventMeshIp = queryStringInfo.get(EventMeshConstants.MANAGE_DEST_IP);
        final String destEventMeshPort = queryStringInfo.get(EventMeshConstants.MANAGE_DEST_PORT);

        // Check the validity of the parameters
        if (!StringUtils.isNumeric(subSystem)
            || StringUtils.isBlank(destEventMeshIp) || StringUtils.isBlank(destEventMeshPort)
            || !StringUtils.isNumeric(destEventMeshPort)) {
            writeText(ctx, "params illegal!");
            return;
        }
        log.info("redirectClientBySubSystem in admin,subsys:{},destIp:{},destPort:{}====================",
            subSystem, destEventMeshIp, destEventMeshPort);

        // Retrieve the mapping between Sessions and their corresponding client address
        final ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();
        final ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
        final StringBuilder redirectResult = new StringBuilder();
        try {
            if (!sessionMap.isEmpty()) {
                // Iterate through the sessionMap to find matching sessions where the client's subsystem id matches the given param
                for (final Session session : sessionMap.values()) {
                    // For each matching session found, redirect the client
                    // to the new EventMesh node specified by given EventMesh IP and port.
                    if (session.getClient().getSubsystem().equals(subSystem)) {
                        redirectResult.append('|')
                            .append(EventMeshTcp2Client.redirectClient2NewEventMesh(eventMeshTCPServer.getTcpThreadPoolGroup(),
                                destEventMeshIp, Integer.parseInt(destEventMeshPort),
                                session, clientSessionGroupMapping));
                    }
                }
            }
        } catch (Exception e) {
            log.error("clientManage|redirectClientBySubSystem|fail|subSystem={}|destEventMeshIp"
                + "={}|destEventMeshPort={}", subSystem, destEventMeshIp, destEventMeshPort, e);

            writeText(ctx, String.format("redirectClientBySubSystem fail! sessionMap size {%d}, {subSystem=%s "
                        + "destEventMeshIp=%s destEventMeshPort=%s}, result {%s}, errorMsg : %s",
                    sessionMap.size(), subSystem, destEventMeshIp, destEventMeshPort, redirectResult, e.getMessage()));
            return;
        }
        writeText(ctx, String.format("redirectClientBySubSystem success! sessionMap size {%d}, {subSystem=%s "
                    + "destEventMeshIp=%s destEventMeshPort=%s}, result {%s} ",
                sessionMap.size(), subSystem, destEventMeshIp, destEventMeshPort, redirectResult));
    }
}
