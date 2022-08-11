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

import io.cloudevents.jackson.JsonFormat;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import org.apache.eventmesh.runtime.admin.request.CreateTopicRequest;
import org.apache.eventmesh.runtime.admin.response.Error;
import org.apache.eventmesh.runtime.admin.utils.HttpExchangeUtils;
import org.apache.eventmesh.runtime.admin.utils.JsonUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.eventmesh.runtime.core.plugin.MQAdminWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * The event handler
 */
public class EventHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationHandler.class);

    private final MQAdminWrapper admin;

    public EventHandler(
            String connectorPluginType
    ) {
        admin = new MQAdminWrapper(connectorPluginType);
        try {
            admin.init(null);
        } catch (Exception ignored) {
            logger.info("failed to initialize MQAdminWrapper");
        }
    }

    /**
     * OPTIONS /event
     */
    void preflight(HttpExchange httpExchange) throws IOException {
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Methods", "*");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
        httpExchange.getResponseHeaders().add("Access-Control-Max-Age", "86400");
        httpExchange.sendResponseHeaders(200, 0);
        OutputStream out = httpExchange.getResponseBody();
        out.close();
    }

    private Map<String, String> queryToMap(String query) {
        if (query == null) {
            return null;
        }
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }

    /**
     * GET /event
     * Return the list of event
     */
    void get(HttpExchange httpExchange) throws IOException {
        OutputStream out = httpExchange.getResponseBody();
        httpExchange.getResponseHeaders().add("Content-Type", "application/json");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        try {
            String queryString = httpExchange.getRequestURI().getQuery();
            if (queryString == null || queryString.equals("")) {
                httpExchange.sendResponseHeaders(401, 0);
                out.close();
                return;
            }

            Map<String, String> queryMap = queryToMap(queryString);
            String topicName = queryMap.get("topicName");
            int offset = Integer.parseInt(queryMap.get("offset"));
            int length = Integer.parseInt(queryMap.get("length"));
            List<CloudEvent> eventList = admin.getEvent(topicName, offset, length);

            List<String> eventJsonList = new ArrayList<>();
            for (CloudEvent event : eventList) {
                byte[]serializedEvent = EventFormatProvider
                        .getInstance()
                        .resolveFormat(JsonFormat.CONTENT_TYPE)
                        .serialize(event);
                eventJsonList.add(new String(serializedEvent, StandardCharsets.UTF_8));
            }
            String result = JsonUtils.toJson(eventJsonList);
            httpExchange.sendResponseHeaders(200, result.getBytes().length);
            out.write(result.getBytes());
        } catch (Exception e) {
            StringWriter writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            e.printStackTrace(printWriter);
            printWriter.flush();
            String stackTrace = writer.toString();

            Error error = new Error(e.toString(), stackTrace);
            String result = JsonUtils.toJson(error);
            httpExchange.sendResponseHeaders(500, result.getBytes().length);
            out.write(result.getBytes());
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

    /**
     * POST /event
     * Create an event
     */
    void post(HttpExchange httpExchange) throws IOException {
        OutputStream out = httpExchange.getResponseBody();
        httpExchange.getResponseHeaders().add("Content-Type", "application/json");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        try {
            String request = HttpExchangeUtils.streamToString(httpExchange.getRequestBody());
            byte[] rawRequest = request.getBytes(StandardCharsets.UTF_8);
            CloudEvent event = EventFormatProvider
                    .getInstance()
                    .resolveFormat(JsonFormat.CONTENT_TYPE).deserialize(rawRequest);
            admin.publish(event);
            httpExchange.sendResponseHeaders(200, 0);
        } catch (Exception e) {
            StringWriter writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            e.printStackTrace(printWriter);
            printWriter.flush();
            String stackTrace = writer.toString();

            Error error = new Error(e.toString(), stackTrace);
            String result = JsonUtils.toJson(error);
            httpExchange.sendResponseHeaders(500, result.getBytes().length);
            out.write(result.getBytes());
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

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        if (httpExchange.getRequestMethod().equals("OPTIONS")) {
            preflight(httpExchange);
        }
        if (httpExchange.getRequestMethod().equals("POST")) {
            post(httpExchange);
        }
        if (httpExchange.getRequestMethod().equals("GET")) {
            get(httpExchange);
        }
    }
}
