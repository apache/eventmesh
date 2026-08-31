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

import org.apache.eventmesh.common.protocol.ByteTransport;
import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.protocol.api.FrameAdaptors;
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
    private org.apache.eventmesh.runtime.session.AgentRegistrar agentRegistrar;
    private org.apache.eventmesh.runtime.session.Matchmaker matchmaker;
    private org.apache.eventmesh.runtime.session.SessionRouter sessionRouter;
    /** Cluster membership for /session/recommend (reads live instances + load). Null = single instance. */
    private org.apache.eventmesh.runtime.cluster.ClusterMembership clusterMembership;
    /** This instance's advertised address (host:port) for instanceUrl; null = not configured (SDK falls back). */
    private String advertisedAddr;

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

    /**
     * Require client certificate authentication (mTLS): the HTTPS handshake demands a client cert
     * signed by the truststore. Configure via {@code -Deventmesh.tls.needClientAuth=true} plus
     * {@code -Deventmesh.tls.truststore=<path>} [+ .password]. No-op when TLS isn't enabled.
     */
    public UniHttpServer withClientAuth(boolean needClientAuth) {
        this.needClientAuth = needClientAuth;
        return this;
    }

    private volatile boolean needClientAuth;

    /** Wire the ingress security filter chain (auth/acl/signature) into publish (§4.5). */
    public UniHttpServer withFilterChain(org.apache.eventmesh.runtime.security.FilterChain filterChain) {
        this.filterChain = filterChain;
        return this;
    }

    /**
     * Wire cluster membership so {@code /session/recommend} can read live instances + load (§3.2).
     */
    public UniHttpServer withClusterMembership(org.apache.eventmesh.runtime.cluster.ClusterMembership membership) {
        this.clusterMembership = membership;
        return this;
    }

    /** Set this instance's advertised address ({@code host:port}); returned as instanceUrl (§3.4). */
    public UniHttpServer withAdvertisedAddr(String advertisedAddr) {
        this.advertisedAddr = advertisedAddr;
        return this;
    }

    /**
     * Wire the agent control-plane registrar so {@code POST /agent/register|ready|heartbeat|unregister}
     * (§5.2) are served. Without this those endpoints return 503.
     */
    public UniHttpServer withAgentRegistrar(org.apache.eventmesh.runtime.session.AgentRegistrar agentRegistrar) {
        this.agentRegistrar = agentRegistrar;
        return this;
    }

    /**
     * Wire the session matchmaker so {@code POST /session/open} (handshake + matchmaking) and
     * {@code POST /session/close/{sessionId}} (§5②⑤) are served. Without this they return 503.
     */
    public UniHttpServer withMatchmaker(org.apache.eventmesh.runtime.session.Matchmaker matchmaker) {
        this.matchmaker = matchmaker;
        return this;
    }

    /**
     * Wire the v2 session router so {@code POST /session/stream/{sessionId}} (SSE) is served. Without
     * this the endpoint returns 503.
     */
    public UniHttpServer withSessionRouter(org.apache.eventmesh.runtime.session.SessionRouter sessionRouter) {
        this.sessionRouter = sessionRouter;
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
            final boolean clientAuth = needClientAuth;
            https.setHttpsConfigurator(new com.sun.net.httpserver.HttpsConfigurator(sslContext) {
                @Override
                public void configure(com.sun.net.httpserver.HttpsParameters params) {
                    params.setNeedClientAuth(clientAuth);
                    try {
                        params.setSSLParameters(sslContext.getDefaultSSLParameters());
                    } catch (Exception e) {
                        log.warn("failed to apply SSL parameters: {}", e.toString());
                    }
                }
            });
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
        // NOTE: publish-bytes/poll-bytes must precede publish/poll — HttpServer matches by longest
        // prefix and "/events/lite/publish" is a prefix of "/events/lite/publish-bytes".
        server.createContext("/events/lite/publish-bytes", this::litePublishBytes);
        server.createContext("/events/lite/poll-bytes", this::litePollBytes);
        server.createContext("/events/lite/publish", this::litePublish);
        server.createContext("/events/lite/poll", this::litePoll);
        server.createContext("/agent/register", this::agentRegister);
        server.createContext("/agent/ready", this::agentReady);
        server.createContext("/agent/heartbeat", this::agentHeartbeat);
        server.createContext("/agent/unregister", this::agentUnregister);
        server.createContext("/session/open", this::sessionOpen);
        server.createContext("/session/recommend", this::sessionRecommend);
        server.createContext("/session/close", this::sessionClose);
        server.createContext("/session/stream", this::sessionStream);
        server.createContext("/session/publish", this::sessionPublish);
        server.createContext("/session/subscribe", this::sessionSubscribe);
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
        // For non-publish endpoints there's no EventMeshFrame to check; build a minimal
        // security-check frame so the filter chain has *something* to evaluate. The filters
        // themselves read tenant / credential from the FilterContext, so a frame with empty
        // attributes is sufficient for the auth/acl decision (#5299 sub-PR B).
        org.apache.eventmesh.common.wire.EventMeshFrame stubFrame =
            org.apache.eventmesh.common.wire.EventMeshFrame.event(java.util.Collections.emptyMap(), new byte[0]);
        org.apache.eventmesh.runtime.security.FilterVerdict verdict = filterChain.check(stubFrame, ctx);
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
            } catch (NumberFormatException ignored) {
                // Content-Length is not a valid number; ignore and proceed with normal parsing
            }
        }
        String topic = param(exchange.getRequestURI(), "topic");
        if (topic == null) {
            writeJson(exchange, 400, error("missing query param 'topic'"));
            return;
        }
        byte[] body = readAll(exchange);
        EventMeshFrame frame;
        try {
            // Ingress: structured CloudEvents JSON bytes → internal EventMeshFrame.
            // (#5299: runtime no longer touches io.cloudevents.CloudEvent directly on the ingress
            // path; the protocol adaptor owns the conversion.)
            frame = FrameAdaptors.get("cloudevents").toFrame(new ByteTransport(body));
        } catch (RuntimeException | org.apache.eventmesh.protocol.api.exception.ProtocolHandleException e) {
            writeJson(exchange, 400, error("invalid CloudEvent: " + e.getMessage()));
            return;
        }
        // Security filter chain (§4.5): auth/acl/signature run before the event enters the pipeline.
        // #5299 sub-PR B: filters now read directly from EventMeshFrame.attributes() — no more
        // CE bridge. Tenant still comes from the CloudEvent extension ("emtenantid") which the
        // cloudevents FrameAdaptor round-trips into frame attributes under the same key.
        if (filterChain != null) {
            String credential = exchange.getRequestHeaders().getFirst("Authorization");
            String tenant = frame.attributes().get("emtenantid");
            org.apache.eventmesh.runtime.security.FilterContext ctx =
                new org.apache.eventmesh.runtime.security.FilterContext(topic, null, tenant, credential,
                    exchange.getRemoteAddress().getAddress().getHostAddress());
            org.apache.eventmesh.runtime.security.FilterVerdict verdict = filterChain.check(frame, ctx);
            if (!verdict.isAllowed()) {
                writeJson(exchange, verdict.getRejectStatus(), error(verdict.getReason()));
                return;
            }
        }
        try {
            ingress.publish(topic, frame).get(10, TimeUnit.SECONDS);
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
            // Batch ingress: each element is a CloudEvents-JSON object → internal EventMeshFrame.
            // (#5299)
            java.util.List<EventMeshFrame> frames = new java.util.ArrayList<>(node.size());
            for (com.fasterxml.jackson.databind.JsonNode el : node) {
                frames.add(FrameAdaptors.get("cloudevents")
                    .toFrame(new ByteTransport(mapper.writeValueAsBytes(el))));
            }
            ingress.publishBatchFrames(topic, frames).get(30, TimeUnit.SECONDS);
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
        // Return the instance the subscriber should pin its subsequent polls to (load balancing, §3.4).
        // Empty when no advertised address is configured → SDK keeps using its original baseUrl.
        out.put("instanceUrl", selfInstanceUrl());
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
            // Egress: push buffer carries Frame; serialize as CloudEvents-JSON via the FrameAdaptor SPI.
            byte[] serialized = org.apache.eventmesh.protocol.api.FrameAdaptors.toCloudEventsJson(be.getEvent());
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
            // Ingress: structured CloudEvents JSON body → internal EventMeshFrame. (#5299)
            EventMeshFrame event = FrameAdaptors.get("cloudevents")
                .toFrame(new ByteTransport(readAll(exchange)));
            if (!checkSecurity(exchange, topic, null)) {
                return;
            }
            // request-reply: send Frame in, get Frame back, then serialize to CloudEvents JSON
            // for the response body via the egress adapter.
            EventMeshFrame reply = ingress.requestFrame(topic, event, timeout);
            byte[] replyBytes = FrameAdaptors.toCloudEventsJson(reply);
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
            // Ingress: CloudEvents JSON → internal EventMeshFrame. (#5299)
            EventMeshFrame replyEvent = FrameAdaptors.get("cloudevents")
                .toFrame(new ByteTransport(mapper.writeValueAsBytes(body.get("event"))));
            // §17.6 reply routing (sticky model - no cross-instance forwarding).
            // Cross-instance reply forwarding is REMOVED with the forward path: the client posts
            // the reply to the instance it sent the request to (pinned via instanceUrl); a reply
            // landing on the wrong instance 404s (unknown correlationId) and the caller retries
            // on the correct instance.
            writeJson(exchange, ingress.replyFrame(corrId, replyEvent) ? 200 : 404, ack("ok"));
        } catch (Exception e) {
            writeJson(exchange, 500, error("reply error: " + e.getMessage()));
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

    private void writeSse(OutputStream out, org.apache.eventmesh.common.stream.StreamChunk chunk) throws IOException {
        String json = mapper.writeValueAsString(chunk);
        out.write(("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
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
            int qc = intParam(exchange.getRequestURI(), "queueCount", 4);
            if (qc == 4) {
                ingress.createLiteTopic(parent, lite);
            } else {
                ingress.createLiteTopic(parent, lite, qc);
            }
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
            // Ingress: structured CloudEvents JSON body → internal EventMeshFrame. (#5299)
            EventMeshFrame event = FrameAdaptors.get("cloudevents")
                .toFrame(new ByteTransport(readAll(exchange)));
            ingress.publishLiteFrame(parent, lite, event).get(10, TimeUnit.SECONDS);
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
            // Egress: drain EventMeshFrames from the LMQ, serialize each as CloudEvents JSON
            // via the egress adapter. (#5299)
            List<EventMeshFrame> events = ingress.pollLiteFrames(parent, lite, max, timeoutMs);
            com.fasterxml.jackson.databind.node.ArrayNode arr = mapper.createArrayNode();
            for (EventMeshFrame e : events) {
                arr.add(mapper.readTree(FrameAdaptors.toCloudEventsJson(e)));
            }
            writeJson(exchange, 200, arr);
        } catch (NumberFormatException e) {
            writeJson(exchange, 400, error("invalid max/timeoutMs parameter"));
        } catch (Exception e) {
            writeJson(exchange, 500, error("lite poll failed: " + e.getMessage()));
        }
    }

    /**
     * {@code POST /events/lite/publish-bytes?topic=<parent>&lite=<lite>} — publish a raw byte payload
     * (a {@link org.apache.eventmesh.common.wire.EventMeshFrame}) to a lite topic, bypassing CloudEvents
     * serialization. The request body IS the frame bytes (Content-Type arbitrary). The internal
     * runtime↔agent streaming wire uses this endpoint (§1.3). 202 on success.
     */
    private void litePublishBytes(HttpExchange exchange) throws IOException {
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
            byte[] payload = readAll(exchange);
            if (payload.length == 0) {
                writeJson(exchange, 400, error("empty body"));
                return;
            }
            ingress.publishLiteBytes(parent, lite, payload).get(10, TimeUnit.SECONDS);
            writeJson(exchange, 202, ack("accepted"));
        } catch (Exception e) {
            writeJson(exchange, 500, error("lite publish-bytes failed: " + e.getMessage()));
        }
    }

    /**
     * {@code GET /events/lite/poll-bytes?topic=<parent>&lite=<lite>&max=&timeoutMs=} → JSON array of
     * base64-encoded raw payloads from the LMQ (each a {@link org.apache.eventmesh.common.wire.EventMeshFrame}).
     * The byte counterpart of {@link #litePoll}; used by the agent over the internal streaming wire.
     */
    private void litePollBytes(HttpExchange exchange) throws IOException {
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
            java.util.List<byte[]> payloads = ingress.pollLiteBytes(parent, lite, max, timeoutMs);
            com.fasterxml.jackson.databind.node.ArrayNode arr = mapper.createArrayNode();
            java.util.Base64.Encoder b64 = java.util.Base64.getEncoder();
            for (byte[] p : payloads) {
                arr.add(b64.encodeToString(p));
            }
            writeJson(exchange, 200, arr);
        } catch (NumberFormatException e) {
            writeJson(exchange, 400, error("invalid max/timeoutMs parameter"));
        } catch (Exception e) {
            writeJson(exchange, 500, error("lite poll-bytes failed: " + e.getMessage()));
        }
    }

    // ---- agent control endpoints (§5.2) ----

    private void agentRegister(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        if (agentRegistrar == null) {
            writeJson(exchange, 503, error("agent registrar not configured"));
            return;
        }
        JsonNode body = readJson(exchange);
        String agentId = text(body, "agentId");
        if (agentId == null) {
            writeJson(exchange, 400, error("missing 'agentId'"));
            return;
        }
        int capacity = body.has("capacity") ? body.get("capacity").asInt() : 100;
        List<String> caps = new java.util.ArrayList<>();
        if (body.has("capabilities") && body.get("capabilities").isArray()) {
            body.get("capabilities").forEach(n -> caps.add(n.asText()));
        }
        try {
            var res = agentRegistrar.register(agentId, caps, capacity);
            java.util.Map<String, Object> out = new java.util.HashMap<>();
            out.put("parent", res.parent());
            out.put("clientParent", res.clientParent());
            writeJson(exchange, 200, out);
        } catch (Exception e) {
            writeJson(exchange, 500, error("register failed: " + e.getMessage()));
        }
    }

    private void agentReady(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        if (agentRegistrar == null) {
            writeJson(exchange, 503, error("agent registrar not configured"));
            return;
        }
        String agentId = text(readJson(exchange), "agentId");
        if (agentId == null) {
            writeJson(exchange, 400, error("missing 'agentId'"));
            return;
        }
        writeJson(exchange, agentRegistrar.ready(agentId) ? 200 : 404, ack("ok"));
    }

    private void agentHeartbeat(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        if (agentRegistrar == null) {
            writeJson(exchange, 503, error("agent registrar not configured"));
            return;
        }
        JsonNode body = readJson(exchange);
        String agentId = text(body, "agentId");
        if (agentId == null) {
            writeJson(exchange, 400, error("missing 'agentId'"));
            return;
        }
        int activeSessions = body.has("activeSessions") ? body.get("activeSessions").asInt() : 0;
        writeJson(exchange, agentRegistrar.heartbeat(agentId, activeSessions) ? 200 : 404, ack("ok"));
    }

    private void agentUnregister(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        if (agentRegistrar == null) {
            writeJson(exchange, 503, error("agent registrar not configured"));
            return;
        }
        String agentId = text(readJson(exchange), "agentId");
        if (agentId == null) {
            writeJson(exchange, 400, error("missing 'agentId'"));
            return;
        }
        agentRegistrar.unregister(agentId);
        writeJson(exchange, 200, ack("ok"));
    }

    private void sessionOpen(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        if (matchmaker == null) {
            writeJson(exchange, 503, error("matchmaker not configured"));
            return;
        }
        JsonNode body = readJson(exchange);
        String clientId = text(body, "clientId");
        if (clientId == null) {
            writeJson(exchange, 400, error("missing 'clientId'"));
            return;
        }
        String model = text(body, "model");
        try {
            var res = matchmaker.open(clientId, model);
            java.util.Map<String, Object> out = new java.util.HashMap<>();
            out.put("sessionId", res.sessionId());
            out.put("agentId", res.agentId());
            // Return the instance the client should pin subsequent turns/close to (§3.4). Empty when
            // no advertised address is configured → SDK keeps using its original baseUrl.
            out.put("instanceUrl", selfInstanceUrl());
            writeJson(exchange, 200, out);
        } catch (org.apache.eventmesh.runtime.session.Matchmaker.NoAgentAvailableException e) {
            writeJson(exchange, 429, error(e.getMessage()));
        }
    }

    /**
     * {@code GET /session/recommend?clientId=&limit=} — pick the least-loaded instance for a new
     * session, returning its {@code instanceUrl}. Single-instance (no cluster membership) → returns
     * self. Scoring (§3.3): {@code activeSessions×w1 + inflowBytesPerSec×w2 + cpuLoad×w3}, lowest
     * wins; byte-rate/CPU dominate so a heavy client doesn't pile onto one instance. Overload
     * avoidance: instances past the load threshold have their score inflated (negative feedback).
     */
    private void sessionRecommend(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        String clientId = param(exchange.getRequestURI(), "clientId");
        java.util.Map<String, Object> out = new java.util.HashMap<>();
        String url = recommendInstanceUrl(clientId);
        out.put("instanceUrl", url);
        writeJson(exchange, 200, out);
    }

    /**
     * Pick the best instance's URL for a new session, or self's advertised URL when not clustered.
     */
    private String recommendInstanceUrl(String clientId) {
        // Single-instance or no membership: return self (or empty if not advertised).
        if (clusterMembership == null) {
            return selfInstanceUrl();
        }
        java.util.Map<String, org.apache.eventmesh.runtime.cluster.ClusterMembership.InstanceInfo> live =
            clusterMembership.liveInstancesWithLoad();
        if (live.isEmpty()) {
            return selfInstanceUrl();
        }
        // Score each live instance; pick the lowest. Weights make byte-rate + CPU dominate over
        // session count (a heavy client shouldn't pick the instance it already overloads).
        final double wSessions = 1.0;
        final double wBytes = 0.001; // bytes/s in the hundreds-to-millions → scale down
        final double wCpu = 1000.0;  // cpuLoad ∈ [0,1] → scale up to compete with bytes
        String bestId = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (java.util.Map.Entry<String, org.apache.eventmesh.runtime.cluster.ClusterMembership.InstanceInfo> e : live.entrySet()) {
            org.apache.eventmesh.runtime.ingress.LoadMeter.Snapshot load = e.getValue().load;
            double score;
            if (load == null) {
                // Peer reports no load → treat as lightly loaded (prefer peers that DO report only if
                // they're genuinely lighter; a no-report peer gets a neutral mid score).
                score = 500.0;
            } else {
                score = load.activeSessions * wSessions
                    + load.inflowBytesPerSec * wBytes
                    + load.outflowBytesPerSec * wBytes
                    + load.cpuLoad * wCpu;
                // Overload negative feedback: an instance near saturation is pushed to the back so new
                // sessions flow to lighter peers (§3.3 mechanism 3).
                if (load.cpuLoad > 0.8 || load.inflowBytesPerSec > 5_000_000) {
                    score += 10000.0;
                }
            }
            if (score < bestScore) {
                bestScore = score;
                bestId = e.getKey();
            }
        }
        if (bestId == null) {
            return selfInstanceUrl();
        }
        org.apache.eventmesh.runtime.cluster.ClusterMembership.InstanceInfo best = live.get(bestId);
        // Address may be instanceId placeholder if advertisedAddr isn't configured; fall back to self.
        String addr = best.address != null && !best.address.isEmpty() ? best.address : null;
        if (addr == null || addr.equals(bestId)) {
            // Best is self or peer without a real address → return self's URL (sticky to this instance).
            return selfInstanceUrl();
        }
        return "http://" + addr;
    }

    /** This instance's own instanceUrl, or empty string if no advertised address configured. */
    private String selfInstanceUrl() {
        return advertisedAddr != null && !advertisedAddr.isEmpty() ? "http://" + advertisedAddr : "";
    }

    private void sessionClose(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        if (matchmaker == null) {
            writeJson(exchange, 503, error("matchmaker not configured"));
            return;
        }
        // path: /session/close/<sessionId>
        String path = exchange.getRequestURI().getPath();
        String sessionId = path.startsWith("/session/close/") ? path.substring("/session/close/".length()) : null;
        if (sessionId == null || sessionId.isEmpty()) {
            writeJson(exchange, 400, error("missing sessionId in path"));
            return;
        }
        boolean closed = matchmaker.close(sessionId);
        if (closed && sessionRouter != null) {
            // Explicit close tears the session's channel down: in mode 2 the per-session reply
            // consumer + the agent's per-session request subscription (kept alive across turns by
            // endTurn) are released here. Idempotent.
            sessionRouter.cancel(sessionId);
        }
        writeJson(exchange, closed ? 200 : 404, ack("ok"));
    }

    /**
     * {@code POST /session/stream/{sessionId}} — v2 SSE streaming (§5③). Resolves the session's agent +
     * channel via the router, publishes one STREAM_REQ, and drains CHUNKs to the client until a terminal
     * chunk, the deadline, or disconnect.
     */
    private void sessionStream(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        if (!ingress.isLiteCapable()) {
            writeJson(exchange, 501, error("storage does not support lite topic (needs rocketmq5)"));
            return;
        }
        if (sessionRouter == null) {
            writeJson(exchange, 503, error("session router not configured"));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String sessionId = path.startsWith("/session/stream/") ? path.substring("/session/stream/".length()) : null;
        if (sessionId == null || sessionId.isEmpty()) {
            writeJson(exchange, 400, error("missing sessionId in path"));
            return;
        }
        JsonNode body = readJson(exchange);
        String prompt = text(body, "prompt");
        if (prompt == null) {
            writeJson(exchange, 400, error("missing 'prompt' in body"));
            return;
        }
        String model = text(body, "model");
        long timeoutMs = body.has("timeoutMs") ? body.get("timeoutMs").asLong() : 0L;

        org.apache.eventmesh.runtime.session.SessionRouter.StreamSink sink;
        try {
            sink = sessionRouter.startStream(sessionId, prompt, model, timeoutMs);
        } catch (org.apache.eventmesh.runtime.session.SessionRouter.NoSuchSessionException e) {
            writeJson(exchange, 404, error(e.getMessage()));
            return;
        } catch (Exception e) {
            writeJson(exchange, 500, error("stream start failed: " + e.getMessage()));
            return;
        }

        // true once a terminal (done/error/timeout) chunk reached the client → the turn ended
        // naturally (keep the session channel alive for multi-turn). False on a mid-stream
        // disconnect → tear the session down. (See SessionRouter.endTurn vs cancel.)
        boolean terminalSent = false;
        try {
            // Header send lives inside the try so an early client disconnect (sendResponseHeaders
            // throws IOException after startStream already registered the sink + published STREAM_REQ)
            // still hits the finally and cancels the session — otherwise the sink orphans until reaper.
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            while (sink.isActive()) {
                long remaining = sink.remainingMs();
                if (remaining <= 0) {
                    writeSse(out, sessionTimeoutChunk(sink));
                    terminalSent = true;
                    break;
                }
                org.apache.eventmesh.common.stream.StreamChunk chunk;
                try {
                    chunk = sink.poll(remaining);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (chunk == null) {
                    writeSse(out, sessionTimeoutChunk(sink));
                    terminalSent = true;
                    break;
                }
                writeSse(out, chunk);
                if (chunk.isDone()) {
                    terminalSent = true;
                    break;
                }
            }
            // Flush any chunk the reply consumer offered but we didn't drain. The consumer offers the
            // terminal chunk then cancels the sink; if that cancel wins the race with our poll() above,
            // the terminal chunk would be stranded in the queue and the client's last chunk would be
            // non-terminal. Drain so the terminal done/error marker always reaches the client.
            org.apache.eventmesh.common.stream.StreamChunk tail;
            while ((tail = sink.pollNoWait()) != null) {
                writeSse(out, tail);
                if (tail.isDone()) {
                    terminalSent = true;
                    break;
                }
            }
        } catch (IOException e) {
            log.debug("session-stream client disconnected: {}", e.toString());
        } finally {
            if (terminalSent) {
                sessionRouter.endTurn(sessionId);
            } else {
                sessionRouter.cancel(sessionId);
            }
            exchange.close();
        }
    }

    private static org.apache.eventmesh.common.stream.StreamChunk sessionTimeoutChunk(
        org.apache.eventmesh.runtime.session.SessionRouter.StreamSink sink) {
        return org.apache.eventmesh.common.stream.StreamChunk.builder()
            .sessionId(sink.sessionId).seq(-1).chunk("").done(true).error("timeout").build();
    }

    /**
     * {@code POST /session/publish/{sessionId}} (§5④, mode 2 pub/sub) — publish one chunk onto the
     * session's lite topic. Body is a {@link org.apache.eventmesh.common.stream.StreamChunk}; the
     * lite key is derived deterministically from {@code sessionId} ({@code session.<sessionId>}), so
     * the publisher and subscriber always agree on the physical topic. One POST per chunk.
     */
    private void sessionPublish(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        if (!ingress.isLiteCapable()) {
            writeJson(exchange, 501, error("storage does not support lite topic (needs rocketmq5)"));
            return;
        }
        if (sessionRouter == null || !sessionRouter.isMode2Enabled()) {
            writeJson(exchange, 503, error("session publish/subscribe not configured on this runtime"));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String sessionId = path.startsWith("/session/publish/")
            ? path.substring("/session/publish/".length()) : null;
        if (sessionId == null || sessionId.isEmpty()) {
            writeJson(exchange, 400, error("missing sessionId in path"));
            return;
        }
        org.apache.eventmesh.common.stream.StreamChunk chunk =
            mapper.treeToValue(readJson(exchange), org.apache.eventmesh.common.stream.StreamChunk.class);
        if (chunk == null) {
            writeJson(exchange, 400, error("missing StreamChunk body"));
            return;
        }
        // Stamp the sessionId from the path so the chunk is self-describing on the lite topic even if
        // the publisher omitted it (it must match the routing key).
        chunk.setSessionId(sessionId);
        try {
            sessionRouter.publishSession(sessionId, chunk);
            writeJson(exchange, 201, ack("published"));
        } catch (Exception e) {
            writeJson(exchange, 500, error("publish failed: " + e.getMessage()));
        }
    }

    /**
     * {@code GET /session/subscribe/{sessionId}} (§5④, mode 2 pub/sub) — open an SSE stream that
     * drains the session's lite topic to the subscriber. Single-cursor: a fresh subscribe replays
     * from the head of the lite (persistence-backed), so a crashed-and-restarted consumer resyncs
     * without coordination. One subscriber per session at a time.
     */
    private void sessionSubscribe(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("method not allowed"));
            return;
        }
        if (!ingress.isLiteCapable()) {
            writeJson(exchange, 501, error("storage does not support lite topic (needs rocketmq5)"));
            return;
        }
        if (sessionRouter == null || !sessionRouter.isMode2Enabled()) {
            writeJson(exchange, 503, error("session publish/subscribe not configured on this runtime"));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String sessionId = path.startsWith("/session/subscribe/")
            ? path.substring("/session/subscribe/".length()) : null;
        if (sessionId == null || sessionId.isEmpty()) {
            writeJson(exchange, 400, error("missing sessionId in path"));
            return;
        }
        org.apache.eventmesh.runtime.session.SessionRouter.StreamSink sink;
        try {
            sink = sessionRouter.startSubscribe(sessionId);
        } catch (IllegalStateException e) {
            writeJson(exchange, 409, error(e.getMessage())); // already a subscriber for this session
            return;
        }
        try {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            while (sink.isActive()) {
                org.apache.eventmesh.common.stream.StreamChunk chunk;
                try {
                    chunk = sink.poll(sink.remainingMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (chunk == null) {
                    continue;
                }
                writeSse(out, chunk);
                if (chunk.isDone()) {
                    break;
                }
            }
        } catch (IOException e) {
            log.debug("session-subscribe client disconnected: {}", e.toString());
        } finally {
            sessionRouter.cancelSubscribe(sessionId);
            exchange.close();
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
