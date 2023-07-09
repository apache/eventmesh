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

import org.apache.eventmesh.api.admin.TopicProperties;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.enums.HttpMethod;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.admin.controller.HttpHandlerManager;
import org.apache.eventmesh.runtime.admin.request.CreateTopicRequest;
import org.apache.eventmesh.runtime.admin.request.DeleteTopicRequest;
import org.apache.eventmesh.runtime.admin.response.Error;
import org.apache.eventmesh.runtime.admin.utils.HttpExchangeUtils;
import org.apache.eventmesh.runtime.common.EventHttpHandler;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.plugin.MQAdminWrapper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Objects;


import com.sun.net.httpserver.HttpExchange;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the {@code /topic} endpoint,
 * corresponding to the {@code eventmesh-dashboard} path {@code /topic},
 * including the "Create Topic" and "Remove" buttons.
 * <p>
 * It provides functionality for managing topics, including retrieving the list of topics (GET),
 * creating a new topic (POST), and deleting an existing topic (DELETE).
 * <p>
 * An instance of {@link MQAdminWrapper} is used to interact with the messaging system.
 *
 * @see AbstractHttpHandler
 * @see MQAdminWrapper
 */

@Slf4j
@EventHttpHandler(path = "/topic")
public class TopicHandler extends AbstractHttpHandler {

    private final MQAdminWrapper admin;

    /**
     * Constructs a new instance with the specified connector plugin type and HTTP handler manager.
     *
     * @param connectorPluginType The name of event storage connector plugin.
     * @param httpHandlerManager httpHandlerManager Manages the registration of {@linkplain com.sun.net.httpserver.HttpHandler HttpHandler}
     *                           for an {@link com.sun.net.httpserver.HttpServer HttpServer}.
     */
    public TopicHandler(
        String connectorPluginType,
        HttpHandlerManager httpHandlerManager
    ) {
        super(httpHandlerManager);
        admin = new MQAdminWrapper(connectorPluginType);
        try {
            admin.init(null);
        } catch (Exception ignored) {
            log.info("failed to initialize MQAdminWrapper");
        }
    }

    /**
     * Handles the OPTIONS request first for {@code /topic}.
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
     * Handles the GET request for {@code /topic}.
     * <p>
     * This method retrieves the list of topics from the {@link MQAdminWrapper} and returns it as a JSON response.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    void get(HttpExchange httpExchange) throws IOException {

        try (OutputStream out = httpExchange.getResponseBody()) {
            httpExchange.getResponseHeaders().add(EventMeshConstants.CONTENT_TYPE, EventMeshConstants.APPLICATION_JSON);
            httpExchange.getResponseHeaders().add(EventMeshConstants.HANDLER_ORIGIN, "*");
            List<TopicProperties> topicList = admin.getTopic();
            String result = JsonUtils.toJSONString(topicList);
            httpExchange.sendResponseHeaders(200, Objects.requireNonNull(result).getBytes(Constants.DEFAULT_CHARSET).length);
            out.write(result.getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            StringWriter writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            e.printStackTrace(printWriter);
            printWriter.flush();
            String stackTrace = writer.toString();

            Error error = new Error(e.toString(), stackTrace);
            String result = JsonUtils.toJSONString(error);
            httpExchange.sendResponseHeaders(500, Objects.requireNonNull(result).getBytes(Constants.DEFAULT_CHARSET).length);
            log.error(result, e);
        }
    }

    /**
     * Handles the POST request for {@code /topic}.
     * <p>
     * This method creates a topic if it doesn't exist based on the request data, then returns {@code 200 OK}.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    void post(HttpExchange httpExchange) throws IOException {

        try (OutputStream out = httpExchange.getResponseBody()) {
            httpExchange.getResponseHeaders().add(EventMeshConstants.CONTENT_TYPE, EventMeshConstants.APPLICATION_JSON);
            httpExchange.getResponseHeaders().add(EventMeshConstants.HANDLER_ORIGIN, "*");
            String request = HttpExchangeUtils.streamToString(httpExchange.getRequestBody());
            CreateTopicRequest createTopicRequest = JsonUtils.parseObject(request, CreateTopicRequest.class);
            String topicName = Objects.requireNonNull(createTopicRequest).getName();
            admin.createTopic(topicName);
            httpExchange.sendResponseHeaders(200, 0);
        } catch (Exception e) {
            StringWriter writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            e.printStackTrace(printWriter);
            printWriter.flush();
            String stackTrace = writer.toString();

            Error error = new Error(e.toString(), stackTrace);
            String result = JsonUtils.toJSONString(error);
            httpExchange.sendResponseHeaders(500, Objects.requireNonNull(result).getBytes(Constants.DEFAULT_CHARSET).length);
            log.error(result, e);
        }
    }

    /**
     * Handles the DELETE request for {@code /topic}.
     * <p>
     * This method deletes a topic if it exists based on the request data, then returns {@code 200 OK}.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    void delete(HttpExchange httpExchange) throws IOException {

        try (OutputStream out = httpExchange.getResponseBody()) {
            httpExchange.getResponseHeaders().add(EventMeshConstants.CONTENT_TYPE, EventMeshConstants.APPLICATION_JSON);
            httpExchange.getResponseHeaders().add(EventMeshConstants.HANDLER_ORIGIN, "*");
            String request = HttpExchangeUtils.streamToString(httpExchange.getRequestBody());
            DeleteTopicRequest deleteTopicRequest = JsonUtils.parseObject(request, DeleteTopicRequest.class);
            String topicName = Objects.requireNonNull(deleteTopicRequest).getName();
            admin.deleteTopic(topicName);
            httpExchange.sendResponseHeaders(200, 0);
        } catch (Exception e) {
            StringWriter writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            e.printStackTrace(printWriter);
            printWriter.flush();
            String stackTrace = writer.toString();

            Error error = new Error(e.toString(), stackTrace);
            String result = JsonUtils.toJSONString(error);
            httpExchange.sendResponseHeaders(500, Objects.requireNonNull(result).getBytes(Constants.DEFAULT_CHARSET).length);
            log.error(result, e);
        }
    }

    /**
     * Handles the HTTP requests for {@code /topic}.
     * <p>
     * It delegates the handling to {@code preflight()}, {@code get()}, {@code post()}, or {@code delete()} methods
     * based on the request method type (OPTIONS, GET, POST or DELETE).
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
            case DELETE:
                delete(httpExchange);
                break;
            case GET:
                get(httpExchange);
                break;
            default:
                break;
        }
    }
}
