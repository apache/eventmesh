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

import org.apache.eventmesh.runtime.admin.request.DeleteHTTPClientRequest;
import org.apache.eventmesh.runtime.admin.response.Error;
import org.apache.eventmesh.runtime.admin.response.GetClientResponse;
import org.apache.eventmesh.runtime.admin.utils.HttpExchangeUtils;
import org.apache.eventmesh.runtime.admin.utils.JsonUtils;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.Client;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * The client handler
 */
public class HTTPClientHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(HTTPClientHandler.class);

    private final EventMeshHTTPServer eventMeshHTTPServer;

    public HTTPClientHandler(
            EventMeshHTTPServer eventMeshHTTPServer
    ) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    /**
     * OPTIONS /client
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
     * DELETE /client/http
     */
    void delete(HttpExchange httpExchange) throws IOException {
        OutputStream out = httpExchange.getResponseBody();
        try {
            String request = HttpExchangeUtils.streamToString(httpExchange.getRequestBody());
            DeleteHTTPClientRequest deleteHTTPClientRequest = JsonUtils.toObject(request, DeleteHTTPClientRequest.class);
            String url = deleteHTTPClientRequest.url;

            for (List<Client> clientList : eventMeshHTTPServer.getSubscriptionManager().getLocalClientInfoMapping().values()) {
                clientList.removeIf(client -> Objects.equals(client.getUrl(), url));
            }

            httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
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

    /**
     * GET /client/http
     * Return a response that contains the list of clients
     */
    void list(HttpExchange httpExchange) throws IOException {
        OutputStream out = httpExchange.getResponseBody();
        httpExchange.getResponseHeaders().add("Content-Type", "application/json");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        try {
            // Get the list of HTTP clients
            List<GetClientResponse> getClientResponseList = new ArrayList<>();

            for (List<Client> clientList : eventMeshHTTPServer.getSubscriptionManager().getLocalClientInfoMapping().values()) {
                for (Client client : clientList) {
                    GetClientResponse getClientResponse = new GetClientResponse(
                            Optional.ofNullable(client.getEnv()).orElseGet(() -> ""),
                            Optional.ofNullable(client.getSys()).orElseGet(() -> ""),
                            Optional.ofNullable(client.getUrl()).orElseGet(() -> ""),
                            "0",
                            Optional.ofNullable(client.getHostname()).orElseGet(() -> ""),
                            0,
                            Optional.ofNullable(client.getApiVersion()).orElseGet(() -> ""),
                            Optional.ofNullable(client.getIdc()).orElseGet(() -> ""),
                            Optional.ofNullable(client.getConsumerGroup()).orElseGet(() -> ""),
                            "",
                            "HTTP"

                    );
                    getClientResponseList.add(getClientResponse);
                }
            }

            getClientResponseList.sort((lhs, rhs) -> {
                if (lhs.host.equals(rhs.host)) {
                    return lhs.host.compareTo(rhs.host);
                }
                return Integer.compare(rhs.port, lhs.port);
            });

            String result = JsonUtils.toJson(getClientResponseList);
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

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        if (httpExchange.getRequestMethod().equals("OPTIONS")) {
            preflight(httpExchange);
        }
        if (httpExchange.getRequestMethod().equals("GET")) {
            list(httpExchange);
        }
        if (httpExchange.getRequestMethod().equals("DELETE")) {
            delete(httpExchange);
        }
    }
}
