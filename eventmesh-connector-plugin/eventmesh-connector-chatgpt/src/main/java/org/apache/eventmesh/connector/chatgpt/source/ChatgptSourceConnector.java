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

package org.apache.eventmesh.connector.chatgpt.source;

import org.apache.eventmesh.connector.SourceConnector;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import lombok.extern.slf4j.Slf4j;

/**
 * New-architecture ChatGPT source connector: exposes an HTTP prompt endpoint (push), optionally
 * completes the prompt through the OpenAI REST API, and hands the result to the runtime as
 * CloudEvents on {@link #poll()}.
 *
 * <p>External-system logic ported from the master-branch (openconnect) chatgpt connector
 * (Vert.x server + OpenaiManager), reduced to JDK HttpServer + a direct REST call so the plugin
 * carries no heavyweight SDK.</p>
 */
@Slf4j
public class ChatgptSourceConnector implements SourceConnector {

    private int port;
    private String path;
    private String openaiApiKey;
    private String model;
    private long pollTimeoutMs;

    private HttpServer server;
    private final LinkedBlockingQueue<CloudEvent> buffer = new LinkedBlockingQueue<>();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void init(Properties props) {
        this.port = Integer.parseInt(props.getProperty("connector.port", "8090"));
        this.path = props.getProperty("connector.path", "/chatgpt");
        this.openaiApiKey = props.getProperty("connector.openaiApiKey", "");
        this.model = props.getProperty("connector.model", "gpt-3.5-turbo");
        this.pollTimeoutMs = Long.parseLong(props.getProperty("connector.pollTimeoutMs", "1000"));
    }

    private synchronized void ensureStarted() {
        if (server != null) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext(path, this::handle);
            server.start();
            log.info("chatgpt source listening on {}{}", port, path);
        } catch (Exception e) {
            throw new RuntimeException("chatgpt source server start failed: " + e.getMessage(), e);
        }
    }

    private void handle(HttpExchange exchange) {
        try {
            byte[] body = exchange.getRequestBody().readAllBytes();
            JsonNode request = mapper.readTree(body);
            String prompt = request.path("prompt").asText("");
            String answer = openaiApiKey.isEmpty() ? "" : complete(prompt);

            CloudEvent event = CloudEventBuilder.v1()
                .withId("chatgpt-" + System.nanoTime())
                .withSource(URI.create("chatgpt"))
                .withType("chatgpt.completion")
                .withSubject(prompt)
                .withDataContentType("application/json")
                .withData(mapper.createObjectNode().put("prompt", prompt).put("answer", answer).toString()
                    .getBytes(StandardCharsets.UTF_8))
                .build();
            buffer.offer(event);

            byte[] resp = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        } catch (Exception e) {
            log.warn("chatgpt source request failed: {}", e.toString());
            try {
                exchange.sendResponseHeaders(500, -1);
            } catch (Exception ignored) {
                // client gone
            }
        }
    }

    /** Call the OpenAI chat-completion REST API (no SDK, plain HttpURLConnection). */
    private String complete(String prompt) {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                new java.net.URL("https://api.openai.com/v1/chat/completions").openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Authorization", "Bearer " + openaiApiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            com.fasterxml.jackson.databind.node.ObjectNode rootPayload = mapper.createObjectNode();
            rootPayload.put("model", model);
            rootPayload.putArray("messages").addObject().put("role", "user").put("content", prompt);
            final String payload = rootPayload.toString();
            conn.getOutputStream().write(payload.getBytes(StandardCharsets.UTF_8));
            if (conn.getResponseCode() != 200) {
                log.warn("openai completion http {}: skipping answer", conn.getResponseCode());
                return "";
            }
            JsonNode root = mapper.readTree(conn.getInputStream().readAllBytes());
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            log.warn("openai completion failed: {}", e.toString());
            return "";
        }
    }

    @Override
    public List<CloudEvent> poll() {
        ensureStarted();
        List<CloudEvent> out = new ArrayList<>();
        try {
            CloudEvent e = buffer.poll(pollTimeoutMs, TimeUnit.MILLISECONDS);
            while (e != null) {
                out.add(e);
                e = buffer.poll();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        return out;
    }

    @Override
    public void commit(CloudEvent lastPublished) {
        // Push-style source: the HTTP handler already enqueued the event; nothing to checkpoint.
    }
}
