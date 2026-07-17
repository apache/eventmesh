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

package org.apache.eventmesh.runtime.http;

import org.apache.eventmesh.runtime.admin.UniAdminService;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.push.BufferedEvent;
import org.apache.eventmesh.runtime.ratelimit.RateLimitedException;
import org.apache.eventmesh.runtime.subscription.DistributionMode;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import lombok.extern.slf4j.Slf4j;

/**
 * Real HTTP ingress for the uni runtime (§6 {@code UniIngressHandler}). Built on the JDK
 * {@link HttpServer} so the new architecture is runnable with zero extra server dependencies; a
 * production deployment can swap this for the existing netty {@code AbstractHTTPServer} without
 * touching the {@link UniIngressService} it delegates to.
 *
 * <p>Endpoints (CloudEvents 1.0 structured JSON on the wire):</p>
 * <ul>
 *   <li>{@code POST /events/publish?topic=...} — structured CloudEvent body → 202 Accepted</li>
 *   <li>{@code POST /events/subscribe} — {@code {clientId, topic, mode}} → {@code {subscriptionId}}</li>
 *   <li>{@code POST /events/ack} — {@code {deliveryId}} → 200 / 404</li>
 *   <li>{@code GET /events/poll?clientId=...&max=...&timeoutMs=...} — {@code [{deliveryId, event}]}</li>
 *   <li>{@code GET /admin/metrics} — counters JSON</li>
 * </ul>
 */
@Slf4j
public class UniHttpServer {

    /** §13.8.2 max message size — payloads larger than this are rejected with 413. */
    static final long MAX_MESSAGE_SIZE = 1024 * 1024;

    private final UniIngressService ingress;
    private final UniAdminService admin;
    private final ObjectMapper mapper = new ObjectMapper();

    private HttpServer server;
    private javax.net.ssl.SSLContext sslContext;
    private org.apache.eventmesh.runtime.security.FilterChain filterChain;
    private org.apache.eventmesh.runtime.transport.http.LegacyHttpBridge legacyBridge;
    private String selfInstanceId;
    private org.apache.eventmesh.runtime.cluster.HttpForwarder forwarder;

    public UniHttpServer(UniIngressService ingress, UniAdminService admin) {
        this.ingress = ingress;
        this.admin = admin;
    }

    /**
     * Serve the legacy {@code /eventmesh/*} API on the same port, backed by {@code bridge}, so old
     * {@code EventMeshHttpClient} clients work unchanged against the new runtime.
     *
     * @return this, for chaining before {@link #start(int)}
     */
    public UniHttpServer withLegacyEndpoints(
        org.apache.eventmesh.runtime.transport.http.LegacyHttpBridge bridge) {
        this.legacyBridge = bridge;
        return this;
    }

