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

import org.apache.eventmesh.common.config.connector.http.HttpRetryConfig;
import org.apache.eventmesh.common.config.connector.http.SinkConnectorConfig;
import org.apache.eventmesh.connector.http.sink.data.HttpConnectRecord;
import org.apache.eventmesh.connector.http.sink.handler.AbstractHttpSinkHandler;
import org.apache.eventmesh.connector.http.sink.handler.HttpSinkHandler;
import org.apache.eventmesh.connector.http.util.HttpUtils;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.net.ConnectException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;

import lombok.extern.slf4j.Slf4j;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;


/**
 * HttpSinkHandlerRetryWrapper is a wrapper class for the HttpSinkHandler that provides retry functionality for failed HTTP requests.
 */
@Slf4j
public class HttpSinkHandlerRetryWrapper extends AbstractHttpSinkHandler {

    private final HttpRetryConfig httpRetryConfig;

    private final HttpSinkHandler sinkHandler;

    private final RetryPolicy<HttpResponse<Buffer>> retryPolicy;

    public HttpSinkHandlerRetryWrapper(SinkConnectorConfig sinkConnectorConfig, HttpSinkHandler sinkHandler) {
        super(sinkConnectorConfig);
        this.sinkHandler = sinkHandler;
        this.httpRetryConfig = getSinkConnectorConfig().getRetryConfig();
        this.retryPolicy = buildRetryPolicy();
    }

    private RetryPolicy<HttpResponse<Buffer>> buildRetryPolicy() {
        return RetryPolicy.<HttpResponse<Buffer>>builder()
            .handleIf(e -> e instanceof ConnectException)
            .handleResultIf(response -> httpRetryConfig.isRetryOnNonSuccess() && !HttpUtils.is2xxSuccessful(response.statusCode()))
            .withMaxRetries(httpRetryConfig.getMaxRetries())
            .withDelay(Duration.ofMillis(httpRetryConfig.getInterval()))
            .onRetry(event -> {
                if (log.isDebugEnabled()) {
                    log.warn("Failed to deliver message after {} attempts. Retrying in {} ms. Error: {}",
                        event.getAttemptCount(), httpRetryConfig.getInterval(), event.getLastException());
                } else {
                    log.warn("Failed to deliver message after {} attempts. Retrying in {} ms.",
                        event.getAttemptCount(), httpRetryConfig.getInterval());
                }
            }).onFailure(event -> {
                if (log.isDebugEnabled()) {
                    log.error("Failed to deliver message after {} attempts. Error: {}",
                        event.getAttemptCount(), event.getException());
                } else {
                    log.error("Failed to deliver message after {} attempts.",
                        event.getAttemptCount());
                }
            }).build();
    }

    /**
     * Initializes the WebClient for making HTTP requests based on the provided SinkConnectorConfig.
     */
    @Override
    public void start() {
        sinkHandler.start();
    }


    /**
     * Processes HttpConnectRecord on specified URL while returning its own processing logic This method provides the retry power to process the
     * HttpConnectRecord
     *
     * @param url               URI to which the HttpConnectRecord should be sent
     * @param httpConnectRecord HttpConnectRecord to process
     * @param attributes        additional attributes to pass to the processing chain
     * @return processing chain
     */
    @Override
    public Future<HttpResponse<Buffer>> deliver(URI url, HttpConnectRecord httpConnectRecord, Map<String, Object> attributes,
        ConnectRecord connectRecord) {
        Failsafe.with(retryPolicy)
            .getStageAsync(() -> sinkHandler.deliver(url, httpConnectRecord, attributes, connectRecord).toCompletionStage());
        return null;
    }


    /**
     * Cleans up and releases resources used by the HTTP/HTTPS handler.
     */
    @Override
    public void stop() {
        sinkHandler.stop();
    }
}
