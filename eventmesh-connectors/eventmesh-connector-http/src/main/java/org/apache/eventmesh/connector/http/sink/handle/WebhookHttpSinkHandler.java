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

import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.connector.http.sink.config.HttpWebhookConfig;
import org.apache.eventmesh.connector.http.sink.config.SinkConnectorConfig;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.LoggerHandler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import lombok.extern.slf4j.Slf4j;

/**
 * HttpSinkHandler with webhook functionality
 */
@Slf4j
public class WebhookHttpSinkHandler extends CommonHttpSinkHandler {

    private final HttpWebhookConfig webhookConfig;

    // store the callback data
    private final BlockingQueue<JSONObject> callbackQueue;

    // receive/export callback data
    private HttpServer callbackServer;

    public WebhookHttpSinkHandler(SinkConnectorConfig sinkConnectorConfig) {
        super(sinkConnectorConfig);
        this.webhookConfig = sinkConnectorConfig.getWebhookConfig();
        this.callbackQueue = new LinkedBlockingQueue<>();
    }

    @Override
    public void start() {
        super.start();
        // Create callback server
        doInitCallbackServer();
        // Start callback server
        Throwable t = this.callbackServer.listen().cause();
        if (t != null) {
            throw new EventMeshException("Failed to start Vertx server. ", t);
        }
    }

    private void doInitCallbackServer() {
        final Vertx vertx = Vertx.vertx();
        final Router router = Router.router(vertx);
        // add logger handler
        router.route().handler(LoggerHandler.create());

        // add callback handler
        router.route()
            .path(this.webhookConfig.getCallbackPath())
            .method(HttpMethod.POST)
            .produces("application/json")
            .handler(ctx -> ctx.request().body().onComplete(ar -> {
                if (ar.succeeded()) {
                    JSONObject callbackData = JSON.parseObject(ar.result().toString());
                    // store callback data
                    if (!this.callbackQueue.offer(callbackData)) {
                        log.error("Callback data is full, discard the data. Data: {}", callbackData);
                    } else {
                        log.debug("Succeed to store callback data. Data: {}", callbackData);
                    }
                    // response 200 OK
                    ctx.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                        .setStatusCode(HttpResponseStatus.OK.code())
                        .end();
                } else {
                    log.error("Failed to parse callback data. ", ar.cause());
                    ctx.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                        .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                        .end();
                }
            }));

        // add export handler
        router.route()
            .path(this.webhookConfig.getExportPath())
            .method(HttpMethod.GET)
            .produces("application/json")
            .handler(ctx -> {
                // get callback data
                JSONObject callbackData = this.callbackQueue.poll();

                if (callbackData != null) {
                    ctx.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                        .setStatusCode(HttpResponseStatus.OK.code())
                        .end(callbackData.toString());
                    log.debug("Succeed to export callback data. Data: {}", callbackData);
                } else {
                    ctx.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                        .setStatusCode(HttpResponseStatus.NO_CONTENT.code())
                        .end();
                    log.debug("No callback data to export.");
                }
            });

        this.callbackServer = vertx.createHttpServer(new HttpServerOptions()
            .setPort(this.webhookConfig.getPort())
            .setIdleTimeout(this.webhookConfig.getIdleTimeout())
            .setIdleTimeoutUnit(TimeUnit.MILLISECONDS)).requestHandler(router);
    }

    @Override
    public void handle(ConnectRecord record) {
        super.handle(record);
    }

    @Override
    public void stop() {
        super.stop();
        // Stop callback server
        if (this.callbackServer != null) {
            Throwable t = this.callbackServer.close().cause();
            if (t != null) {
                throw new EventMeshException("Failed to stop Vertx server. ", t);
            }
        } else {
            log.warn("Callback server is null, ignore.");
        }
    }
}