    /** Enable HTTPS (TLS) on the traffic port with the given SSLContext (§4.5). */
    public UniHttpServer withTls(javax.net.ssl.SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    /** Wire the ingress security filter chain (auth/acl/signature) into publish (§4.5). */
    public UniHttpServer withFilterChain(org.apache.eventmesh.runtime.security.FilterChain filterChain) {
        this.filterChain = filterChain;
        return this;
    }

    /**
     * Wire cross-instance forwarding (§13.2.5 / §17.6). {@code selfInstanceId} identifies this
     * instance (for self-addressed reply routing); {@code forwarder} does the HTTP POST to peers.
     */
    public UniHttpServer withCluster(String selfInstanceId, org.apache.eventmesh.runtime.cluster.HttpForwarder forwarder) {
        this.selfInstanceId = selfInstanceId;
        this.forwarder = forwarder;
        return this;
    }

    /**
     * Bind to {@code port} (0 = auto-select) and start serving.
     *
     * @return the actual bound port
     */
    public int start(int port) throws IOException {
        if (sslContext != null) {
            com.sun.net.httpserver.HttpsServer https = com.sun.net.httpserver.HttpsServer.create(new InetSocketAddress(port), 0);
            https.setHttpsConfigurator(new com.sun.net.httpserver.HttpsConfigurator(sslContext));
            server = https;
        } else {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        }
        server.createContext("/events/publish", this::publish);
        server.createContext("/events/publish-batch", this::publishBatch);
        server.createContext("/events/subscribe", this::subscribe);
        server.createContext("/events/unsubscribe", this::unsubscribe);
        server.createContext("/events/ack", this::ack);
        server.createContext("/events/poll", this::poll);
        server.createContext("/events/request", this::request);
        server.createContext("/events/reply", this::reply);
        server.createContext("/events/stream", this::stream);
        server.createContext("/events/lite/create", this::liteCreate);
        server.createContext("/events/lite/publish", this::litePublish);
        server.createContext("/events/lite/poll", this::litePoll);
        server.createContext("/internal/forward", this::forwardInternal);
        server.createContext("/internal/reply-forward", this::replyForwardInternal);
        if (legacyBridge != null) {
            server.createContext("/eventmesh/publish", this::legacyPublish);
            server.createContext("/eventmesh/subscribe", this::legacySubscribe);
            server.createContext("/eventmesh/unsubscribe", this::legacyUnsubscribe);
        }
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        int bound = server.getAddress().getPort();
        log.info("uni HTTP ingress started on port {}", bound);
        return bound;
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    /**
     * Run the security filter chain (if configured) for any request. Returns true if allowed,
     * false if already rejected (response written).
     */
    private boolean checkSecurity(HttpExchange exchange, String topic, String clientId) throws IOException {
        if (filterChain == null) {
            return true;
        }
        String credential = exchange.getRequestHeaders().getFirst("Authorization");
        String tenant = null;
        org.apache.eventmesh.runtime.security.FilterContext ctx =
            new org.apache.eventmesh.runtime.security.FilterContext(topic, clientId, tenant, credential,
                exchange.getRemoteAddress().getAddress().getHostAddress());
        // For non-publish endpoints there's no CloudEvent body to check; use a minimal stub.
        io.cloudevents.CloudEvent stubEvent = io.cloudevents.core.builder.CloudEventBuilder.v1()
            .withId("security-check").withSource(java.net.URI.create("eventmesh")).withType("security").build();
        org.apache.eventmesh.runtime.security.FilterVerdict verdict = filterChain.check(stubEvent, ctx);
        if (!verdict.isAllowed()) {
            writeJson(exchange, verdict.getRejectStatus(), error(verdict.getReason()));
            return false;
        }
        return true;
    }

    // ---- handlers ----

    private void publish(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        // §13.8.2: reject oversized payloads up front (no auto-sharding — data goes to external storage).
        String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
        if (contentLength != null) {
            try {
                if (Long.parseLong(contentLength) > MAX_MESSAGE_SIZE) {
                    writeJson(exchange, 413, error("payload too large (max " + MAX_MESSAGE_SIZE + " bytes)"));
                    return;
                }
            } catch (NumberFormatException expected) {
            }
        }
        String topic = param(exchange.getRequestURI(), "topic");
        if (topic == null) {
            writeJson(exchange, 400, error("missing query param 'topic'"));
            return;
        }
        byte[] body = readAll(exchange);
        CloudEvent event;
        try {
            event = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE).deserialize(body);
        } catch (RuntimeException e) {
            writeJson(exchange, 400, error("invalid CloudEvent: " + e.getMessage()));
            return;
        }
        // Security filter chain (§4.5): auth/acl/signature run before the event enters the pipeline.
        if (filterChain != null) {
            String credential = exchange.getRequestHeaders().getFirst("Authorization");
            String tenant = event.getExtension("emtenantid") != null ? event.getExtension("emtenantid").toString() : null;
            org.apache.eventmesh.runtime.security.FilterContext ctx =
                new org.apache.eventmesh.runtime.security.FilterContext(topic, null, tenant, credential,
                    exchange.getRemoteAddress().getAddress().getHostAddress());
            org.apache.eventmesh.runtime.security.FilterVerdict verdict = filterChain.check(event, ctx);
            if (!verdict.isAllowed()) {
                writeJson(exchange, verdict.getRejectStatus(), error(verdict.getReason()));
                return;
            }
        }
        try {
            ingress.publish(topic, event).get(10, TimeUnit.SECONDS);
            writeJson(exchange, 202, ack("accepted"));
        } catch (Exception e) {
            // §6.6: a RateLimitedException (per-topic token bucket exhausted) is a 429, not a 500 —
            // lets clients distinguish "slow down, retry" from a genuine server fault. The future
            // wraps it in ExecutionException, so unwrap the cause chain.
            if (isRateLimited(e)) {
                writeJson(exchange, 429, error("rate limited: " + e.getMessage()));
            } else {
                writeJson(exchange, 500, error("publish failed: " + e.getMessage()));
            }
        }
    }

    /** Batch publish (§13.7.3): body is a CloudEvent JSON array → 202 Accepted. */
    private void publishBatch(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        String topic = param(exchange.getRequestURI(), "topic");
        if (topic == null) {
            writeJson(exchange, 400, error("missing query param 'topic'"));
            return;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(readAll(exchange));
            if (!node.isArray()) {
                writeJson(exchange, 400, error("expected a CloudEvent JSON array"));
                return;
            }
            java.util.List<CloudEvent> events = new java.util.ArrayList<>(node.size());
            for (com.fasterxml.jackson.databind.JsonNode el : node) {
                events.add(EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE)
                    .deserialize(mapper.writeValueAsBytes(el)));
            }
            ingress.publishBatch(topic, events).get(30, TimeUnit.SECONDS);
            writeJson(exchange, 202, ack("accepted"));
        } catch (Exception e) {
            if (isRateLimited(e)) {
                writeJson(exchange, 429, error("rate limited: " + e.getMessage()));
            } else {
                writeJson(exchange, 500, error("batch publish failed: " + e.getMessage()));
            }
        }
    }

