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

import org.apache.eventmesh.runtime.admin.handler.AbstractHttpHandler;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.common.EventMeshHttpHandler;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the HTTP requests of {@code /clientManage/showClient} endpoint.
 * <p>
 * It is used to query information about all clients connected to the current EventMesh server node and to provide statistics on the number of clients
 * in each subsystem.
 *
 * @see AbstractHttpHandler
 */

@Slf4j
@EventMeshHttpHandler(path = "/clientManage/showClient")
public class ShowClientHandler extends AbstractHttpHandler {

    private final EventMeshTCPServer eventMeshTCPServer;

    /**
     * Constructs a new instance with the provided server instance.
     *
     * @param eventMeshTCPServer the TCP server instance of EventMesh
     */
    public ShowClientHandler(EventMeshTCPServer eventMeshTCPServer) {
        super();
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public void handle(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        StringBuilder result = new StringBuilder();
        String newLine = System.getProperty("line.separator");
        log.info("showAllClient=================");
        ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();

        // Store the subsystem and the corresponding client count.
        HashMap<String, AtomicInteger> statMap = new HashMap<>();

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
        writeText(ctx, result.toString());
    }
}
