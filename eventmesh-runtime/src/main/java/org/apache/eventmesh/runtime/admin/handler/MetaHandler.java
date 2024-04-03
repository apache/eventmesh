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

import org.apache.eventmesh.api.meta.dto.EventMeshDataInfo;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.admin.response.GetRegistryResponse;
import org.apache.eventmesh.runtime.common.EventMeshHttpHandler;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.meta.MetaStorage;
import org.apache.eventmesh.runtime.util.HttpResponseUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * This class handles the {@code /registry} endpoint, corresponding to the {@code eventmesh-dashboard} path {@code /registry}.
 * <p>
 * This handler is responsible for retrieving a list of EventMesh clusters from the {@link MetaStorage} object, encapsulate them into a list of {@link
 * GetRegistryResponse} objects, and sort them by {@code EventMeshClusterName}.
 *
 * @see AbstractHttpHandler
 */

@Slf4j
@EventMeshHttpHandler(path = "/meta")
public class MetaHandler extends AbstractHttpHandler {

    private final MetaStorage eventMeshMetaStorage;

    /**
     * @param eventMeshMetaStorage The {@link MetaStorage} instance used for retrieving EventMesh cluster information.
     */
    public MetaHandler(MetaStorage eventMeshMetaStorage) {
        super();
        this.eventMeshMetaStorage = eventMeshMetaStorage;
    }

    @Override
    protected void get(HttpRequest httpRequest, ChannelHandlerContext ctx) throws IOException {
        HttpHeaders responseHeaders = new DefaultHttpHeaders();
        responseHeaders.add(EventMeshConstants.CONTENT_TYPE, EventMeshConstants.APPLICATION_JSON);
        responseHeaders.add(EventMeshConstants.HANDLER_ORIGIN, "*");
        try {
            List<GetRegistryResponse> getRegistryResponseList = new ArrayList<>();
            List<EventMeshDataInfo> eventMeshDataInfos = eventMeshMetaStorage.findAllEventMeshInfo();
            for (EventMeshDataInfo eventMeshDataInfo : eventMeshDataInfos) {
                GetRegistryResponse getRegistryResponse = new GetRegistryResponse(
                    eventMeshDataInfo.getEventMeshClusterName(),
                    eventMeshDataInfo.getEventMeshName(),
                    eventMeshDataInfo.getEndpoint(),
                    eventMeshDataInfo.getLastUpdateTimestamp(),
                    eventMeshDataInfo.getMetadata().toString());
                getRegistryResponseList.add(getRegistryResponse);
            }
            getRegistryResponseList.sort(Comparator.comparing(GetRegistryResponse::getEventMeshClusterName));
            String result = JsonUtils.toJSONString(getRegistryResponseList);
            HttpResponse httpResponse =
                HttpResponseUtils.getHttpResponse(Objects.requireNonNull(result).getBytes(Constants.DEFAULT_CHARSET), ctx, responseHeaders,
                    HttpResponseStatus.OK);
            write(ctx, httpResponse);
        } catch (NullPointerException e) {
            // registry not initialized, return empty list
            String result = JsonUtils.toJSONString(new ArrayList<>());
            HttpResponse httpResponse =
                HttpResponseUtils.getHttpResponse(Objects.requireNonNull(result).getBytes(Constants.DEFAULT_CHARSET), ctx, responseHeaders,
                    HttpResponseStatus.OK);
            write(ctx, httpResponse);
        }
    }
}
