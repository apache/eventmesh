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
import org.apache.eventmesh.runtime.admin.response.v1.GetConfigurationResponse;
import org.apache.eventmesh.runtime.common.EventMeshHttpHandler;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the {@code /configuration} endpoint, corresponding to the {@code eventmesh-dashboard} path {@code /}.
 * <p>
 * This handler is responsible for retrieving the current configuration information of the EventMesh node, including service name, service
 * environment, and listening ports for various protocols.
 * <p>
 * TODO The path of endpoints under v1 package shall be changed to {@code /v1/configuration} with Next.js EventMesh Dashboard.
 *
 * @see AbstractHttpHandler
 */

@Slf4j
@EventMeshHttpHandler(path = "/configuration")
public class ConfigurationHandlerV1 extends AbstractHttpHandler {

    private final EventMeshTCPConfiguration eventMeshTCPConfiguration;
    private final EventMeshHTTPConfiguration eventMeshHTTPConfiguration;
    private final EventMeshGrpcConfiguration eventMeshGrpcConfiguration;

    /**
     * Constructs a new instance with the provided configurations.
     *
     * @param eventMeshTCPConfiguration  the TCP configuration for EventMesh
     * @param eventMeshHTTPConfiguration the HTTP configuration for EventMesh
     * @param eventMeshGrpcConfiguration the gRPC configuration for EventMesh
     */
    public ConfigurationHandlerV1(
        EventMeshTCPConfiguration eventMeshTCPConfiguration,
        EventMeshHTTPConfiguration eventMeshHTTPConfiguration,
        EventMeshGrpcConfiguration eventMeshGrpcConfiguration) {
        super();
        this.eventMeshTCPConfiguration = eventMeshTCPConfiguration;
        this.eventMeshHTTPConfiguration = eventMeshHTTPConfiguration;
        this.eventMeshGrpcConfiguration = eventMeshGrpcConfiguration;
    }

    @Override
    protected void get(HttpRequest httpRequest, ChannelHandlerContext ctx) throws Exception {
        GetConfigurationResponse getConfigurationResponse = new GetConfigurationResponse(
            eventMeshTCPConfiguration.getSysID(),
            eventMeshTCPConfiguration.getMetaStorageAddr(),
            eventMeshTCPConfiguration.getEventMeshEnv(),
            eventMeshTCPConfiguration.getEventMeshIDC(),
            eventMeshTCPConfiguration.getEventMeshCluster(),
            eventMeshTCPConfiguration.getEventMeshServerIp(),
            eventMeshTCPConfiguration.getEventMeshName(),
            eventMeshTCPConfiguration.getEventMeshWebhookOrigin(),
            eventMeshTCPConfiguration.isEventMeshServerSecurityEnable(),
            eventMeshTCPConfiguration.isEventMeshServerMetaStorageEnable(),
            // TCP Configuration
            eventMeshTCPConfiguration.getEventMeshTcpServerPort(),
            // HTTP Configuration
            eventMeshHTTPConfiguration.getHttpServerPort(),
            eventMeshHTTPConfiguration.isEventMeshServerUseTls(),
            // gRPC Configuration
            eventMeshGrpcConfiguration.getGrpcServerPort(),
            eventMeshGrpcConfiguration.isEventMeshServerUseTls());
        String result = JsonUtils.toJSONString(getConfigurationResponse);
        writeJson(ctx, result);
    }
}