    private void subscribe(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        JsonNode body = readJson(exchange);
        String clientId = text(body, "clientId");
        String topic = text(body, "topic");
        String modeName = text(body, "mode");
        if (clientId == null || topic == null || modeName == null) {
            writeJson(exchange, 400, error("missing clientId/topic/mode"));
            return;
        }
        if (!checkSecurity(exchange, topic, clientId)) {
            return;
        }
        DistributionMode mode = DistributionMode.valueOf(modeName);
        String subId = ingress.subscribe(topic, clientId, mode, null);
        Map<String, String> out = new HashMap<>();
        out.put("subscriptionId", subId);
        writeJson(exchange, 200, out);
    }

    /**
     * {@code POST /events/unsubscribe} body {@code {clientId, topic?}}:
     * <ul>
     *   <li>{@code topic} present → remove that one topic's subscription for the client (keep others);</li>
     *   <li>{@code topic} absent → remove ALL the client's subscriptions.</li>
     * </ul>
     * Returns {@code {removed: true|false}}.
     */
    private void unsubscribe(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        JsonNode body = readJson(exchange);
        String clientId = text(body, "clientId");
        String topic = text(body, "topic");
        if (clientId == null) {
            writeJson(exchange, 400, error("missing clientId"));
            return;
        }
        Map<String, Object> out = new HashMap<>();
        if (topic != null) {
            out.put("removed", ingress.unsubscribe(topic, clientId));
        } else {
            out.put("removed", ingress.unsubscribeByClient(clientId) > 0);
        }
        writeJson(exchange, 200, out);
    }

    private void ack(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        JsonNode body = readJson(exchange);
        String deliveryId = text(body, "deliveryId");
        if (deliveryId == null) {
            writeJson(exchange, 400, error("missing deliveryId"));
            return;
        }
        if (ingress.ack(deliveryId)) {
            writeJson(exchange, 200, ack("acked"));
        } else {
            writeJson(exchange, 404, error("unknown deliveryId"));
        }
    }

    private static Map<String, Object> ack(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", msg);
        return m;
    }

