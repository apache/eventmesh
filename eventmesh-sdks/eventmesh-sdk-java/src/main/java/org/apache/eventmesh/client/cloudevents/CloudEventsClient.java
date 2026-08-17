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

package org.apache.eventmesh.client.cloudevents;

import org.apache.eventmesh.client.cloudevents.stream.DefaultStreamingOperations;
import org.apache.eventmesh.client.cloudevents.stream.DefaultStreamingResponse;
import org.apache.eventmesh.client.cloudevents.stream.SessionPublisher;
import org.apache.eventmesh.client.cloudevents.stream.StreamRequest;
import org.apache.eventmesh.client.cloudevents.stream.StreamingOperations;
import org.apache.eventmesh.client.cloudevents.stream.StreamingResponse;
import org.apache.eventmesh.common.stream.StreamChunk;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/**
 * New EventMesh client for the simplified runtime — HTTP + CloudEvents only (§5). Four core
 * operations over {@code /events/*}: {@code publish}, {@code request} (blocking request-reply),
 * {@code subscribe} (long-poll loop driving a handler), and {@code ack}. No TCP/gRPC, no MQ group
 * semantics — just CloudEvents over HTTP.
 *
 * <pre>
 *   CloudEventsClient client = CloudEventsClient.builder()
 *       .runtimeUrl("http://localhost:8080").clientId("order-svc").build();
 *   client.publish("orders", event);
 *   client.subscribe("orders", "BROADCAST", e -> handle(e));
 * </pre>
 */
@Slf4j
public class CloudEventsClient {

    private final String baseUrl;
    private final String clientId;
    /** Base URL of the runtime's WebSocket push server (separate port from {@link #baseUrl}); null
     *  means subscribeWs falls back to {@link #baseUrl}. */
    private final String wsBaseUrl;
    private final long pollIntervalMs;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService pollExecutor;
    private final AtomicBoolean polling = new AtomicBoolean(false);
    /** SSE loop's own active flag (separate from {@link #polling} — the poll-loop CAS must not be
     *  blocked by SSE/WS being active). */
    private final AtomicBoolean sseActive = new AtomicBoolean(false);
    private volatile Consumer<CloudEvent> autoHandler;
    private volatile java.util.function.Predicate<CloudEvent> manualAckHandler;
    private java.net.http.WebSocket webSocket;
    private java.net.http.HttpClient wsHttpClient;
    /** Tracked normal-topic subscriptions ({@code subscribe}/{@code subscribeWithAck}), for per-topic
     *  {@link #unsubscribe(String)}: when this empties, the shared long-poll loop is stopped. */
    private final java.util.Set<String> subscribedTopics = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Active lite subscriptions: {@code parent#lite → per-subscription stop flag} for {@link #unsubscribeLite}. */
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicBoolean> liteSubs =
        new java.util.concurrent.ConcurrentHashMap<>();

    CloudEventsClient(String runtimeUrl, String clientId, long pollIntervalMs, String wsUrl) {
        this.baseUrl = runtimeUrl.endsWith("/") ? runtimeUrl.substring(0, runtimeUrl.length() - 1) : runtimeUrl;
        this.pollBaseUrl = this.baseUrl;
        this.clientId = clientId;
        this.wsBaseUrl = wsUrl == null ? null : (wsUrl.endsWith("/") ? wsUrl.substring(0, wsUrl.length() - 1) : wsUrl);
        this.pollIntervalMs = pollIntervalMs;
        // Java 21 virtual threads: the long-poll / SSE / WebSocket loops do blocking HTTP with
        // readTimeouts up to 70s. Running each on its own virtual thread (instead of a single parked
        // platform thread) frees the carrier pool while blocked. Thread-per-task keeps one VT per
        // submitted loop; virtual threads are daemon by default.
        this.pollExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("eventmesh-client-poll-" + clientId + "-", 1).factory());
    }

    /** This client's base URL. */
    public String baseUrl() {
        return baseUrl;
    }

    /** Base URL for the poll loop — pinned to the instance returned by /events/subscribe (§3.4), or
     *  {@link #baseUrl} when the runtime didn't advertise one. */
    private volatile String pollBaseUrl;

