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

package org.apache.eventmesh.connector.mcp.sink.handler.impl;

import org.apache.eventmesh.common.config.connector.mcp.McpRetryConfig;
import org.apache.eventmesh.common.config.connector.mcp.SinkConnectorConfig;
import org.apache.eventmesh.connector.http.util.HttpUtils;
import org.apache.eventmesh.connector.mcp.sink.data.McpConnectRecord;
import org.apache.eventmesh.connector.mcp.sink.handler.AbstractMcpSinkHandler;
import org.apache.eventmesh.connector.mcp.sink.handler.McpSinkHandler;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.net.ConnectException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * McpSinkHandlerRetryWrapper is a wrapper class for the McpSinkHandler that provides retry functionality for failed Mcp requests.
 */
@Slf4j
public class McpSinkHandlerRetryWrapper extends AbstractMcpSinkHandler {

    private final McpRetryConfig mcpRetryConfig;

    private final McpSinkHandler sinkHandler;

    private final RetryPolicy<HttpResponse<Buffer>> retryPolicy;

    public McpSinkHandlerRetryWrapper(SinkConnectorConfig sinkConnectorConfig, McpSinkHandler sinkHandler) {
        super(sinkConnectorConfig);
        this.sinkHandler = sinkHandler;
        this.mcpRetryConfig = getSinkConnectorConfig().getRetryConfig();
        this.retryPolicy = buildRetryPolicy();
    }

    private RetryPolicy<HttpResponse<Buffer>> buildRetryPolicy() {
        return RetryPolicy.<HttpResponse<Buffer>>builder()
            .handleIf(e -> e instanceof ConnectException)
            .handleResultIf(response -> mcpRetryConfig.isRetryOnNonSuccess() && !HttpUtils.is2xxSuccessful(response.statusCode()))
            .withMaxRetries(mcpRetryConfig.getMaxRetries())
            .withDelay(Duration.ofMillis(mcpRetryConfig.getInterval()))
            .onRetry(event -> {
                if (log.isDebugEnabled()) {
                    log.warn("Failed to deliver message after {} attempts. Retrying in {} ms. Error: {}",
                        event.getAttemptCount(), mcpRetryConfig.getInterval(), event.getLastException());
                } else {
                    log.warn("Failed to deliver message after {} attempts. Retrying in {} ms.",
                        event.getAttemptCount(), mcpRetryConfig.getInterval());
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
     * Initializes the WebClient for making Mcp requests based on the provided SinkConnectorConfig.
     */
    @Override
    public void start() {
        sinkHandler.start();
    }


    /**
     * Processes McpConnectRecord on specified URL while returning its own processing logic This method provides the retry power to process the
     * McpConnectRecord
     *
     * @param url               URI to which the McpConnectRecord should be sent
     * @param mcpConnectRecord McpConnectRecord to process
     * @param attributes        additional attributes to pass to the processing chain
     * @return processing chain
     */
    @Override
    public Future<HttpResponse<Buffer>> deliver(URI url, McpConnectRecord mcpConnectRecord, Map<String, Object> attributes,
                                                ConnectRecord connectRecord) {
        Failsafe.with(retryPolicy)
            .getStageAsync(() -> sinkHandler.deliver(url, mcpConnectRecord, attributes, connectRecord).toCompletionStage());
        return null;
    }


    /**
     * Cleans up and releases resources used by the Mcp handler.
     */
    @Override
    public void stop() {
        sinkHandler.stop();
    }
}
