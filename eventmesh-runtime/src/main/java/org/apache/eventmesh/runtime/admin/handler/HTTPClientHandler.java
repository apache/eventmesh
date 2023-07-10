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
import org.apache.eventmesh.runtime.admin.request.DeleteHTTPClientRequest;
import org.apache.eventmesh.runtime.admin.response.Error;
import org.apache.eventmesh.runtime.admin.response.GetClientResponse;
import org.apache.eventmesh.runtime.admin.utils.HttpExchangeUtils;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.common.EventHttpHandler;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.Client;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;


import com.sun.net.httpserver.HttpExchange;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the {@code /client/http} endpoint,
 * corresponding to the {@code eventmesh-dashboard} path {@code /http}.
 * <p>
 * It is responsible for managing operations on HTTP clients,
 * including retrieving the information list of connected HTTP clients
 * and deleting HTTP clients by disconnecting their connections based on the provided host and port.
 *
 * @see AbstractHttpHandler
 */

@Slf4j
@EventHttpHandler(path = "/client/http")
public class HTTPClientHandler extends AbstractHttpHandler {

    private final EventMeshHTTPServer eventMeshHTTPServer;

    /**
     * Constructs a new instance with the provided server instance and HTTP handler manager.
     *
     * @param eventMeshHTTPServer  the HTTP server instance of EventMesh
     * @param httpHandlerManager  Manages the registration of {@linkplain com.sun.net.httpserver.HttpHandler HttpHandler}
     *                            for an {@link com.sun.net.httpserver.HttpServer HttpServer}.
     */
    public HTTPClientHandler(
        EventMeshHTTPServer eventMeshHTTPServer, HttpHandlerManager httpHandlerManager
    ) {
        super(httpHandlerManager);
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    /**
     * Handles the OPTIONS request first for {@code /client/http}.
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
     * Handles the DELETE request for {@code /client/http}.
     * <p>
     * This method deletes a connected HTTP client by disconnecting their connections
     * based on the provided host and port, then returns {@code 200 OK}.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    void delete(HttpExchange httpExchange) throws IOException {
        try (OutputStream out = httpExchange.getResponseBody()) {
            String request = HttpExchangeUtils.streamToString(httpExchange.getRequestBody());
            DeleteHTTPClientRequest deleteHTTPClientRequest = JsonUtils.parseObject(request, DeleteHTTPClientRequest.class);
            String url = Objects.requireNonNull(deleteHTTPClientRequest).getUrl();

            for (List<Client> clientList : eventMeshHTTPServer.getSubscriptionManager().getLocalClientInfoMapping().values()) {
                clientList.removeIf(client -> Objects.equals(client.getUrl(), url));
            }

            httpExchange.getResponseHeaders().add(EventMeshConstants.HANDLER_ORIGIN, "*");
            httpExchange.sendResponseHeaders(200, 0);
        } catch (Exception e) {
            StringWriter writer = new StringWriter();
            PrintWriter printWriter = new PrintWriter(writer);
            e.printStackTrace(printWriter);
            printWriter.flush();
            String stackTrace = writer.toString();

            Error error = new Error(e.toString(), stackTrace);
            String result = JsonUtils.toJSONString(error);
            httpExchange.sendResponseHeaders(500, 0);
            log.error(result, e);
        }
    }

    /**
     * Handles the GET request for {@code /client/http}.
     * <p>
     * This method retrieves the list of connected HTTP clients and returns it as a JSON response.
     *
     * @param httpExchange the exchange containing the request from the client and used to send the response
     * @throws IOException if an I/O error occurs while handling the request
     */
    void list(HttpExchange httpExchange) throws IOException {
        OutputStream out = httpExchange.getResponseBody();
        // Set the response headers
        httpExchange.getResponseHeaders().add(EventMeshConstants.CONTENT_TYPE, EventMeshConstants.APPLICATION_JSON);
        httpExchange.getResponseHeaders().add(EventMeshConstants.HANDLER_ORIGIN, "*");

        try {
            // Get the list of HTTP clients
            List<GetClientResponse> getClientResponseList = new ArrayList<>();

            for (List<Client> clientList : eventMeshHTTPServer.getSubscriptionManager().getLocalClientInfoMapping().values()) {
                // Convert each Client object to GetClientResponse and add to getClientResponseList
                for (Client client : clientList) {
                    GetClientResponse getClientResponse = new GetClientResponse(
                        Optional.ofNullable(client.getEnv()).orElse(""),
                        Optional.ofNullable(client.getSys()).orElse(""),
                        Optional.ofNullable(client.getUrl()).orElse(""),
                        "0",
                        Optional.ofNullable(client.getHostname()).orElse(""),
                        0,
                        Optional.ofNullable(client.getApiVersion()).orElse(""),
                        Optional.ofNullable(client.getIdc()).orElse(""),
                        Optional.ofNullable(client.getConsumerGroup()).orElse(""),
                        "",
                        EventMeshConstants.PROTOCOL_HTTP.toUpperCase()

                    );
                    getClientResponseList.add(getClientResponse);
                }
            }

            // Sort the getClientResponseList by host and port
            getClientResponseList.sort((lhs, rhs) -> {
                if (lhs.getHost().equals(rhs.getHost())) {
                    return lhs.getHost().compareTo(rhs.getHost());
                }
                return Integer.compare(rhs.getPort(), lhs.getPort());
            });

            // Convert getClientResponseList to JSON and send the response
            String result = JsonUtils.toJSONString(getClientResponseList);
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
     * Handles the HTTP requests for {@code /client/http}.
     * <p>
     * It delegates the handling to {@code preflight()}, {@code list()} or {@code delete()} methods
     * based on the request method type (OPTIONS, GET or DELETE).
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
            case GET:
                list(httpExchange);
                break;
            case DELETE:
                delete(httpExchange);
                break;
            default:
                break;
        }
    }
}
