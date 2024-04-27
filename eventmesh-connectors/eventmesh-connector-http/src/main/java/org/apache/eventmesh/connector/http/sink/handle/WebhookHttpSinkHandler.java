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
import org.apache.eventmesh.connector.http.sink.data.HttpConnectRecord;
import org.apache.eventmesh.connector.http.util.HttpUtils;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.handler.LoggerHandler;

import com.alibaba.fastjson2.JSONObject;

import lombok.extern.slf4j.Slf4j;

/**
 * Extends CommonHttpSinkHandler to provide additional functionality for handling webhook features, including sending requests to callback servers,
 * allowing longer response wait times, storing responses returned from callback servers, and exposing received data through an HTTP service.
 */
@Slf4j
public class WebhookHttpSinkHandler extends CommonHttpSinkHandler {

    // the configuration for webhook
    private final HttpWebhookConfig webhookConfig;

    // the server for exporting the received data
    private HttpServer exportServer;

    // store the received data, when webhook is enabled
    private final BlockingQueue<Object> receivedDataQueue;

    public WebhookHttpSinkHandler(SinkConnectorConfig sinkConnectorConfig) {
        super(sinkConnectorConfig);
        this.webhookConfig = sinkConnectorConfig.getWebhookConfig();
        this.receivedDataQueue = new LinkedBlockingQueue<>();
        // init the export server
        doInitExportServer();
    }

    /**
     * Initialize the server for exporting the received data
     */
    private void doInitExportServer() {
        final Vertx vertx = Vertx.vertx();
        final Router router = Router.router(vertx);
        // add logger handler
        router.route().handler(LoggerHandler.create());
        // add export handler
        router.route()
            .path(this.webhookConfig.getExportPath())
            .method(HttpMethod.GET)
            .produces("application/json")
            .handler(ctx -> {
                // get received data
                Object data = this.receivedDataQueue.poll();
                if (data != null) {

                    // export the received data
                    ctx.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                        .setStatusCode(HttpResponseStatus.OK.code())
                        .send(JSONObject.of("data", data).toJSONString());
                    if (log.isDebugEnabled()) {
                        log.debug("Succeed to export callback data. Data: {}", data);
                    } else {
                        log.info("Succeed to export callback data.");
                    }
                } else {
                    // no data to export
                    ctx.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                        .setStatusCode(HttpResponseStatus.NO_CONTENT.code())
                        .end();
                    log.info("No callback data to export.");
                }
            });
        // create the export server
        this.exportServer = vertx.createHttpServer(new HttpServerOptions()
            .setPort(this.webhookConfig.getPort())
            .setIdleTimeout(this.webhookConfig.getIdleTimeout())
            .setIdleTimeoutUnit(TimeUnit.MILLISECONDS)).requestHandler(router);
    }

    /**
     * Starts the HTTP/HTTPS handler by creating a WebClient with configured options and starting the export server.
     */
    @Override
    public void start() {
        // start the webclient
        super.start();
        // start the export server
        Throwable t = this.exportServer.listen().cause();
        if (t != null) {
            throw new EventMeshException("Failed to start Vertx server. ", t);
        }
    }

    /**
     * Processes the ConnectRecord multiple times by sending it over HTTP or HTTPS to all configured URLs.
     *
     * @param record the ConnectRecord to handle
     */
    @Override
    public void multiHandle(ConnectRecord record) {
        for (URI url : super.getUrls()) {
            // convert ConnectRecord to HttpConnectRecord
            String type = String.format("%s.%s.%s", this.getConnectorConfig().getConnectorName(), url.getScheme(), "webhook");
            HttpConnectRecord httpConnectRecord = HttpConnectRecord.convertConnectRecord(record, type);
            // handle the HttpConnectRecord
            handle(url, httpConnectRecord);
        }
    }

    /**
     * Processes the ConnectRecord once by sending it over HTTP or HTTPS to the specified URL. If the status code is 2xx, the received data will be
     * stored in the queue.
     *
     * @param url               the URL to send the ConnectRecord to
     * @param httpConnectRecord the ConnectRecord to handle
     * @return the Future of the HTTP request
     */
    @Override
    public Future<HttpResponse<Buffer>> handle(URI url, HttpConnectRecord httpConnectRecord) {
        // send the request
        Future<HttpResponse<Buffer>> responseFuture = super.handle(url, httpConnectRecord);
        // store the received data
        return responseFuture.onSuccess(res -> {
            // Determine whether the status code is 2xx
            if (!HttpUtils.is2xxSuccessful(res.statusCode())) {
                return;
            }
            // Get the received data
            String receivedData = res.bodyAsString();
            if (receivedData.isEmpty()) {
                log.warn("Received data is empty.");
                return;
            }
            // If the queue is full, remove the oldest element
            if (receivedDataQueue.size() == Integer.MAX_VALUE) {
                Object removedData = receivedDataQueue.poll();
                if (log.isDebugEnabled()) {
                    log.debug("The queue is full, remove the oldest element: {}", removedData);
                } else {
                    log.info("The queue is full, remove the oldest element");
                }
            }
            // Try to put the received data into the queue
            if (receivedDataQueue.offer(receivedData)) {
                if (log.isDebugEnabled()) {
                    log.debug("Successfully put the received data into the queue: {}", receivedData);
                } else {
                    log.info("Successfully put the received data into the queue");
                }
            } else {
                log.error("Failed to put the received data into the queue: {}", receivedData);
            }

        });
    }

    /**
     * Cleans up and releases resources used by the HTTP/HTTPS handler.
     */
    @Override
    public void stop() {
        // stop the webclient
        super.stop();
        // stop the export server
        if (this.exportServer != null) {
            Throwable t = this.exportServer.close().cause();
            if (t != null) {
                throw new EventMeshException("Failed to stop Vertx server. ", t);
            }
        } else {
            log.warn("Callback server is null, ignore.");
        }
    }
}
