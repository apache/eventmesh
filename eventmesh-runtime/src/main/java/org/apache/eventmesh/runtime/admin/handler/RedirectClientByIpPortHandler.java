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

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.NetUtils;
import org.apache.eventmesh.runtime.admin.controller.HttpHandlerManager;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.common.EventHttpHandler;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcp2Client;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


import com.sun.net.httpserver.HttpExchange;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@EventHttpHandler(path = "/clientManage/redirectClientByIpPort")
public class RedirectClientByIpPortHandler extends AbstractHttpHandler {

    private final EventMeshTCPServer eventMeshTCPServer;

    public RedirectClientByIpPortHandler(EventMeshTCPServer eventMeshTCPServer, HttpHandlerManager httpHandlerManager) {
        super(httpHandlerManager);
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String result = "";
        try (OutputStream out = httpExchange.getResponseBody()) {
            String queryString = httpExchange.getRequestURI().getQuery();
            Map<String, String> queryStringInfo = NetUtils.formData2Dic(queryString);
            String ip = queryStringInfo.get(EventMeshConstants.MANAGE_IP);
            String port = queryStringInfo.get(EventMeshConstants.MANAGE_PORT);
            String destEventMeshIp = queryStringInfo.get(EventMeshConstants.MANAGE_DEST_IP);
            String destEventMeshPort = queryStringInfo.get(EventMeshConstants.MANAGE_DEST_PORT);

            if (StringUtils.isBlank(ip) || !StringUtils.isNumeric(port)
                || StringUtils.isBlank(destEventMeshIp) || StringUtils.isBlank(destEventMeshPort)
                || !StringUtils.isNumeric(destEventMeshPort)) {
                NetUtils.sendSuccessResponseHeaders(httpExchange);
                result = "params illegal!";
                out.write(result.getBytes(Constants.DEFAULT_CHARSET));
                return;
            }
            log.info("redirectClientByIpPort in admin,ip:{},port:{},destIp:{},destPort:{}====================", ip,
                port, destEventMeshIp, destEventMeshPort);
            ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();
            ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
            StringBuilder redirectResult = new StringBuilder();
            try {
                if (!sessionMap.isEmpty()) {
                    for (Session session : sessionMap.values()) {
                        if (session.getClient().getHost().equals(ip) && String.valueOf(
                            session.getClient().getPort()).equals(port)) {
                            redirectResult.append("|");
                            redirectResult.append(
                                EventMeshTcp2Client.redirectClient2NewEventMesh(eventMeshTCPServer,
                                    destEventMeshIp, Integer.parseInt(destEventMeshPort),
                                    session, clientSessionGroupMapping));
                        }
                    }
                }
            } catch (Exception e) {
                log.error("clientManage|redirectClientByIpPort|fail|ip={}|port={}|destEventMeshIp"
                    +
                    "={}|destEventMeshPort={},errMsg={}", ip, port, destEventMeshIp, destEventMeshPort, e);
                result = String.format("redirectClientByIpPort fail! sessionMap size {%d}, {clientIp=%s clientPort=%s "
                        +
                        "destEventMeshIp=%s destEventMeshPort=%s}, result {%s}, errorMsg : %s",
                    sessionMap.size(), ip, port, destEventMeshIp, destEventMeshPort,
                    redirectResult.toString(), e
                        .getMessage());
                NetUtils.sendSuccessResponseHeaders(httpExchange);
                out.write(result.getBytes(Constants.DEFAULT_CHARSET));
                return;
            }
            result = String.format("redirectClientByIpPort success! sessionMap size {%d}, {ip=%s port=%s "
                    +
                    "destEventMeshIp=%s destEventMeshPort=%s}, result {%s} ",
                sessionMap.size(), ip, port, destEventMeshIp, destEventMeshPort,
                redirectResult.toString());
            NetUtils.sendSuccessResponseHeaders(httpExchange);
            out.write(result.getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            log.error("redirectClientByIpPort fail...", e);
        }

    }
}
