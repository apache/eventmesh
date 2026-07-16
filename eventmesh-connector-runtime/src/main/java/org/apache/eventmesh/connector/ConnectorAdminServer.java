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

package org.apache.eventmesh.connector;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import lombok.extern.slf4j.Slf4j;

/**
 * Lightweight admin HTTP endpoint for the Connector Runtime process. Aggregates health/offsets/
 * metrics across all connector runtimes managed by a {@link ConnectorManager}.
 */
@Slf4j
public class ConnectorAdminServer {

    private final ConnectorManager manager;
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    public ConnectorAdminServer(ConnectorManager manager) {
        this.manager = manager;
    }

    public int start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", this::health);
        server.createContext("/offsets", this::offsets);
        server.createContext("/metrics", this::metrics);
        server.createContext("/control/start", this::controlStart);
        server.createContext("/control/stop", this::controlStop);
        server.createContext("/control/status", this::controlStatus);
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        int bound = server.getAddress().getPort();
        log.info("connector admin started on port {}", bound);
        return bound;
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void health(HttpExchange exchange) throws IOException {
        List<Map<String, Object>> runtimes = new ArrayList<>();
        for (ConnectorRuntime rt : manager.getRuntimes()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("running", rt.isRunning());
            r.put("source", rt.hasSource());
            r.put("sink", rt.hasSink());
            runtimes.add(r);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runtimes", runtimes);
        out.put("count", manager.size());
        writeJson(exchange, 200, out);
    }

    private void offsets(HttpExchange exchange) throws IOException {
        Map<String, String> all = new LinkedHashMap<>();
        for (ConnectorRuntime rt : manager.getRuntimes()) {
            if (rt.getOffsetStore() != null) {
                all.putAll(rt.getOffsetStore().all());
            }
        }
        writeJson(exchange, 200, all);
    }

    private void metrics(HttpExchange exchange) throws IOException {
        long totalPublished = 0;
        long totalProcessed = 0;
        for (ConnectorRuntime rt : manager.getRuntimes()) {
            totalPublished += rt.getSourcePublishedCount();
            totalProcessed += rt.getSinkProcessedCount();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalSourcePublished", totalPublished);
        out.put("totalSinkProcessed", totalProcessed);
        writeJson(exchange, 200, out);
    }

    // ---- dynamic control (runtime-driven, §8) ----

    private void controlStart(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, err("method not allowed"));
            return;
        }
        try {
            ConnectorDef def = mapper.readValue(exchange.getRequestBody().readAllBytes(), ConnectorDef.class);
            if (def.getId() == null || def.getId().isEmpty()) {
                writeJson(exchange, 400, err("missing id"));
                return;
            }
            manager.startConnector(def.getId(), def);
            writeJson(exchange, 200, ack("started"));
        } catch (Exception e) {
            writeJson(exchange, 500, err("start failed: " + e.getMessage()));
        }
    }

    private void controlStop(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, err("method not allowed"));
            return;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode body = mapper.readTree(exchange.getRequestBody().readAllBytes());
            String id = body.has("id") ? body.get("id").asText() : null;
            if (id == null) {
                writeJson(exchange, 400, err("missing id"));
                return;
            }
            manager.stopConnector(id);
            writeJson(exchange, 200, ack("stopped"));
        } catch (Exception e) {
            writeJson(exchange, 500, err("stop failed: " + e.getMessage()));
        }
    }

    private void controlStatus(HttpExchange exchange) throws IOException {
        writeJson(exchange, 200, manager.status());
    }

    private static Map<String, Object> ack(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", msg);
        return m;
    }

    private static Map<String, Object> err(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", msg);
        return m;
    }

    private void writeJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] out = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, out.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
        } finally {
            exchange.close();
        }
    }
}
