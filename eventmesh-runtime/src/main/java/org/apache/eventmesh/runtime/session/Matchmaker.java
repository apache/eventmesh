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

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

import lombok.extern.slf4j.Slf4j;

/**
 * Implements {@code POST /session/open} matchmaking (§5②/§10). For a clientId: reuse the existing
 * binding if the agent is still READY/fresh/healthy and within capacity; otherwise pick the
 * least-loaded healthy agent matching the requested capability (model) — ties broken at random so
 * distinct clients spread evenly across equally-loaded agents. Then mint a sessionId
 * ({@code <agentId>:<uuid>}) and record the session + binding.
 *
 * <p>Throws {@link NoAgentAvailableException} when no eligible agent exists (the HTTP layer maps that
 * to 429). {@code now} is injected for deterministic tests.</p>
 */
@Slf4j
public class Matchmaker {

    private final SessionRegistry registry;
    private final BrokerGroupHealth health;
    private final long bindingTtlMs;
    private final LongSupplier clock;

    public Matchmaker(SessionRegistry registry, BrokerGroupHealth health, long bindingTtlMs) {
        this(registry, health, bindingTtlMs, System::currentTimeMillis);
    }

    public Matchmaker(SessionRegistry registry, BrokerGroupHealth health, long bindingTtlMs, LongSupplier clock) {
        this.registry = registry;
        this.health = health;
        this.bindingTtlMs = bindingTtlMs;
        this.clock = clock;
    }

    /** Resolve (or establish) the client→agent binding and mint a new sessionId for this conversation. */
    public OpenResult open(String clientId, String model) {
        AgentRecord agent = resolveAgent(clientId, model);
        String sessionId = agent.getAgentId() + ":" + UUID.randomUUID().toString().replace("-", "");
        registry.putSession(sessionId, clientId, agent.getAgentId());
        log.info("session opened: clientId={} agentId={} sessionId={}",
            clientId, agent.getAgentId(), sessionId);
        return new OpenResult(sessionId, agent.getAgentId(), agent.getParent());
    }

    /** Drop a session (best-effort; the binding is kept for affinity). */
    public boolean close(String sessionId) {
        boolean removed = registry.removeSession(sessionId);
        if (removed) {
            log.info("session closed: sessionId={}", sessionId);
        }
        return removed;
    }

    private AgentRecord resolveAgent(String clientId, String model) {
        // 1. reuse the sticky binding if the agent is still good
        SessionRegistry.AgentBinding binding = registry.binding(clientId);
        if (binding != null) {
            AgentRecord bound = registry.agent(binding.getAgentId());
            boolean stale = (clock.getAsLong() - binding.getBoundAt()) > bindingTtlMs;
            if (bound == null || stale) {
                // agent gone, or affinity expired — drop the dead binding so the table is bounded
                // (the session reaper cleans sessions; this is the matching control-plane growth).
                registry.unbind(clientId);
            } else if (isRoutable(bound) && health.healthy(bound.getParent()) && matches(bound, model)) {
                return bound;
            }
            // else: agent temporarily unavailable (capacity/broker/model) — keep binding for affinity
        }
        // 2. otherwise matchmake a fresh agent
        AgentRecord picked = pick(model);
        if (picked == null) {
            throw new NoAgentAvailableException(
                "no READY agent" + (model == null ? "" : " for model=" + model) + " available");
        }
        registry.bind(clientId, picked.getAgentId());
        return picked;
    }

    /**
     * Pick the least-loaded healthy agent matching the requested capability. When several agents tie
     * on load (the common case — most agents report load=0 between requests), pick randomly from that
     * tier so different clientIds spread evenly across agents instead of all funneling to the
     * alphabetically-first one.
     */
    private AgentRecord pick(String model) {
        List<AgentRecord> candidates = registry.readyAgents().stream()
            .filter(a -> health.healthy(a.getParent()))
            .filter(a -> matches(a, model))
            .sorted(Comparator.comparingInt(AgentRecord::getLoad))
            .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        int minLoad = candidates.get(0).getLoad();
        List<AgentRecord> leastLoaded = candidates.stream()
            .filter(a -> a.getLoad() == minLoad)
            .toList();
        return leastLoaded.get(ThreadLocalRandom.current().nextInt(leastLoaded.size()));
    }

    private static boolean isRoutable(AgentRecord a) {
        return AgentStatus.READY.name().equals(a.getStatus()) && a.getLoad() < a.getCapacity();
    }

    private static boolean matches(AgentRecord a, String model) {
        if (model == null) {
            return true;
        }
        List<String> caps = a.getCapabilities();
        return caps == null || caps.isEmpty() || caps.contains(model);
    }

    /** Result of {@code /session/open}: the minted sessionId + the chosen agentId + its parent shard. */
    public record OpenResult(String sessionId, String agentId, String parent) {
    }

    /** No eligible agent (HTTP layer → 429). */
    public static class NoAgentAvailableException extends RuntimeException {
        public NoAgentAvailableException(String message) {
            super(message);
        }
    }
}
