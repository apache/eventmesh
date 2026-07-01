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

package org.apache.eventmesh.runtime.admin.handler.v1;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.admin.handler.AbstractHttpHandler;
import org.apache.eventmesh.runtime.admin.request.DeleteHTTPClientRequest;
import org.apache.eventmesh.runtime.admin.response.v1.GetClientResponse;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.common.EventMeshHttpHandler;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.Client;
import org.apache.eventmesh.runtime.util.HttpRequestUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the {@code /client/http} endpoint, corresponding to the {@code eventmesh-dashboard} path {@code /http}.
 * <p>
 * It is responsible for managing operations on HTTP clients, including retrieving the information list of connected HTTP clients and deleting HTTP
 * clients by disconnecting their connections based on the provided host and port.
 *
 * @see AbstractHttpHandler
 */

@Slf4j
@EventMeshHttpHandler(path = "/client/http")
public class HTTPClientHandler extends AbstractHttpHandler {

    private final EventMeshHTTPServer eventMeshHTTPServer;

    /**
     * Constructs a new instance with the provided server instance.
     *
     * @param eventMeshHTTPServer the HTTP server instance of EventMesh
     */
    public HTTPClientHandler(
        EventMeshHTTPServer eventMeshHTTPServer) {
        super();
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    protected void delete(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        Map<String, Object> body = HttpRequestUtil.parseHttpRequestBody(httpRequest);
        if (!Objects.isNull(body)) {
            DeleteHTTPClientRequest deleteHTTPClientRequest = JsonUtils.mapToObject(body, DeleteHTTPClientRequest.class);
            String url = Objects.requireNonNull(deleteHTTPClientRequest).getUrl();

            for (List<Client> clientList : eventMeshHTTPServer.getSubscriptionManager().getLocalClientInfoMapping().values()) {
                // Find the client that matches the url to be deleted
                clientList.removeIf(client -> Objects.equals(client.getUrl(), url));
            }
            // Set the response headers and send a 200 status code empty response
            writeText(ctx, "");
        }

    }

    /**
     * Handles the GET request for {@code /client/http}.
     * <p>
     * This method retrieves the list of connected HTTP clients and returns it as a JSON response.
     *
     * @throws Exception if an I/O error occurs while handling the request
     */
    protected void get(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
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
        writeJson(ctx, result);
    }
}
