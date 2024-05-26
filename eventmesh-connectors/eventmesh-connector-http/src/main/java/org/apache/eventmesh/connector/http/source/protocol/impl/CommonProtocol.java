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
import org.apache.eventmesh.connector.http.common.BoundedConcurrentQueue;
import org.apache.eventmesh.connector.http.source.config.SourceConnectorConfig;
import org.apache.eventmesh.connector.http.source.data.WebhookRequest;
import org.apache.eventmesh.connector.http.source.data.WebhookResponse;
import org.apache.eventmesh.connector.http.source.protocol.Protocol;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.handler.BodyHandler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter.Feature;

import lombok.extern.slf4j.Slf4j;

/**
 * Common Protocol.
 * This class represents the common webhook protocol. The processing method of this class does not perform any other operations
 * except storing the request and returning a general response.
 */
@Slf4j
public class CommonProtocol implements Protocol {

    public static final String PROTOCOL_NAME = "Common";

    /**
     * Initialize the protocol
     *
     * @param sourceConnectorConfig source connector config
     */
    @Override
    public void initialize(SourceConnectorConfig sourceConnectorConfig) {

    }

    /**
     * Set the handler for the route
     *
     * @param route     route
     * @param boundedQueue queue info
     */
    @Override
    public void setHandler(Route route, BoundedConcurrentQueue<Object> boundedQueue) {
        route.method(HttpMethod.POST)
            .handler(BodyHandler.create())
            .handler(ctx -> {
                // Get the payload
                String payload = ctx.body().asString(Constants.DEFAULT_CHARSET.toString());

                // Create and store the webhook request
                Map<String, String> headerMap = new HashMap<>();
                ctx.request().headers().forEach(header -> headerMap.put(header.getKey(), header.getValue()));

                WebhookRequest webhookRequest = WebhookRequest.builder()
                    .protocolName(PROTOCOL_NAME)
                    .url(ctx.request().absoluteURI())
                    .headers(headerMap)
                    .payload(payload)
                    .build();

                // Add the webhook request to the queue, thread-safe
                boundedQueue.offerWithReplace(webhookRequest);

                // Return 200 OK
                WebhookResponse response = WebhookResponse.builder()
                    .msg("success")
                    .handleTime(LocalDateTime.now())
                    .build();

                ctx.response().setStatusCode(HttpResponseStatus.OK.code())
                    .send(JSON.toJSONString(response, Feature.WriteMapNullValue));

            })
            .failureHandler(ctx -> {
                log.error("Failed to handle the request from github.", ctx.failure());

                WebhookResponse response = WebhookResponse.builder()
                    .msg(ctx.failure().getMessage())
                    .handleTime(LocalDateTime.now())
                    .build();

                // Return Bad Response
                ctx.response().setStatusCode(ctx.statusCode())
                    .send(JSON.toJSONString(response, Feature.WriteMapNullValue));
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
        return ((WebhookRequest) message).convertToConnectRecord();
    }
}
