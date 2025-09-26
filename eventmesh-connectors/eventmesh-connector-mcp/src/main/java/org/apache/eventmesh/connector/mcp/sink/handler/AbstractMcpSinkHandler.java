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

package org.apache.eventmesh.connector.mcp.sink.handler;

import lombok.Getter;
import org.apache.eventmesh.common.config.connector.mcp.SinkConnectorConfig;
import org.apache.eventmesh.connector.mcp.sink.data.McpAttemptEvent;
import org.apache.eventmesh.connector.mcp.sink.data.McpConnectRecord;
import org.apache.eventmesh.connector.mcp.sink.data.MultiMcpRequestContext;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public abstract class AbstractMcpSinkHandler implements McpSinkHandler {
    @Getter
    private final SinkConnectorConfig sinkConnectorConfig;

    @Getter
    private final List<URI> urls;

    private final McpDeliveryStrategy deliveryStrategy;

    private int roundRobinIndex = 0;

    protected AbstractMcpSinkHandler(SinkConnectorConfig sinkConnectorConfig) {
        this.sinkConnectorConfig = sinkConnectorConfig;
        this.deliveryStrategy = McpDeliveryStrategy.valueOf(sinkConnectorConfig.getDeliveryStrategy());
        // Initialize URLs
        String[] urlStrings = sinkConnectorConfig.getUrls();
        this.urls = Arrays.stream(urlStrings)
                .map(URI::create)
                .collect(Collectors.toList());
    }

    /**
     * Processes a ConnectRecord by sending it over HTTP or HTTPS. This method should be called for each ConnectRecord that needs to be processed.
     *
     * @param record the ConnectRecord to process
     */
    @Override
    public void handle(ConnectRecord record) {
        // build attributes
        Map<String, Object> attributes = new ConcurrentHashMap<>();

        switch (deliveryStrategy) {
            case ROUND_ROBIN:
                attributes.put(MultiMcpRequestContext.NAME, new MultiMcpRequestContext(1));
                URI url = urls.get(roundRobinIndex);
                roundRobinIndex = (roundRobinIndex + 1) % urls.size();
                sendRecordToUrl(record, attributes, url);
                break;
            case BROADCAST:
                attributes.put(MultiMcpRequestContext.NAME, new MultiMcpRequestContext(urls.size()));
                // send the record to all URLs
                urls.forEach(url0 -> sendRecordToUrl(record, attributes, url0));
                break;
            default:
                throw new IllegalArgumentException("Unknown delivery strategy: " + deliveryStrategy);
        }
    }

    private void sendRecordToUrl(ConnectRecord record, Map<String, Object> attributes, URI url) {
        // convert ConnectRecord to HttpConnectRecord
        String type = String.format("%s.%s.%s",
                this.sinkConnectorConfig.getConnectorName(), url.getScheme(),
                "common");
        McpConnectRecord mcpConnectRecord = McpConnectRecord.convertConnectRecord(record, type);

        // add AttemptEvent to the attributes
        McpAttemptEvent attemptEvent = new McpAttemptEvent(this.sinkConnectorConfig.getRetryConfig().getMaxRetries() + 1);
        attributes.put(McpAttemptEvent.PREFIX + mcpConnectRecord.getMcpRecordId(), attemptEvent);

        // deliver the record
        deliver(url, mcpConnectRecord, attributes, record);
    }
}
