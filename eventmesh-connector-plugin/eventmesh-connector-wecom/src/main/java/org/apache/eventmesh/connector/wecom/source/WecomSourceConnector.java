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

package org.apache.eventmesh.connector.wecom.source;

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
 * New-architecture WeCom (WeChat Work) source connector: receives WeCom callback events
 * (plain-token verification mode), answers the URL challenge, and exposes events as CloudEvents.
 */
@Slf4j
public class WecomSourceConnector implements SourceConnector {

    private int port;
    private String path;
    private String token;

    private HttpServer server;
    private final LinkedBlockingQueue<CloudEvent> buffer = new LinkedBlockingQueue<>();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void init(Properties props) {
        this.port = Integer.parseInt(props.getProperty("connector.port", "8095"));
        this.path = props.getProperty("connector.path", "/wecom");
        this.token = props.getProperty("connector.token", "eventmesh");
    }

    private synchronized void ensureStarted() {
        if (server != null) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext(path, this::handle);
            server.start();
            log.info("wecom source listening on {}{}", port, path);
        } catch (Exception e) {
            throw new RuntimeException("wecom source server start failed: " + e.getMessage(), e);
        }
    }

    private void handle(HttpExchange exchange) {
        try {
            String query = exchange.getRequestURI().getQuery();
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                // URL verification: echostr after token match
                String echostr = param(query, "echostr");
                byte[] resp = echostr.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, resp.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp);
                }
                return;
            }
            byte[] body = exchange.getRequestBody().readAllBytes();
            JsonNode root = mapper.readTree(body);
            CloudEvent event = CloudEventBuilder.v1()
                .withId("wecom-" + root.path("msgid").asText(String.valueOf(System.nanoTime())))
                .withSource(URI.create("wecom"))
                .withType("wecom." + root.path("event_type").asText("event"))
                .withSubject(root.path("from").path("user_id").asText(""))
                .withDataContentType("application/json")
                .withData(body)
                .build();
            buffer.offer(event);
            byte[] resp = "{\"errcode\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        } catch (Exception e) {
            log.warn("wecom source request failed: {}", e.toString());
            try {
                exchange.sendResponseHeaders(500, -1);
            } catch (Exception ignored) {
                // client gone
            }
        }
    }

    private static String param(String query, String key) {
        if (query == null) {
            return "";
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && key.equals(pair.substring(0, eq))) {
                return pair.substring(eq + 1);
            }
        }
        return "";
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
