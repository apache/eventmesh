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
import org.apache.eventmesh.connector.http.sink.data.HttpExportMetadata;
import org.apache.eventmesh.connector.http.sink.data.HttpExportMetadata.HttpExportMetadataBuilder;
import org.apache.eventmesh.connector.http.sink.data.HttpExportRecord;
import org.apache.eventmesh.connector.http.util.HttpUtils;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.net.ConnectException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;

import lombok.extern.slf4j.Slf4j;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import dev.failsafe.RetryPolicyBuilder;
import dev.failsafe.event.ExecutionEvent;


@Slf4j
public class RetryHttpSinkHandler implements HttpSinkHandler {

    private final SinkConnectorConfig connectorConfig;

    // Retry policy builder
    private RetryPolicyBuilder<HttpResponse<Buffer>> retryPolicyBuilder;

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

        this.retryPolicyBuilder = RetryPolicy.<HttpResponse<Buffer>>builder()
            .handleIf(e -> e instanceof ConnectException)
            .handleResultIf(response -> httpRetryConfig.isRetryOnNonSuccess() && !HttpUtils.is2xxSuccessful(response.statusCode()))
            .withMaxRetries(httpRetryConfig.getMaxRetries())
            .withDelay(Duration.ofMillis(httpRetryConfig.getInterval()));
    }


    /**
     * Initializes the WebClient for making HTTP requests based on the provided SinkConnectorConfig.
     */
    @Override
    public void start() {
        sinkHandler.start();
    }


    /**
     * Processes a ConnectRecord by sending it over HTTP or HTTPS. This method should be called for each ConnectRecord that needs to be processed.
     *
     * @param record the ConnectRecord to process
     */
    @Override
    public void handle(ConnectRecord record) {
        for (URI url : this.urls) {
            // convert ConnectRecord to HttpConnectRecord
            String type = String.format("%s.%s.%s",
                this.connectorConfig.getConnectorName(), url.getScheme(),
                this.connectorConfig.getWebhookConfig().isActivate() ? "webhook" : "common");
            HttpConnectRecord httpConnectRecord = HttpConnectRecord.convertConnectRecord(record, type);
            // handle the HttpConnectRecord
            deliver(url, httpConnectRecord);
        }
    }


    /**
     * Processes HttpConnectRecord on specified URL while returning its own processing logic This method provides the retry power to process the
     * HttpConnectRecord
     *
     * @param url               URI to which the HttpConnectRecord should be sent
     * @param httpConnectRecord HttpConnectRecord to process
     * @return processing chain
     */
    @Override
    public Future<HttpResponse<Buffer>> deliver(URI url, HttpConnectRecord httpConnectRecord) {
        // Only webhook mode needs to use the firstTryId
        String firstTryId = UUID.randomUUID().toString();

        // Build the retry policy
        RetryPolicy<HttpResponse<Buffer>> retryPolicy = retryPolicyBuilder
            .onSuccess(e -> {
                if (connectorConfig.getWebhookConfig().isActivate()) {
                    // convert the result to an HttpExportRecord
                    HttpExportRecord exportRecord = covertToExportRecord(e, e.getResult(), e.getException(), url, firstTryId);
                    // add the data to the queue
                    ((WebhookHttpSinkHandler) sinkHandler).addDataToQueue(exportRecord);
                }
            })
            .onRetry(e -> {
                if (log.isDebugEnabled()) {
                    log.warn("Retrying the request to {} for the {} time. HttpConnectRecord= {}", url, e.getAttemptCount(), httpConnectRecord);
                } else {
                    log.warn("Retrying the request to {} for the {} time.", url, e.getAttemptCount());
                }
                if (connectorConfig.getWebhookConfig().isActivate()) {
                    HttpExportRecord exportRecord = covertToExportRecord(e, e.getLastResult(), e.getLastException(), url, firstTryId);
                    ((WebhookHttpSinkHandler) sinkHandler).addDataToQueue(exportRecord);
                }
            })
            .onFailure(e -> {
                if (log.isDebugEnabled()) {
                    log.error("Failed to send the request to {} after {} attempts. HttpConnectRecord= {}", url, e.getAttemptCount(),
                        httpConnectRecord, e.getException());
                } else {
                    log.error("Failed to send the request to {} after {} attempts.", url, e.getAttemptCount(), e.getException());
                }
                if (connectorConfig.getWebhookConfig().isActivate()) {
                    HttpExportRecord exportRecord = covertToExportRecord(e, e.getResult(), e.getException(), url, firstTryId);
                    ((WebhookHttpSinkHandler) sinkHandler).addDataToQueue(exportRecord);
                }
            }).build();

        // Handle the HttpConnectRecord with retry
        Failsafe.with(retryPolicy)
            .getStageAsync(() -> sinkHandler.deliver(url, httpConnectRecord).toCompletionStage());

        return null;
    }

    /**
     * Converts the ExecutionCompletedEvent to an HttpExportRecord.
     *
     * @param event      the ExecutionCompletedEvent to convert
     * @param response   the response of the request, may be null
     * @param e          the exception thrown during the request, may be null
     * @param url        the URL the request was sent to
     * @param firstTryId the UUID of the first try
     * @return the converted HttpExportRecord
     */
    private HttpExportRecord covertToExportRecord(ExecutionEvent event, HttpResponse<Buffer> response, Throwable e, URI url, String firstTryId) {
        HttpExportMetadataBuilder builder = HttpExportMetadata.builder()
            .url(url.toString())
            .receivedTime(LocalDateTime.now())
            .retryNum(event.getAttemptCount() - 1);

        if (event.getAttemptCount() == 1) {
            builder.retriedBy(null)
                .uuid(firstTryId);
        } else {
            builder.retriedBy(firstTryId)
                .uuid(UUID.randomUUID().toString());
        }

        if (response != null) {
            // record the response
            builder.code(response.statusCode())
                .message(response.statusMessage());
        } else {
            // record the exception
            builder.code(-1)
                .message(e.getMessage());
        }

        return new HttpExportRecord(builder.build(), response == null ? null : response.bodyAsString());
    }

    /**
     * Cleans up and releases resources used by the HTTP/HTTPS handler.
     */
    @Override
    public void stop() {
        sinkHandler.stop();
    }
}
