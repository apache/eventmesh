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
import org.apache.eventmesh.common.enums.HttpMethod;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.admin.controller.HttpHandlerManager;
import org.apache.eventmesh.runtime.admin.response.Error;
import org.apache.eventmesh.runtime.admin.utils.HttpExchangeUtils;
import org.apache.eventmesh.runtime.common.EventHttpHandler;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.plugin.MQAdminWrapper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

import com.sun.net.httpserver.HttpExchange;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the {@code /event} endpoint,
 * corresponding to the {@code eventmesh-dashboard} path {@code /event}.
 * <p>
 * It is responsible for managing operations on events,
 * including retrieving the event list and creating events.
 * <p>
 * The GET method supports querying events by {@code topicName},
 * and uses {@code offset} and {@code length} parameters for pagination.
 * <p>
 * An instance of {@link MQAdminWrapper} is used to interact with the messaging system.
 *
 * @see AbstractHttpHandler
 * @see MQAdminWrapper
 */

@Slf4j
@EventHttpHandler(path = "/event")
public class EventHandler extends AbstractHttpHandler {

    private final MQAdminWrapper admin;

    /**
     * Constructs a new instance with the specified connector plugin type and HTTP handler manager.
     *
     * @param connectorPluginType The name of event storage connector plugin.
     * @param httpHandlerManager httpHandlerManager Manages the registration of {@linkplain com.sun.net.httpserver.HttpHandler HttpHandler}
     *                           for an {@link com.sun.net.httpserver.HttpServer HttpServer}.
     */
    public EventHandler(
        String connectorPluginType,
        HttpHandlerManager httpHandlerManager) {
        super(httpHandlerManager);
        admin = new MQAdminWrapper(connectorPluginType);
        try {
            admin.init(null);
        } catch (Exception ignored) {
            log.info("failed to initialize MQAdminWrapper");
        }
    }

