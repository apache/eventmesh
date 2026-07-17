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

package org.apache.eventmesh.runtime.admin;

import org.apache.eventmesh.runtime.connector.ConnectorDef;
import org.apache.eventmesh.runtime.connector.ConnectorScheduler;
import org.apache.eventmesh.runtime.subscription.Subscription;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import lombok.extern.slf4j.Slf4j;

/**
 * Independent admin HTTP server (§7.5 / §13.5.4), on a separate port from the traffic
 * {@code UniHttpServer} so management traffic and data traffic never interfere. Exposes the
 * cluster-wide admin view backed by {@link UniAdminService}:
 * <ul>
 *   <li>{@code GET  /admin/metrics} — operational counters</li>
 *   <li>{@code GET  /admin/subscriptions?topic=} — live subscriptions</li>
 *   <li>{@code GET  /admin/offsets?topic=} — distribution offset lag</li>
 *   <li>{@code GET  /admin/clients?topic=} — online clients + pending</li>
 *   <li>{@code POST /admin/client/reject?clientId=} — evict a client</li>
 *   <li>{@code POST /admin/dlq/replay?topic=&max=} — replay dead-lettered events</li>
 *   <li>{@code GET  /admin/health} — liveness + pending</li>
 * </ul>
 */
@Slf4j
public class UniAdminServer {

    private final UniAdminService admin;
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private ConnectorScheduler connectorScheduler;

    public UniAdminServer(UniAdminService admin) {
        this.admin = admin;
    }

    /** Enable connector CRUD + worker-registry endpoints (§8 dynamic scheduling). */
    public UniAdminServer withConnectorScheduler(ConnectorScheduler scheduler) {
        this.connectorScheduler = scheduler;
        return this;
    }

