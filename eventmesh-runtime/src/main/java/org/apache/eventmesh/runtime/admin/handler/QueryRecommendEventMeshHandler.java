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
import org.apache.eventmesh.runtime.core.protocol.tcp.client.recommend.EventMeshRecommendImpl;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.recommend.EventMeshRecommendStrategy;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;


import com.sun.net.httpserver.HttpExchange;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the HTTP requests of {@code /eventMesh/recommend} endpoint,
 * which is used to calculate and return the recommended EventMesh server node to the client
 * based on the provided {@code group} and {@code purpose} parameters.
 * <p>
 * It uses an {@link EventMeshRecommendStrategy} which is implemented by {@link EventMeshRecommendImpl}
 * to calculate the recommended EventMesh server node.
 *
 * @see AbstractHttpHandler
 */
@Slf4j
@EventHttpHandler(path = "/eventMesh/recommend")
public class QueryRecommendEventMeshHandler extends AbstractHttpHandler {

    private final EventMeshTCPServer eventMeshTCPServer;

    /**
     * Constructs a new instance with the provided server instance and HTTP handler manager.
     *
     * @param eventMeshTCPServer  the TCP server instance of EventMesh
     * @param httpHandlerManager  Manages the registration of {@linkplain com.sun.net.httpserver.HttpHandler HttpHandler}
     *                            for an {@link com.sun.net.httpserver.HttpServer HttpServer}.
     */
    public QueryRecommendEventMeshHandler(EventMeshTCPServer eventMeshTCPServer, HttpHandlerManager httpHandlerManager) {
        super(httpHandlerManager);
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    /**
     * Handles the HTTP requests by calculating a recommended EventMesh server node.
     * <p>
     * This method is an implementation of {@linkplain com.sun.net.httpserver.HttpHandler#handle(HttpExchange)  HttpHandler.handle()}.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        String result = "";
        try (OutputStream out = httpExchange.getResponseBody()) {
            // Check whether the registry is enabled in the EventMesh TCP configuration
            if (!eventMeshTCPServer.getEventMeshTCPConfiguration().isEventMeshServerRegistryEnable()) {
                throw new Exception("registry enable config is false, not support");
            }
            // Retrieve the query string from the request URI and parses it into a key-value pair Map
            String queryString = httpExchange.getRequestURI().getQuery();
            Map<String, String> queryStringInfo = NetUtils.formData2Dic(queryString);
            // Extract the 'group' and 'purpose' parameters from the query string
            String group = queryStringInfo.get(EventMeshConstants.MANAGE_GROUP);
            String purpose = queryStringInfo.get(EventMeshConstants.MANAGE_PURPOSE);
            // If either of them is missing, respond an error message indicating that the parameters are illegal.
            if (StringUtils.isBlank(group) || StringUtils.isBlank(purpose)) {
                NetUtils.sendSuccessResponseHeaders(httpExchange);
                result = "params illegal!";
                out.write(result.getBytes(Constants.DEFAULT_CHARSET));
                return;
            }

            // If both 'group' and 'purpose' parameters are provided
            EventMeshRecommendStrategy eventMeshRecommendStrategy = new EventMeshRecommendImpl(eventMeshTCPServer);
            // Calculate the recommended EventMesh node according to the given group and purpose
            String recommendEventMeshResult = eventMeshRecommendStrategy.calculateRecommendEventMesh(group, purpose);
            // Serialize the result and write it to the response output stream to be sent back to the client
            result = (recommendEventMeshResult == null) ? "null" : recommendEventMeshResult;
            log.info("recommend eventmesh:{},group:{},purpose:{}", result, group, purpose);
            NetUtils.sendSuccessResponseHeaders(httpExchange);
            out.write(result.getBytes(Constants.DEFAULT_CHARSET));
        } catch (Exception e) {
            log.error("QueryRecommendEventMeshHandler fail...", e);
        }
    }
}
