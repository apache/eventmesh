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
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.protocol.tcp.EventMeshProtocolTCPServer;
import org.apache.eventmesh.protocol.tcp.client.group.ClientGroupWrapper;
import org.apache.eventmesh.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.protocol.tcp.client.session.Session;
import org.apache.eventmesh.protocol.tcp.config.TcpProtocolConstants;
import org.apache.eventmesh.protocol.tcp.utils.NetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * query client subscription by topic
 */
public class ShowListenClientByTopicHandler implements HttpHandler {

    private Logger logger = LoggerFactory.getLogger(ShowListenClientByTopicHandler.class);

    private final EventMeshProtocolTCPServer eventMeshTCPServer;

    public ShowListenClientByTopicHandler(EventMeshProtocolTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String result = "";
        try (OutputStream out = httpExchange.getResponseBody()) {
            String queryString = httpExchange.getRequestURI().getQuery();
            Map<String, String> queryStringInfo = NetUtils.formData2Dic(queryString);
            String topic = queryStringInfo.get(TcpProtocolConstants.MANAGE_TOPIC);

            String newLine = System.getProperty("line.separator");
            logger.info("showListeningClientByTopic,topic:{}=================", topic);
            ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();
            ConcurrentHashMap<String, ClientGroupWrapper> clientGroupMap = clientSessionGroupMapping.getClientGroupMap();
            if (!clientGroupMap.isEmpty()) {
                for (ClientGroupWrapper cgw : clientGroupMap.values()) {
                    Set<Session> listenSessionSet = cgw.getTopic2sessionInGroupMapping().get(topic);
                    if (listenSessionSet != null && listenSessionSet.size() > 0) {
                        result += String.format("group:%s", cgw.getConsumerGroup()) + newLine;
                        for (Session session : listenSessionSet) {
                            UserAgent userAgent = session.getClient();
                            result += String.format("pid=%s | ip=%s | port=%s | path=%s | version=%s", userAgent.getPid(), userAgent
                                    .getHost(), userAgent.getPort(), userAgent.getPath(), userAgent.getVersion()) + newLine;
                        }
                    }
                }
            }
            httpExchange.sendResponseHeaders(200, 0);
            out.write(result.getBytes());
        } catch (Exception e) {
            logger.error("ShowListenClientByTopicHandler fail...", e);
        }
    }
}
