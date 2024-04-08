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

import org.apache.eventmesh.common.utils.NetUtils;
import org.apache.eventmesh.runtime.admin.handler.AbstractHttpHandler;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.common.EventMeshHttpHandler;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.recommend.EventMeshRecommendImpl;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.recommend.EventMeshRecommendStrategy;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.Map;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the HTTP requests of {@code /eventMesh/recommend} endpoint, which is used to calculate and return the recommended EventMesh
 * server node to the client based on the provided {@code group} and {@code purpose} parameters.
 * <p>
 * Parameters:
 * <ul>
 *     <li>client group: {@code group} | Example: {@code EventmeshTestGroup}</li>
 *     <li>client purpose: {@code purpose} | Example: {@code sub}</li>
 * </ul>
 * It uses an {@link EventMeshRecommendStrategy} which is implemented by {@link EventMeshRecommendImpl}
 * to calculate the recommended EventMesh server node.
 *
 * @see AbstractHttpHandler
 */
@Slf4j
@EventMeshHttpHandler(path = "/eventMesh/recommend")
public class QueryRecommendEventMeshHandler extends AbstractHttpHandler {

    private final EventMeshTCPServer eventMeshTCPServer;

    /**
     * Constructs a new instance with the provided server instance.
     *
     * @param eventMeshTCPServer the TCP server instance of EventMesh
     */
    public QueryRecommendEventMeshHandler(EventMeshTCPServer eventMeshTCPServer) {
        super();
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public void handle(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        String result = "";
        if (!eventMeshTCPServer.getEventMeshTCPConfiguration().isEventMeshServerMetaStorageEnable()) {
            throw new Exception("registry enable config is false, not support");
        }
        String queryString = URI.create(httpRequest.uri()).getQuery();
        Map<String, String> queryStringInfo = NetUtils.formData2Dic(queryString);
        // Extract parameters from the query string
        String group = queryStringInfo.get(EventMeshConstants.MANAGE_GROUP);
        String purpose = queryStringInfo.get(EventMeshConstants.MANAGE_PURPOSE);
        // Check the validity of the parameters
        if (StringUtils.isBlank(group) || StringUtils.isBlank(purpose)) {
            result = "params illegal!";
            writeText(ctx, result);
            return;
        }

        EventMeshRecommendStrategy eventMeshRecommendStrategy = new EventMeshRecommendImpl(eventMeshTCPServer);
        // Calculate the recommended EventMesh node according to the given group and purpose
        String recommendEventMeshResult = eventMeshRecommendStrategy.calculateRecommendEventMesh(group, purpose);
        result = (recommendEventMeshResult == null) ? "null" : recommendEventMeshResult;
        log.info("recommend eventmesh:{},group:{},purpose:{}", result, group, purpose);
        writeText(ctx, result);
    }
}
