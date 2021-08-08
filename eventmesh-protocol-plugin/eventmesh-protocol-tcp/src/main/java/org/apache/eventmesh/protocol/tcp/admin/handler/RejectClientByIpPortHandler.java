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
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.protocol.tcp.EventMeshProtocolTCPServer;
import org.apache.eventmesh.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.protocol.tcp.client.session.Session;
import org.apache.eventmesh.protocol.tcp.config.TcpProtocolConstants;
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

public class RejectClientByIpPortHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(RejectClientByIpPortHandler.class);

    private EventMeshProtocolTCPServer eventMeshTCPServer;

    public RejectClientByIpPortHandler(EventMeshProtocolTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String result = "";
        try (OutputStream out = httpExchange.getResponseBody()) {
            String queryString = httpExchange.getRequestURI().getQuery();
            Map<String, String> queryStringInfo = NetUtils.formData2Dic(queryString);
            String ip = queryStringInfo.get(TcpProtocolConstants.MANAGE_IP);
            String port = queryStringInfo.get(TcpProtocolConstants.MANAGE_PORT);

            if (StringUtils.isBlank(ip) || StringUtils.isBlank(port)) {
                httpExchange.sendResponseHeaders(200, 0);
                result = "params illegal!";
                out.write(result.getBytes());
                return;
            }
            logger.info("rejectClientByIpPort in admin,ip:{},port:{}====================", ip, port);
            ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();
            ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
            final List<InetSocketAddress> successRemoteAddrs = new ArrayList<InetSocketAddress>();
            try {
                if (!sessionMap.isEmpty()) {
                    for (Map.Entry<InetSocketAddress, Session> entry : sessionMap.entrySet()) {
                        if (entry.getKey().getHostString().equals(ip) && String.valueOf(entry.getKey().getPort()).equals(port)) {
                            InetSocketAddress addr = EventMeshTcp2Client.serverGoodby2Client(eventMeshTCPServer, entry.getValue(), clientSessionGroupMapping);
                            if (addr != null) {
                                successRemoteAddrs.add(addr);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("clientManage|rejectClientByIpPort|fail|ip={}|port={},errMsg={}", ip, port, e);
                result = String.format("rejectClientByIpPort fail! {ip=%s port=%s}, had reject {%s}, errorMsg : %s", ip,
                        port, NetUtils.addressToString(successRemoteAddrs), e.getMessage());
                httpExchange.sendResponseHeaders(200, 0);
                out.write(result.getBytes());
                return;
            }

            result = String.format("rejectClientByIpPort success! {ip=%s port=%s}, had reject {%s}", ip, port,
                    NetUtils.addressToString(successRemoteAddrs));
            httpExchange.sendResponseHeaders(200, 0);
            out.write(result.getBytes());
        } catch (Exception e) {
            logger.error("rejectClientByIpPort fail...", e);
        }
    }
}
