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

import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.runtime.admin.request.DeleteClientRequest;
import org.apache.eventmesh.runtime.admin.response.Error;
import org.apache.eventmesh.runtime.admin.response.GetClientResponse;
import org.apache.eventmesh.runtime.admin.utils.HttpExchangeUtils;
import org.apache.eventmesh.runtime.admin.utils.JsonUtils;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.ConsumerManager;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupClient;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.Client;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcp2Client;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.group.ClientSessionGroupMapping;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * The client handler
 */
public class ClientHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    private final EventMeshTCPServer eventMeshTCPServer;
    private final EventMeshHTTPServer eventMeshHTTPServer;
    private final EventMeshGrpcServer eventMeshGrpcServer;

    public ClientHandler(
            EventMeshTCPServer eventMeshTCPServer,
            EventMeshHTTPServer eventMeshHTTPServer,
            EventMeshGrpcServer eventMeshGrpcServer
    ) {
        this.eventMeshTCPServer = eventMeshTCPServer;
        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.eventMeshGrpcServer = eventMeshGrpcServer;
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
     * DELETE /client
     */
    void delete(HttpExchange httpExchange) throws IOException {
        OutputStream out = httpExchange.getResponseBody();
        try {
            String request = HttpExchangeUtils.streamToString(httpExchange.getRequestBody());
            DeleteClientRequest deleteClientRequest = JsonUtils.toObject(request, DeleteClientRequest.class);
            String host = deleteClientRequest.host;
            int port = deleteClientRequest.port;
            String protocol = deleteClientRequest.protocol;
            String url = deleteClientRequest.url;

            if (Objects.equals(protocol, "TCP")) {
                ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();
                ConcurrentHashMap<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
                if (!sessionMap.isEmpty()) {
                    for (Map.Entry<InetSocketAddress, Session> entry : sessionMap.entrySet()) {
                        if (entry.getKey().getHostString().equals(host) && entry.getKey().getPort() == port) {
                            EventMeshTcp2Client.serverGoodby2Client(
                                    eventMeshTCPServer,
                                    entry.getValue(),
                                    clientSessionGroupMapping
                            );
                        }
                    }
                }
            }

            if (Objects.equals(protocol, "HTTP")) {
                for (List<Client> clientList : eventMeshHTTPServer.localClientInfoMapping.values()) {
                    clientList.removeIf(client -> Objects.equals(client.url, url));
                }
            }

            if (Objects.equals(protocol, "gRPC")) {
                ConsumerManager consumerManager = eventMeshGrpcServer.getConsumerManager();
                Map<String, List<ConsumerGroupClient>> clientTable = consumerManager.getClientTable();
                for (List<ConsumerGroupClient> clientList : clientTable.values()) {
                    for (ConsumerGroupClient client : clientList) {
                        if (Objects.equals(client.getUrl(), url)) {
                            consumerManager.deregisterClient(client);
                        }
                    }
                }
            }

            httpExchange.getResponseHeaders().add("Content-Type", "application/json");
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
     * GET /client
     * Return a response that contains the list of clients
     */
    void list(HttpExchange httpExchange) throws IOException {
        OutputStream out = httpExchange.getResponseBody();
        httpExchange.getResponseHeaders().add("Content-Type", "application/json");
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        try {
            // Get the list of TCP clients
            ClientSessionGroupMapping clientSessionGroupMapping = eventMeshTCPServer.getClientSessionGroupMapping();
            Map<InetSocketAddress, Session> sessionMap = clientSessionGroupMapping.getSessionMap();
            List<GetClientResponse> getClientResponseList = new ArrayList<>();
            for (Session session : sessionMap.values()) {
                UserAgent userAgent = session.getClient();
                GetClientResponse getClientResponse = new GetClientResponse(
                        Optional.ofNullable(userAgent.getEnv()).orElse(""),
                        Optional.ofNullable(userAgent.getSubsystem()).orElse(""),
                        Optional.ofNullable(userAgent.getPath()).orElse(""),
                        String.valueOf(userAgent.getPid()),
                        Optional.ofNullable(userAgent.getHost()).orElse(""),
                        userAgent.getPort(),
                        Optional.ofNullable(userAgent.getVersion()).orElse(""),
                        Optional.ofNullable(userAgent.getIdc()).orElse(""),
                        Optional.ofNullable(userAgent.getGroup()).orElse(""),
                        Optional.ofNullable(userAgent.getPurpose()).orElse(""),
                        "TCP"
                );
                getClientResponseList.add(getClientResponse);
            }

            // Get the list of HTTP clients
            for (List<Client> clientList : eventMeshHTTPServer.localClientInfoMapping.values()) {
                for (Client client : clientList) {
                    GetClientResponse getClientResponse = new GetClientResponse(
                            Optional.ofNullable(client.env).orElse(""),
                            Optional.ofNullable(client.sys).orElse(""),
                            Optional.ofNullable(client.url).orElse(""),
                            "0",
                            Optional.ofNullable(client.hostname).orElse(""),
                            0,
                            Optional.ofNullable(client.apiVersion).orElse(""),
                            Optional.ofNullable(client.idc).orElse(""),
                            Optional.ofNullable(client.consumerGroup).orElse(""),
                            "",
                            "HTTP"
                    );
                    getClientResponseList.add(getClientResponse);
                }
            }

            // Get the list of gRPC clients
            ConsumerManager consumerManager = eventMeshGrpcServer.getConsumerManager();
            Map<String, List<ConsumerGroupClient>> clientTable = consumerManager.getClientTable();
            for (List<ConsumerGroupClient> clientList : clientTable.values()) {
                for (ConsumerGroupClient client : clientList) {
                    GetClientResponse getClientResponse = new GetClientResponse(
                            Optional.ofNullable(client.env).orElse(""),
                            Optional.ofNullable(client.sys).orElse(""),
                            Optional.ofNullable(client.url).orElse(""),
                            "0",
                            Optional.ofNullable(client.hostname).orElse(""),
                            0,
                            Optional.ofNullable(client.apiVersion).orElse(""),
                            Optional.ofNullable(client.idc).orElse(""),
                            Optional.ofNullable(client.consumerGroup).orElse(""),
                            "",
                            "gRPC"
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