    /**
     * A lightweight clone pointing at {@code url} instead of this client's baseUrl, for session
     * pinning (StreamingSession pins its turns/close to the instanceUrl returned by /session/open,
     * §3.4). Shares the mapper; the pollExecutor is fresh (the pinned clone only does session calls,
     * not long-poll subscriptions, so it never submits to the executor).
     */
    public CloudEventsClient withBaseUrl(String url) {
        return new CloudEventsClient(url, this.clientId, this.pollIntervalMs, this.wsBaseUrl);
    }

    public static CloudEventsClientBuilder builder() {
        return new CloudEventsClientBuilder();
    }

    /** Publish one CloudEvent. @return true on 202 Accepted. */
    public boolean publish(String topic, CloudEvent event) {
        int status = post(baseUrl + "/events/publish?topic=" + enc(topic), serialize(event), "application/cloudevents+json");
        return status == 202;
    }

    /**
     * Batch publish (§13.7.3). POST a CloudEvent JSON array to {@code /events/publish-batch}.
     * @return true if the server accepted all events (202).
     */
    public boolean publish(String topic, java.util.List<CloudEvent> events) {
        if (events == null || events.isEmpty()) {
            return true;
        }
        try {
            com.fasterxml.jackson.databind.node.ArrayNode arr = mapper.createArrayNode();
            for (CloudEvent e : events) {
                arr.add(mapper.readTree(serialize(e)));
            }
            int status = post(baseUrl + "/events/publish-batch?topic=" + enc(topic),
                mapper.writeValueAsBytes(arr), "application/cloudevents-batch+json");
            return status == 202;
        } catch (Exception e) {
            log.warn("batch publish to {} failed: {}", topic, e.toString());
            return false;
        }
    }

    /** Blocking request-reply (§17). @return the reply CloudEvent, or null on timeout/error. */
    public CloudEvent request(String topic, CloudEvent event, long timeoutMs) {
        byte[] resp = postBytes(baseUrl + "/events/request?topic=" + enc(topic) + "&timeoutMs=" + timeoutMs,
            serialize(event), "application/cloudevents+json");
        return resp == null ? null : deserialize(resp);
    }

    /** Deliver a reply to a pending request (responder side). */
    public boolean reply(String correlationId, CloudEvent replyEvent) {
        ObjectNode body = mapper.createObjectNode();
        body.put("correlationId", correlationId);
        body.set("event", mapper.valueToTree(toMap(replyEvent)));
        int status = post(baseUrl + "/events/reply", json(body), "application/json");
        return status == 200;
    }

    // ---- Lite Topic (RIP-83, only against a RocketMQ 5.x backend) ----
    // These hit the runtime's /events/lite/* endpoints, which 501 if the storage plugin isn't
    // LiteTopicCapable (i.e. only the rocketmq5 backend serves them).

    /**
     * Create/declare a lite topic under {@code parentTopic} (ensures the parent is lite-capable).
     * @return true on 200; false if the backend doesn't support lite (4.x/kafka/standalone) or on error.
     */
    public boolean createLiteTopic(String parentTopic, String liteTopic) {
        ObjectNode body = mapper.createObjectNode();
        return post(baseUrl + "/events/lite/create?topic=" + enc(parentTopic) + "&lite=" + enc(liteTopic),
            json(body), "application/json") == 200;
    }

    /**
     * Publish one CloudEvent to a lite topic (the runtime routes it into the lite topic's LMQ).
     * @return true on 202; false if the backend doesn't support lite (4.x/kafka/standalone) or on error.
     */
    public boolean publishLite(String parentTopic, String liteTopic, CloudEvent event) {
        return post(baseUrl + "/events/lite/publish?topic=" + enc(parentTopic) + "&lite=" + enc(liteTopic),
            serialize(event), "application/cloudevents+json") == 202;
    }

