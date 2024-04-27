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

import org.apache.eventmesh.connector.http.sink.config.HttpRetryConfig;
import org.apache.eventmesh.connector.http.sink.config.SinkConnectorConfig;
import org.apache.eventmesh.connector.http.sink.data.HttpConnectRecord;
import org.apache.eventmesh.connector.http.util.HttpUtils;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.net.ConnectException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RetryHttpSinkHandler implements HttpSinkHandler {

    private final SinkConnectorConfig connectorConfig;

    private Retry retry;

    private ScheduledExecutorService scheduler;

    private final List<URI> urls;

    private final HttpSinkHandler sinkHandler;


    public RetryHttpSinkHandler(SinkConnectorConfig connectorConfig, HttpSinkHandler sinkHandler) {
        this.connectorConfig = connectorConfig;
        this.sinkHandler = sinkHandler;

        // Initialize retry
        initRetry();

        // Initialize URLs
        String[] urlStrings = connectorConfig.getUrls();
        this.urls = Arrays.stream(urlStrings)
            .map(URI::create)
            .collect(Collectors.toList());
    }

    private void initRetry() {
        HttpRetryConfig httpRetryConfig = this.connectorConfig.getRetryConfig();
        // Create a custom RetryConfig
        RetryConfig retryConfig = RetryConfig.<HttpResponse<Buffer>>custom()
            .maxAttempts(httpRetryConfig.getMaxAttempts())
            .waitDuration(Duration.ofMillis(httpRetryConfig.getInterval()))
            .retryOnException(throwable -> throwable instanceof ConnectException)
            .retryOnResult(response -> httpRetryConfig.isRetryAll() && !HttpUtils.is2xxSuccessful(response.statusCode()))
            .failAfterMaxAttempts(true)
            .build();

        // Create a RetryRegistry with a custom global configuration
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);

        // Get or create a Retry from the registry
        this.retry = retryRegistry.retry("retryHttpSinkHandler");

        // Create a ScheduledExecutorService with the number of threads equal to the maximum connection pool size
        this.scheduler = Executors.newScheduledThreadPool(this.connectorConfig.getMaxConnectionPoolSize());

        // register event listeners
        retry.getEventPublisher()
            .onSuccess(event -> log.info(event.toString()))
            .onError(event -> log.error(event.toString()));
    }


    /**
     * Initializes the WebClient for making HTTP requests based on the provided SinkConnectorConfig.
     */
    @Override
    public void start() {
        sinkHandler.start();
    }

    /**
     * Handles the ConnectRecord by sending it to all configured URLs using the WebClient.
     *
     * @param record the ConnectRecord to handle
     */
    @Override
    public void multiHandle(ConnectRecord record) {
        for (URI url : this.urls) {
            // convert ConnectRecord to HttpConnectRecord
            String type = String.format("%s.%s.%s",
                this.connectorConfig.getConnectorName(), url.getScheme(),
                this.connectorConfig.getWebhookConfig().isActivate() ? "webhook" : "common");
            HttpConnectRecord httpConnectRecord = HttpConnectRecord.convertConnectRecord(record, type);
            // handle the HttpConnectRecord
            handle(url, httpConnectRecord);
        }
    }

    /**
     * Handles the HttpConnectRecord by sending it to the specified URL using the WebClient. If the request fails, it will be retried according to the
     * RetryConfig.
     *
     * @param url               the URL to send the HttpConnectRecord to
     * @param httpConnectRecord the HttpConnectRecord to send
     * @return a Future representing the result of the HTTP request
     */
    @Override
    public Future<HttpResponse<Buffer>> handle(URI url, HttpConnectRecord httpConnectRecord) {
        this.retry.executeCompletionStage(scheduler, () ->
            this.sinkHandler.handle(url, httpConnectRecord).toCompletionStage());
        return null;
    }

    /**
     * Cleans up and releases resources used by the HTTP/HTTPS handler.
     */
    @Override
    public void stop() {
        sinkHandler.stop();
        // Shutdown the scheduler
        scheduler.shutdown();
    }
}
