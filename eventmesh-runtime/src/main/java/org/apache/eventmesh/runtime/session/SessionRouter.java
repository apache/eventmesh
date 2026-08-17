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

package org.apache.eventmesh.runtime.session;

import org.apache.eventmesh.common.stream.StreamChunk;
import org.apache.eventmesh.common.stream.StreamRequest;
import org.apache.eventmesh.runtime.ingress.UniIngressService;

import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

/**
 * Runtime-side data path for streaming sessions.
 *
 * <h3>Mode 1 — streaming call (client ↔ agent, runtime-mediated)</h3>
 * {@code POST /session/stream/{sessionId}} (§5③). For each stream it: (1) resolves the session's
 * agent + channel addresses via {@link ChannelStrategy}, (2) starts (if needed) one reply-consumer
 * per client lite that demuxes {@code CHUNK}s by sessionId into the right {@link StreamSink}, (3)
 * publishes one {@code STREAM_REQ} to the agent's channel. The HTTP/SSE handler drains the returned
 * {@link StreamSink}. Replies are multiplexed on {@code client.<clientId>} — one consumer per client,
 * sessionId-demuxed.
 *
 * <h3>Mode 2 — publish/subscribe (§5④)</h3>
 * Gateway publishes chunks to a per-session lite topic ({@code session.<sessionId>}); a consumer
 * subscribes via SSE. The runtime bridges the lite topic to the SSE connection. No agent, no
 * matchmaking — the sessionId is the routing key. See {@link #publishSession} /
 * {@link #startSubscribe} / {@link #cancelSubscribe}.
 */
@Slf4j
public class SessionRouter {

    private static final int PUBLISH_ATTEMPTS = 6;
    /** Default idle TTL before the reaper expires a session (5 min). Override via the 6-arg ctor. */
    private static final long DEFAULT_SESSION_TTL_MS = 300_000L;
    /** Max chunks buffered per stream before dropping (bounds memory if an SSE client stalls). */
    private static final int MAX_BUFFERED_CHUNKS = 1024;

