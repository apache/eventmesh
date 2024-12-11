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

import org.apache.eventmesh.common.config.connector.http.SinkConnectorConfig;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.http.sink.data.HttpAttemptEvent;
import org.apache.eventmesh.connector.http.sink.data.HttpConnectRecord;
import org.apache.eventmesh.connector.http.sink.data.MultiHttpRequestContext;
import org.apache.eventmesh.connector.http.sink.handler.AbstractHttpSinkHandler;
import org.apache.eventmesh.connector.http.util.HttpUtils;
import org.apache.eventmesh.openconnect.offsetmgmt.api.callback.SendExceptionContext;
import org.apache.eventmesh.openconnect.offsetmgmt.api.callback.SendResult;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.net.URI;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
public class CommonHttpSinkHandler extends AbstractHttpSinkHandler {

    private WebClient webClient;


    public CommonHttpSinkHandler(SinkConnectorConfig sinkConnectorConfig) {
        super(sinkConnectorConfig);
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
        SinkConnectorConfig sinkConnectorConfig = getSinkConnectorConfig();
        final Vertx vertx = Vertx.vertx();
        WebClientOptions options = new WebClientOptions()
            .setKeepAlive(sinkConnectorConfig.isKeepAlive())
            .setKeepAliveTimeout(sinkConnectorConfig.getKeepAliveTimeout() / 1000)
            .setIdleTimeout(sinkConnectorConfig.getIdleTimeout())
            .setIdleTimeoutUnit(TimeUnit.MILLISECONDS)
            .setConnectTimeout(sinkConnectorConfig.getConnectionTimeout())
            .setMaxPoolSize(sinkConnectorConfig.getMaxConnectionPoolSize())
            .setPipelining(sinkConnectorConfig.isParallelized());
        this.webClient = WebClient.create(vertx, options);
    }

    /**
     * Processes HttpConnectRecord on specified URL while returning its own processing logic. This method sends the HttpConnectRecord to the specified
     * URL using the WebClient.
     *
     * @param url               URI to which the HttpConnectRecord should be sent
     * @param httpConnectRecord HttpConnectRecord to process
     * @param attributes        additional attributes to be used in processing
     * @return processing chain
     */
    @Override
    public Future<HttpResponse<Buffer>> deliver(URI url, HttpConnectRecord httpConnectRecord, Map<String, Object> attributes,
                                                ConnectRecord connectRecord) {
        // create headers
        Map<String, Object> extensionMap = new HashMap<>();
        Set<String> extensionKeySet = httpConnectRecord.getExtensions().keySet();
        for (String extensionKey : extensionKeySet) {
            Object v = httpConnectRecord.getExtensions().getObject(extensionKey);
            extensionMap.put(extensionKey, v);
        }

        MultiMap headers = HttpHeaders.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8")
            .set(HttpHeaderNames.ACCEPT, "application/json; charset=utf-8")
            .set("extension", JsonUtils.toJSONString(extensionMap));
        // get timestamp and offset
        Long timestamp = httpConnectRecord.getCreateTime()
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();

        // send the request
        return this.webClient.post(url.getPath())
            .host(url.getHost())
            .port(url.getPort() == -1 ? (Objects.equals(url.getScheme(), "https") ? 443 : 80) : url.getPort())
            .putHeaders(headers)
            .ssl(Objects.equals(url.getScheme(), "https"))
            .sendJson(httpConnectRecord.getData())
            .onSuccess(res -> {
                log.info("Request sent successfully. Record: timestamp={}", timestamp);

                Exception e = null;

                // log the response
                if (HttpUtils.is2xxSuccessful(res.statusCode())) {
                    if (log.isDebugEnabled()) {
                        log.debug("Received successful response: statusCode={}. Record: timestamp={}, responseBody={}",
                            res.statusCode(), timestamp, res.bodyAsString());
                    } else {
                        log.info("Received successful response: statusCode={}. Record: timestamp={}", res.statusCode(), timestamp);
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.warn("Received non-2xx response: statusCode={}. Record: timestamp={}, responseBody={}",
                            res.statusCode(), timestamp, res.bodyAsString());
                    } else {
                        log.warn("Received non-2xx response: statusCode={}. Record: timestamp={}", res.statusCode(), timestamp);
                    }

                    e = new RuntimeException("Unexpected HTTP response code: " + res.statusCode());
                }

                // try callback
                tryCallback(httpConnectRecord, e, attributes, connectRecord);
            }).onFailure(err -> {
                log.error("Request failed to send. Record: timestamp={}", timestamp, err);

                // try callback
                tryCallback(httpConnectRecord, err, attributes, connectRecord);
            });
    }

