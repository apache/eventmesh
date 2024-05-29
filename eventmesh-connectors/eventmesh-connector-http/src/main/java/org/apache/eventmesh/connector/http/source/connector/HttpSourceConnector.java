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

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.http.HttpSourceConfig;
import org.apache.eventmesh.common.exception.EventMeshException;

import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.CloudEventUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;
import io.cloudevents.http.vertx.VertxMessageFactory;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.LoggerHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpSourceConnector implements Source {

    private static final int DEFAULT_BATCH_SIZE = 10;

    private HttpSourceConfig sourceConfig;
    private BlockingQueue<CloudEvent> queue;
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
        this.queue = new LinkedBlockingQueue<>(1000);

        final Vertx vertx = Vertx.vertx();
        final Router router = Router.router(vertx);
        router.route()
            .path(this.sourceConfig.connectorConfig.getPath())
            .method(HttpMethod.POST)
            .handler(LoggerHandler.create())
            .handler(ctx -> {
                VertxMessageFactory.createReader(ctx.request())
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
                        queue.add(event);
                        log.info("[HttpSourceConnector] Succeed to convert payload into CloudEvent. StatusCode={}", HttpResponseStatus.OK.code());
                        ctx.response().setStatusCode(HttpResponseStatus.OK.code()).end();
                    })
                    .onFailure(t -> {
                        log.error("[HttpSourceConnector] Malformed request. StatusCode={}", HttpResponseStatus.BAD_REQUEST.code(), t);
                        ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
                    });
            });
        this.server = vertx.createHttpServer(new HttpServerOptions()
            .setPort(this.sourceConfig.connectorConfig.getPort())
            .setIdleTimeout(this.sourceConfig.connectorConfig.getIdleTimeout())).requestHandler(router);
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
        List<ConnectRecord> connectRecords = new ArrayList<>(DEFAULT_BATCH_SIZE);
        for (int i = 0; i < DEFAULT_BATCH_SIZE; i++) {
            try {
                CloudEvent event = queue.poll(3, TimeUnit.SECONDS);
                if (event == null) {
                    break;
                }
                connectRecords.add(CloudEventUtil.convertEventToRecord(event));
            } catch (InterruptedException e) {
                break;
            }
        }
        return connectRecords;
    }

}
