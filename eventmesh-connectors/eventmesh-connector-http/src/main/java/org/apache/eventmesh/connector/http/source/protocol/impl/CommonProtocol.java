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

package org.apache.eventmesh.connector.http.source.protocol.impl;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.config.connector.http.SourceConnectorConfig;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.http.common.SynchronizedCircularFifoQueue;
import org.apache.eventmesh.connector.http.source.data.CommonResponse;
import org.apache.eventmesh.connector.http.source.data.WebhookRequest;
import org.apache.eventmesh.connector.http.source.protocol.Protocol;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.handler.BodyHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * Common Protocol. This class represents the common webhook protocol. The processing method of this class does not perform any other operations
 * except storing the request and returning a general response.
 */
@Slf4j
public class CommonProtocol implements Protocol {

    public static final String PROTOCOL_NAME = "Common";

    private SourceConnectorConfig sourceConnectorConfig;

    /**
     * Initialize the protocol
     *
     * @param sourceConnectorConfig source connector config
     */
    @Override
    public void initialize(SourceConnectorConfig sourceConnectorConfig) {
        this.sourceConnectorConfig = sourceConnectorConfig;
    }

    /**
     * Set the handler for the route
     *
     * @param route route
     * @param queue queue info
     */
    @Override
    public void setHandler(Route route, SynchronizedCircularFifoQueue<Object> queue) {
        route.method(HttpMethod.POST)
            .handler(BodyHandler.create())
            .handler(ctx -> {
                // Get the payload
                Object payload = ctx.body().asString(Constants.DEFAULT_CHARSET.toString());
                payload = JsonUtils.parseObject(payload.toString(), String.class);

                // Create and store the webhook request
                Map<String, String> headerMap = ctx.request().headers().entries().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                WebhookRequest webhookRequest = new WebhookRequest(PROTOCOL_NAME, ctx.request().absoluteURI(), headerMap, payload, ctx);
                if (!queue.offer(webhookRequest)) {
                    throw new IllegalStateException("Failed to store the request.");
                }

                if (!sourceConnectorConfig.isDataConsistencyEnabled()) {
                    // Return 200 OK
                    ctx.response()
                        .setStatusCode(HttpResponseStatus.OK.code())
                        .end(CommonResponse.success().toJsonStr());
                }

            })
            .failureHandler(ctx -> {
                log.error("Failed to handle the request. ", ctx.failure());

                // Return Bad Response
                ctx.response()
                    .setStatusCode(ctx.statusCode())
                    .end(CommonResponse.base(ctx.failure().getMessage()).toJsonStr());
            });

    }

    /**
     * Convert the message to a connect record
     *
     * @param message message
     * @return connect record
     */
    @Override
    public ConnectRecord convertToConnectRecord(Object message) {
        WebhookRequest request = (WebhookRequest) message;
        ConnectRecord connectRecord = new ConnectRecord(null, null, System.currentTimeMillis(), request.getPayload());
        connectRecord.addExtension("source", request.getProtocolName());
        connectRecord.addExtension("url", request.getUrl());
        request.getHeaders().forEach((k, v) -> {
            if (k.equalsIgnoreCase("extension")) {
                JsonObject extension = new JsonObject(v);
                extension.forEach(e -> connectRecord.addExtension(e.getKey(), e.getValue()));
            }
        });
        // check recordUniqueId
        if (!connectRecord.getExtensions().containsKey("recordUniqueId")) {
            connectRecord.addExtension("recordUniqueId", connectRecord.getRecordId());
        }

        // check data
        if (connectRecord.getExtensionObj("isBase64") != null) {
            if (Boolean.parseBoolean(connectRecord.getExtensionObj("isBase64").toString())) {
                byte[] data = Base64.getDecoder().decode(connectRecord.getData().toString());
                connectRecord.setData(data);
            }
        }
        if (request.getRoutingContext() != null) {
            connectRecord.addExtension("routingContext", request.getRoutingContext());
        }
        return connectRecord;
    }
}