    private void poll(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        String clientId = param(exchange.getRequestURI(), "clientId");
        if (clientId == null) {
            writeJson(exchange, 400, error("missing clientId"));
            return;
        }
        if (!checkSecurity(exchange, null, clientId)) {
            return;
        }
        int max = intParam(exchange.getRequestURI(), "max", 100);
        long timeoutMs = longParam(exchange.getRequestURI(), "timeoutMs", 1000L);
        List<BufferedEvent> events = ingress.poll(clientId, max, timeoutMs);

        // Each entry is {deliveryId, event:<structured CloudEvent JSON>}.
        List<ObjectNode> out = new java.util.ArrayList<>(events.size());
        for (BufferedEvent be : events) {
            byte[] serialized = EventFormatProvider.getInstance()
                .resolveFormat(JsonFormat.CONTENT_TYPE).serialize(be.getEvent());
            ObjectNode entry = mapper.createObjectNode();
            entry.put("deliveryId", be.getDeliveryId());
            entry.set("event", mapper.readTree(serialized));
            out.add(entry);
        }
        writeJson(exchange, 200, mapper.createArrayNode().addAll(out));
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

    private void request(HttpExchange exchange) throws IOException {
        // §17 blocking request-reply: body = CloudEvent; reply returned as the response body.
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        String topic = param(exchange.getRequestURI(), "topic");
        long timeout = longParam(exchange.getRequestURI(), "timeoutMs", 30_000L);
        try {
            CloudEvent event = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE)
                .deserialize(readAll(exchange));
            if (!checkSecurity(exchange, topic, null)) {
                return;
            }
            CloudEvent reply = ingress.request(topic, event, timeout);
            byte[] replyBytes = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE).serialize(reply);
            exchange.getResponseHeaders().add("Content-Type", "application/cloudevents+json");
            exchange.sendResponseHeaders(200, replyBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(replyBytes);
            }
        } catch (Exception e) {
            writeJson(exchange, 504, error("request timeout/error: " + e.getMessage()));
        }
    }

    private void reply(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        try {
            JsonNode body = readJson(exchange);
            String corrId = text(body, "correlationId");
            CloudEvent replyEvent = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE)
                .deserialize(mapper.writeValueAsBytes(body.get("event")));
            // §17.6 self-addressed routing: if the requestor lives on another instance, forward there.
            Object replyInst = replyEvent.getExtension("emreplyinstance");
            if (replyInst != null && !replyInst.toString().equals(selfInstanceId) && forwarder != null) {
                boolean ok = forwarder.forwardReply(replyInst.toString(), corrId, replyEvent);
                writeJson(exchange, ok ? 200 : 502, ack(ok ? "forwarded" : "forward failed"));
                return;
            }
            writeJson(exchange, ingress.reply(corrId, replyEvent) ? 200 : 404, ack("ok"));
        } catch (Exception e) {
            writeJson(exchange, 500, error("reply error: " + e.getMessage()));
        }
    }

    /** Cross-instance message forward (§13.2.5): peer pulled a message whose subscriber is here. */
    private void forwardInternal(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        try {
            JsonNode body = readJson(exchange);
            String clientId = text(body, "clientId");
            String topic = text(body, "topic");
            CloudEvent event = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE)
                .deserialize(mapper.writeValueAsBytes(body.get("event")));
            boolean ok = ingress.deliverLocal(topic, clientId, event);
            writeJson(exchange, ok ? 200 : 404, ack(ok ? "delivered" : "no local subscriber"));
        } catch (Exception e) {
            writeJson(exchange, 500, error("forward error: " + e.getMessage()));
        }
    }

    /** Cross-instance reply forward (§17.6): peer received a reply whose requestor is here. */
    private void replyForwardInternal(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        try {
            JsonNode body = readJson(exchange);
            String corrId = text(body, "correlationId");
            CloudEvent replyEvent = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE)
                .deserialize(mapper.writeValueAsBytes(body.get("event")));
            writeJson(exchange, ingress.reply(corrId, replyEvent) ? 200 : 404, ack("ok"));
        } catch (Exception e) {
            writeJson(exchange, 500, error("reply-forward error: " + e.getMessage()));
        }
    }

    private void stream(HttpExchange exchange) throws IOException {
        // SSE: hold the response open and pump buffered events to the client (§5). Blocks this
        // thread until the client disconnects; Java-21 virtual threads (Phase 7) will make this cheap.
        if (!"GET".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        String clientId = param(exchange.getRequestURI(), "clientId");
        if (clientId == null) {
            writeJson(exchange, 400, error("missing clientId"));
            return;
        }
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, 0);
        OutputStream out = exchange.getResponseBody();
        org.apache.eventmesh.runtime.push.SseConnection conn = new org.apache.eventmesh.runtime.push.SseConnection(out);
        org.apache.eventmesh.runtime.push.ConnectionPushPump pump =
            new org.apache.eventmesh.runtime.push.ConnectionPushPump(ingress.getPushService(), clientId, conn);
        try {
            while (conn.isOpen()) {
                pump.pumpOnce(100);
                Thread.sleep(20);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            conn.close();
            exchange.close();
        }
    }

    // ---- lite topic handlers (RIP-83, 5.x-only) ----

    /** {@code POST /events/lite/create?topic=<parent>&lite=<lite>} — ensure parent is lite-capable + declare lite sub-topic. */
    private void liteCreate(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        if (!ingress.isLiteCapable()) {
            writeJson(exchange, 501, error("storage does not support lite topic (needs rocketmq5)"));
            return;
        }
        String parent = param(exchange.getRequestURI(), "topic");
        String lite = param(exchange.getRequestURI(), "lite");
        if (parent == null || lite == null) {
            writeJson(exchange, 400, error("missing query param 'topic' and/or 'lite'"));
            return;
        }
        if (!checkSecurity(exchange, parent, null)) {
            return;
        }
        try {
            readAll(exchange); // drain any body
            ingress.createLiteTopic(parent, lite);
            writeJson(exchange, 200, ack("created"));
        } catch (Exception e) {
            writeJson(exchange, 500, error("create lite topic failed: " + e.getMessage()));
        }
    }

    /** {@code POST /events/lite/publish?topic=<parent>&lite=<lite>} body=structured CloudEvent → 202. */
    private void litePublish(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        if (!ingress.isLiteCapable()) {
            writeJson(exchange, 501, error("storage does not support lite topic (needs rocketmq5)"));
            return;
        }
        String parent = param(exchange.getRequestURI(), "topic");
        String lite = param(exchange.getRequestURI(), "lite");
        if (parent == null || lite == null) {
            writeJson(exchange, 400, error("missing query param 'topic' and/or 'lite'"));
            return;
        }
        if (!checkSecurity(exchange, parent, null)) {
            return;
        }
        try {
            CloudEvent event = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE)
                .deserialize(readAll(exchange));
            ingress.publishLite(parent, lite, event).get(10, TimeUnit.SECONDS);
            writeJson(exchange, 202, ack("accepted"));
        } catch (Exception e) {
            writeJson(exchange, 500, error("lite publish failed: " + e.getMessage()));
        }
    }

    /** {@code GET /events/lite/poll?topic=<parent>&lite=<lite>&max=&timeoutMs=} → CloudEvent JSON array from the LMQ. */
    private void litePoll(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        if (!ingress.isLiteCapable()) {
            writeJson(exchange, 501, error("storage does not support lite topic (needs rocketmq5)"));
            return;
        }
        String parent = param(exchange.getRequestURI(), "topic");
        String lite = param(exchange.getRequestURI(), "lite");
        if (parent == null || lite == null) {
            writeJson(exchange, 400, error("missing query param 'topic' and/or 'lite'"));
            return;
        }
        if (!checkSecurity(exchange, parent, null)) {
            return;
        }
        try {
            int max = intParam(exchange.getRequestURI(), "max", 100);
            long timeoutMs = longParam(exchange.getRequestURI(), "timeoutMs", 1000L);
            List<CloudEvent> events = ingress.pollLite(parent, lite, max, timeoutMs);
            com.fasterxml.jackson.databind.node.ArrayNode arr = mapper.createArrayNode();
            for (CloudEvent e : events) {
                arr.add(mapper.readTree(EventFormatProvider.getInstance()
                    .resolveFormat(JsonFormat.CONTENT_TYPE).serialize(e)));
            }
            writeJson(exchange, 200, arr);
        } catch (NumberFormatException e) {
            writeJson(exchange, 400, error("invalid max/timeoutMs parameter"));
        } catch (Exception e) {
            writeJson(exchange, 500, error("lite poll failed: " + e.getMessage()));
        }
    }

    // ---- legacy /eventmesh/* handlers (old EventMeshHttpClient compat) ----

    private void legacyPublish(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        try {
            legacyBridge.publish(readAll(exchange)).get(10, TimeUnit.SECONDS);
            writeJson(exchange, 200, ack("ok"));
        } catch (Exception e) {
            writeJson(exchange, 500, error("legacy publish failed: " + e.getMessage()));
        }
    }

    private void legacySubscribe(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        try {
            legacyBridge.subscribe(readAll(exchange));
            writeJson(exchange, 200, ack("ok"));
        } catch (Exception e) {
            writeJson(exchange, 500, error("legacy subscribe failed: " + e.getMessage()));
        }
    }

    private void legacyUnsubscribe(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        int removed = legacyBridge.unsubscribe(readAll(exchange));
        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.put("status", "ok");
        out.put("removed", removed);
        writeJson(exchange, 200, out);
    }

    // ---- helpers ----

    /** True if {@code e} (or any wrapped cause) is a {@link RateLimitedException} — the future
     *  returned by ingress.publish wraps it in ExecutionException, so check the chain. */
    private static boolean isRateLimited(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof RateLimitedException) {
                return true;
            }
            t = t.getCause();
        }
        return false;
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

    private static Map<String, Object> error(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("error", msg);
        return m;
    }


    private byte[] readAll(HttpExchange exchange) throws IOException {
        return exchange.getRequestBody().readAllBytes();
    }

    private JsonNode readJson(HttpExchange exchange) throws IOException {
        byte[] body = readAll(exchange);
        return body.length == 0 ? mapper.createObjectNode() : mapper.readTree(body);
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.has(field) ? node.get(field).asText() : null;
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

    private static int intParam(URI uri, String name, int dflt) {
        String v = param(uri, name);
        return v == null ? dflt : Integer.parseInt(v);
    }

    private static long longParam(URI uri, String name, long dflt) {
        String v = param(uri, name);
        return v == null ? dflt : Long.parseLong(v);
    }
}



