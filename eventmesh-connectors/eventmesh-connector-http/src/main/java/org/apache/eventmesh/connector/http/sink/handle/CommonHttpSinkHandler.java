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

import org.apache.eventmesh.common.remote.offset.http.HttpRecordOffset;
import org.apache.eventmesh.connector.http.sink.config.SinkConnectorConfig;
import org.apache.eventmesh.connector.http.sink.data.HttpConnectRecord;
import org.apache.eventmesh.connector.http.util.HttpUtils;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Common HTTP/HTTPS Sink Handler implementation to handle ConnectRecords by sending them over HTTP or HTTPS to configured URLs.
 *
 * <p>This handler initializes a WebClient for making HTTP requests based on the provided SinkConnectorConfig.
 * It handles processing ConnectRecords by converting them to HttpConnectRecord and sending them asynchronously to each configured URL using the
 * WebClient.</p>
 *
 * <p>The handler uses Vert.x's WebClient to perform HTTP/HTTPS requests. It initializes the WebClient in the {@link #start()}
 * method and closes it in the {@link #stop()} method to manage resources efficiently.</p>
 *
 * <p>Each ConnectRecord is processed and sent to all configured URLs concurrently using asynchronous HTTP requests.</p>
 */
@Slf4j
@Getter
public class CommonHttpSinkHandler implements HttpSinkHandler {

    private final SinkConnectorConfig connectorConfig;

    private final List<URI> urls;

    private WebClient webClient;


    public CommonHttpSinkHandler(SinkConnectorConfig sinkConnectorConfig) {
        this.connectorConfig = sinkConnectorConfig;
        // Initialize URLs
        String[] urlStrings = sinkConnectorConfig.getUrls();
        this.urls = Arrays.stream(urlStrings)
            .map(URI::create)
            .collect(Collectors.toList());
    }

    /**
     * Initializes the WebClient for making HTTP requests based on the provided SinkConnectorConfig.
     */
    @Override
    public void start() {
        // Create WebClient
        doInitWebClient();
    }

    /**
     * Initializes the WebClient with the provided configuration options.
     */
    private void doInitWebClient() {
        final Vertx vertx = Vertx.vertx();
        WebClientOptions options = new WebClientOptions()
            .setKeepAlive(this.connectorConfig.isKeepAlive())
            .setKeepAliveTimeout(this.connectorConfig.getKeepAliveTimeout() / 1000)
            .setIdleTimeout(this.connectorConfig.getIdleTimeout())
            .setIdleTimeoutUnit(TimeUnit.MILLISECONDS)
            .setConnectTimeout(this.connectorConfig.getConnectionTimeout())
            .setMaxPoolSize(this.connectorConfig.getMaxConnectionPoolSize());
        this.webClient = WebClient.create(vertx, options);
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
            String type = String.format("%s.%s.%s", connectorConfig.getConnectorName(), url.getScheme(), "common");
            HttpConnectRecord httpConnectRecord = HttpConnectRecord.convertConnectRecord(record, type);
            deliver(url, httpConnectRecord);
        }
    }


    /**
     * Processes HttpConnectRecord on specified URL while returning its own processing logic.
     * This method sends the HttpConnectRecord to the specified URL using the WebClient.
     *
     * @param url               URI to which the HttpConnectRecord should be sent
     * @param httpConnectRecord HttpConnectRecord to process
     * @return processing chain
     */
    @Override
    public Future<HttpResponse<Buffer>> deliver(URI url, HttpConnectRecord httpConnectRecord) {
        // create headers
        MultiMap headers = HttpHeaders.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8")
            .set(HttpHeaderNames.ACCEPT, "application/json; charset=utf-8");

        // get timestamp and offset
        Long timestamp = httpConnectRecord.getData().getTimestamp();
        Map<String, ?> offset = null;
        try {
            // May throw NullPointerException.
            offset = ((HttpRecordOffset) httpConnectRecord.getData().getPosition().getRecordOffset()).getOffsetMap();
        } catch (NullPointerException e) {
            // ignore null pointer exception
        }
        final Map<String, ?> finalOffset = offset;

        // send the request
        return this.webClient.post(url.getPath())
            .host(url.getHost())
            .port(url.getPort() == -1 ? (Objects.equals(url.getScheme(), "https") ? 443 : 80) : url.getPort())
            .putHeaders(headers)
            .ssl(Objects.equals(url.getScheme(), "https"))
            .sendJson(httpConnectRecord)
            .onSuccess(res -> {
                log.info("Request sent successfully. Record: timestamp={}, offset={}", timestamp, finalOffset);
                // log the response
                if (HttpUtils.is2xxSuccessful(res.statusCode())) {
                    if (log.isDebugEnabled()) {
                        log.debug("Received successful response: statusCode={}. Record: timestamp={}, offset={}, responseBody={}",
                            res.statusCode(), timestamp, finalOffset, res.bodyAsString());
                    } else {
                        log.info("Received successful response: statusCode={}. Record: timestamp={}, offset={}", res.statusCode(), timestamp,
                            finalOffset);
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.warn("Received non-2xx response: statusCode={}. Record: timestamp={}, offset={}, responseBody={}",
                            res.statusCode(), timestamp, finalOffset, res.bodyAsString());
                    } else {
                        log.warn("Received non-2xx response: statusCode={}. Record: timestamp={}, offset={}", res.statusCode(), timestamp,
                            finalOffset);
                    }
                }

            })
            .onFailure(err -> log.error("Request failed to send. Record: timestamp={}, offset={}", timestamp, finalOffset, err));
    }


    /**
     * Cleans up and releases resources used by the HTTP/HTTPS handler.
     */
    @Override
    public void stop() {
        if (this.webClient != null) {
            this.webClient.close();
        } else {
            log.warn("WebClient is null, ignore.");
        }
    }


}