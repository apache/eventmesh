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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link Matchmaker}: matchmaking, sticky reuse, load/capability selection, binding TTL. */
class MatchmakerTest {

    private AtomicLong now;
    private SessionRegistry registry;
    private Matchmaker matchmaker;

    @BeforeEach
    void setUp() {
        now = new AtomicLong(1_000_000L);
        registry = new SessionRegistry(new InMemoryMetaStore(), 30_000L, now::get);
        matchmaker = new Matchmaker(registry, BrokerGroupHealth.alwaysHealthy(), 1_800_000L, now::get);
    }

    private void reg(String agentId, List<String> caps, int capacity) {
        registry.register(agentId, "agent-parent-0", caps, capacity);
        registry.markReady(agentId);
    }

    @Test
    void noAgentThrows() {
        assertThatThrownBy(() -> matchmaker.open("c1", null))
            .isInstanceOf(Matchmaker.NoAgentAvailableException.class);
    }

    @Test
    void openMintsSessionIdAndBinds() {
        reg("a1", List.of("m1"), 10);

        Matchmaker.OpenResult r = matchmaker.open("c1", null);

        assertThat(r.agentId()).isEqualTo("a1");
        assertThat(r.sessionId()).startsWith("a1:");
        assertThat(r.parent()).isEqualTo("agent-parent-0");
        assertThat(registry.session(r.sessionId()).getAgentId()).isEqualTo("a1");
        assertThat(registry.binding("c1").getAgentId()).isEqualTo("a1");
    }

    @Test
    void stickyBindingReusesAgentDifferentSessionId() {
        reg("a1", List.of("m1"), 10);

        Matchmaker.OpenResult r1 = matchmaker.open("c1", null);
        Matchmaker.OpenResult r2 = matchmaker.open("c1", null);

        assertThat(r2.agentId()).isEqualTo("a1"); // same agent (client affinity)
        assertThat(r2.sessionId()).isNotEqualTo(r1.sessionId()); // fresh session each open
    }

    @Test
    void picksLowestLoad() {
        reg("a1", List.of("m1"), 10);
        reg("a2", List.of("m1"), 10);
        registry.heartbeat("a1", 5);
        registry.heartbeat("a2", 1);

        // unbound client → matchmake → a2 has lower load
        assertThat(matchmaker.open("cX", null).agentId()).isEqualTo("a2");
    }

    @Test
    void capabilityFilterSelectsMatchingModel() {
        reg("a1", List.of("coding"), 10);
        reg("a2", List.of("chat"), 10);

        assertThat(matchmaker.open("cX", "chat").agentId()).isEqualTo("a2");
        assertThat(matchmaker.open("cY", "coding").agentId()).isEqualTo("a1");
    }

    @Test
    void bindingExpiryRematches() {
        reg("a1", List.of("m1"), 10);
        matchmaker.open("c1", null); // binds c1→a1 (boundAt = 1_000_000)
        reg("a2", List.of("m1"), 10);

        now.addAndGet(1_800_001L); // binding past TTL
        registry.heartbeat("a1", 9); // a1 fresh but loaded
        registry.heartbeat("a2", 0); // a2 fresh, idle

        // expired binding → re-matchmake → lowest-load healthy agent (a2)
        assertThat(matchmaker.open("c1", null).agentId()).isEqualTo("a2");
    }

    @Test
    void expiredBindingIsUnbound() {
        reg("a1", List.of("m1"), 10);
        matchmaker.open("c1", null);
        assertThat(registry.binding("c1")).isNotNull();

        now.addAndGet(1_800_001L); // binding past TTL
        reg("a2", List.of("m1"), 10);

        matchmaker.open("c1", null);

        // the expired c1→a1 binding is deleted (re-bound to a2), not left as stale growth
        assertThat(registry.binding("c1").getAgentId()).isEqualTo("a2");
    }

    @Test
    void agentGoneBindingIsUnboundAndRematches() {
        reg("a1", List.of("m1"), 10);
        matchmaker.open("c1", null); // binds c1→a1
        reg("a2", List.of("m1"), 10);

        registry.unregister("a1"); // a1 dies → c1's binding now orphaned

        // open() must drop the dead binding and matchmake a live agent (a2), not leak the binding
        assertThat(matchmaker.open("c1", null).agentId()).isEqualTo("a2");
        assertThat(registry.binding("c1").getAgentId()).isEqualTo("a2");
    }


    @Test
    void closeRemovesSession() {
        reg("a1", List.of("m1"), 10);
        Matchmaker.OpenResult r = matchmaker.open("c1", null);

        assertThat(matchmaker.close(r.sessionId())).isTrue();
        assertThat(registry.session(r.sessionId())).isNull();
        assertThat(matchmaker.close(r.sessionId())).isFalse();
    }
}