    /**
     * Tries to call the callback based on the result of the request.
     *
     * @param httpConnectRecord the HttpConnectRecord to use
     * @param e                 the exception thrown during the request, may be null
     * @param attributes        additional attributes to be used in processing
     */
    private void tryCallback(HttpConnectRecord httpConnectRecord, Throwable e, Map<String, Object> attributes, ConnectRecord record) {
        // get and update the attempt event
        HttpAttemptEvent attemptEvent = (HttpAttemptEvent) attributes.get(HttpAttemptEvent.PREFIX + httpConnectRecord.getHttpRecordId());
        attemptEvent.updateEvent(e);

        // get and update the multiHttpRequestContext
        MultiHttpRequestContext multiHttpRequestContext = getAndUpdateMultiHttpRequestContext(attributes, attemptEvent);

        if (multiHttpRequestContext.isAllRequestsProcessed()) {
            // do callback
            if (record.getCallback() == null) {
                if (log.isDebugEnabled()) {
                    log.warn("ConnectRecord callback is null. Ignoring callback. {}", record);
                } else {
                    log.warn("ConnectRecord callback is null. Ignoring callback.");
                }
                return;
            }

            // get the last failed event
            HttpAttemptEvent lastFailedEvent = multiHttpRequestContext.getLastFailedEvent();
            if (lastFailedEvent == null) {
                // success
                record.getCallback().onSuccess(convertToSendResult(record));
            } else {
                // failure
                record.getCallback().onException(buildSendExceptionContext(record, lastFailedEvent.getLastException()));
            }
        } else {
            log.warn("still have requests to process, size {}|attempt num {}",
                multiHttpRequestContext.getRemainingRequests(), attemptEvent.getAttempts());
        }
    }


    /**
     * Gets and updates the multi http request context based on the provided attributes and HttpConnectRecord.
     *
     * @param attributes   the attributes to use
     * @param attemptEvent the HttpAttemptEvent to use
     * @return the updated multi http request context
     */
    private MultiHttpRequestContext getAndUpdateMultiHttpRequestContext(Map<String, Object> attributes, HttpAttemptEvent attemptEvent) {
        // get the multi http request context
        MultiHttpRequestContext multiHttpRequestContext = (MultiHttpRequestContext) attributes.get(MultiHttpRequestContext.NAME);

        // Check if the current attempted event has completed
        if (attemptEvent.isComplete()) {
            // decrement the counter
            multiHttpRequestContext.decrementRemainingRequests();

            if (attemptEvent.getLastException() != null) {
                // if all attempts are exhausted, set the last failed event
                multiHttpRequestContext.setLastFailedEvent(attemptEvent);
            }
        }

        return multiHttpRequestContext;
    }

    private SendResult convertToSendResult(ConnectRecord record) {
        SendResult result = new SendResult();
        result.setMessageId(record.getRecordId());
        if (org.apache.commons.lang3.StringUtils.isNotEmpty(record.getExtension("topic"))) {
            result.setTopic(record.getExtension("topic"));
        }
        return result;
    }

    private SendExceptionContext buildSendExceptionContext(ConnectRecord record, Throwable e) {
        SendExceptionContext sendExceptionContext = new SendExceptionContext();
        sendExceptionContext.setMessageId(record.getRecordId());
        sendExceptionContext.setCause(e);
        if (org.apache.commons.lang3.StringUtils.isNotEmpty(record.getExtension("topic"))) {
            sendExceptionContext.setTopic(record.getExtension("topic"));
        }
        return sendExceptionContext;
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