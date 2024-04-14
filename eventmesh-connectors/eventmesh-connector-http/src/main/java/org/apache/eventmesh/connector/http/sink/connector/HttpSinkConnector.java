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

package org.apache.eventmesh.connector.http.sink.connector;

import org.apache.eventmesh.connector.http.sink.config.HttpSinkConfig;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.List;
import java.util.Objects;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class HttpSinkConnector implements Sink {

    private HttpSinkConfig httpSinkConfig;

    private WebClient webClient;

    private volatile boolean isRunning = false;

    @Override
    public Class<? extends Config> configClass() {
        return HttpSinkConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        httpSinkConfig = (HttpSinkConfig) config;
        doInit();
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        this.httpSinkConfig = (HttpSinkConfig) sinkConnectorContext.getSinkConfig();
        doInit();
    }

    @SneakyThrows
    private void doInit() {
        final Vertx vertx = Vertx.vertx();
        // TODO Add more configurations
        WebClientOptions options = new WebClientOptions()
            .setDefaultHost(this.httpSinkConfig.connectorConfig.getHost())
            .setDefaultPort(this.httpSinkConfig.connectorConfig.getPort())
            .setSsl(this.httpSinkConfig.connectorConfig.isSsl())
            .setIdleTimeout(this.httpSinkConfig.connectorConfig.getIdleTimeout());
        this.webClient = WebClient.create(vertx, options);
    }

    @Override
    public void start() throws Exception {
        this.isRunning = true;
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.httpSinkConfig.connectorConfig.getConnectorName();
    }

    @Override
    public void stop() throws Exception {
        this.isRunning = false;
        this.webClient.close();
    }

    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        for (ConnectRecord sinkRecord : sinkRecords) {
            try {
                if (Objects.isNull(sinkRecord)) {
                    log.warn("ConnectRecord data is null, ignore.");
                    continue;
                }
                sendMessage(sinkRecord);
            } catch (Exception e) {
                log.error("Failed to sink message via HTTP. ", e);
            }
        }
    }

    private void sendMessage(ConnectRecord record) {
        this.webClient.post(this.httpSinkConfig.connectorConfig.getPath())
            .putHeader("Content-Type", "application/json; charset=utf-8")
            .sendJson(record, ar -> {
                if (ar.succeeded()) {
                    if (ar.result().statusCode() != HttpResponseStatus.OK.code()) {
                        log.error("[HttpSinkConnector] Failed to send message via HTTP. Response: {}", ar.result());
                    } else {
                        log.info("[HttpSinkConnector] Successfully send message via HTTP. ");
                    }
                } else {
                    // This function is accessed only when an error occurs at the network level
                    log.error("[HttpSinkConnector] Failed to send message via HTTP. Exception: {}", ar.cause().getMessage());
                }
            });
    }
}