    /**
     * Subscribe to a lite topic (background loop, push-style like {@link #subscribe}): repeatedly
     * pulls the lite topic's LMQ and invokes {@code handler} per event. The lite consumer's offset
     * self-manages in the storage plugin (no ACK / no reliability layer — lite is a direct pull).
     * Stop with {@link #unsubscribe()} or {@link #shutdown()}. Only against a 5.x backend.
     */
    public void subscribeLite(String parentTopic, String liteTopic, Consumer<CloudEvent> handler) {
        String liteKey = parentTopic + "#" + liteTopic;
        java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean old = liteSubs.put(liteKey, stop);
        if (old != null) {
            old.set(true); // stop any prior loop for the same (parent, lite) — prevents orphan + duplicate delivery
        }
        pollExecutor.submit(() -> {
            while (!stop.get()) {
                try {
                    byte[] resp = getBytes(baseUrl + "/events/lite/poll?topic=" + enc(parentTopic)
                        + "&lite=" + enc(liteTopic) + "&max=100&timeoutMs=" + pollIntervalMs);
                    boolean got = false;
                    if (resp != null && resp.length > 0) {
                        JsonNode arr = mapper.readTree(resp);
                        for (JsonNode el : arr) {
                            CloudEvent e = deserialize(mapper.writeValueAsBytes(el));
                            if (e != null) {
                                handler.accept(e);
                                got = true;
                            }
                        }
                    }
                    if (!got) {
                        Thread.sleep(pollIntervalMs); // idle backoff (lite pull returns immediately when empty)
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    log.warn("subscribeLite poll error: {}", e.toString());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
    }

    /**
     * Publish a raw byte payload to a lite topic, bypassing CloudEvents (the internal private-wire
     * path — e.g. a {@link org.apache.eventmesh.common.wire.EventMeshFrame}). Body IS the payload bytes.
     * @return true on 202; false on non-lite backend or error.
     */
    public boolean publishLiteBytes(String parentTopic, String liteTopic, byte[] payload) {
        return post(baseUrl + "/events/lite/publish-bytes?topic=" + enc(parentTopic) + "&lite=" + enc(liteTopic),
            payload, "application/octet-stream") == 202;
    }

    /**
     * Subscribe to a lite topic as raw byte payloads (the byte counterpart of {@link #subscribeLite}).
     * Each polled payload (base64 on the wire) is decoded and handed to {@code handler}; the caller
     * interprets it as a {@link org.apache.eventmesh.common.wire.EventMeshFrame}. Stop with
     * {@link #unsubscribeLite(String)} or {@link #shutdown()}.
     */
    public void subscribeLiteBytes(String parentTopic, String liteTopic, java.util.function.Consumer<byte[]> handler) {
        String liteKey = parentTopic + "#" + liteTopic;
        java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean old = liteSubs.put(liteKey, stop);
        if (old != null) {
            old.set(true);
        }
        pollExecutor.submit(() -> {
            java.util.Base64.Decoder b64 = java.util.Base64.getDecoder();
            while (!stop.get()) {
                try {
                    byte[] resp = getBytes(baseUrl + "/events/lite/poll-bytes?topic=" + enc(parentTopic)
                        + "&lite=" + enc(liteTopic) + "&max=100&timeoutMs=" + pollIntervalMs);
                    boolean got = false;
                    if (resp != null && resp.length > 0) {
                        JsonNode arr = mapper.readTree(resp);
                        for (JsonNode el : arr) {
                            try {
                                handler.accept(b64.decode(el.asText()));
                                got = true;
                            } catch (IllegalArgumentException bad) {
                                log.debug("lite poll-bytes: skipping non-base64 entry: {}", bad.toString());
                            }
                        }
                    }
                    if (!got) {
                        Thread.sleep(pollIntervalMs);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    log.warn("subscribeLiteBytes poll error: {}", e.toString());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
    }

    // v1 streamCall (shared req-lite + per-stream resp-<id>) removed — replaced by the v2 session
    // flow openSession/streamSession/close (Phase 7). The agent side uses subscribeLite /
    // publishLite directly (unchanged).

    // -------------------- v2 session flow --------------------

    /** Result of {@code POST /session/open}: the minted sessionId + the chosen agentId. */
    public static class SessionHandle {
        public final String sessionId;
        public final String agentId;
        /** Instance URL the client should pin subsequent turns/close to (§3.4); empty = keep baseUrl. */
        public final String instanceUrl;

        public SessionHandle(String sessionId, String agentId) {
            this(sessionId, agentId, "");
        }

        public SessionHandle(String sessionId, String agentId, String instanceUrl) {
            this.sessionId = sessionId;
            this.agentId = agentId;
            this.instanceUrl = instanceUrl == null ? "" : instanceUrl;
        }
    }

    /**
     * {@code GET /session/recommend?clientId=} → {@code {instanceUrl}}: the least-loaded instance for
     * a new session (§3.3). Single-instance deployments return the contacted instance's URL.
     */
    public String recommendInstance(String clientId) {
        try {
            byte[] resp = getBytes(baseUrl + "/session/recommend?clientId=" + enc(clientId));
            if (resp == null) {
                return "";
            }
            JsonNode node = mapper.readTree(resp);
            JsonNode url = node.get("instanceUrl");
            return url == null ? "" : url.asText();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * {@code POST /session/open {clientId, model?}} → {@code {sessionId, agentId, instanceUrl}} (mode 1
     * handshake, §5②). The matchmaker binds the client to an agent and mints a sessionId. The
     * returned {@code instanceUrl} is the instance the client should pin to for load balancing.
     */
    public SessionHandle openSession(String clientId, String model) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("clientId", clientId);
            if (model != null) {
                body.put("model", model);
            }
            byte[] resp = postBytes(baseUrl + "/session/open", json(body), "application/json");
            if (resp == null) {
                throw new RuntimeException("openSession failed (runtime returned non-2xx)");
            }
            JsonNode node = mapper.readTree(resp);
            String sid = node.get("sessionId").asText();
            String agentId = node.get("agentId").asText();
            JsonNode url = node.get("instanceUrl");
            return new SessionHandle(sid, agentId, url == null ? "" : url.asText());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("openSession failed: " + e, e);
        }
    }

    /** Convenience: {@code openSession(clientId, null)}. */
    public SessionHandle openSession(String clientId) {
        return openSession(clientId, null);
    }

    /**
     * {@code POST /session/stream/{sessionId} {prompt}} → drain SSE {@code data:{...}} frames into
     * {@code handler} until a terminal ({@code done=true}) chunk, error, or disconnect. The returned
     * future completes when the stream ends. Now a thin wrapper over {@link #openStreamResponse}
     * (reuses the SSE→queue→callback logic in {@link DefaultStreamingResponse}); prefer
     * {@link #streaming()} for new code (it returns a first-class {@link StreamingResponse}).
     */
    public java.util.concurrent.CompletableFuture<Void> streamSession(String sessionId, String prompt,
                                                                      Consumer<StreamChunk> handler) {
        StreamRequest req = StreamRequest.builder().prompt(prompt).build();
        StreamingResponse resp = openStreamResponse(sessionId, "", req, () -> {
        });
        return resp.forEach(handler).whenComplete((v, ex) -> resp.close());
    }

    /** {@code POST /session/close/{sessionId}} — drop the session (best-effort). */
    public void closeSession(String sessionId) {
        postBytes(baseUrl + "/session/close/" + enc(sessionId), new byte[0], "application/json");
    }

    /** This client's configured clientId (used by one-shot calls). */
    public String clientId() {
        return clientId;
    }

    /**
     * Entry point for the first-class streaming API. Returns a facade that wraps this client's
     * session methods with {@link org.apache.eventmesh.client.cloudevents.stream.StreamingSession}
     * and one-shot entry points.
     */
    public StreamingOperations streaming() {
        return DefaultStreamingOperations.forClient(this);
    }

    /**
     * Open one turn's SSE stream as a {@link StreamingResponse} (the queue + three consumption
     * postures). Public so the streaming-session types (in the {@code stream} subpackage) can build
     * a response without accessing this client's {@code baseUrl} / {@code mapper} directly.
     *
     * @param sessionId the session id to stream under
     * @param agentId   the agent id (returned by {@code /session/open}), surfaced via
     *                  {@link StreamingResponse#agentId()}
     * @param req       the request (prompt + optional model / per-call timeout)
     * @param onClose   hook run when the response is closed (no-op for multi-turn; session-close
     *                  for one-shot)
     */
    public StreamingResponse openStreamResponse(String sessionId, String agentId,
                                                StreamRequest req, Runnable onClose) {
        return DefaultStreamingResponse.start(baseUrl, mapper, sessionId, agentId,
            req.toJsonString(), DefaultStreamingResponse.DEFAULT_QUEUE_CAPACITY, onClose);
    }

    /**
     * Mode 2 (publish/subscribe, §5④) — subscribe to a session's stream as a {@link StreamingResponse}
     * (the same {@code forEach} consumption posture as mode 1). Opens {@code GET /session/subscribe/
     * {sessionId}} (SSE); the runtime drains the session's lite topic. The lite key is derived from
     * the sessionId deterministically, so the publisher and subscriber always agree on the physical
     * topic without a binding table. Single-cursor replay-from-start lets a crashed-and-restarted
     * consumer resync.
     *
     * @param sessionId the session id to subscribe to (the routing key)
     * @return a live streaming response — call {@link StreamingResponse#forEach} to consume, then
     *         {@link StreamingResponse#close()} to unsubscribe
     */
    public StreamingResponse subscribeSession(String sessionId) {
        return DefaultStreamingResponse.startSubscribe(baseUrl, mapper, sessionId,
            DefaultStreamingResponse.DEFAULT_QUEUE_CAPACITY, () -> {
            });
    }

    /**
     * Mode 2 (publish/subscribe, §5④) — open a publisher for a session stream. Each {@code publish}
     * POSTs one chunk to {@code /session/publish/{sessionId}}; the runtime writes it onto the
     * session's lite topic. {@link SessionPublisher#close()} emits the terminal chunk.
     */
    public SessionPublisher openSessionPublisher(String sessionId) {
        return new SessionPublisher(baseUrl, mapper, sessionId);
    }

    /**
     * Subscribe and drive {@code handler} with each delivered event (long-poll loop). Each event is
     * ACKed automatically after the handler returns.
     */
    public void subscribe(String topic, String mode, Consumer<CloudEvent> handler) {
        this.autoHandler = handler;
        this.manualAckHandler = null;
        ObjectNode body = mapper.createObjectNode();
        body.put("clientId", clientId);
        body.put("topic", topic);
        body.put("mode", mode);
        capturePollInstance(postBytes(baseUrl + "/events/subscribe", json(body), "application/json"));
        subscribedTopics.add(topic);
        startPollLoop();
    }

    /**
     * Subscribe with manual ACK (§13.3.5): the handler returns {@code true} to ACK (event processed,
     * offset advances) or {@code false} to leave it unacked so the dispatcher redelivers after the
     * ACK timeout — at-least-once with a client-controlled idempotency window. This is the
     * reliability-correct alternative to {@link #subscribe}, which auto-ACKs on handler return.
     */
    public void subscribeWithAck(String topic, String mode, java.util.function.Predicate<CloudEvent> handler) {
        this.manualAckHandler = handler;
        this.autoHandler = null;
        ObjectNode body = mapper.createObjectNode();
        body.put("clientId", clientId);
        body.put("topic", topic);
        body.put("mode", mode);
        capturePollInstance(postBytes(baseUrl + "/events/subscribe", json(body), "application/json"));
        subscribedTopics.add(topic);
        startPollLoop();
    }

    /**
     * Read the {@code instanceUrl} from a /events/subscribe response and pin subsequent polls to it
     * (load balancing, §3.4). Empty → keep the original baseUrl.
     */
    private void capturePollInstance(byte[] resp) {
        if (resp == null || resp.length == 0) {
            return;
        }
        try {
            JsonNode node = mapper.readTree(resp);
            JsonNode url = node.get("instanceUrl");
            if (url != null && !url.asText().isEmpty()) {
                this.pollBaseUrl = url.asText();
            }
        } catch (Exception ignored) {
            // older runtime without instanceUrl in the response → poll against baseUrl
        }
    }

    /**
     * Subscribe and receive pushed events over SSE (§5.1.1 / §13.7.1). The server holds the HTTP
     * response open and writes {@code data:} frames; this reads the stream and auto-ACKs each event.
     */
    public void subscribeSse(String topic, String mode, Consumer<CloudEvent> handler) {
        ObjectNode body = mapper.createObjectNode();
        body.put("clientId", clientId);
        body.put("topic", topic);
        body.put("mode", mode);
        post(baseUrl + "/events/subscribe", json(body), "application/json");
        sseActive.set(true);
        pollExecutor.submit(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + "/events/stream?clientId=" + enc(clientId)).openConnection();
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setReadTimeout(0); // long-lived stream
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while (sseActive.get() && (line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            parsePushFrame(line.substring(6).trim(), handler);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("sse stream error: {}", e.toString());
            }
        });
    }

    /**
     * Subscribe and receive pushed events over WebSocket (§5.1.1 default / §15.6). Uses the JDK
     * {@code java.net.http.WebSocket} client; auto-ACKs each event. Close with {@link #shutdown}.
     */
    public void subscribeWs(String topic, String mode, Consumer<CloudEvent> handler) {
        ObjectNode body = mapper.createObjectNode();
        body.put("clientId", clientId);
        body.put("topic", topic);
        body.put("mode", mode);
        post(baseUrl + "/events/subscribe", json(body), "application/json");
        // Close any prior WS connection before reconnecting (avoids HttpClient/executor leak).
        if (webSocket != null) {
            try {
                webSocket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "reconnect");
            } catch (Exception ignored) {
                // best-effort close during reconnect
            }
        }
        if (wsHttpClient != null) {
            try {
                wsHttpClient.close();
            } catch (Exception ignored) {
                // best-effort close during reconnect
            }
        }
        String wsBase = wsBaseUrl != null ? wsBaseUrl : baseUrl;
        String wsUrl = wsBase.replaceFirst("http://", "ws://").replaceFirst("https://", "wss://")
            + "/events/stream?clientId=" + enc(clientId);
        // Daemon-threaded HttpClient so the WS client's selector/callback threads don't keep the JVM
        // alive after shutdown (the default newHttpClient() uses non-daemon threads).
        wsHttpClient = java.net.http.HttpClient.newBuilder()
            .executor(java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "em-client-ws");
                t.setDaemon(true);
                return t;
            }))
            .build();
        java.util.concurrent.CompletableFuture<java.net.http.WebSocket> wsConnect = wsHttpClient.newWebSocketBuilder()
            .buildAsync(java.net.URI.create(wsUrl), new java.net.http.WebSocket.Listener() {
                @Override
                public java.util.concurrent.CompletionStage<?> onText(java.net.http.WebSocket ws, CharSequence data, boolean last) {
                    parsePushFrame(data.toString(), handler);
                    ws.request(1);
                    return null;
                }

                @Override
                public void onError(java.net.http.WebSocket ws, Throwable error) {
                    log.warn("ws stream error: {}", error.toString());
                }
            });
        try {
            this.webSocket = wsConnect.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("subscribeWs: WebSocket connect to {} failed: {}", wsUrl, e.toString());
        }
    }

    private void parsePushFrame(String json, Consumer<CloudEvent> handler) {
        try {
            JsonNode node = mapper.readTree(json);
            String deliveryId = node.has("deliveryId") ? node.get("deliveryId").asText() : null;
            CloudEvent event = deserialize(mapper.writeValueAsBytes(node.get("event")));
            if (event != null) {
                handler.accept(event);
            }
            if (deliveryId != null) {
                ack(deliveryId);
            }
        } catch (Exception e) {
            log.warn("push frame parse error: {}", e.toString());
        }
    }

    /**
     * Unsubscribe from one topic: server-side removal of the {@code {clientId, topic}} subscription
     * (the topic's events stop on ALL transports — poll/SSE/WS — while other subscriptions keep
     * running). If no normal topics remain, the shared long-poll loop is stopped.
     */
    public void unsubscribe(String topic) {
        ObjectNode body = mapper.createObjectNode();
        body.put("clientId", clientId);
        body.put("topic", topic);
        post(baseUrl + "/events/unsubscribe", json(body), "application/json");
        subscribedTopics.remove(topic);
        if (subscribedTopics.isEmpty()) {
            polling.set(false); // no normal topics left → stop the shared long-poll loop
        }
    }

    /**
     * Unsubscribe everything for this client: remove ALL server-side subscriptions (by clientId),
     * stop the long-poll loop, stop every lite pull loop, and (on {@link #shutdown()}) close the
     * SSE/WS push connections.
     */
    public void unsubscribe() {
        ObjectNode body = mapper.createObjectNode();
        body.put("clientId", clientId);
        post(baseUrl + "/events/unsubscribe", json(body), "application/json");
        polling.set(false);
        sseActive.set(false);
        subscribedTopics.clear();
        for (java.util.concurrent.atomic.AtomicBoolean stop : liteSubs.values()) {
            stop.set(true);
        }
        liteSubs.clear();
    }

    /**
     * Stop one lite subscription's background pull loop (lite has no server-side registration, so this
     * is purely client-side). Other lite / normal subscriptions are unaffected.
     */
    public void unsubscribeLite(String parentTopic, String liteTopic) {
        java.util.concurrent.atomic.AtomicBoolean stop = liteSubs.remove(parentTopic + "#" + liteTopic);
        if (stop != null) {
            stop.set(true);
        }
    }

    /** Shut down the client's poll executor. */
    public void shutdown() {
        polling.set(false);
        sseActive.set(false);
        if (webSocket != null) {
            try {
                webSocket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "shutdown");
            } catch (Exception ignored) {
                // best-effort close during shutdown
            }
        }
        if (wsHttpClient != null) {
            try {
                wsHttpClient.close(); // Java 21: terminates the client's selector/callback threads
            } catch (Exception ignored) {
                // best-effort close during shutdown
            }
        }
        pollExecutor.shutdownNow();
    }

    private void startPollLoop() {
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        pollExecutor.submit(() -> {
            while (polling.get()) {
                try {
                    byte[] resp = getBytes(pollBaseUrl + "/events/poll?clientId=" + enc(clientId) + "&max=100&timeoutMs=" + pollIntervalMs);
                    if (resp == null || resp.length == 0) {
                        continue;
                    }
                    JsonNode arr = mapper.readTree(resp);
                    for (JsonNode entry : arr) {
                        String deliveryId = entry.get("deliveryId").asText();
                        CloudEvent event = deserialize(mapper.writeValueAsBytes(entry.get("event")));
                        if (event == null) {
                            continue;
                        }
                        if (manualAckHandler != null) {
                            if (manualAckHandler.test(event)) {
                                ack(deliveryId); // processed → ack, offset advances
                            }
                            // else: don't ack — dispatcher redelivers after ACK timeout (at-least-once)
                        } else if (autoHandler != null) {
                            autoHandler.accept(event);
                            ack(deliveryId);
                        }
                    }
                } catch (Exception e) {
                    log.warn("poll loop error: {}", e.toString());
                    // Reconnect backoff (§Phase3 DoD "SDK auto-reconnect"): avoid a tight retry loop
                    // when the server is down; sleep 1s then the while-loop retries.
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
    }

    private boolean ack(String deliveryId) {
        ObjectNode body = mapper.createObjectNode();
        body.put("deliveryId", deliveryId);
        return post(pollBaseUrl + "/events/ack", json(body), "application/json") == 200;
    }

    // ---- HTTP helpers (HttpURLConnection, Java 8) ----

    private int post(String url, byte[] body, String contentType) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", contentType);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(60000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
            int status = conn.getResponseCode();
            drain(conn);
            return status;
        } catch (IOException e) {
            log.warn("POST {} failed: {}", url, e.toString());
            return -1;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private byte[] postBytes(String url, byte[] body, String contentType) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", contentType);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(70000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                drain(conn);
                return null;
            }
            try (java.io.InputStream is = conn.getInputStream()) {
                return is.readAllBytes();
            }
        } catch (IOException e) {
            log.warn("POST {} failed: {}", url, e.toString());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private byte[] getBytes(String url) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(70000);
            if (conn.getResponseCode() >= 400) {
                drain(conn);
                return null;
            }
            try (java.io.InputStream is = conn.getInputStream()) {
                return is.readAllBytes();
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void drain(HttpURLConnection conn) {
        try {
            java.io.InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
            if (is != null) {
                is.skip(Long.MAX_VALUE);
                is.close();
            }
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private byte[] serialize(CloudEvent event) {
        return EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE).serialize(event);
    }

    private CloudEvent deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE).deserialize(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    /** toMap for JSON-embedding a CloudEvent (reply body). */
    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> toMap(CloudEvent event) {
        // Re-serialize and parse so the CloudEvent becomes a plain JSON tree.
        try {
            return mapper.readValue(serialize(event), java.util.Map.class);
        } catch (Exception e) {
            return java.util.Collections.emptyMap();
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Serialize a Jackson tree node, wrapping the checked exception. */
    private byte[] json(ObjectNode node) {
        try {
            return mapper.writeValueAsBytes(node);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /** Build a minimal CloudEvent (convenience). */
    public static CloudEvent event(String id, String source, String type, byte[] data) {
        CloudEventBuilder b = CloudEventBuilder.v1()
            .withId(id).withSource(java.net.URI.create(source)).withType(type)
            .withDataContentType("application/octet-stream");
        if (data != null) {
            b.withData(data);
        }
        return b.build();
    }
}
