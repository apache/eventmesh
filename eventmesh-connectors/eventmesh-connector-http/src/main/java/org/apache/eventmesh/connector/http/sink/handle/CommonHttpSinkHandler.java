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

import org.apache.eventmesh.connector.http.sink.config.SinkConnectorConfig;
import org.apache.eventmesh.connector.http.sink.data.HttpConnectRecord;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import lombok.extern.slf4j.Slf4j;

/**
 * Common HttpSinkHandler to handle ConnectRecord
 */
@Slf4j
public class CommonHttpSinkHandler implements HttpSinkHandler {

    private final SinkConnectorConfig connectorConfig;

    private WebClient webClient;

    private final String type;

    // store the received data, when webhook is enabled
    private final BlockingQueue<JSONObject> receivedDataQueue;

    public CommonHttpSinkHandler(SinkConnectorConfig sinkConnectorConfig) {
        SinkConnectorConfig.populateFieldsWithDefaults(sinkConnectorConfig);
        this.connectorConfig = sinkConnectorConfig;
        this.receivedDataQueue = this.connectorConfig.isWebhook() ? new LinkedBlockingQueue<>() : null;
        type = String.format("%s.%s.%s",
            sinkConnectorConfig.getConnectorName(),
            sinkConnectorConfig.isSsl() ? "https" : "http",
            sinkConnectorConfig.isWebhook() ? "webhook" : "common");
    }

    /**
     * Get the oldest data in the queue
     *
     * @return received data
     */
    public Object getReceivedData() {
        if (!this.connectorConfig.isWebhook()) {
            return null;
        }
        return this.receivedDataQueue.poll();
    }

    /**
     * Get all received data
     *
     * @return all received data
     */
    public Object[] getAllReceivedData() {
        if (!connectorConfig.isWebhook() || receivedDataQueue.isEmpty()) {
            return new Object[0];
        }
        Object[] arr = receivedDataQueue.toArray();
        receivedDataQueue.clear();
        return arr;
    }


    @Override
    public void start() {
        // Create WebClient
        doInitWebClient();
    }

    private void doInitWebClient() {
        final Vertx vertx = Vertx.vertx();
        WebClientOptions options = new WebClientOptions()
            .setDefaultHost(this.connectorConfig.getHost())
            .setDefaultPort(this.connectorConfig.getPort())
            .setSsl(this.connectorConfig.isSsl())
            .setKeepAlive(this.connectorConfig.isKeepAlive())
            .setKeepAliveTimeout(this.connectorConfig.getKeepAliveTimeout() / 1000)
            .setIdleTimeout(this.connectorConfig.getIdleTimeout())
            .setIdleTimeoutUnit(TimeUnit.MILLISECONDS)
            .setConnectTimeout(this.connectorConfig.getConnectionTimeout())
            .setMaxPoolSize(this.connectorConfig.getMaxConnectionPoolSize());
        this.webClient = WebClient.create(vertx, options);
    }


    @Override
    public void handle(ConnectRecord record) {
        // create headers
        MultiMap headers = HttpHeaders.headers()
            .set(HttpHeaderNames.ACCEPT, "application/json; charset=utf-8");

        // convert ConnectRecord to HttpConnectRecord
        HttpConnectRecord httpConnectRecord = convertToHttpConnectRecord(record);

        // send the request
        this.webClient.post(this.connectorConfig.getPath())
            .putHeaders(headers)
            .sendJson(httpConnectRecord)
            .onSuccess(res -> {
                Long timestamp = record.getTimestamp();
                Map<String, ?> offset = record.getPosition().getOffset().getOffset();
                log.info("Request sent successfully. Record: timestamp={}, offset={}", timestamp, offset);
                // Determine whether the status code is 200
                if (res.statusCode() == HttpResponseStatus.OK.code()) {
                    // store the received data, when webhook is enabled
                    if (this.connectorConfig.isWebhook()) {
                        String dataStr = res.body().toString();
                        if (dataStr.isEmpty()) {
                            log.warn("Received data is empty.");
                            return;
                        }
                        JSONObject receivedData = JSON.parseObject(dataStr);
                        if (receivedDataQueue.size() == Integer.MAX_VALUE) {
                            // if the queue is full, remove the oldest element
                            JSONObject removedData = receivedDataQueue.poll();
                            log.info("The queue is full, remove the oldest element: {}", removedData);
                        }
                        boolean b = receivedDataQueue.offer(receivedData);
                        if (b) {
                            log.info("Successfully put the received data into the queue: {}", receivedData);
                        } else {
                            log.error("Failed to put the received data into the queue: {}", receivedData);
                        }
                    }
                } else {
                    log.error("Unexpected response received. Record: timestamp={}, offset={}. Response: code={} header={}, body={}",
                        timestamp,
                        offset,
                        res.statusCode(),
                        res.headers(),
                        res.body().toString()
                    );
                }
            })
            .onFailure(err -> {
                Long timestamp = record.getTimestamp();
                Map<String, ?> offset = record.getPosition().getOffset().getOffset();
                log.error("Request failed to send. Record: timestamp={}, offset={}", timestamp, offset, err);
            });
    }

    /**
     * Convert ConnectRecord to HttpConnectRecord
     *
     * @param record the ConnectRecord to convert
     * @return the converted HttpConnectRecord
     */
    private HttpConnectRecord convertToHttpConnectRecord(ConnectRecord record) {
        HttpConnectRecord httpConnectRecord = new HttpConnectRecord();
        httpConnectRecord.setType(this.type);
        LocalDateTime currentTime = LocalDateTime.now();
        httpConnectRecord.setTimestamp(currentTime.toString());
        httpConnectRecord.setData(record);
        return httpConnectRecord;
    }


    @Override
    public void stop() {
        if (this.webClient != null) {
            this.webClient.close();
        } else {
            log.warn("WebClient is null, ignore.");
        }
    }
}