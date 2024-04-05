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
import org.apache.eventmesh.runtime.admin.request.DeleteGrpcClientRequest;
import org.apache.eventmesh.runtime.admin.response.v1.GetClientResponse;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.common.EventMeshHttpHandler;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.ConsumerManager;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupClient;
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
 * This class handles the {@code /client/grpc} endpoint, corresponding to the {@code eventmesh-dashboard} path {@code /grpc}.
 * <p>
 * It is responsible for managing operations on gRPC clients, including retrieving the information list of connected gRPC clients and deleting gRPC
 * clients by disconnecting their connections based on the provided host and port.
 *
 * @see AbstractHttpHandler
 */

@Slf4j
@EventMeshHttpHandler(path = "/client/grpc")
public class GrpcClientHandler extends AbstractHttpHandler {

    private final EventMeshGrpcServer eventMeshGrpcServer;

    /**
     * Constructs a new instance with the provided server instance.
     *
     * @param eventMeshGrpcServer the gRPC server instance of EventMesh
     */
    public GrpcClientHandler(
        EventMeshGrpcServer eventMeshGrpcServer) {
        super();
        this.eventMeshGrpcServer = eventMeshGrpcServer;
    }

    @Override
    protected void delete(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        Map<String, Object> body = HttpRequestUtil.parseHttpRequestBody(httpRequest);
        Objects.requireNonNull(body, "body can not be null");
        DeleteGrpcClientRequest deleteGrpcClientRequest = JsonUtils.mapToObject(body, DeleteGrpcClientRequest.class);
        String url = Objects.requireNonNull(deleteGrpcClientRequest).getUrl();
        ConsumerManager consumerManager = eventMeshGrpcServer.getConsumerManager();
        Map<String, List<ConsumerGroupClient>> clientTable = consumerManager.getClientTable();
        // Find the client that matches the url to be deleted
        for (List<ConsumerGroupClient> clientList : clientTable.values()) {
            for (ConsumerGroupClient client : clientList) {
                if (Objects.equals(client.getUrl(), url)) {
                    // Call the deregisterClient method to close the gRPC client stream and remove it
                    consumerManager.deregisterClient(client);
                }
            }
        }
        writeText(ctx, "");
    }

    @Override
    protected void get(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        // Get the list of gRPC clients
        List<GetClientResponse> getClientResponseList = new ArrayList<>();

        ConsumerManager consumerManager = eventMeshGrpcServer.getConsumerManager();
        Map<String, List<ConsumerGroupClient>> clientTable = consumerManager.getClientTable();
        for (List<ConsumerGroupClient> clientList : clientTable.values()) {
            // Convert each Client object to GetClientResponse and add to getClientResponseList
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
                    "gRPC");
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
