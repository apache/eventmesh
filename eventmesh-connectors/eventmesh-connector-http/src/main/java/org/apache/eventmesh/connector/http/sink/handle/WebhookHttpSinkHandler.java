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
import org.apache.eventmesh.connector.http.sink.data.HttpExportMetadata;
import org.apache.eventmesh.connector.http.sink.data.HttpExportRecord;
import org.apache.eventmesh.connector.http.sink.data.HttpExportRecordPage;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    private final SinkConnectorConfig sinkConnectorConfig;

    // the configuration for webhook
    private final HttpWebhookConfig webhookConfig;

    // the server for exporting the received data
    private HttpServer exportServer;

    // store the received data, when webhook is enabled
    private final ConcurrentLinkedQueue<HttpExportRecord> receivedDataQueue;

    // the maximum queue size
    private final int maxQueueSize;

    // the current queue size
    private final AtomicInteger currentQueueSize;

    public WebhookHttpSinkHandler(SinkConnectorConfig sinkConnectorConfig) {
        super(sinkConnectorConfig);
        this.sinkConnectorConfig = sinkConnectorConfig;
        this.webhookConfig = sinkConnectorConfig.getWebhookConfig();
        this.maxQueueSize = this.webhookConfig.getMaxStorageSize();
        this.currentQueueSize = new AtomicInteger(0);
        this.receivedDataQueue = new ConcurrentLinkedQueue<>();
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

                if (currentQueueSize.get() == 0) {
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
                    exportRecords = getDataFromQueue(0, pageSize, true);
                } else {
                    // If the type is peek, the specified page of data is exported without removing
                    int startIndex = (pageNum - 1) * pageSize;
                    int endIndex = startIndex + pageSize;
                    exportRecords = getDataFromQueue(startIndex, endIndex, false);
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
        Throwable t = this.exportServer.listen().cause();
        if (t != null) {
            throw new EventMeshException("Failed to start Vertx server. ", t);
        }
    }

    /**
     * Processes a ConnectRecord by sending it over HTTP or HTTPS. This method should be called for each ConnectRecord that needs to be processed.
     *
     * @param record the ConnectRecord to process
     */
    @Override
    public void handle(ConnectRecord record) {
        for (URI url : super.getUrls()) {
            // convert ConnectRecord to HttpConnectRecord
            String type = String.format("%s.%s.%s", this.getConnectorConfig().getConnectorName(), url.getScheme(), "webhook");
            HttpConnectRecord httpConnectRecord = HttpConnectRecord.convertConnectRecord(record, type);
            // handle the HttpConnectRecord
            deliver(url, httpConnectRecord);
        }
    }


    /**
     * Processes HttpConnectRecord on specified URL while returning its own processing logic This method sends the HttpConnectRecord to the specified
     * URL by super class method and stores the received data.
     *
     * @param url               URI to which the HttpConnectRecord should be sent
     * @param httpConnectRecord HttpConnectRecord to process
     * @return processing chain
     */
    @Override
    public Future<HttpResponse<Buffer>> deliver(URI url, HttpConnectRecord httpConnectRecord) {
        // send the request
        Future<HttpResponse<Buffer>> responseFuture = super.deliver(url, httpConnectRecord);
        // store the received data
        return responseFuture.onComplete(arr -> {
            // If open retry, return directly and handled by RetryHttpSinkHandler
            if (sinkConnectorConfig.getRetryConfig().getMaxRetries() > 0) {
                return;
            }
            // create ExportMetadataBuilder
            HttpResponse<Buffer> response = arr.succeeded() ? arr.result() : null;

            HttpExportMetadata httpExportMetadata = HttpExportMetadata.builder()
                .url(url.toString())
                .code(response != null ? response.statusCode() : -1)
                .message(response != null ? response.statusMessage() : arr.cause().getMessage())
                .receivedTime(LocalDateTime.now())
                .retriedBy(null)
                .uuid(httpConnectRecord.getUuid())
                .retryNum(0)
                .build();

            // create ExportRecord
            HttpExportRecord exportRecord = new HttpExportRecord(httpExportMetadata, arr.succeeded() ? arr.result().bodyAsString() : null);
            // add the data to the queue
            addDataToQueue(exportRecord);
        });
    }


    /**
     * Adds the received data to the queue.
     *
     * @param exportRecord the received data to add to the queue
     */
    public void addDataToQueue(HttpExportRecord exportRecord) {
        // If the current queue size is greater than or equal to the maximum queue size, remove the oldest element
        if (currentQueueSize.get() >= maxQueueSize) {
            Object removedData = receivedDataQueue.poll();
            if (log.isDebugEnabled()) {
                log.debug("The queue is full, remove the oldest element: {}", removedData);
            } else {
                log.info("The queue is full, remove the oldest element");
            }
            currentQueueSize.decrementAndGet();
        }
        // Try to put the received data into the queue
        if (receivedDataQueue.offer(exportRecord)) {
            currentQueueSize.incrementAndGet();
            log.debug("Successfully put the received data into the queue: {}", exportRecord);
        } else {
            log.error("Failed to put the received data into the queue: {}", exportRecord);
        }
    }

    /**
     * Gets the received data from the queue.
     *
     * @param startIndex the start index of the data to get
     * @param endIndex   the end index of the data to get
     * @param removed    whether to remove the data from the queue
     * @return the received data
     */
    private List<HttpExportRecord> getDataFromQueue(int startIndex, int endIndex, boolean removed) {
        Iterator<HttpExportRecord> iterator = receivedDataQueue.iterator();

        List<HttpExportRecord> pageItems = new ArrayList<>(endIndex - startIndex);
        int count = 0;
        while (iterator.hasNext() && count < endIndex) {
            HttpExportRecord item = iterator.next();
            if (count >= startIndex) {
                pageItems.add(item);
                if (removed) {
                    iterator.remove();
                    currentQueueSize.decrementAndGet();
                }
            }
            count++;
        }
        return pageItems;
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