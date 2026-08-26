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

import org.apache.eventmesh.runtime.cluster.MetaStore;
import org.apache.eventmesh.runtime.state.SessionStore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Control-plane store for the streaming-session design (§5/§11.1). Sits on the existing {@link
 * MetaStore} abstraction (prod {@code NacosMetaStore} / test {@code InMemoryMetaStore}) and holds:
 *
 * <ul>
 *   <li><b>agent registry</b> — {@code /em/agents/<agentId>} = {@link AgentRecord} (capabilities /
 *       capacity / parent / load / status / heartbeat).</li>
 *   <li><b>client bindings</b> — {@code /em/bindings/<clientId>} = {@link AgentBinding} (client
 *       affinity, §7.1).</li>
 *   <li><b>session metadata</b> — {@code /em/sessions/<sessionId>} = {@link SessionMeta}.</li>
 * </ul>
 *
 * <p>It is consulted only at lifecycle points (register / open / heartbeat / close) — never on the
 * per-message data path. {@link MetaStore} has no native TTL, so agent liveness is enforced by the
 * {@code heartbeatTtlMs} window: {@link #readyAgents()} drops agents whose {@code hb} is stale.</p>
 */
@Slf4j
public class SessionRegistry implements SessionStore {

    public static final String AGENT_PREFIX = "/em/agents/";
    public static final String BINDING_PREFIX = "/em/bindings/";
    public static final String SESSION_PREFIX = "/em/sessions/";

    private final MetaStore meta;
    private final long heartbeatTtlMs;
    private final LongSupplier clock;
    private final ObjectMapper json = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public SessionRegistry(MetaStore meta, long heartbeatTtlMs) {
        this(meta, heartbeatTtlMs, System::currentTimeMillis);
    }

    /** Testable variant with an injected clock (epoch millis). */
    public SessionRegistry(MetaStore meta, long heartbeatTtlMs, LongSupplier clock) {
        this.meta = meta;
        this.heartbeatTtlMs = heartbeatTtlMs;
        this.clock = clock;
    }

    /**
     * Per-JVM caches for read-after-write consistency (NacosMetaStore's publishConfig is async — an
     * immediate get after put may return null). Agents registered/sessions opened on this instance
     * are cached locally so markReady/session() see them instantly. Cross-instance reads (matchmaking
     * on a peer) fall through to MetaStore, which catches up within ~1s.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, AgentRecord> agentCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, SessionMeta> sessionCache = new java.util.concurrent.ConcurrentHashMap<>();

    // -------------------- agents --------------------

    /** Register a new agent in {@link AgentStatus#PENDING} (flipped to READY after subscribe, §5.2). */
    public void registerAgent(String agentId, String parent, List<String> capabilities, int capacity) {
        AgentRecord r = AgentRecord.builder()
            .agentId(agentId).parent(parent).capabilities(capabilities).capacity(capacity)
            .load(0).status(AgentStatus.PENDING.name()).hb(clock.getAsLong()).build();
        agentCache.put(agentId, r);
        meta.put(AGENT_PREFIX + agentId, write(r, AgentRecord.class));
    }

    /** Flip a PENDING agent to READY (after it has subscribed to its channel). {@code false} if unknown. */
    public boolean markAgentReady(String agentId) {
        AgentRecord r = agent(agentId);
        if (r == null) {
            return false;
        }
        // P1-2 fix: build a new immutable copy instead of mutating the shared bean in place.
        AgentRecord updated = AgentRecord.builder()
            .agentId(r.getAgentId()).parent(r.getParent()).capabilities(r.getCapabilities())
            .capacity(r.getCapacity()).load(r.getLoad())
            .status(AgentStatus.READY.name()).hb(clock.getAsLong()).build();
        agentCache.put(agentId, updated);
        meta.put(AGENT_PREFIX + agentId, write(updated, AgentRecord.class));
        return true;
    }

    /** Refresh heartbeat + reported load. {@code false} if the agent isn't registered. */
    public boolean heartbeat(String agentId, int activeSessions) {
        AgentRecord r = agent(agentId);
        if (r == null) {
            return false;
        }
        // P1-2 fix: build a new immutable copy instead of mutating the shared bean in place.
        AgentRecord updated = AgentRecord.builder()
            .agentId(r.getAgentId()).parent(r.getParent()).capabilities(r.getCapabilities())
            .capacity(r.getCapacity()).load(activeSessions)
            .status(r.getStatus()).hb(clock.getAsLong()).build();
        agentCache.put(agentId, updated);
        meta.put(AGENT_PREFIX + agentId, write(updated, AgentRecord.class));
        return true;
    }

    public void unregisterAgent(String agentId) {
        agentCache.remove(agentId);
        meta.delete(AGENT_PREFIX + agentId);
        removeBindingsForAgent(agentId);
    }

    /**
     * Delete every binding pointing at {@code agentId}. Called from {@link #unregister} so a dying
     * agent doesn't leave orphaned client bindings (which would otherwise survive until each client
     * reconnects and the lazy cleanup in {@code Matchmaker.resolveAgent} drops them).
     */
    public void removeBindingsForAgent(String agentId) {
        for (java.util.Map.Entry<String, String> e : meta.getWithPrefix(BINDING_PREFIX).entrySet()) {
            AgentBinding b = read(e.getValue(), AgentBinding.class);
            if (b != null && agentId.equals(b.getAgentId())) {
                meta.delete(e.getKey());
            }
        }
    }

    public AgentRecord agent(String agentId) {
        AgentRecord cached = agentCache.get(agentId);
        if (cached != null) {
            return cached;
        }
        String v = meta.get(AGENT_PREFIX + agentId);
        return v == null ? null : read(v, AgentRecord.class);
    }

    /**
     * All agents eligible for matchmaking: {@link AgentStatus#READY}, heartbeat within the TTL window,
     * and not at capacity. Broker-group health filtering is the matchmaker's job (it pairs this list
     * with a {@link BrokerGroupHealth}).
     */
    public List<AgentRecord> readyAgents() {
        long now = clock.getAsLong();
        List<AgentRecord> out = new ArrayList<>();
        for (AgentRecord r : listAgents()) {
            boolean fresh = (now - r.getHb()) <= heartbeatTtlMs;
            if (r.getStatus() != null && AgentStatus.READY.name().equals(r.getStatus()) && fresh && r.getLoad() < r.getCapacity()) {
                out.add(r);
            }
        }
        return out;
    }

    /** All registered agents regardless of status/freshness (local cache + MetaStore merged). */
    public List<AgentRecord> listAgents() {
        java.util.Map<String, AgentRecord> byId = new java.util.HashMap<>();
        byId.putAll(agentCache);
        for (String v : meta.getWithPrefix(AGENT_PREFIX).values()) {
            AgentRecord r = read(v, AgentRecord.class);
            if (r != null) {
                byId.put(r.getAgentId(), r);
            }
        }
        return new ArrayList<>(byId.values());
    }

    // -------------------- client bindings (affinity) --------------------

    public void bind(String clientId, String agentId) {
        meta.put(BINDING_PREFIX + clientId, write(AgentBinding.builder().agentId(agentId).boundAt(clock.getAsLong()).build(), AgentBinding.class));
    }

    public AgentBinding binding(String clientId) {
        String v = meta.get(BINDING_PREFIX + clientId);
        return v == null ? null : read(v, AgentBinding.class);
    }

    public boolean unbind(String clientId) {
        return meta.delete(BINDING_PREFIX + clientId);
    }

    // -------------------- session metadata --------------------

    /** Record a mode-1 streaming-call session (opened via {@code POST /session/open}). */
    public void putSession(String sessionId, String clientId, String agentId) {
        SessionMeta m = SessionMeta.builder().clientId(clientId).agentId(agentId)
            .lastActiveAt(clock.getAsLong()).build();
        sessionCache.put(sessionId, m);
        meta.put(SESSION_PREFIX + sessionId, write(m, SessionMeta.class));
    }

    public SessionMeta session(String sessionId) {
        SessionMeta cached = sessionCache.get(sessionId);
        if (cached != null) {
            return cached;
        }
        String v = meta.get(SESSION_PREFIX + sessionId);
        return v == null ? null : read(v, SessionMeta.class);
    }

    /**
     * Refresh this session's {@code lastActiveAt} (called on each {@code STREAM_REQ}). Best-effort:
     * no-op if the session is gone (already expired/closed).
     */
    public void touchSession(String sessionId) {
        SessionMeta m = session(sessionId);
        if (m == null) {
            return;
        }
        // P1-2 fix: build a new immutable copy instead of mutating the shared bean in place.
        SessionMeta updated = SessionMeta.builder()
            .clientId(m.getClientId()).agentId(m.getAgentId())
            .lastActiveAt(clock.getAsLong()).build();
        sessionCache.put(sessionId, updated);
        meta.put(SESSION_PREFIX + sessionId, write(updated, SessionMeta.class));
    }

    /**
     * Remove every session idle for longer than {@code sessionTtlMs} (last activity older than the
     * deadline). Returns the expired sessionIds so the caller ({@code SessionRouter}) can tear down
     * their data-path state (sinks / consumers / mode-2 channels). Callers that never want reaping
     * pass {@code sessionTtlMs <= 0}.
     */
    public List<String> expireStaleSessions(long sessionTtlMs) {
        if (sessionTtlMs <= 0) {
            return List.of();
        }
        long deadline = clock.getAsLong() - sessionTtlMs;
        List<String> expired = new ArrayList<>();
        for (String key : meta.getWithPrefix(SESSION_PREFIX).keySet()) {
            String sessionId = key.substring(SESSION_PREFIX.length());
            SessionMeta m = session(sessionId);
            if (m != null && m.getLastActiveAt() < deadline) {
                expired.add(sessionId);
            }
        }
        for (String sessionId : expired) {
            removeSession(sessionId);
        }
        return expired;
    }

    public boolean removeSession(String sessionId) {
        sessionCache.remove(sessionId);
        return meta.delete(SESSION_PREFIX + sessionId);
    }

    // -------------------- helpers --------------------

    private <T> String write(T value, Class<T> type) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize " + type.getSimpleName() + ": " + e, e);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (Exception e) {
            log.warn("failed to parse {} (stale/corrupt?): {}", type.getSimpleName(), e.toString());
            return null;
        }
    }

    /** Client→agent affinity binding. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentBinding {
        private String agentId;
        /** Epoch millis the binding was (re)written; the matchmaker treats bindings past the TTL as stale. */
        private long boundAt;
    }

    /** Per-session metadata. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionMeta {
        private String clientId;
        private String agentId;
        /** Epoch millis the session was last active (OPEN or STREAM_REQ). Drives the session reaper. */
        private long lastActiveAt;
    }
}
