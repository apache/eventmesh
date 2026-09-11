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

package org.apache.eventmesh.connector.lark.source;

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
 * New-architecture Lark source connector: subscribes to Lark event callbacks (event subscription
 * mode), answers the url_verification challenge, and exposes event pushes as CloudEvents.
 */
@Slf4j
public class LarkSourceConnector implements SourceConnector {

    private int port;
    private String path;
    private String verificationToken;

    private HttpServer server;
    private final LinkedBlockingQueue<CloudEvent> buffer = new LinkedBlockingQueue<>();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void init(Properties props) {
        this.port = Integer.parseInt(props.getProperty("connector.port", "8092"));
        this.path = props.getProperty("connector.path", "/lark");
        this.verificationToken = props.getProperty("connector.verificationToken", "");
    }

    private synchronized void ensureStarted() {
        if (server != null) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext(path, this::handle);
            server.start();
            log.info("lark source listening on {}{}", port, path);
        } catch (Exception e) {
            throw new RuntimeException("lark source server start failed: " + e.getMessage(), e);
        }
    }

    private void handle(HttpExchange exchange) {
        try {
            byte[] body = exchange.getRequestBody().readAllBytes();
            JsonNode root = mapper.readTree(body);
            byte[] resp;
            if ("url_verification".equals(root.path("type").asText(""))) {
                // challenge handshake: echo the challenge back
                resp = mapper.createObjectNode().put("challenge", root.path("challenge").asText("")).toString()
                    .getBytes(StandardCharsets.UTF_8);
            } else {
                if (!verificationToken.isEmpty()
                    && !verificationToken.equals(root.path("token").asText(""))) {
                    exchange.sendResponseHeaders(403, -1);
                    return;
                }
                CloudEvent event = CloudEventBuilder.v1()
                    .withId("lark-" + root.path("header").path("event_id").asText(String.valueOf(System.nanoTime())))
                    .withSource(URI.create("lark"))
                    .withType("lark." + root.path("header").path("event_type").asText("event"))
                    .withSubject(root.path("header").path("app_id").asText(""))
                    .withDataContentType("application/json")
                    .withData(body)
                    .build();
                buffer.offer(event);
                resp = "{\"code\":0}".getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        } catch (Exception e) {
            log.warn("lark source request failed: {}", e.toString());
            try {
                exchange.sendResponseHeaders(500, -1);
            } catch (Exception ignored) {
                // client gone
            }
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