    private final UniIngressService ingress;
    private final SessionRegistry registry;
    /** Mode 1: the channel strategy (agent-anchored multiplexing). */
    private final ChannelStrategy strategy;
    private final long defaultTimeoutMs;
    /** Max idle time before the reaper reclaims a session; {@code <= 0} disables reaping. */
    private final long sessionTtlMs;
    /**
     * Mode 2: the parent topic for per-session pub/sub lites ({@code session.<sessionId>}).
     * {@code null} = mode 2 is disabled on this runtime.
     */
    private final String sessionStreamParent;
    private final ExecutorService io = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("em-session-router-", 1).factory());
    /** Background session reaper; null when {@code sessionTtlMs <= 0} or no registry (unit tests). */
    private final ScheduledExecutorService reaper;

    // ---- mode 1 state ----
    private final ConcurrentHashMap<String, StreamSink> sinks = new ConcurrentHashMap<>();
    /** client reply-lite key (parent#lite) → active sessionIds routed through that consumer. */
    private final ConcurrentHashMap<String, Set<String>> clientSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> clientRunning = new ConcurrentHashMap<>();

    // ---- mode 2 state ----
    /** Session lites we've lazily created (first publish). */
    private final Set<String> sessionLitesCreated = ConcurrentHashMap.newKeySet();
    /** Active subscribe sinks (sessionId → sink). */
    private final ConcurrentHashMap<String, StreamSink> subscribeSinks = new ConcurrentHashMap<>();

    /**
     * Simple constructor: mode 1 only (no mode-2 pub/sub). {@code registry} may be null for unit tests
     * (no reaper, no session lookups).
     */
    public SessionRouter(UniIngressService ingress, SessionRegistry registry,
                         ChannelStrategy strategy, long defaultTimeoutMs) {
        this(ingress, registry, strategy, defaultTimeoutMs, DEFAULT_SESSION_TTL_MS, null);
    }

    /**
     * Full constructor: mode 1 (streaming call) + optional mode 2 (pub/sub). {@code sessionStreamParent}
     * is the parent topic for mode-2 session lites; pass {@code null} to disable mode 2.
     */
    public SessionRouter(UniIngressService ingress, SessionRegistry registry,
                         ChannelStrategy strategy, long defaultTimeoutMs,
                         long sessionTtlMs, String sessionStreamParent) {
        this.ingress = ingress;
        this.registry = registry;
        this.strategy = strategy;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.sessionTtlMs = sessionTtlMs;
        this.sessionStreamParent = sessionStreamParent;
        this.reaper = startReaperIfEnabled();
    }

    /**
     * Start the session-reaper scheduler if reaping is enabled and a registry is wired. Returns the
     * scheduler (or null). Disabled in pure unit tests that pass a null registry.
     */
    private ScheduledExecutorService startReaperIfEnabled() {
        if (sessionTtlMs <= 0 || registry == null) {
            return null;
        }
        long intervalMs = Math.min(sessionTtlMs / 2, 60_000L);
        ScheduledThreadPoolExecutor sched = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "em-session-reaper");
            t.setDaemon(true);
            return t;
        });
        sched.setRemoveOnCancelPolicy(true);
        sched.scheduleAtFixedRate(this::reapStaleSessions, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        return sched;
    }

    /** One reaper tick: expire idle sessions in the registry, then tear down their data-path state. */
    private void reapStaleSessions() {
        try {
            List<String> expired = registry.expireStaleSessions(sessionTtlMs);
            for (String sessionId : expired) {
                cancel(sessionId); // idempotent: cleans sinks / consumers
                log.info("reaper expired idle session: sessionId={} (idle > {}ms)", sessionId, sessionTtlMs);
            }
        } catch (Exception e) {
            log.warn("session reaper tick failed: {}", e.toString());
        }
    }

    // ---- mode 1: streaming call ----

    /**
     * Begin a streaming call: register a sink, ensure the reply consumer, publish STREAM_REQ.
     * @return the sink the SSE handler drains until a terminal chunk or deadline.
     */
    public StreamSink startStream(String sessionId, String prompt, String model, long timeoutMs) throws Exception {
        SessionRegistry.SessionMeta meta = registry.session(sessionId);
        if (meta == null) {
            throw new NoSuchSessionException(sessionId);
        }
        AgentRecord agent = registry.agent(meta.getAgentId());
        if (agent == null) {
            throw new IllegalStateException("agent " + meta.getAgentId() + " not registered for session " + sessionId);
        }
        String clientId = meta.getClientId();
        long deadline = System.currentTimeMillis() + Math.max(1000L, timeoutMs > 0 ? timeoutMs : defaultTimeoutMs);

        // Refresh the session's idle timestamp so the reaper doesn't reclaim an active session.
        registry.touchSession(sessionId);

        ChannelStrategy.Address reply = strategy.replyAddress(sessionId, clientId);
        String clientKey = reply.encoded();

        StreamSink sink = new StreamSink(sessionId, clientKey, deadline);
        sinks.put(sessionId, sink);
        addReplySession(clientKey, sessionId);
        ensureReplyConsumer(reply, clientKey);

        String agentId = meta.getAgentId();
        ChannelStrategy.Address req = strategy.reqAddress(sessionId, agentId, agent.getParent());
        StreamRequest sreq = StreamRequest.builder()
            .sessionId(sessionId).replyTo(clientKey).prompt(prompt).model(model).build();
        Exception last = publishWithRetry(req, sreq);
        if (last != null) {
            cancel(sessionId);
            throw new RuntimeException("publish STREAM_REQ failed after " + PUBLISH_ATTEMPTS + " attempts: "
                + last.getMessage(), last);
        }
        log.info("session stream started: sessionId={} req={} reply={}", sessionId, req.encoded(), clientKey);
        return sink;
    }

    private Exception publishWithRetry(ChannelStrategy.Address req, StreamRequest sreq) {
        Exception last = null;
        for (int attempt = 0; attempt < PUBLISH_ATTEMPTS; attempt++) {
            try {
                // Internal wire (mode 1 runtime→agent over /events/lite/publish-bytes): compact
                // EventMeshFrame bytes via the pluggable WireCodec SPI, ≈10× smaller than CloudEvents-JSON.
                byte[] frame = org.apache.eventmesh.common.wire.WireCodecs.get().encode(sreq);
                ingress.publishLiteBytes(req.parent(), req.lite(), frame).get(10, TimeUnit.SECONDS);
                return null;
            } catch (Exception e) {
                last = e;
                log.warn("session req publish attempt {} failed ({}): {}", attempt + 1, req.encoded(), e.toString());
                try {
                    Thread.sleep(1500L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return ie;
                }
            }
        }
        return last;
    }

    /**
     * Add {@code sessionId} to its client reply-key's consumer work-set, creating the set if absent.
     * Retries if the set was removed (by a concurrently-exiting consumer's cleanup) between
     * {@code computeIfAbsent} and the lock — guarantees the add lands on the live set, not an orphan
     * the map no longer references.
     */
    private void addReplySession(String clientKey, String sessionId) {
        while (true) {
            Set<String> sids = clientSessions.computeIfAbsent(clientKey, k -> ConcurrentHashMap.newKeySet());
            synchronized (sids) {
                if (clientSessions.get(clientKey) == sids) {
                    sids.add(sessionId);
                    return;
                }
            }
        }
    }

    private void ensureReplyConsumer(ChannelStrategy.Address reply, String clientKey) {
        AtomicBoolean running = clientRunning.computeIfAbsent(clientKey, k -> new AtomicBoolean(false));
        if (running.compareAndSet(false, true)) {
            io.submit(() -> runReplyConsumer(reply, clientKey, running));
        }
    }

    private void runReplyConsumer(ChannelStrategy.Address reply, String clientKey, AtomicBoolean running) {
        Set<String> sids = clientSessions.get(clientKey);
        try {
            while (sids != null && !sids.isEmpty()) {
                try {
                    // Internal wire (mode 1 agent→runtime over client.<clientId>): agent publishes
                    // EventMeshFrame bytes; decode each to a StreamChunk via WireCodec, demux by sessionId.
                    for (byte[] frame : ingress.pollLiteBytes(reply.parent(), reply.lite(), 100, 500L)) {
                        StreamChunk c = org.apache.eventmesh.common.wire.WireCodecs.get().decodeChunk(frame);
                        StreamSink sink = sinks.get(c.getSessionId());
                        if (sink != null) {
                            sink.offer(c);
                            if (c.isDone()) {
                                sink.cancel();
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.warn("session reply poll transient error ({}): {}", clientKey, ex.toString());
                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        } finally {
            running.set(false);
            clientRunning.remove(clientKey, running);
            // Drop the work-set if it drained to empty and is still the live one, so distinct client
            // reply-keys don't leave permanent empty sets. Synchronized vs addReplySession so an add
            // racing with this cleanup can't land on a set we're about to remove.
            if (sids != null) {
                synchronized (sids) {
                    if (sids.isEmpty()) {
                        clientSessions.remove(clientKey, sids);
                    }
                }
            }
        }
    }

    /** Drop a stream (mid-stream client disconnect or explicit session close). Idempotent. */
    public void cancel(String sessionId) {
        StreamSink s = sinks.remove(sessionId);
        if (s != null) {
            Set<String> sids = clientSessions.get(s.clientKey);
            if (sids != null) {
                sids.remove(sessionId);
            }
        }
    }

    /**
     * Natural end of one turn (the SSE handler delivered a terminal chunk to the client). Drops this
     * turn's sink so the next turn registers a fresh one, but keeps the session's reply consumer alive
     * for multi-turn reuse.
     */
    public void endTurn(String sessionId) {
        StreamSink s = sinks.remove(sessionId);
        if (s == null) {
            return;
        }
        Set<String> sids = clientSessions.get(s.clientKey);
        if (sids != null) {
            sids.remove(sessionId);
        }
    }

    // ---- mode 2: publish/subscribe ----

    /**
     * @return true iff mode 2 is enabled on this runtime.
     */
    public boolean isMode2Enabled() {
        return sessionStreamParent != null;
    }

    /** Total active streams (mode-1 in-flight turns + mode-2 subscribers) for load metering. */
    public int activeStreamCount() {
        return sinks.size() + subscribeSinks.size();
    }

    /**
     * Publish one chunk to the session's lite topic ({@code session.<sessionId>} under
     * {@code sessionStreamParent}). The lite is created lazily on the first publish. Throws if
     * mode 2 is not enabled.
     */
    public void publishSession(String sessionId, StreamChunk chunk) {
        assertMode2Enabled();
        String lite = "session." + sessionId;
        // Lazily create the lite topic on the first publish.
        if (sessionLitesCreated.add(sessionId)) {
            try {
                ingress.createLiteTopic(sessionStreamParent, lite, 1);
            } catch (Exception e) {
                sessionLitesCreated.remove(sessionId);
                throw new RuntimeException("create session lite topic failed: " + sessionId, e);
            }
        }
        try {
            byte[] frame = org.apache.eventmesh.common.wire.WireCodecs.get().encode(chunk);
            ingress.publishLiteBytes(sessionStreamParent, lite, frame).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("publish session chunk failed: " + sessionId, e);
        }
    }

    /**
     * Start subscribing to a session stream (mode 2). Returns a {@link StreamSink} the SSE handler
     * drains until the terminal chunk or disconnect. One subscriber per session at a time.
     */
    public StreamSink startSubscribe(String sessionId) {
        assertMode2Enabled();
        String lite = "session." + sessionId;
        StreamSink sink = new StreamSink(sessionId, sessionStreamParent + "#" + lite,
            System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24)); // long deadline
        if (subscribeSinks.putIfAbsent(sessionId, sink) != null) {
            throw new IllegalStateException("a subscriber is already active for session " + sessionId);
        }
        io.submit(() -> runSubscribeConsumer(sessionId, lite, sink));
        log.info("subscribe started: sessionId={}", sessionId);
        return sink;
    }

    /** Cancel the active subscribe for a session (disconnect or close). Idempotent. */
    public void cancelSubscribe(String sessionId) {
        StreamSink sink = subscribeSinks.remove(sessionId);
        if (sink != null) {
            sink.cancel();
        }
    }

    private void runSubscribeConsumer(String sessionId, String lite, StreamSink sink) {
        try {
            while (sink.isActive() && subscribeSinks.get(sessionId) == sink) {
                try {
                    for (byte[] frame : ingress.pollLiteBytes(sessionStreamParent, lite, 100, 500L)) {
                        StreamChunk c = org.apache.eventmesh.common.wire.WireCodecs.get().decodeChunk(frame);
                        sink.offer(c);
                        if (c.isDone()) {
                            sink.cancel();
                            return;
                        }
                    }
                } catch (Exception ex) {
                    log.warn("subscribe poll error (sessionId={}): {}", sessionId, ex.toString());
                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        } finally {
            subscribeSinks.remove(sessionId, sink);
        }
    }

    private void assertMode2Enabled() {
        if (sessionStreamParent == null) {
            throw new IllegalStateException("mode 2 (publish/subscribe) is not enabled on this runtime");
        }
    }

    // ---- lifecycle ----

    public void shutdown() {
        if (reaper != null) {
            reaper.shutdownNow();
        }
        io.shutdownNow();
    }

    /** Per-stream state the SSE handler drains. */
    public static class StreamSink {

        public final String sessionId;
        public final String clientKey;
        private final long deadlineMs;
        private final BlockingQueue<StreamChunk> queue = new LinkedBlockingQueue<>(MAX_BUFFERED_CHUNKS);
        private final AtomicBoolean active = new AtomicBoolean(true);

        StreamSink(String sessionId, String clientKey, long deadlineMs) {
            this.sessionId = sessionId;
            this.clientKey = clientKey;
            this.deadlineMs = deadlineMs;
        }

        public boolean isActive() {
            return active.get() && System.currentTimeMillis() <= deadlineMs;
        }

        public long remainingMs() {
            return Math.max(0L, deadlineMs - System.currentTimeMillis());
        }

        public void cancel() {
            active.set(false);
        }

        void offer(StreamChunk chunk) {
            if (!queue.offer(chunk)) {
                // A slow SSE client (or a stall) filled the per-stream backlog. Drop the chunk rather
                // than grow unbounded — the client's turn times out and retries. Bounds memory.
                log.warn("stream backlog full, dropping chunk (slow client? sessionId={} cap={})",
                    sessionId, MAX_BUFFERED_CHUNKS);
            }
        }

        public StreamChunk poll(long timeoutMs) throws InterruptedException {
            return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        }

        /** Non-blocking drain of the next queued chunk, or {@code null} if the queue is empty. */
        public StreamChunk pollNoWait() {
            return queue.poll();
        }
    }

    public static class NoSuchSessionException extends RuntimeException {
        public NoSuchSessionException(String sessionId) {
            super("unknown sessionId: " + sessionId);
        }
    }
}