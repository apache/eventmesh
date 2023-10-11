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

package org.apache.eventmesh.admin.rocketmq.handler;

import static org.apache.eventmesh.admin.rocketmq.Constants.APPLICATION_JSON;
import static org.apache.eventmesh.admin.rocketmq.Constants.CONTENT_TYPE;
import static org.apache.eventmesh.admin.rocketmq.Constants.TOPIC_ERROR;
import static org.apache.eventmesh.admin.rocketmq.Constants.TOPIC_MANAGE_PATH;

import org.apache.eventmesh.admin.rocketmq.request.TopicCreateRequest;
import org.apache.eventmesh.admin.rocketmq.response.TopicResponse;
import org.apache.eventmesh.admin.rocketmq.util.RequestMapping;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.NetUtils;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.OutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * This class is responsible for handling HTTP requests related to topics.
 * <p>
 * It implements the {@link HttpHandler} interface to process incoming requests,
 * which is used by the {@linkplain com.sun.net.httpserver.HttpServer HttpServer}.
 * <p>
 * It uses the {@link RequestMapping} class to match the request URL and the HTTP method.
 * <p>
 * It uses the {@link TopicCreateRequest} class to parse the request body
 * and the {@link TopicResponse} class to create the response.
 *
 * @see HttpHandler
 */

@Slf4j
public class TopicsHandler implements HttpHandler {

    /**
     * Handles the HTTP request for creating topics.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs
     * @see HttpHandler#handle(HttpExchange)
     */
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {

        // If the request matches, then create a new topic.
        if (RequestMapping.postMapping(TOPIC_MANAGE_PATH, httpExchange)) {
            createTopicHandler(httpExchange);
            return;
        }

        // Otherwise, it prepares an error response and sends it back to the client.
        OutputStream out = httpExchange.getResponseBody();
        httpExchange.sendResponseHeaders(500, 0);
        String result = String.format("Please check your request url: %s", httpExchange.getRequestURI());
        log.error(result);
        // Write the response data.
        out.write(result.getBytes(Constants.DEFAULT_CHARSET));
    }

    /**
     * Handles the creation of a new topic.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs
     */
    public void createTopicHandler(HttpExchange httpExchange) throws IOException {
        String result;
        try (OutputStream out = httpExchange.getResponseBody()) {
            // Parses the request body into a TopicCreateRequest object.
            String params = NetUtils.parsePostBody(httpExchange);
            TopicCreateRequest topicCreateRequest =
                JsonUtils.parseObject(params, TopicCreateRequest.class);
            // Gets the topic name from the request body.
            String topic = topicCreateRequest.getTopic();

            // If the topic name is empty, then returns an error message.
            if (StringUtils.isBlank(topic)) {
                result = "Create topic failed. Parameter topic not found.";
                log.error(result);
                out.write(result.getBytes(Constants.DEFAULT_CHARSET));
                return;
            }

            // TBD: A new rocketmq service will be implemented for creating topics
            TopicResponse topicResponse = null; // Temporary variable for topic response
            if (topicResponse != null) {
                log.info("create a new topic: {}", topic);
                httpExchange.getResponseHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
                NetUtils.sendSuccessResponseHeaders(httpExchange);
                result = JsonUtils.toJSONString(topicResponse);
                log.info(result);
                out.write(result.getBytes(Constants.DEFAULT_CHARSET));
            } else {
                // If the topic creation fails, then returns an error message.
                httpExchange.sendResponseHeaders(500, 0);
                result = TOPIC_ERROR;
                log.error(result);
                out.write(result.getBytes(Constants.DEFAULT_CHARSET));
            }
        } catch (Exception e) {
            // If an exception occurs, then returns an error message.
            httpExchange.getResponseHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
            httpExchange.sendResponseHeaders(500, 0);
            result = TOPIC_ERROR;
            log.error(result, e);
        }
    }

}
