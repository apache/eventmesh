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

import org.apache.eventmesh.connector.http.sink.config.SinkConnectorConfig;
import org.apache.eventmesh.connector.http.sink.data.HttpConnectRecord;
import org.apache.eventmesh.connector.http.sink.data.HttpRetryEvent;
import org.apache.eventmesh.connector.http.sink.data.MultiHttpRequestContext;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AbstractHttpSinkHandler is an abstract class that provides a base implementation for HttpSinkHandler.
 */
public abstract class AbstractHttpSinkHandler implements HttpSinkHandler {

    private final SinkConnectorConfig sinkConnectorConfig;

    private final List<URI> urls;

    protected AbstractHttpSinkHandler(SinkConnectorConfig sinkConnectorConfig) {
        this.sinkConnectorConfig = sinkConnectorConfig;
        // Initialize URLs
        String[] urlStrings = sinkConnectorConfig.getUrls();
        this.urls = Arrays.stream(urlStrings)
            .map(URI::create)
            .collect(Collectors.toList());
    }

    public SinkConnectorConfig getSinkConnectorConfig() {
        return sinkConnectorConfig;
    }

    public List<URI> getUrls() {
        return urls;
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
        attributes.put(MultiHttpRequestContext.NAME, new MultiHttpRequestContext(urls.size()));

        // send the record to all URLs
        for (URI url : urls) {
            // convert ConnectRecord to HttpConnectRecord
            String type = String.format("%s.%s.%s",
                this.sinkConnectorConfig.getConnectorName(), url.getScheme(),
                this.sinkConnectorConfig.getWebhookConfig().isActivate() ? "webhook" : "common");
            HttpConnectRecord httpConnectRecord = HttpConnectRecord.convertConnectRecord(record, type);

            // add retry event to attributes
            HttpRetryEvent retryEvent = new HttpRetryEvent();
            retryEvent.setMaxRetries(sinkConnectorConfig.getRetryConfig().getMaxRetries());
            attributes.put(HttpRetryEvent.PREFIX + httpConnectRecord.getHttpRecordId(), retryEvent);

            // deliver the record
            deliver(url, httpConnectRecord, attributes);
        }
    }

}
