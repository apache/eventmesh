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

package org.apache.eventmesh.connector.slack.source;

import org.apache.eventmesh.connector.SourceConnector;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 * New-architecture Slack source connector: receives Slack Events API callbacks, verifies the
 * request signature (v0 HMAC scheme), and exposes event pushes as CloudEvents.
 */
@Slf4j
public class SlackSourceConnector implements SourceConnector {

    private int port;
    private String path;
    private String signingSecret;

    private HttpServer server;
    private final LinkedBlockingQueue<CloudEvent> buffer = new LinkedBlockingQueue<>();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void init(Properties props) {
        this.port = Integer.parseInt(props.getProperty("connector.port", "8093"));
        this.path = props.getProperty("connector.path", "/slack");
        this.signingSecret = props.getProperty("connector.signingSecret", "");
    }

    private synchronized void ensureStarted() {
        if (server != null) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext(path, this::handle);
            server.start();
            log.info("slack source listening on {}{}", port, path);
        } catch (Exception e) {
            throw new RuntimeException("slack source server start failed: " + e.getMessage(), e);
        }
    }

    private void handle(HttpExchange exchange) {
        try {
            byte[] body = exchange.getRequestBody().readAllBytes();
            if (!signingSecret.isEmpty()
                && !verify(exchange.getRequestHeaders().getFirst("X-Slack-Signature"),
                    exchange.getRequestHeaders().getFirst("X-Slack-Request-Timestamp"), body)) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }
            JsonNode root = mapper.readTree(body);
            if ("url_verification".equals(root.path("type").asText(""))) {
                byte[] resp = root.path("challenge").asText("").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, resp.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp);
                }
                return;
            }
            CloudEvent event = CloudEventBuilder.v1()
                .withId("slack-" + root.path("event_id").asText(String.valueOf(System.nanoTime())))
                .withSource(URI.create("slack"))
                .withType("slack." + root.path("event").path("type").asText("event"))
                .withSubject(root.path("team_id").asText(""))
                .withDataContentType("application/json")
                .withData(body)
                .build();
            buffer.offer(event);
            byte[] resp = "".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        } catch (Exception e) {
            log.warn("slack source request failed: {}", e.toString());
            try {
                exchange.sendResponseHeaders(500, -1);
            } catch (Exception ignored) {
                // client gone
            }
        }
    }

    /** Slack signing: v0=HMAC-SHA256("v0:timestamp:body", signingSecret) hex. */
    private boolean verify(String signature, String timestamp, byte[] body) {
        if (signature == null || timestamp == null) {
            return false;
        }
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String base = "v0:" + timestamp + ":" + new String(body, StandardCharsets.UTF_8);
            StringBuilder hex = new StringBuilder("v0=");
            for (byte b : mac.doFinal(base.getBytes(StandardCharsets.UTF_8))) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString().equals(signature);
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
        // Push-style source: accepted callback payloads are checkpoint-free.
    }
}
