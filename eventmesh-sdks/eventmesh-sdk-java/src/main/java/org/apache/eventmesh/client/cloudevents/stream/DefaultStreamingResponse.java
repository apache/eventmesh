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

package org.apache.eventmesh.client.cloudevents.stream;

import org.apache.eventmesh.common.stream.StreamChunk;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Default implementation of {@link StreamingResponse}: reads SSE frames from an already-opened
 * connection on one virtual thread, deserializes {@link StreamChunk} JSON into a bounded
 * {@link LinkedBlockingQueue}, and drains via {@link #forEach(Consumer)}.
 *
 * <h3>Backpressure</h3>
 * The queue is bounded (default 100k). When full, {@code put()} blocks the SSE-read thread,
 * back-pressuring the read, which slows the runtime's SSE write.
 *
 * <h3>Threading</h3>
 * The SSE-read VT starts in the constructor. {@code forEach} spawns its own VT to drain. Only one
 * posture may be active per instance; calling {@code forEach} twice throws.
 *
 * <h3>Cancel vs complete</h3>
 * The SSE reader offers a sentinel ({@link #EOS}) on ANY terminal condition (terminal chunk,
 * read error, or interrupt from {@link #close()}). The drain loop checks a {@code cancelled}
 * flag to suppress terminal callbacks for deliberate cancels.
 */
@Slf4j
public class DefaultStreamingResponse implements StreamingResponse {

    /** Sentinel offered to the queue when the stream ends (data, error, or cancel). */
    private static final StreamChunk EOS = StreamChunk.builder().sessionId("__eos__").seq(Integer.MIN_VALUE).build();

    /** Default queue capacity (same as reference impl: AgentScope RocketMQStreamClient). */
    public static final int DEFAULT_QUEUE_CAPACITY = 100_000;

    private final String sessionId;
    private final String agentId;
    private final LinkedBlockingQueue<StreamChunk> queue;
    private final ObjectMapper mapper;
    private final BufferedReader reader;
    private final HttpURLConnection conn;
    private final Runnable onClose;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean consumerClaimed = new AtomicBoolean(false);
    private final AtomicBoolean eosOffered = new AtomicBoolean(false);
    private volatile Throwable terminalError;
    private volatile Thread readerThread;

    /**
     * Package-private: constructed by the SDK internals after opening the SSE connection. The
     * constructor immediately starts the SSE-read virtual thread.
     *
     * @param sessionId      the runtime-assigned session id
     * @param agentId        the agent that handled this call
     * @param reader         buffered reader over the SSE response stream (already past headers)
     * @param conn           the connection (for disconnect on close)
     * @param mapper         Jackson ObjectMapper for deserializing StreamChunk JSON
     * @param queueCapacity  bounded queue capacity (backpressure threshold)
     * @param onClose        hook run after SSE read stops
     */
    DefaultStreamingResponse(String sessionId, String agentId, BufferedReader reader,
                             HttpURLConnection conn, ObjectMapper mapper,
                             int queueCapacity, Runnable onClose) {
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.mapper = mapper;
        this.reader = reader;
        this.conn = conn;
        this.onClose = onClose;
        this.queue = new LinkedBlockingQueue<>(queueCapacity);
        startReader();
    }

    /**
     * Open a {@code POST /session/stream/{sessionId}} SSE connection (mode 1, streaming call),
     * write the request body, and return a live {@link DefaultStreamingResponse} (its SSE-read
     * virtual thread starts here).
     *
     * @param baseUrl       runtime base URL (e.g. {@code http://localhost:8080})
     * @param mapper        Jackson ObjectMapper for deserializing StreamChunk JSON
     * @param sessionId     the session id
     * @param agentId       the agent id (for {@link #agentId()})
     * @param bodyJson      the JSON request body (e.g. {@code {"prompt":"..."}})
     * @param queueCapacity bounded queue capacity (≤ 0 → {@link #DEFAULT_QUEUE_CAPACITY})
     * @param onClose       hook run after SSE read stops
     * @return a live streaming response
     */
    public static DefaultStreamingResponse start(String baseUrl, ObjectMapper mapper,
                                                 String sessionId, String agentId, String bodyJson,
                                                 int queueCapacity, Runnable onClose) {
        return start(baseUrl, mapper, "POST", "/session/stream/" + encode(sessionId), sessionId, agentId,
            bodyJson, queueCapacity, onClose);
    }

    /**
     * Shared connection-open. Method + path select mode 1 ({@code POST /session/stream}) vs mode 2
     * ({@code GET /session/subscribe}); {@code bodyJson} is written only when non-null (mode 1 only).
     */
    private static DefaultStreamingResponse start(String baseUrl, ObjectMapper mapper, String method,
                                                  String path, String sessionId, String agentId,
                                                  String bodyJson, int queueCapacity, Runnable onClose) {
        int capacity = queueCapacity <= 0 ? DEFAULT_QUEUE_CAPACITY : queueCapacity;
        try {
            HttpURLConnection conn = (HttpURLConnection)
                new java.net.URL(baseUrl + path).openConnection();
            conn.setRequestMethod(method);
            if (bodyJson != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(bodyJson.getBytes(StandardCharsets.UTF_8));
                }
            }
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(0); // keep the SSE stream open
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            return new DefaultStreamingResponse(sessionId, agentId, reader, conn, mapper, capacity, onClose);
        } catch (java.io.IOException e) {
            throw new RuntimeException("open stream session '" + sessionId + "' failed: " + e, e);
        }
    }

    /**
     * Open a {@code GET /session/subscribe/{sessionId}} SSE connection (mode 2, pub/sub) and return a
     * live {@link DefaultStreamingResponse}. No request body (mode 2 has no prompt — the stream is
     * whatever a publisher is writing onto the session's lite topic).
     *
     * @param baseUrl       runtime base URL
     * @param mapper        Jackson ObjectMapper for deserializing StreamChunk JSON
     * @param sessionId     the session id to subscribe to
     * @param queueCapacity bounded queue capacity (≤ 0 → {@link #DEFAULT_QUEUE_CAPACITY})
     * @param onClose       hook run after SSE read stops
     * @return a live streaming response
     */
    public static DefaultStreamingResponse startSubscribe(String baseUrl, ObjectMapper mapper,
                                                          String sessionId, int queueCapacity,
                                                          Runnable onClose) {
        return start(baseUrl, mapper, "GET", "/session/subscribe/" + encode(sessionId), sessionId, sessionId,
            null, queueCapacity, onClose);
    }

    private static String encode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ---- SSE reader virtual thread ----

    private void startReader() {
        Thread vt = Thread.ofVirtual().name("em-sse-" + sessionId).start(() -> {
            readerThread = Thread.currentThread();
            try {
                String line = null;
                while (!cancelled.get() && (line = reader.readLine()) != null) {
                    if (!line.startsWith("data: ")) {
                        continue;
                    }
                    StreamChunk chunk = mapper.readValue(line.substring(6), StreamChunk.class);
                    if (chunk.isDone()) {
                        if (chunk.getError() != null) {
                            terminalError = new StreamException(sessionId,
                                new RuntimeException(chunk.getError()));
                        }
                        break;
                    }
                    queue.put(chunk); // blocking backpressure
                }
                if (line == null && !cancelled.get()) {
                    terminalError = new IOException("SSE stream closed by server");
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                // cancelled or closed — terminalError stays null
            } catch (IOException e) {
                if (!cancelled.get()) {
                    terminalError = e;
                }
            } catch (RuntimeException e) {
                if (!cancelled.get()) {
                    terminalError = e;
                }
            } finally {
                offerEos();
                try {
                    conn.disconnect();
                } catch (RuntimeException ignored) {
                    // best-effort disconnect
                }
                try {
                    onClose.run();
                } catch (RuntimeException e) {
                    log.warn("stream onClose hook failed: {}", e.toString());
                }
            }
        });
    }

    private void offerEos() {
        if (eosOffered.compareAndSet(false, true)) {
            queue.offer(EOS); // non-blocking: the terminal MUST reach the consumer
        }
    }

    // ---- Accessors ----

    @Override
    public String sessionId() {
        return sessionId;
    }

    @Override
    public String agentId() {
        return agentId;
    }

    // ---- forEach ----

    @Override
    public CompletableFuture<Void> forEach(Consumer<StreamChunk> onChunk) {
        claimConsumer();
        CompletableFuture<Void> future = new CompletableFuture<>();
        Thread.ofVirtual().name("em-drain-" + sessionId).start(() -> drainForEach(future, onChunk));
        return future;
    }

    private void drainForEach(CompletableFuture<Void> future, Consumer<StreamChunk> onChunk) {
        try {
            while (true) {
                StreamChunk c = queue.take();
                if (c == EOS) {
                    break;
                }
                onChunk.accept(c);
            }
            finish(future);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            future.completeExceptionally(ie);
        } catch (RuntimeException t) {
            future.completeExceptionally(t);
        }
    }

    /** Complete the future without firing terminal callbacks (used by forEach and cancel paths). */
    private void finish(CompletableFuture<Void> future) {
        if (cancelled.get()) {
            future.complete(null);
            return;
        }
        if (terminalError != null) {
            future.completeExceptionally(terminalError);
        } else {
            future.complete(null);
        }
    }

    // ---- Lifecycle ----

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return; // already closed
        }
        cancelled.set(true);
        if (readerThread != null) {
            readerThread.interrupt();
        }
        // Force-close the socket so any blocked read() wakes up immediately.
        try {
            conn.disconnect();
        } catch (RuntimeException ignored) {
            // best-effort
        }
        // Wake any blocked queue consumer (offerEos is idempotent).
        offerEos();
        try {
            onClose.run();
        } catch (RuntimeException e) {
            log.warn("stream onClose hook failed: {}", e.toString());
        }
    }

    // ---- Internal ----

    /** Guard: only one consumption posture may be active per instance. */
    private void claimConsumer() {
        if (!consumerClaimed.compareAndSet(false, true)) {
            throw new IllegalStateException(
                "StreamingResponse already consumed");
        }
    }
}