    /** Bind to {@code port} (0 = auto-select) and start serving. @return the bound port. */
    public int start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/admin/metrics", this::metrics);
        server.createContext("/admin/subscriptions", this::subscriptions);
        server.createContext("/admin/offsets", this::offsets);
        server.createContext("/admin/clients", this::clients);
        server.createContext("/admin/client/reject", this::rejectClient);
        server.createContext("/admin/dlq/replay", this::dlqReplay);
        server.createContext("/admin/dlq/browse", this::dlqBrowse);
        server.createContext("/admin/ratelimit", this::ratelimit);
        server.createContext("/admin/health", this::health);
        server.createContext("/connector/offset", this::connectorOffset);
        server.createContext("/admin/connectors", this::connectors);
        server.createContext("/admin/connector-workers", this::connectorWorkers);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        int bound = server.getAddress().getPort();
        log.info("uni admin HTTP server started on port {}", bound);
        return bound;
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private void metrics(HttpExchange exchange) throws IOException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("publishCount", admin.metrics().getPublishCount());
        out.put("publishFailed", admin.metrics().getPublishFailed());
        out.put("rateLimited", admin.metrics().getRateLimited());
        out.put("eventsDispatched", admin.metrics().getEventsDispatched());
        out.put("ackCount", admin.metrics().getAckCount());
        out.put("redeliveries", admin.metrics().getRedeliveries());
        out.put("dlqCount", admin.metrics().getDlqCount());
        out.put("pendingDeliveries", admin.pendingDeliveries());
        writeJson(exchange, 200, out);
    }

    private void subscriptions(HttpExchange exchange) throws IOException {
        String topic = topic(exchange);
        if (topic == null) {
            return;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Subscription s : admin.subscriptions(topic)) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("subscriptionId", s.getSubscriptionId());
            e.put("clientId", s.getClientId());
            e.put("topic", s.getTopic());
            e.put("mode", s.getMode());
            out.add(e);
        }
        writeJson(exchange, 200, out);
    }

    private void offsets(HttpExchange exchange) throws IOException {
        String topic = topic(exchange);
        if (topic == null) {
            return;
        }
        writeJson(exchange, 200, admin.offsets(topic));
    }

    private void clients(HttpExchange exchange) throws IOException {
        String topic = topic(exchange);
        if (topic == null) {
            return;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Subscription s : admin.subscriptions(topic)) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("clientId", s.getClientId());
            e.put("pending", admin.clientPending(s.getClientId()));
            out.add(e);
        }
        writeJson(exchange, 200, out);
    }

    private void rejectClient(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, err("method not allowed"));
            return;
        }
        String clientId = param(exchange.getRequestURI(), "clientId");
        if (clientId == null) {
            writeJson(exchange, 400, err("missing clientId"));
            return;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("clientId", clientId);
        out.put("removed", admin.rejectClient(clientId));
        writeJson(exchange, 200, out);
    }

    private void dlqReplay(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, err("method not allowed"));
            return;
        }
        String topic = param(exchange.getRequestURI(), "topic");
        if (topic == null) {
            writeJson(exchange, 400, err("missing topic"));
            return;
        }
        int max = parseInt(exchange.getRequestURI(), "max", 100);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("topic", topic);
        out.put("replayed", admin.dlqReplay(topic, max));
        writeJson(exchange, 200, out);
    }

    private void health(HttpExchange exchange) throws IOException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "UP");
        out.put("pendingDeliveries", admin.pendingDeliveries());
        // §13.5.4: include the partition-ownership view when clustering is enabled.
        org.apache.eventmesh.runtime.cluster.PartitionOwnership po =
            admin.getIngress().getPartitionOwnership();
        if (po != null) {
            Map<String, Object> ownership = new LinkedHashMap<>();
            for (String t : admin.getIngress().getSubscriptionManager().activeTopics()) {
                List<Integer> owned = po.ownedPartitions(t);
                ownership.put(t, owned == null ? "all" : owned);
            }
            out.put("partitionOwnership", ownership);
        }
        writeJson(exchange, 200, out);
    }

    /** Browse (not replay) dead-lettered event ids (§13.5.4). */
    private void dlqBrowse(HttpExchange exchange) throws IOException {
        String topic = topic(exchange);
        if (topic == null) {
            return;
        }
        int max = parseInt(exchange.getRequestURI(), "max", 100);
        writeJson(exchange, 200, admin.dlqBrowse(topic, max));
    }

    /** Push a per-topic rate-limit rule (§13.5.4 / §13.6.1). PUT body: {topic, capacity, rate}. */
    private void ratelimit(HttpExchange exchange) throws IOException {
        if (!"PUT".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, err("method not allowed"));
            return;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode body = mapper.readTree(exchange.getRequestBody().readAllBytes());
            String topic = body.has("topic") ? body.get("topic").asText() : null;
            if (topic == null) {
                writeJson(exchange, 400, err("missing topic"));
                return;
            }
            long capacity = body.has("capacity") ? body.get("capacity").asLong() : 0L;
            double rate = body.has("rate") ? body.get("rate").asDouble() : 0.0;
            admin.setRateLimit(topic, capacity, rate);
            writeJson(exchange, 200, ack("ok"));
        } catch (Exception e) {
            writeJson(exchange, 500, err("ratelimit set failed: " + e.getMessage()));
        }
    }

    // ---- connector offset (remote offset store for connector runtime, §8.9) ----

    private void connectorOffset(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String connectorId = param(exchange.getRequestURI(), "connectorId");
        if ("GET".equals(method)) {
            if (connectorId == null) {
                writeJson(exchange, 400, err("missing connectorId"));
                return;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            String offset = admin.getIngress().getConnectorOffset(connectorId);
            out.put("connectorId", connectorId);
            out.put("offset", offset != null ? offset : "");
            writeJson(exchange, 200, out);
        } else if ("POST".equals(method)) {
            try {
                com.fasterxml.jackson.databind.JsonNode body = mapper.readTree(exchange.getRequestBody().readAllBytes());
                String cid = body.has("connectorId") ? body.get("connectorId").asText() : null;
                String offset = body.has("offset") ? body.get("offset").asText() : null;
                if (cid == null || offset == null) {
                    writeJson(exchange, 400, err("missing connectorId or offset"));
                    return;
                }
                admin.getIngress().putConnectorOffset(cid, offset);
                writeJson(exchange, 200, ack("ok"));
            } catch (Exception e) {
                writeJson(exchange, 500, err("offset write failed: " + e.getMessage()));
            }
        } else {
            writeJson(exchange, 405, err("method not allowed"));
        }
    }

    private static Map<String, Object> ack(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", msg);
        return m;
    }

    // ---- connector scheduling (§8) ----

    private void connectors(HttpExchange exchange) throws IOException {
        if (connectorScheduler == null) {
            writeJson(exchange, 503, err("connector scheduling disabled (no meta)"));
            return;
        }
        String method = exchange.getRequestMethod();
        if ("GET".equals(method)) {
            List<ConnectorDef> defs = connectorScheduler.listConnectors();
            Map<String, String> assigned = connectorScheduler.assignments();
            List<Map<String, Object>> out = new ArrayList<>();
            for (ConnectorDef d : defs) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("id", d.getId());
                e.put("className", d.getClassName());
                e.put("mode", d.getMode());
                e.put("topic", d.getTopic());
                e.put("clientId", d.getClientId());
                e.put("sinkClass", d.getSinkClass());
                e.put("owner", assigned.get(d.getId()));
                out.add(e);
            }
            writeJson(exchange, 200, out);
        } else if ("POST".equals(method)) {
            try {
                ConnectorDef def = mapper.readValue(exchange.getRequestBody().readAllBytes(), ConnectorDef.class);
                if (def.getId() == null || def.getId().isEmpty()) {
                    writeJson(exchange, 400, err("missing id"));
                    return;
                }
                connectorScheduler.createConnector(def);
                writeJson(exchange, 200, ack("created"));
            } catch (Exception e) {
                writeJson(exchange, 500, err("create failed: " + e.getMessage()));
            }
        } else if ("DELETE".equals(method)) {
            String id = param(exchange.getRequestURI(), "id");
            if (id == null) {
                writeJson(exchange, 400, err("missing id"));
                return;
            }
            boolean existed = connectorScheduler.deleteConnector(id);
            writeJson(exchange, 200, existed ? ack("deleted") : err("not found"));
        } else {
            writeJson(exchange, 405, err("method not allowed"));
        }
    }

    private void connectorWorkers(HttpExchange exchange) throws IOException {
        if (connectorScheduler == null) {
            writeJson(exchange, 503, err("connector scheduling disabled (no meta)"));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if ("GET".equals(method)) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (ConnectorScheduler.Worker w : connectorScheduler.liveWorkers()) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("id", w.id);
                e.put("address", w.address);
                out.add(e);
            }
            writeJson(exchange, 200, out);
        } else if ("POST".equals(method) && path.endsWith("/register")) {
            try {
                com.fasterxml.jackson.databind.JsonNode body = mapper.readTree(exchange.getRequestBody().readAllBytes());
                String id = body.has("id") ? body.get("id").asText() : null;
                String addr = body.has("address") ? body.get("address").asText() : null;
                if (id == null || addr == null) {
                    writeJson(exchange, 400, err("missing id or address"));
                    return;
                }
                connectorScheduler.registerWorker(id, addr);
                writeJson(exchange, 200, ack("registered"));
            } catch (Exception e) {
                writeJson(exchange, 500, err("register failed: " + e.getMessage()));
            }
        } else if ("POST".equals(method) && path.endsWith("/heartbeat")) {
            try {
                com.fasterxml.jackson.databind.JsonNode body = mapper.readTree(exchange.getRequestBody().readAllBytes());
                String id = body.has("id") ? body.get("id").asText() : null;
                String addr = body.has("address") ? body.get("address").asText() : null;
                if (id == null || addr == null) {
                    writeJson(exchange, 400, err("missing id or address"));
                    return;
                }
                connectorScheduler.heartbeat(id, addr);
                writeJson(exchange, 200, ack("ok"));
            } catch (Exception e) {
                writeJson(exchange, 500, err("heartbeat failed: " + e.getMessage()));
            }
        } else if ("POST".equals(method) && path.endsWith("/leave")) {
            try {
                com.fasterxml.jackson.databind.JsonNode body = mapper.readTree(exchange.getRequestBody().readAllBytes());
                String id = body.has("id") ? body.get("id").asText() : null;
                if (id == null) {
                    writeJson(exchange, 400, err("missing id"));
                    return;
                }
                connectorScheduler.leaveWorker(id);
                writeJson(exchange, 200, ack("left"));
            } catch (Exception e) {
                writeJson(exchange, 500, err("leave failed: " + e.getMessage()));
            }
        } else {
            writeJson(exchange, 405, err("method not allowed"));
        }
    }

    /** Returns the topic query param or writes a 400 and returns null. */
    private String topic(HttpExchange exchange) throws IOException {
        String topic = param(exchange.getRequestURI(), "topic");
        if (topic == null) {
            writeJson(exchange, 400, err("missing topic"));
        }
        return topic;
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

    private static Map<String, Object> err(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("error", msg);
        return m;
    }

    private static String param(URI uri, String name) {
        String query = uri.getQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static int parseInt(URI uri, String name, int dflt) {
        String v = param(uri, name);
        return v == null ? dflt : Integer.parseInt(v);
    }
}
