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

import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * This handler used to print the total client info
 */
public class ShowClientHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(ShowClientHandler.class);

    private final EventMeshTCPServer eventMeshTCPServer;

    public ShowClientHandler(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String result = "";
        OutputStream out = httpExchange.getResponseBody();
        try {
            String newLine = System.getProperty("line.separator");
            logger.info("showAllClient=================");
            ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();

            HashMap<String, AtomicInteger> statMap = new HashMap<String, AtomicInteger>();

            Map<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
            if (!sessionMap.isEmpty()) {
                for (Session session : sessionMap.values()) {
                    String key = session.getClient().getSubsystem();
                    if (!statMap.containsKey(key)) {
                        statMap.put(key, new AtomicInteger(1));
                    } else {
                        statMap.get(key).incrementAndGet();
                    }
                }

                for (Map.Entry<String, AtomicInteger> entry : statMap.entrySet()) {
                    result += String.format("System=%s | ClientNum=%d", entry.getKey(), entry.getValue().intValue())
                            +
                            newLine;
                }
            }

            httpExchange.sendResponseHeaders(200, 0);
            out.write(result.getBytes());
        } catch (Exception e) {
            logger.error("ShowClientHandler fail...", e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    logger.warn("out close failed...", e);
                }
            }
        }

    }
}
