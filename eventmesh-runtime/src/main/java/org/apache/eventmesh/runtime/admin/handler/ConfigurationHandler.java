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
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.admin.response.GetConfigurationResponse;
import org.apache.eventmesh.runtime.common.EventHttpHandler;
import org.apache.eventmesh.runtime.configuration.EventMeshGrpcConfiguration;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.util.HttpResponseUtils;

import java.util.Objects;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the {@code /configuration} endpoint, corresponding to the {@code eventmesh-dashboard} path {@code /}.
 * <p>
 * This handler is responsible for retrieving the current configuration information of the EventMesh node, including service name, service
 * environment, and listening ports for various protocols.
 *
 * @see AbstractHttpHandler
 */

@Slf4j
@EventHttpHandler(path = "/configuration")
public class ConfigurationHandler extends AbstractHttpHandler {

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
    public ConfigurationHandler(
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
        HttpHeaders responseHeaders = new DefaultHttpHeaders();
        responseHeaders.add(EventMeshConstants.CONTENT_TYPE, EventMeshConstants.APPLICATION_JSON);
        responseHeaders.add(EventMeshConstants.HANDLER_ORIGIN, "*");
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
        HttpResponse httpResponse =
            HttpResponseUtils.getHttpResponse(Objects.requireNonNull(result).getBytes(Constants.DEFAULT_CHARSET), ctx, responseHeaders,
                HttpResponseStatus.OK);
        write(ctx, httpResponse);
    }
}
