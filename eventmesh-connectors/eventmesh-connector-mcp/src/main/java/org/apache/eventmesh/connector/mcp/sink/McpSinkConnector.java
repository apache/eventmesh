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
import org.apache.eventmesh.common.config.connector.http.HttpSinkConfig;
import org.apache.eventmesh.common.config.connector.http.SinkConnectorConfig;
import org.apache.eventmesh.connector.mcp.sink.handler.McpSinkHandler;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.common.config.connector.mcp.*;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class McpSinkConnector implements Sink, ConnectorCreateService<Sink> {

    private HttpSinkConfig httpSinkConfig;

    @Getter
    private McpSinkHandler sinkHandler;

    private ThreadPoolExecutor executor;

    private final LinkedBlockingQueue<ConnectRecord> queue = new LinkedBlockingQueue<>(10000);

    private final AtomicBoolean isStart = new AtomicBoolean(true);

    @Override
    public Class<? extends Config> configClass() {
        return HttpSinkConfig.class;
    }

    @Override
    public Sink create() {
        return new McpSinkConnector();
    }

    @Override
    public void init(Config config) throws Exception {
        this.httpSinkConfig = (HttpSinkConfig) config;
        doInit();
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        this.httpSinkConfig = (HttpSinkConfig) sinkConnectorContext.getSinkConfig();
        doInit();
    }

    @SneakyThrows
    private void doInit() {
        // Fill default values if absent
        SinkConnectorConfig.populateFieldsWithDefaults(this.httpSinkConfig.connectorConfig);
        // Create different handlers for different configurations
        HttpSinkHandler nonRetryHandler;
        if (this.httpSinkConfig.connectorConfig.getWebhookConfig().isActivate()) {
            nonRetryHandler = new WebhookHttpSinkHandler(this.httpSinkConfig.connectorConfig);
        } else {
            nonRetryHandler = new CommonHttpSinkHandler(this.httpSinkConfig.connectorConfig);
        }

        int maxRetries = this.httpSinkConfig.connectorConfig.getRetryConfig().getMaxRetries();
        if (maxRetries == 0) {
            // Use the original sink handler
            this.sinkHandler = nonRetryHandler;
        } else if (maxRetries > 0) {
            // Wrap the sink handler with a retry handler
            this.sinkHandler = new HttpSinkHandlerRetryWrapper(this.httpSinkConfig.connectorConfig, nonRetryHandler);
        } else {
            throw new IllegalArgumentException("Max retries must be greater than or equal to 0.");
        }
        boolean isParallelized = this.httpSinkConfig.connectorConfig.isParallelized();
        int parallelism = isParallelized ? this.httpSinkConfig.connectorConfig.getParallelism() : 1;
        executor = new ThreadPoolExecutor(parallelism, parallelism, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(), new EventMeshThreadFactory("http-sink-handler"));
    }

    @Override
    public void start() throws Exception {
        this.sinkHandler.start();
        for (int i = 0; i < this.httpSinkConfig.connectorConfig.getParallelism(); i++) {
            executor.execute(() -> {
                while (isStart.get()) {
                    ConnectRecord connectRecord = null;
                    try {
                        connectRecord = queue.poll(2, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    if (connectRecord != null) {
                        sinkHandler.handle(connectRecord);
                    }
                }
            });
        }
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.httpSinkConfig.connectorConfig.getConnectorName();
    }

    @Override
    public void onException(ConnectRecord record) {

    }

    @Override
    public void stop() throws Exception {
        isStart.set(false);
        while (!queue.isEmpty()) {
            ConnectRecord record = queue.poll();
            this.sinkHandler.handle(record);
        }
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.sinkHandler.stop();
        log.info("All tasks completed, start shut down http sink connector");
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        for (ConnectRecord sinkRecord : sinkRecords) {
            try {
                if (Objects.isNull(sinkRecord)) {
                    log.warn("ConnectRecord data is null, ignore.");
                    continue;
                }
                queue.put(sinkRecord);
            } catch (Exception e) {
                log.error("Failed to sink message via HTTP. ", e);
            }
        }
    }
}
