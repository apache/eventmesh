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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * Fronts the agent↔runtime control endpoints ({@code POST /agent/register|ready|heartbeat|unregister},
 * §5.2). On register it (1) selects/assigns an agent-parent shard, (2) ensures that parent (and the
 * client reply parent) is lite-capable, (3) writes a {@link AgentStatus#PENDING} record via {@link
 * SessionRegistry}. The agent flips itself to READY only after subscribing to its channel
 * (ready-before-route, §5.2); heartbeats refresh liveness + load.
 *
 * <p>Phase 2 assigns a single configured {@code agent-parent} (shard selection by load/health lands
 * in Phase 6). The {@link ParentEnsurer} indirection keeps this unit-testable without a real broker:
 * production wires it to {@code ingress.createLiteTopic}, tests pass a no-op.</p>
 */
@Slf4j
public class AgentRegistrar {

    private final SessionRegistry registry;
    private final ParentEnsurer ensurer;
    private final List<String> agentParents;
    private final String clientParent;
    private final BrokerGroupHealth health;

    /** Single-shard convenience (health = always healthy). */
    public AgentRegistrar(SessionRegistry registry, ParentEnsurer ensurer,
                          String defaultAgentParent, String clientParent) {
        this(registry, ensurer, java.util.List.of(defaultAgentParent), clientParent, BrokerGroupHealth.alwaysHealthy());
    }

    /**
     * Multi-shard: on each register, picks the least-loaded healthy shard (agent count per parent) so
     * agents spread evenly across broker groups; falls back to the first shard if all are unhealthy.
     */
    public AgentRegistrar(SessionRegistry registry, ParentEnsurer ensurer,
                          List<String> agentParents, String clientParent, BrokerGroupHealth health) {
        this.registry = registry;
        this.ensurer = ensurer;
        this.agentParents = agentParents;
        this.clientParent = clientParent;
        this.health = health;
    }

    /** Register a new agent (PENDING). {@return} the assigned parent (agent replies via clientParent). */
    public RegisterResult register(String agentId, List<String> capabilities, int capacity) {
        String parent = pickShard();
        ensure(parent);
        ensure(clientParent);
        registry.registerAgent(agentId, parent, capabilities, capacity);
        log.info("agent registered: agentId={} parent={} capabilities={} capacity={}", agentId, parent, capabilities, capacity);
        return new RegisterResult(parent, clientParent);
    }

    /** Least-loaded healthy shard (agent count per parent); first shard if all unhealthy. */
    private String pickShard() {
        Map<String, Long> load = new HashMap<>();
        for (AgentRecord a : registry.listAgents()) {
            if (a.getParent() != null) {
                load.merge(a.getParent(), 1L, Long::sum);
            }
        }
        return agentParents.stream()
            .filter(health::healthy)
            .min(Comparator.comparingLong((String p) -> load.getOrDefault(p, 0L))
                .thenComparing(Comparator.naturalOrder()))
            .orElse(agentParents.get(0));
    }

    /** Flip to READY (after subscribe). {@code false} if the agent isn't registered. */
    public boolean ready(String agentId) {
        boolean ok = registry.markAgentReady(agentId);
        log.info("agent ready: agentId={} ok={}", agentId, ok);
        return ok;
    }

    /** Refresh heartbeat + reported load. {@code false} if the agent isn't registered. */
    public boolean heartbeat(String agentId, int activeSessions) {
        return registry.heartbeat(agentId, activeSessions);
    }

    public void unregister(String agentId) {
        registry.unregisterAgent(agentId);
        log.info("agent unregistered: agentId={}", agentId);
    }

    private void ensure(String parent) {
        try {
            ensurer.ensure(parent);
        } catch (Exception e) {
            // best-effort: a parent that's already lite-capable is fine; a missing one surfaces later
            // as a subscribe/publish failure (Phase 6 makes this robust with retries + targeted create).
            log.warn("ensure parent {} failed (best-effort): {}", parent, e.toString());
        }
    }

    /** Agent-parent + client-reply-parent the agent should use. */
    public record RegisterResult(String parent, String clientParent) {
    }

    /** Ensures a parent topic is lite-capable (production: {@code ingress.createLiteTopic(parent, lite)}). */
    @FunctionalInterface
    public interface ParentEnsurer {
        void ensure(String parent) throws Exception;
    }
}
