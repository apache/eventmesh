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

package org.apache.eventmesh.connector.mcp.sink;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.eventmesh.common.EventMeshThreadFactory;
import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.mcp.McpSinkConfig;
import org.apache.eventmesh.common.config.connector.mcp.SinkConnectorConfig;
import org.apache.eventmesh.connector.mcp.sink.handler.McpSinkHandler;
import org.apache.eventmesh.connector.mcp.sink.handler.impl.CommonMcpSinkHandler;
import org.apache.eventmesh.connector.mcp.sink.handler.impl.McpSinkHandlerRetryWrapper;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


@Slf4j
public class McpSinkConnector implements Sink, ConnectorCreateService<Sink> {

    private McpSinkConfig mcpSinkConfig;

    @Getter
    private McpSinkHandler sinkHandler;

    private ThreadPoolExecutor executor;

    private final AtomicBoolean isStart = new AtomicBoolean(false);

    @Override
    public Class<? extends Config> configClass() {
        return McpSinkConfig.class;
    }

    @Override
    public Sink create() {
        return new McpSinkConnector();
    }

    @Override
    public void init(Config config) throws Exception {
        this.mcpSinkConfig = (McpSinkConfig) config;
        doInit();
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        this.mcpSinkConfig = (McpSinkConfig) sinkConnectorContext.getSinkConfig();
        doInit();
    }

    @SneakyThrows
    private void doInit() {
        // Fill default values if absent
        SinkConnectorConfig.populateFieldsWithDefaults(this.mcpSinkConfig.connectorConfig);
        // Create different handlers for different configurations
        McpSinkHandler nonRetryHandler;

        nonRetryHandler = new CommonMcpSinkHandler(this.mcpSinkConfig.connectorConfig);

        int maxRetries = this.mcpSinkConfig.connectorConfig.getRetryConfig().getMaxRetries();
        if (maxRetries == 0) {
            // Use the original sink handler
            this.sinkHandler = nonRetryHandler;
        } else if (maxRetries > 0) {
            // Wrap the sink handler with a retry handler
            this.sinkHandler = new McpSinkHandlerRetryWrapper(this.mcpSinkConfig.connectorConfig, nonRetryHandler);
        } else {
            throw new IllegalArgumentException("Max retries must be greater than or equal to 0.");
        }

        boolean isParallelized = this.mcpSinkConfig.connectorConfig.isParallelized();
        int parallelism = isParallelized ? this.mcpSinkConfig.connectorConfig.getParallelism() : 1;

        // Use the executor's built-in queue with a reasonable capacity
        executor = new ThreadPoolExecutor(
                parallelism,
                parallelism,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(10000), // Built-in queue with capacity
                new EventMeshThreadFactory("mcp-sink-handler")
        );
    }

    @Override
    public void start() throws Exception {
        this.sinkHandler.start();
        isStart.set(true);
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.mcpSinkConfig.connectorConfig.getConnectorName();
    }

    @Override
    public void onException(ConnectRecord record) {

    }

    @Override
    public void stop() throws Exception {
        isStart.set(false);

        log.info("Stopping mcp sink connector, shutting down executor...");
        executor.shutdown();

        try {
            // Wait for existing tasks to complete
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate gracefully, forcing shutdown");
                executor.shutdownNow();
                // Wait a bit more for tasks to respond to being cancelled
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.error("Executor did not terminate after forced shutdown");
                }
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for executor termination");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("All tasks completed, stopping mcp sink handler");
        this.sinkHandler.stop();
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        if (!isStart.get()) {
            log.warn("Connector is not started, ignoring sink records");
            return;
        }

        for (ConnectRecord sinkRecord : sinkRecords) {
            if (Objects.isNull(sinkRecord)) {
                log.warn("ConnectRecord data is null, ignore.");
                continue;
            }

            try {
                // Use executor.submit() instead of custom queue
                executor.submit(() -> {
                    try {
                        sinkHandler.handle(sinkRecord);
                    } catch (Exception e) {
                        log.error("Failed to handle sink record via mcp", e);
                    }
                });
            } catch (Exception e) {
                log.error("Failed to submit sink record to executor", e);
            }
        }
    }
}