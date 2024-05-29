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

package org.apache.eventmesh.connector.http.source.connector;

import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.connector.http.common.BoundedConcurrentQueue;
import org.apache.eventmesh.connector.http.source.config.HttpSourceConfig;
import org.apache.eventmesh.connector.http.source.protocol.Protocol;
import org.apache.eventmesh.connector.http.source.protocol.ProtocolFactory;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.LoggerHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpSourceConnector implements Source {

    private HttpSourceConfig sourceConfig;

    private BoundedConcurrentQueue<Object> boundedQueue;

    private int batchSize;

    private Protocol protocol;

    private HttpServer server;


    @Override
    public Class<? extends Config> configClass() {
        return HttpSourceConfig.class;
    }

    @Override
    public void init(Config config) {
        this.sourceConfig = (HttpSourceConfig) config;
        doInit();
    }

    @Override
    public void init(ConnectorContext connectorContext) {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        this.sourceConfig = (HttpSourceConfig) sourceConnectorContext.getSourceConfig();
        doInit();
    }

    private void doInit() {
        // init queue
        int maxQueueSize = this.sourceConfig.getConnectorConfig().getMaxStorageSize();
        this.boundedQueue = new BoundedConcurrentQueue<>(maxQueueSize);

        // init batch size
        this.batchSize = this.sourceConfig.getConnectorConfig().getBatchSize();

        // init protocol
        String protocolName = this.sourceConfig.getConnectorConfig().getProtocol();
        this.protocol = ProtocolFactory.getInstance(this.sourceConfig.connectorConfig, protocolName);

        final Vertx vertx = Vertx.vertx();
        final Router router = Router.router(vertx);
        final Route route = router.route()
            .path(this.sourceConfig.connectorConfig.getPath())
            .handler(LoggerHandler.create());

        // set protocol handler
        this.protocol.setHandler(route, boundedQueue);

        // create server
        this.server = vertx.createHttpServer(new HttpServerOptions()
            .setPort(this.sourceConfig.connectorConfig.getPort())
            .setMaxFormAttributeSize(this.sourceConfig.connectorConfig.getMaxFormAttributeSize())
            .setIdleTimeout(this.sourceConfig.connectorConfig.getIdleTimeout())
            .setIdleTimeoutUnit(TimeUnit.MILLISECONDS)).requestHandler(router);
    }

    @Override
    public void start() {
        Throwable t = this.server.listen().cause();
        if (t != null) {
            throw new EventMeshException("failed to start Vertx server", t);
        }
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sourceConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() {
        Throwable t = this.server.close().cause();
        if (t != null) {
            throw new EventMeshException("failed to stop Vertx server", t);
        }
    }

    @Override
    public List<ConnectRecord> poll() {
        // if queue is empty, return empty list
        if (boundedQueue.getCurrSize() == 0) {
            return Collections.emptyList();
        }
        // poll from queue
        List<ConnectRecord> connectRecords = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            Object obj = boundedQueue.poll();
            if (obj == null) {
                break;
            }
            // convert to ConnectRecord
            ConnectRecord connectRecord = protocol.convertToConnectRecord(obj);
            connectRecords.add(connectRecord);
        }
        return connectRecords;
    }

}