    /**
     * Handles the OPTIONS request first for {@code /event}.
     * <p>
     * This method adds CORS (Cross-Origin Resource Sharing) response headers to
     * the {@link HttpExchange} object and sends a 200 status code.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    void preflight(HttpExchange httpExchange) throws IOException {
        httpExchange.getResponseHeaders().add(EventMeshConstants.HANDLER_ORIGIN, "*");
        httpExchange.getResponseHeaders().add(EventMeshConstants.HANDLER_METHODS, "*");
        httpExchange.getResponseHeaders().add(EventMeshConstants.HANDLER_HEADERS, "*");
        httpExchange.getResponseHeaders().add(EventMeshConstants.HANDLER_AGE, EventMeshConstants.MAX_AGE);
        httpExchange.sendResponseHeaders(200, 0);
        OutputStream out = httpExchange.getResponseBody();
        out.close();
    }

    /**
     * Converts a query string to a map of key-value pairs.
     * <p>
     * This method takes a query string and parses it to create a map of key-value pairs,
     * where each key and value are extracted from the query string separated by '='.
     * <p>
     * If the query string is null, an empty map is returned.
     *
     * @param query the query string to convert to a map
     * @return a map containing the key-value pairs from the query string
     */
    private Map<String, String> queryToMap(String query) {
        if (query == null) {
            return new HashMap<>();
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
     * Handles the GET request for {@code /event}.
     * <p>
     * This method retrieves the list of events from the {@link MQAdminWrapper} and returns it as a JSON response.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    void get(HttpExchange httpExchange) {
        httpExchange.getResponseHeaders().add(EventMeshConstants.CONTENT_TYPE, EventMeshConstants.APPLICATION_JSON);
        httpExchange.getResponseHeaders().add(EventMeshConstants.HANDLER_ORIGIN, "*");

        try (OutputStream out = httpExchange.getResponseBody()) {
            String queryString = httpExchange.getRequestURI().getQuery();
            if (queryString == null || "".equals(queryString)) {
                httpExchange.sendResponseHeaders(401, 0);
                return;
            }

            Map<String, String> queryMap = queryToMap(queryString);
            String topicName = queryMap.get("topicName");
            int offset = Integer.parseInt(queryMap.get("offset"));
            int length = Integer.parseInt(queryMap.get("length"));
            List<CloudEvent> eventList = admin.getEvent(topicName, offset, length);

            List<String> eventJsonList = new ArrayList<>();
            for (CloudEvent event : eventList) {
                byte[] serializedEvent = Objects.requireNonNull(EventFormatProvider
                    .getInstance()
                    .resolveFormat(JsonFormat.CONTENT_TYPE))
                    .serialize(event);
                eventJsonList.add(new String(serializedEvent, StandardCharsets.UTF_8));
            }
            String result = JsonUtils.toJSONString(eventJsonList);
            httpExchange.sendResponseHeaders(200, Objects.requireNonNull(result).getBytes(Constants.DEFAULT_CHARSET).length);
            out.write(result.getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            try (OutputStream out = httpExchange.getResponseBody()) {
                StringWriter writer = new StringWriter();
                PrintWriter printWriter = new PrintWriter(writer);
                e.printStackTrace(printWriter);
                printWriter.flush();
                String stackTrace = writer.toString();

                Error error = new Error(e.toString(), stackTrace);
                String result = JsonUtils.toJSONString(error);
                httpExchange.sendResponseHeaders(500, Objects.requireNonNull(result).getBytes(Constants.DEFAULT_CHARSET).length);
                out.write(result.getBytes(Constants.DEFAULT_CHARSET));
            } catch (IOException ioe) {
                log.warn("out close failed...", ioe);
            }
        }
    }

    /**
     * Handles the POST request for {@code /event}.
     * <p>
     * This method creates an event based on the request data, then returns {@code 200 OK}.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    void post(HttpExchange httpExchange) {
        httpExchange.getResponseHeaders().add(EventMeshConstants.CONTENT_TYPE, EventMeshConstants.APPLICATION_JSON);
        httpExchange.getResponseHeaders().add(EventMeshConstants.HANDLER_ORIGIN, "*");

        try {
            String request = HttpExchangeUtils.streamToString(httpExchange.getRequestBody());
            byte[] rawRequest = request.getBytes(StandardCharsets.UTF_8);
            CloudEvent event = Objects.requireNonNull(EventFormatProvider
                .getInstance()
                .resolveFormat(JsonFormat.CONTENT_TYPE)).deserialize(rawRequest);
            admin.publish(event);
            httpExchange.sendResponseHeaders(200, 0);
        } catch (Exception e) {
            try (OutputStream out = httpExchange.getResponseBody()) {
                StringWriter writer = new StringWriter();
                PrintWriter printWriter = new PrintWriter(writer);
                e.printStackTrace(printWriter);
                printWriter.flush();
                String stackTrace = writer.toString();

                Error error = new Error(e.toString(), stackTrace);
                String result = JsonUtils.toJSONString(error);
                httpExchange.sendResponseHeaders(500, Objects.requireNonNull(result).getBytes(Constants.DEFAULT_CHARSET).length);
                out.write(result.getBytes(Constants.DEFAULT_CHARSET));
            } catch (IOException ioe) {
                log.warn("out close failed...", ioe);
            }
        }
    }

    /**
     * Handles the HTTP requests for {@code /event}.
     * <p>
     * It delegates the handling to {@code preflight()}, {@code get()} or {@code post()} methods
     * based on the request method type (OPTIONS, GET or POST).
     * <p>
     * This method is an implementation of {@linkplain com.sun.net.httpserver.HttpHandler#handle(HttpExchange)  HttpHandler.handle()}.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        switch (HttpMethod.valueOf(httpExchange.getRequestMethod())) {
            case OPTIONS:
                preflight(httpExchange);
                break;
            case POST:
                post(httpExchange);
                break;
            case GET:
                get(httpExchange);
                break;
            default:
                break;
        }
    }
}
