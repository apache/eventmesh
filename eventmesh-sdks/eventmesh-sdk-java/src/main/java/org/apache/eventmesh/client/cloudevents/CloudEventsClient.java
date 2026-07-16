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
        post(baseUrl + "/events/subscribe", json(body), "application/json");
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
        post(baseUrl + "/events/subscribe", json(body), "application/json");
        subscribedTopics.add(topic);
        startPollLoop();
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
            }
        }
        if (wsHttpClient != null) {
            try {
                wsHttpClient.close();
            } catch (Exception ignored) {
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
     * Stop one lite subscription's background pull loop (lite has no server-side registration, so this
     * is purely client-side). Other lite / normal subscriptions are unaffected.
     */
    public void unsubscribeLite(String parentTopic, String liteTopic) {
        java.util.concurrent.atomic.AtomicBoolean stop = liteSubs.remove(parentTopic + "#" + liteTopic);
        if (stop != null) {
            stop.set(true);
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

    /** Shut down the client's poll executor. */
    public void shutdown() {
        polling.set(false);
        sseActive.set(false);
        if (webSocket != null) {
            try {
                webSocket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "shutdown");
            } catch (Exception ignored) {
            }
        }
        if (wsHttpClient != null) {
            try {
                wsHttpClient.close(); // Java 21: terminates the client's selector/callback threads
            } catch (Exception ignored) {
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
                    byte[] resp = getBytes(baseUrl + "/events/poll?clientId=" + enc(clientId) + "&max=100&timeoutMs=" + pollIntervalMs);
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
        return post(baseUrl + "/events/ack", json(body), "application/json") == 200;
    }

    // ---- HTTP helpers (HttpURLConnection, Java 8) ----

    private int post(String url, byte[] body, String contentType) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
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
        }
    }

    private byte[] postBytes(String url, byte[] body, String contentType) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
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
        }
    }

    private byte[] getBytes(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
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
