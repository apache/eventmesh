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

package org.apache.eventmesh.connector.http.sink;

import org.apache.eventmesh.connector.http.sink.config.HttpSinkConfig;
import org.apache.eventmesh.connector.http.sink.handle.CommonHttpSinkHandler;
import org.apache.eventmesh.connector.http.sink.handle.HttpSinkHandler;
import org.apache.eventmesh.connector.http.sink.handle.WebhookHttpSinkHandler;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.List;
import java.util.Objects;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class HttpSinkConnector implements Sink {

    private HttpSinkConfig httpSinkConfig;

    private HttpSinkHandler sinkHandler;

    @Override
    public Class<? extends Config> configClass() {
        return HttpSinkConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        this.httpSinkConfig = (HttpSinkConfig) config;
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
        // Create different handlers for different configurations
        if (this.httpSinkConfig.connectorConfig.getWebhookConfig().isActivate()) {
            this.sinkHandler = new WebhookHttpSinkHandler(this.httpSinkConfig.connectorConfig);
        } else {
            this.sinkHandler = new CommonHttpSinkHandler(this.httpSinkConfig.connectorConfig);
        }
    }

    @Override
    public void start() throws Exception {
        this.sinkHandler.start();
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
        this.sinkHandler.stop();
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        for (ConnectRecord sinkRecord : sinkRecords) {
            try {
                if (Objects.isNull(sinkRecord)) {
                    log.warn("ConnectRecord data is null, ignore.");
                    continue;
                }
                // Handle the ConnectRecord
                this.sinkHandler.handle(sinkRecord);
            } catch (Exception e) {
                log.error("Failed to sink message via HTTP. ", e);
            }
        }
    }
}
