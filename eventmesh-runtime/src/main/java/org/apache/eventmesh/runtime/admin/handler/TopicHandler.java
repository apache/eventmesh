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
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.admin.controller.HttpHandlerManager;
import org.apache.eventmesh.runtime.admin.request.CreateTopicRequest;
import org.apache.eventmesh.runtime.admin.request.DeleteTopicRequest;
import org.apache.eventmesh.runtime.admin.response.Error;
import org.apache.eventmesh.runtime.admin.utils.HttpExchangeUtils;
import org.apache.eventmesh.runtime.common.EventHttpHandler;
import org.apache.eventmesh.runtime.core.plugin.MQAdminWrapper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;


import com.sun.net.httpserver.HttpExchange;

import lombok.extern.slf4j.Slf4j;

/**
 * The topic handler
 */
@Slf4j
@EventHttpHandler(path = "/topic")
public class TopicHandler extends AbstractHttpHandler {

    private final MQAdminWrapper admin;

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
     * OPTIONS /topic
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

    /**
     * GET /topic
     * Return a response that contains the list of topics
     */
    void get(HttpExchange httpExchange) throws IOException {
        OutputStream out = httpExchange.getResponseBody();
        httpExchange.getResponseHeaders().add("Content-Type", "application/json");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        try {
            List<TopicProperties> topicList = admin.getTopic();
            String result = JsonUtils.toJSONString(topicList);
            httpExchange.sendResponseHeaders(200, result.getBytes(Constants.DEFAULT_CHARSET).length);
            out.write(result.getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            StringWriter writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            e.printStackTrace(printWriter);
            printWriter.flush();
            String stackTrace = writer.toString();

            Error error = new Error(e.toString(), stackTrace);
            String result = JsonUtils.toJSONString(error);
            httpExchange.sendResponseHeaders(500, result.getBytes(Constants.DEFAULT_CHARSET).length);
            out.write(result.getBytes(Constants.DEFAULT_CHARSET));
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    log.warn("out close failed...", e);
                }
            }
        }
    }

    /**
     * POST /topic
     * Create a topic if it doesn't exist
     */
    void post(HttpExchange httpExchange) throws IOException {
        OutputStream out = httpExchange.getResponseBody();
        httpExchange.getResponseHeaders().add("Content-Type", "application/json");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        try {
            String request = HttpExchangeUtils.streamToString(httpExchange.getRequestBody());
            CreateTopicRequest createTopicRequest = JsonUtils.parseObject(request, CreateTopicRequest.class);
            String topicName = createTopicRequest.getName();
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
            httpExchange.sendResponseHeaders(500, result.getBytes(Constants.DEFAULT_CHARSET).length);
            out.write(result.getBytes(Constants.DEFAULT_CHARSET));
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    log.warn("out close failed...", e);
                }
            }
        }
    }

    /**
     * DELETE /topic
     * Delete a topic if it exists
     */
    void delete(HttpExchange httpExchange) throws IOException {
        OutputStream out = httpExchange.getResponseBody();
        httpExchange.getResponseHeaders().add("Content-Type", "application/json");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        try {
            String request = HttpExchangeUtils.streamToString(httpExchange.getRequestBody());
            DeleteTopicRequest deleteTopicRequest = JsonUtils.parseObject(request, DeleteTopicRequest.class);
            String topicName = deleteTopicRequest.getName();
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
            httpExchange.sendResponseHeaders(500, result.getBytes(Constants.DEFAULT_CHARSET).length);
            out.write(result.getBytes(Constants.DEFAULT_CHARSET));
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    log.warn("out close failed...", e);
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
        if (httpExchange.getRequestMethod().equals("DELETE")) {
            delete(httpExchange);
        }
        if (httpExchange.getRequestMethod().equals("GET")) {
            get(httpExchange);
        }
    }
}
