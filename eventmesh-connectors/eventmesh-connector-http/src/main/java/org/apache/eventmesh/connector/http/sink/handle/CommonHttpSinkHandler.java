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

package org.apache.eventmesh.connector.http.sink.handle;

import org.apache.eventmesh.connector.http.sink.config.SinkConnectorConfig;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import lombok.extern.slf4j.Slf4j;

/**
 * Common HttpSinkHandler to handle ConnectRecord over HTTP/HTTPS
 */
@Slf4j
public class CommonHttpSinkHandler implements HttpSinkHandler {

    private final SinkConnectorConfig connectorConfig;

    private WebClient webClient;

    public CommonHttpSinkHandler(SinkConnectorConfig sinkConnectorConfig) {
        this.connectorConfig = sinkConnectorConfig;
    }

    @Override
    public void start() {
        // Create WebClient
        doInitWebClient();
    }

    private void doInitWebClient() {
        final Vertx vertx = Vertx.vertx();
        // TODO add more configurations
        WebClientOptions options = new WebClientOptions()
            .setDefaultHost(this.connectorConfig.getHost())
            .setDefaultPort(this.connectorConfig.getPort())
            .setSsl(this.connectorConfig.isSsl())
            .setIdleTimeout(this.connectorConfig.getIdleTimeout())
            .setIdleTimeoutUnit(TimeUnit.MILLISECONDS);

        this.webClient = WebClient.create(vertx, options);
    }


    @Override
    public void handle(ConnectRecord record) {
        this.webClient.post(this.connectorConfig.getPath())
            .putHeader("Content-Type", "application/json; charset=utf-8")
            .sendJson(record)
            .onComplete(ar -> {
                Long timestamp = record.getTimestamp();
                Map<String, ?> offset = record.getPosition().getOffset().getOffset();
                if (ar.succeeded()) {
                    log.info("Request sent successfully. Record: timestamp={}, offset={}", timestamp, offset);
                    // Determine whether the status code is 200
                    if (ar.result().statusCode() != HttpResponseStatus.OK.code()) {
                        log.error("Unexpected response received. Record: timestamp={}, offset={}. Response: code={} header={}, body={}",
                            timestamp,
                            offset,
                            ar.result().statusCode(),
                            ar.result().headers(),
                            ar.result().bodyAsString()
                        );
                    }
                } else {
                    // This branch is only entered if an error occurs at the network layer
                    log.error("Request failed to send. Record: timestamp={}, offset={}", timestamp, offset, ar.cause());
                }
            });
    }

    @Override
    public void stop() {
        if (this.webClient != null) {
            this.webClient.close();
        } else {
            log.warn("WebClient is null, ignore.");
        }
    }
}
