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

package org.apache.eventmesh.connector.http.sink.handler;

import org.apache.eventmesh.common.config.connector.http.SinkConnectorConfig;
import org.apache.eventmesh.connector.http.sink.data.HttpAttemptEvent;
import org.apache.eventmesh.connector.http.sink.data.HttpConnectRecord;
import org.apache.eventmesh.connector.http.sink.data.MultiHttpRequestContext;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.Getter;

/**
 * AbstractHttpSinkHandler is an abstract class that provides a base implementation for HttpSinkHandler.
 */
public abstract class AbstractHttpSinkHandler implements HttpSinkHandler {

    @Getter
    private final SinkConnectorConfig sinkConnectorConfig;

    @Getter
    private final List<URI> urls;

    private final HttpDeliveryStrategy deliveryStrategy;

    private int roundRobinIndex = 0;

    protected AbstractHttpSinkHandler(SinkConnectorConfig sinkConnectorConfig) {
        this.sinkConnectorConfig = sinkConnectorConfig;
        this.deliveryStrategy = HttpDeliveryStrategy.valueOf(sinkConnectorConfig.getDeliveryStrategy());
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
                attributes.put(MultiHttpRequestContext.NAME, new MultiHttpRequestContext(1));
                URI url = urls.get(roundRobinIndex);
                roundRobinIndex = (roundRobinIndex + 1) % urls.size();
                sendRecordToUrl(record, attributes, url);
                break;
            case BROADCAST:
                for (URI broadcastUrl : urls) {
                    attributes.put(MultiHttpRequestContext.NAME, new MultiHttpRequestContext(urls.size()));
                    sendRecordToUrl(record, attributes, broadcastUrl);
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown delivery strategy: " + deliveryStrategy);
        }
    }

    private void sendRecordToUrl(ConnectRecord record, Map<String, Object> attributes, URI url) {
        // convert ConnectRecord to HttpConnectRecord
        String type = String.format("%s.%s.%s",
            this.sinkConnectorConfig.getConnectorName(), url.getScheme(),
            this.sinkConnectorConfig.getWebhookConfig().isActivate() ? "webhook" : "common");
        HttpConnectRecord httpConnectRecord = HttpConnectRecord.convertConnectRecord(record, type);

        // add AttemptEvent to the attributes
        HttpAttemptEvent attemptEvent = new HttpAttemptEvent(this.sinkConnectorConfig.getRetryConfig().getMaxRetries() + 1);
        attributes.put(HttpAttemptEvent.PREFIX + httpConnectRecord.getHttpRecordId(), attemptEvent);

        // deliver the record
        deliver(url, httpConnectRecord, attributes, record);
    }

}
