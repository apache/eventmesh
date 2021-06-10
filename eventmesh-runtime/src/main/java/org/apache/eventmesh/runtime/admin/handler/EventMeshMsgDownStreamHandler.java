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
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventMeshMsgDownStreamHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(EventMeshMsgDownStreamHandler.class);

    private final EventMeshTCPServer eventMeshTCPServer;

    public EventMeshMsgDownStreamHandler(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String result = "false";
        OutputStream out = httpExchange.getResponseBody();
        try {
//                Map<String, Object> queryStringInfo =  parsePostParameters(httpExchange);
//                String msgStr = (String)queryStringInfo.get("msg");
//                String groupName = (String)queryStringInfo.get("group");
//                logger.info("recieve msg from other eventMesh, group:{}, msg:{}", groupName, msgStr);
//                if (StringUtils.isBlank(msgStr) || StringUtils.isBlank(groupName)) {
//                    logger.warn("msg or groupName is null");
//                    httpExchange.sendResponseHeaders(200, 0);
//                    out.write(result.getBytes());
//                    return;
//                }
//                MessageExt messageExt = JSON.parseObject(msgStr, MessageExt.class);
//                String topic = messageExt.getTopic();
//
//                if (!EventMeshUtil.isValidRMBTopic(topic)) {
//                    logger.warn("msg topic is illegal");
//                    httpExchange.sendResponseHeaders(200, 0);
//                    out.write(result.getBytes());
//                    return;
//                }
//
//                DownstreamDispatchStrategy downstreamDispatchStrategy = eventMeshTCPServer.getClientSessionGroupMapping().getClientGroupWrapper(groupName).getDownstreamDispatchStrategy();
//                Set<Session> groupConsumerSessions = eventMeshTCPServer.getClientSessionGroupMapping().getClientGroupWrapper(groupName).getGroupConsumerSessions();
//                Session session = downstreamDispatchStrategy.select(groupName, topic, groupConsumerSessions);
//
//                if(session == null){
//                    logger.error("DownStream msg,retry other eventMesh found no session again");
//                    httpExchange.sendResponseHeaders(200, 0);
//                    out.write(result.getBytes());
//                    return;
//                }
//
//                DownStreamMsgContext downStreamMsgContext =
//                        new DownStreamMsgContext(messageExt, session, eventMeshTCPServer.getClientSessionGroupMapping().getClientGroupWrapper(groupName).getPersistentMsgConsumer(), null, true);
//                eventMeshTCPServer.getClientSessionGroupMapping().getClientGroupWrapper(groupName).getDownstreamMap().putIfAbsent(downStreamMsgContext.seq, downStreamMsgContext);
//
//                if (session.isCanDownStream()) {
//                    session.downstreamMsg(downStreamMsgContext);
//                    httpExchange.sendResponseHeaders(200, 0);
//                    result = "true";
//                    out.write(result.getBytes());
//                    return;
//                }
//
//                logger.warn("EventMeshMsgDownStreamHandler|dispatch retry, seq[{}]", downStreamMsgContext.seq);
//                long delayTime = EventMeshUtil.isService(downStreamMsgContext.msgExt.getTopic()) ? 0 : eventMeshTCPServer.getAccessConfiguration().eventMeshTcpMsgRetryDelayInMills;
//                downStreamMsgContext.delay(delayTime);
//                eventMeshTCPServer.getEventMeshTcpRetryer().pushRetry(downStreamMsgContext);
//                result = "true";
//                httpExchange.sendResponseHeaders(200, 0);
//                out.write(result.getBytes());

        } catch (Exception e) {
            logger.error("EventMeshMsgDownStreamHandler handle fail...", e);
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

    private Map<String, Object> parsePostParameters(HttpExchange exchange)
            throws IOException {
        Map<String, Object> parameters = new HashMap<>();
        if ("post".equalsIgnoreCase(exchange.getRequestMethod())) {
            InputStreamReader isr =
                    new InputStreamReader(exchange.getRequestBody(), "utf-8");
            BufferedReader br = new BufferedReader(isr);
            String query = br.readLine();
            parseQuery(query, parameters);
        }
        return parameters;
    }

    @SuppressWarnings("unchecked")
    private void parseQuery(String query, Map<String, Object> parameters)
            throws UnsupportedEncodingException {

        if (query != null) {
            String pairs[] = query.split("&");

            for (String pair : pairs) {
                String param[] = pair.split("=");

                String key = null;
                String value = null;
                if (param.length > 0) {
                    key = URLDecoder.decode(param[0], "UTF-8");
                }

                if (param.length > 1) {
                    value = URLDecoder.decode(param[1], "UTF-8");
                }

                if (parameters.containsKey(key)) {
                    Object obj = parameters.get(key);
                    if (obj instanceof List<?>) {
                        List<String> values = (List<String>) obj;
                        values.add(value);
                    } else if (obj instanceof String) {
                        List<String> values = new ArrayList<String>();
                        values.add((String) obj);
                        values.add(value);
                        parameters.put(key, values);
                    }
                } else {
                    parameters.put(key, value);
                }
            }
        }
    }
}
