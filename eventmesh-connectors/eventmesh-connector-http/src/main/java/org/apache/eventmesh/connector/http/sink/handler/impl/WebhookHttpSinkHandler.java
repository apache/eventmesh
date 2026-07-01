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

package org.apache.eventmesh.connector.http.sink.handler.impl;

import org.apache.eventmesh.common.config.connector.http.HttpWebhookConfig;
import org.apache.eventmesh.common.config.connector.http.SinkConnectorConfig;
import org.apache.eventmesh.common.exception.EventMeshException;
import org.apache.eventmesh.connector.http.common.SynchronizedCircularFifoQueue;
import org.apache.eventmesh.connector.http.sink.data.HttpAttemptEvent;
import org.apache.eventmesh.connector.http.sink.data.HttpConnectRecord;
import org.apache.eventmesh.connector.http.sink.data.HttpExportMetadata;
import org.apache.eventmesh.connector.http.sink.data.HttpExportRecord;
import org.apache.eventmesh.connector.http.sink.data.HttpExportRecordPage;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.handler.LoggerHandler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

import lombok.Getter;
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
    private final SynchronizedCircularFifoQueue<HttpExportRecord> receivedDataQueue;

    private volatile boolean exportStarted = false;

    private volatile boolean exportDestroyed = false;

    public boolean isExportStarted() {
        return exportStarted;
    }

    public boolean isExportDestroyed() {
        return exportDestroyed;
    }

    public WebhookHttpSinkHandler(SinkConnectorConfig sinkConnectorConfig) {
        super(sinkConnectorConfig);

        this.webhookConfig = sinkConnectorConfig.getWebhookConfig();
        int maxQueueSize = this.webhookConfig.getMaxStorageSize();
        this.receivedDataQueue = new SynchronizedCircularFifoQueue<>(maxQueueSize);
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
                // Validate the request parameters
                MultiMap params = ctx.request().params();
                String pageNumStr = params.get(ParamEnum.PAGE_NUM.getValue());
                String pageSizeStr = params.get(ParamEnum.PAGE_SIZE.getValue());
                String type = params.get(ParamEnum.TYPE.getValue());

                // 1. type must be "poll" or "peek" or null
                // 2. if type is "peek", pageNum must be greater than 0
                // 3. pageSize must be greater than 0
                if ((type != null && !Objects.equals(type, TypeEnum.PEEK.getValue()) && !Objects.equals(type, TypeEnum.POLL.getValue()))
                    || (Objects.equals(type, TypeEnum.PEEK.getValue()) && (StringUtils.isBlank(pageNumStr) || Integer.parseInt(pageNumStr) < 1))
                    || (StringUtils.isBlank(pageSizeStr) || Integer.parseInt(pageSizeStr) < 1)) {

                    // Return 400 Bad Request if the request parameters are invalid
                    ctx.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                        .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                        .end();
                    log.info("Invalid request parameters. pageNum: {}, pageSize: {}, type: {}", pageNumStr, pageSizeStr, type);
                    return;
                }

                // Parse the request parameters
                if (type == null) {
                    type = TypeEnum.PEEK.getValue();
                }
                int pageNum = StringUtils.isBlank(pageNumStr) ? 1 : Integer.parseInt(pageNumStr);
                int pageSize = Integer.parseInt(pageSizeStr);

                if (receivedDataQueue.isEmpty()) {
                    ctx.response()
                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                        .setStatusCode(HttpResponseStatus.NO_CONTENT.code())
                        .end();
                    log.info("No callback data to export.");
                    return;
                }

                // Get the received data
                List<HttpExportRecord> exportRecords;
                if (Objects.equals(type, TypeEnum.POLL.getValue())) {
                    // If the type is poll, only the first page of data is exported and removed
                    exportRecords = receivedDataQueue.fetchRange(0, pageSize, true);
                } else {
                    // If the type is peek, the specified page of data is exported without removing
                    int startIndex = (pageNum - 1) * pageSize;
                    int endIndex = startIndex + pageSize;
                    exportRecords = receivedDataQueue.fetchRange(startIndex, endIndex, false);
                }

                // Create HttpExportRecordPage
                HttpExportRecordPage page = new HttpExportRecordPage(pageNum, exportRecords.size(), exportRecords);

                // export the received data
                ctx.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                    .setStatusCode(HttpResponseStatus.OK.code())
                    .send(JSON.toJSONString(page, JSONWriter.Feature.WriteMapNullValue));
                if (log.isDebugEnabled()) {
                    log.debug("Succeed to export callback data. Data: {}", page);
                } else {
                    log.info("Succeed to export callback data.");
                }
            });
        // create the export server
        this.exportServer = vertx.createHttpServer(new HttpServerOptions()
            .setPort(this.webhookConfig.getPort())
            .setIdleTimeout(this.webhookConfig.getServerIdleTimeout())
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
        this.exportServer.listen(res -> {
            if (res.succeeded()) {
                this.exportStarted = true;
                log.info("WebhookHttpExportServer started on port: {}", this.webhookConfig.getPort());
            } else {
                log.error("WebhookHttpExportServer failed to start on port: {}", this.webhookConfig.getPort());
                throw new EventMeshException("Failed to start Vertx server. ", res.cause());
            }
        });
    }


    /**
     * Processes HttpConnectRecord on specified URL while returning its own processing logic This method sends the HttpConnectRecord to the specified
     * URL by super class method and stores the received data.
     *
     * @param url               URI to which the HttpConnectRecord should be sent
     * @param httpConnectRecord HttpConnectRecord to process
     * @param attributes        additional attributes to be used in processing
     * @return processing chain
     */
    @Override
    public Future<HttpResponse<Buffer>> deliver(URI url, HttpConnectRecord httpConnectRecord, Map<String, Object> attributes,
        ConnectRecord connectRecord) {
        // send the request
        Future<HttpResponse<Buffer>> responseFuture = super.deliver(url, httpConnectRecord, attributes, connectRecord);
        // store the received data
        return responseFuture.onComplete(arr -> {
            // get HttpAttemptEvent
            HttpAttemptEvent attemptEvent = (HttpAttemptEvent) attributes.get(HttpAttemptEvent.PREFIX + httpConnectRecord.getHttpRecordId());

            // get the response
            HttpResponse<Buffer> response = arr.succeeded() ? arr.result() : null;

            // create ExportMetadata
            HttpExportMetadata httpExportMetadata = buildHttpExportMetadata(url, response, httpConnectRecord, attemptEvent);

            // create ExportRecord
            HttpExportRecord exportRecord = new HttpExportRecord(httpExportMetadata, arr.succeeded() ? arr.result().bodyAsString() : null);
            // add the data to the queue
            receivedDataQueue.offer(exportRecord);
        });
    }

    /**
     * Builds the HttpExportMetadata object based on the response, HttpConnectRecord, and HttpRetryEvent.
     *
     * @param url               the URI to which the HttpConnectRecord was sent
     * @param response          the response received from the URI
     * @param httpConnectRecord the HttpConnectRecord that was sent
     * @param attemptEvent      the HttpAttemptEvent that was used to send the HttpConnectRecord
     * @return the HttpExportMetadata object
     */
    private HttpExportMetadata buildHttpExportMetadata(URI url, HttpResponse<Buffer> response, HttpConnectRecord httpConnectRecord,
        HttpAttemptEvent attemptEvent) {

        String msg = null;
        // order of precedence: lastException > response > null
        if (attemptEvent.getLastException() != null) {
            msg = attemptEvent.getLimitedExceptionMessage();
        } else if (response != null) {
            msg = response.statusMessage();
        }

        return HttpExportMetadata.builder()
            .url(url.toString())
            .code(response != null ? response.statusCode() : -1)
            .message(msg)
            .receivedTime(LocalDateTime.now())
            .recordId(httpConnectRecord.getHttpRecordId())
            .retryNum(attemptEvent.getAttempts() - 1)
            .build();
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
            this.exportServer.close(res -> {
                if (res.succeeded()) {
                    this.exportDestroyed = true;
                    log.info("WebhookHttpExportServer stopped on port: {}", this.webhookConfig.getPort());
                } else {
                    log.error("WebhookHttpExportServer failed to stop on port: {}", this.webhookConfig.getPort());
                    throw new EventMeshException("Failed to stop Vertx server. ", res.cause());
                }
            });
        } else {
            log.warn("Callback server is null, ignore.");
        }
    }


    @Getter
    public enum ParamEnum {
        PAGE_NUM("pageNum"),
        PAGE_SIZE("pageSize"),
        TYPE("type");

        private final String value;

        ParamEnum(String value) {
            this.value = value;
        }

    }


    @Getter
    public enum TypeEnum {
        POLL("poll"),
        PEEK("peek");

        private final String value;

        TypeEnum(String value) {
            this.value = value;
        }

    }
}