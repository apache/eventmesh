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

package org.apache.eventmesh.connector.mcp.source;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.LoggerHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.http.HttpSourceConfig;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.connector.mcp.source.data.McpResponse;
import org.apache.eventmesh.connector.mcp.source.protocol.Protocol;
import org.apache.eventmesh.connector.mcp.source.protocol.ProtocolFactory;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
public class McpSourceConnector implements Source, ConnectorCreateService<Source> {

    private HttpSourceConfig sourceConfig;

    private BlockingQueue<Object> queue;

    private int batchSize;

    private Route route;

    private Protocol protocol;

    private HttpServer server;

    @Getter
    private volatile boolean started = false;

    @Getter
    private volatile boolean destroyed = false;


    @Override
    public Class<? extends Config> configClass() {
        return HttpSourceConfig.class;
    }

    @Override
    public Source create() {
        return new McpSourceConnector();
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
        this.queue = new LinkedBlockingQueue<>(maxQueueSize);

        // init batch size
        this.batchSize = this.sourceConfig.getConnectorConfig().getBatchSize();

        // init protocol
        String protocolName = this.sourceConfig.getConnectorConfig().getProtocol();
        this.protocol = ProtocolFactory.getInstance(this.sourceConfig.connectorConfig, protocolName);

        final Vertx vertx = Vertx.vertx();
        final Router router = Router.router(vertx);
        route = router.route()
            .path(this.sourceConfig.connectorConfig.getPath())
            .handler(LoggerHandler.create());

        // set protocol handler
        this.protocol.setHandler(route, queue);

        // create server
        this.server = vertx.createHttpServer(new HttpServerOptions()
            .setPort(this.sourceConfig.connectorConfig.getPort())
            .setMaxFormAttributeSize(this.sourceConfig.connectorConfig.getMaxFormAttributeSize())
            .setIdleTimeout(this.sourceConfig.connectorConfig.getIdleTimeout())
            .setIdleTimeoutUnit(TimeUnit.MILLISECONDS)).requestHandler(router);
    }

    @Override
    public void start() {
        this.server.listen(res -> {
            if (res.succeeded()) {
                this.started = true;
                log.info("McpSourceConnector started on port: {}", this.sourceConfig.getConnectorConfig().getPort());
            } else {
                log.error("McpSourceConnector failed to start on port: {}", this.sourceConfig.getConnectorConfig().getPort());
                throw new EventMeshException("failed to start Vertx server", res.cause());
            }
        });
    }

    @Override
    public void commit(ConnectRecord record) {
        if (sourceConfig.getConnectorConfig().isDataConsistencyEnabled()) {
            log.debug("McpSourceConnector commit record: {}", record.getRecordId());
            RoutingContext routingContext = (RoutingContext) record.getExtensionObj("routingContext");
            if (routingContext != null) {
                routingContext.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(HttpResponseStatus.OK.code())
                    .end(McpResponse.success(null, "0").toJsonStr());// todo set session and outputs
            } else {
                log.error("Failed to commit the record, routingContext is null, recordId: {}", record.getRecordId());
            }
        }
    }

    @Override
    public String name() {
        return this.sourceConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void onException(ConnectRecord record) {
        if (this.route != null) {
            this.route.failureHandler(ctx -> {
                log.error("Failed to handle the request, recordId {}. ", record.getRecordId(), ctx.failure());
                // Return Bad Response
                ctx.response()
                    .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                    .end("{\"status\":\"failed\",\"recordId\":\"" + record.getRecordId() + "\"}");
            });
        }
    }

    @Override
    public void stop() {
        if (this.server != null) {
            this.server.close(res -> {
                    if (res.succeeded()) {
                        this.destroyed = true;
                        log.info("McpSourceConnector stopped on port: {}", this.sourceConfig.getConnectorConfig().getPort());
                    } else {
                        log.error("McpSourceConnector failed to stop on port: {}", this.sourceConfig.getConnectorConfig().getPort());
                        throw new EventMeshException("failed to stop Vertx server", res.cause());
                    }
                }
            );
        } else {
            log.warn("McpSourceConnector server is null, ignore.");
        }
    }

    @Override
    public List<ConnectRecord> poll() {
        long startTime = System.currentTimeMillis();
        long maxPollWaitTime = 5000;
        long remainingTime = maxPollWaitTime;

        // poll from queue
        List<ConnectRecord> connectRecords = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            try {
                Object obj = queue.poll(remainingTime, TimeUnit.MILLISECONDS);
                if (obj == null) {
                    break;
                }
                // convert to ConnectRecord
                ConnectRecord connectRecord = protocol.convertToConnectRecord(obj);
                connectRecords.add(connectRecord);

                // calculate elapsed time and update remaining time for next poll
                long elapsedTime = System.currentTimeMillis() - startTime;
                remainingTime = maxPollWaitTime > elapsedTime ? maxPollWaitTime - elapsedTime : 0;
            } catch (Exception e) {
                log.error("Failed to poll from queue.", e);
                throw new RuntimeException(e);
            }

        }
        return connectRecords;
    }

}
