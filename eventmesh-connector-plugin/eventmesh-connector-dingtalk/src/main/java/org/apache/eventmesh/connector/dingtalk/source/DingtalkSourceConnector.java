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

package org.apache.eventmesh.connector.dingtalk.source;

import org.apache.eventmesh.connector.SourceConnector;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import lombok.extern.slf4j.Slf4j;

/**
 * New-architecture DingTalk source connector: receives outgoing-callback (robot) pushes from
 * DingTalk on an HTTP endpoint, verifies the HMAC signature, and exposes them as CloudEvents.
 *
 * <p>Push-only platform — same receiver pattern as the master-branch IM connectors, but the
 * source side ingests the platform webhook instead of polling an API.</p>
 */
@Slf4j
public class DingtalkSourceConnector implements SourceConnector {

    private int port;
    private String path;
    private String appSecret;

    private HttpServer server;
    private final LinkedBlockingQueue<CloudEvent> buffer = new LinkedBlockingQueue<>();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void init(Properties props) {
        this.port = Integer.parseInt(props.getProperty("connector.port", "8091"));
        this.path = props.getProperty("connector.path", "/dingtalk");
        this.appSecret = props.getProperty("connector.appSecret", "");
    }

    private synchronized void ensureStarted() {
        if (server != null) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext(path, this::handle);
            server.start();
            log.info("dingtalk source listening on {}{}", port, path);
        } catch (Exception e) {
            throw new RuntimeException("dingtalk source server start failed: " + e.getMessage(), e);
        }
    }

    private void handle(HttpExchange exchange) {
        try {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String timestamp = exchange.getRequestHeaders().getFirst("timestamp");
            String sign = exchange.getRequestHeaders().getFirst("sign");
            if (!appSecret.isEmpty() && !verify(timestamp, sign)) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }
            JsonNode root = mapper.readTree(body);
            CloudEvent event = CloudEventBuilder.v1()
                .withId("dingtalk-" + root.path("msgId").asText(String.valueOf(System.nanoTime())))
                .withSource(URI.create("dingtalk"))
                .withType("dingtalk.message")
                .withSubject(root.path("conversationId").asText(""))
                .withDataContentType("application/json")
                .withData(body)
                .build();
            buffer.offer(event);
            byte[] resp = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        } catch (Exception e) {
            log.warn("dingtalk source request failed: {}", e.toString());
            try {
                exchange.sendResponseHeaders(500, -1);
            } catch (Exception ignored) {
                // client gone
            }
        }
    }

    /** DingTalk robot outgoing callback: base64(HMAC-SHA256(timestamp + "\n" + appSecret)). */
    private boolean verify(String timestamp, String sign) {
        if (timestamp == null || sign == null) {
            return false;
        }
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = Base64.getEncoder().encodeToString(
                mac.doFinal((timestamp + "\n" + appSecret).getBytes(StandardCharsets.UTF_8)));
            return expected.equals(sign);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<CloudEvent> poll() {
        ensureStarted();
        List<CloudEvent> out = new ArrayList<>(buffer.size());
        buffer.drainTo(out);
        return out;
    }

    @Override
    public void commit(CloudEvent lastPublished) {
        // Push-style source: accepted webhook payloads are checkpoint-free.
    }
}
