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
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.admin.handler.AbstractHttpHandler;
import org.apache.eventmesh.runtime.admin.request.DeleteTCPClientRequest;
import org.apache.eventmesh.runtime.admin.response.v1.GetClientResponse;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.common.EventMeshHttpHandler;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcp2Client;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.util.HttpRequestUtil;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the {@code /client/tcp} endpoint, corresponding to the {@code eventmesh-dashboard} path {@code /tcp}.
 * <p>
 * It is responsible for managing operations on TCP clients, including retrieving the information list of connected TCP clients and deleting TCP
 * clients by disconnecting their connections based on the provided host and port.
 *
 * @see AbstractHttpHandler
 */

@Slf4j
@EventMeshHttpHandler(path = "/client/tcp")
public class TCPClientHandler extends AbstractHttpHandler {

    private final EventMeshTCPServer eventMeshTCPServer;

    /**
     * Constructs a new instance with the provided server instance.
     *
     * @param eventMeshTCPServer the TCP server instance of EventMesh
     */
    public TCPClientHandler(
        EventMeshTCPServer eventMeshTCPServer) {
        super();
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    protected void delete(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        // Parse the request body string into a DeleteTCPClientRequest object
        Map<String, Object> body = HttpRequestUtil.parseHttpRequestBody(httpRequest);
        Objects.requireNonNull(body, "body can not be null");
        DeleteTCPClientRequest deleteTCPClientRequest = JsonUtils.mapToObject(body, DeleteTCPClientRequest.class);
        String host = Objects.requireNonNull(deleteTCPClientRequest).getHost();
        int port = deleteTCPClientRequest.getPort();

        ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();
        ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
        if (!sessionMap.isEmpty()) {
            for (Map.Entry<InetSocketAddress, Session> entry : sessionMap.entrySet()) {
                // Find the Session object that matches the host and port to be deleted
                if (entry.getKey().getHostString().equals(host) && entry.getKey().getPort() == port) {
                    // Call the serverGoodby2Client method in EventMeshTcp2Client to disconnect the client's connection
                    EventMeshTcp2Client.serverGoodby2Client(
                        eventMeshTCPServer.getTcpThreadPoolGroup(),
                        entry.getValue(),
                        clientSessionGroupMapping);
                }
            }
        }

        // Set the response headers and send a 200 status code empty response
        writeText(ctx, "");
    }

    @Override
    protected void get(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        // Get the list of connected TCP clients
        ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();
        Map<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
        List<GetClientResponse> getClientResponseList = new ArrayList<>();
        // Convert each Session object to GetClientResponse and add to getClientResponseList
        for (Session session : sessionMap.values()) {
            UserAgent userAgent = session.getClient();
            GetClientResponse getClientResponse = new GetClientResponse(
                Optional.ofNullable(userAgent.getEnv()).orElse(""),
                Optional.ofNullable(userAgent.getSubsystem()).orElse(""),
                Optional.ofNullable(userAgent.getPath()).orElse(""),
                String.valueOf(userAgent.getPid()),
                Optional.ofNullable(userAgent.getHost()).orElse(""),
                userAgent.getPort(),
                Optional.ofNullable(userAgent.getVersion()).orElse(""),
                Optional.ofNullable(userAgent.getIdc()).orElse(""),
                Optional.ofNullable(userAgent.getGroup()).orElse(""),
                Optional.ofNullable(userAgent.getPurpose()).orElse(""),
                "TCP");
            getClientResponseList.add(getClientResponse);
        }

        // Sort the getClientResponseList by host and port
        getClientResponseList.sort((lhs, rhs) -> {
            if (lhs.getHost().equals(rhs.getHost())) {
                return lhs.getHost().compareTo(rhs.getHost());
            }
            return Integer.compare(rhs.getPort(), lhs.getPort());
        });

        // Convert getClientResponseList to JSON and send the response
        String result = JsonUtils.toJSONString(getClientResponseList);
        writeJson(ctx, result);
    }
}
