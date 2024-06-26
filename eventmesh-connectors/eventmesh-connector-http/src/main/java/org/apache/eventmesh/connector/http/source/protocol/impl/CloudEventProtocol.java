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

import org.apache.eventmesh.common.config.connector.http.SourceConnectorConfig;
import org.apache.eventmesh.connector.http.common.SynchronizedCircularFifoQueue;
import org.apache.eventmesh.connector.http.source.data.CommonResponse;
import org.apache.eventmesh.connector.http.source.protocol.Protocol;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.CloudEventUtil;

import io.cloudevents.CloudEvent;
import io.cloudevents.http.vertx.VertxMessageFactory;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;

import lombok.extern.slf4j.Slf4j;

/**
 * CloudEvent Protocol.
 */
@Slf4j
public class CloudEventProtocol implements Protocol {

    // Protocol name
    public static final String PROTOCOL_NAME = "CloudEvent";


    /**
     * Initialize the protocol.
     *
     * @param sourceConnectorConfig source connector config
     */
    @Override
    public void initialize(SourceConnectorConfig sourceConnectorConfig) {

    }


    /**
     * Handle the protocol message for CloudEvent.
     *
     * @param route     route
     * @param queue queue info
     */
    @Override
    public void setHandler(Route route, SynchronizedCircularFifoQueue<Object> queue) {
        route.method(HttpMethod.POST)
            .handler(ctx -> VertxMessageFactory.createReader(ctx.request())
                .map(reader -> {
                    CloudEvent event = reader.toEvent();
                    if (event.getSubject() == null) {
                        throw new IllegalStateException("attribute 'subject' cannot be null");
                    }
                    if (event.getDataContentType() == null) {
                        throw new IllegalStateException("attribute 'datacontenttype' cannot be null");
                    }
                    if (event.getData() == null) {
                        throw new IllegalStateException("attribute 'data' cannot be null");
                    }
                    return event;
                })
                .onSuccess(event -> {
                    // Add the event to the queue, thread-safe
                    if (!queue.offer(event)) {
                        throw new IllegalStateException("Failed to store the request.");
                    }
                    log.info("[HttpSourceConnector] Succeed to convert payload into CloudEvent. StatusCode={}", HttpResponseStatus.OK.code());
                    ctx.response()
                        .setStatusCode(HttpResponseStatus.OK.code())
                        .end(CommonResponse.success().toJsonStr());
                })
                .onFailure(t -> {
                    log.error("[HttpSourceConnector] Malformed request. StatusCode={}", HttpResponseStatus.BAD_REQUEST.code(), t);
                    ctx.response()
                        .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                        .end(CommonResponse.base(t.getMessage()).toJsonStr());
                }));
    }

    /**
     * Convert the message to ConnectRecord.
     *
     * @param message message
     * @return ConnectRecord
     */
    @Override
    public ConnectRecord convertToConnectRecord(Object message) {
        return CloudEventUtil.convertEventToRecord((CloudEvent) message);
    }
}
