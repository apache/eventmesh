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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcp2Client;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.util.NetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RejectClientBySubSystemHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(RejectClientBySubSystemHandler.class);

    private EventMeshTCPServer eventMeshTCPServer;

    public RejectClientBySubSystemHandler(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    /**
     * remove c client by dcn and susysId
     * @param httpExchange
     * @throws IOException
     */
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String result = "";
        OutputStream out = httpExchange.getResponseBody();
        try {
            String queryString = httpExchange.getRequestURI().getQuery();
            Map<String, String> queryStringInfo = NetUtils.formData2Dic(queryString);
            String dcn = queryStringInfo.get(EventMeshConstants.MANAGE_DCN);
            String subSystem = queryStringInfo.get(EventMeshConstants.MANAGE_SUBSYSTEM);

            if (StringUtils.isBlank(dcn) || StringUtils.isBlank(subSystem)) {
                httpExchange.sendResponseHeaders(200, 0);
                result = "params illegal!";
                out.write(result.getBytes());
                return;
            }

            logger.info("rejectClientBySubSystem in admin,subsys:{},dcn:{}====================", subSystem, dcn);
            ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();
            ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
            final List<InetSocketAddress> successRemoteAddrs = new ArrayList<>();
            try {
                if (!sessionMap.isEmpty()) {
                    for (Session session : sessionMap.values()) {
                        if (session.getClient().getDcn().equals(dcn) && session.getClient().getSubsystem().equals(subSystem)) {
                            InetSocketAddress addr = EventMeshTcp2Client.serverGoodby2Client(eventMeshTCPServer, session, clientSessionGroupMapping);
                            if (addr != null) {
                                successRemoteAddrs.add(addr);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("clientManage|rejectClientBySubSystem|fail|dcn={}|subSystemId={},errMsg={}", dcn, subSystem, e);
                result = String.format("rejectClientBySubSystem fail! sessionMap size {%d}, had reject {%s} , {dcn=%s " +
                                "port=%s}, errorMsg : %s", sessionMap.size(), NetUtils.addressToString(successRemoteAddrs), dcn,
                        subSystem, e.getMessage());
                httpExchange.sendResponseHeaders(200, 0);
                out.write(result.getBytes());
                return;
            }
            result = String.format("rejectClientBySubSystem success! sessionMap size {%d}, had reject {%s} , {dcn=%s " +
                    "port=%s}", sessionMap.size(), NetUtils.addressToString(successRemoteAddrs), dcn, subSystem);
            httpExchange.sendResponseHeaders(200, 0);
            out.write(result.getBytes());
        } catch (Exception e) {
            logger.error("rejectClientBySubSystem fail...", e);
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
