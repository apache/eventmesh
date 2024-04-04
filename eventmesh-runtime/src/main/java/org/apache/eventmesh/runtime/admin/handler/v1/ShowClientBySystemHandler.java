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

import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.utils.NetUtils;
import org.apache.eventmesh.runtime.admin.handler.AbstractHttpHandler;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.common.EventMeshHttpHandler;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the HTTP requests of {@code /clientManage/showClientBySystem} endpoint, which is used to display connected clients information
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
@EventMeshHttpHandler(path = "/clientManage/showClientBySystem")
public class ShowClientBySystemHandler extends AbstractHttpHandler {

    private final EventMeshTCPServer eventMeshTCPServer;

    /**
     * Constructs a new instance with the provided server instance.
     *
     * @param eventMeshTCPServer the TCP server instance of EventMesh
     */
    public ShowClientBySystemHandler(EventMeshTCPServer eventMeshTCPServer) {
        super();
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public void handle(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        StringBuilder result = new StringBuilder();
        String queryString = URI.create(httpRequest.uri()).getQuery();
        Map<String, String> queryStringInfo = NetUtils.formData2Dic(queryString);
        // Extract parameter from the query string
        String subSystem = queryStringInfo.get(EventMeshConstants.MANAGE_SUBSYSTEM);

        String newLine = System.getProperty("line.separator");
        log.info("showClientBySubsys,subsys:{}", subSystem);
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
                            userAgent.getPid(), userAgent.getHost(), userAgent.getPort(), userAgent.getPath(), userAgent.getPurpose()))
                        .append(newLine);
                }
            }
        }
        writeText(ctx, result.toString());
    }
}
