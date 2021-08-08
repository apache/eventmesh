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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RedirectClientByIpPortHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(RedirectClientByIpPortHandler.class);

    private final EventMeshProtocolTCPServer eventMeshTCPServer;

    public RedirectClientByIpPortHandler(EventMeshProtocolTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String result = "";
        OutputStream out = httpExchange.getResponseBody();
        try {
            String queryString = httpExchange.getRequestURI().getQuery();
            Map<String, String> queryStringInfo = NetUtils.formData2Dic(queryString);
            String ip = queryStringInfo.get(TcpProtocolConstants.MANAGE_IP);
            String port = queryStringInfo.get(TcpProtocolConstants.MANAGE_PORT);
            String destEventMeshIp = queryStringInfo.get(TcpProtocolConstants.MANAGE_DEST_IP);
            String destEventMeshPort = queryStringInfo.get(TcpProtocolConstants.MANAGE_DEST_PORT);

            if (StringUtils.isBlank(ip) || !StringUtils.isNumeric(port)
                    || StringUtils.isBlank(destEventMeshIp) || StringUtils.isBlank(destEventMeshPort)
                    || !StringUtils.isNumeric(destEventMeshPort)) {
                httpExchange.sendResponseHeaders(200, 0);
                result = "params illegal!";
                out.write(result.getBytes());
                return;
            }
            logger.info("redirectClientByIpPort in admin,ip:{},port:{},destIp:{},destPort:{}====================", ip, port, destEventMeshIp, destEventMeshPort);
            ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();
            ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
            String redirectResult = "";
            try {
                if (!sessionMap.isEmpty()) {
                    for (Session session : sessionMap.values()) {
                        if (session.getClient().getHost().equals(ip) && String.valueOf(session.getClient().getPort()).equals(port)) {
                            redirectResult += "|";
                            redirectResult += EventMeshTcp2Client.redirectClient2NewEventMesh(eventMeshTCPServer, destEventMeshIp, Integer.parseInt(destEventMeshPort),
                                    session, clientSessionGroupMapping);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("clientManage|redirectClientByIpPort|fail|ip={}|port={}|destEventMeshIp" +
                        "={}|destEventMeshPort={},errMsg={}", ip, port, destEventMeshIp, destEventMeshPort, e);
                result = String.format("redirectClientByIpPort fail! sessionMap size {%d}, {clientIp=%s clientPort=%s " +
                                "destEventMeshIp=%s destEventMeshPort=%s}, result {%s}, errorMsg : %s",
                        sessionMap.size(), ip, port, destEventMeshIp, destEventMeshPort, redirectResult, e
                                .getMessage());
                httpExchange.sendResponseHeaders(200, 0);
                out.write(result.getBytes());
                return;
            }
            result = String.format("redirectClientByIpPort success! sessionMap size {%d}, {ip=%s port=%s " +
                            "destEventMeshIp=%s destEventMeshPort=%s}, result {%s} ",
                    sessionMap.size(), ip, port, destEventMeshIp, destEventMeshPort, redirectResult);
            httpExchange.sendResponseHeaders(200, 0);
            out.write(result.getBytes());
        } catch (Exception e) {
            logger.error("redirectClientByIpPort fail...", e);
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
