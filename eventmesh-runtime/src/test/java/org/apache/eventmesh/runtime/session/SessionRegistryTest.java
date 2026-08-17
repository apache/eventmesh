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

import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * Exercises {@link SessionRegistry} over an {@link InMemoryMetaStore} with an injected clock so the
 * heartbeat-TTL window is deterministic. Covers register/ready lifecycle, heartbeat freshness +
 * capacity filtering, and bindings/sessions CRUD.
 */
class SessionRegistryTest {

    private static final List<String> CAPS = List.of("gpt-4o-mini");

    @Test
    void registerPendingThenReady() {
        AtomicLong now = new AtomicLong(1_000_000L);
        SessionRegistry reg = new SessionRegistry(new InMemoryMetaStore(), 30_000L, now::get);

        reg.register("a1", "agent-parent-0", CAPS, 10);
        assertThat(reg.agent("a1").getStatus()).isEqualTo(AgentStatus.PENDING.name());
        assertThat(reg.readyAgents()).isEmpty(); // PENDING, not yet routable

        assertThat(reg.markReady("a1")).isTrue();
        assertThat(reg.agent("a1").getStatus()).isEqualTo(AgentStatus.READY.name());
        assertThat(reg.markReady("nope")).isFalse();

        assertThat(reg.readyAgents()).extracting(AgentRecord::getAgentId).containsExactly("a1");
    }

    @Test
    void heartbeatRefreshesHbAndLoad() {
        AtomicLong now = new AtomicLong(1_000_000L);
        SessionRegistry reg = new SessionRegistry(new InMemoryMetaStore(), 30_000L, now::get);
        reg.register("a1", "agent-parent-0", CAPS, 10);
        reg.markReady("a1");

        now.addAndGet(5_000L);
        assertThat(reg.heartbeat("a1", 3)).isTrue();
        AgentRecord r = reg.agent("a1");
        assertThat(r.getHb()).isEqualTo(1_005_000L);
        assertThat(r.getLoad()).isEqualTo(3);
        assertThat(reg.heartbeat("nope", 0)).isFalse();
        // still routable: fresh (0ms age) and under capacity
        assertThat(reg.readyAgents()).extracting(AgentRecord::getAgentId).containsExactly("a1");
    }

    @Test
    void staleHeartbeatExcludedFromReady() {
        AtomicLong now = new AtomicLong(1_000_000L);
        SessionRegistry reg = new SessionRegistry(new InMemoryMetaStore(), 30_000L, now::get);
        reg.register("a1", "agent-parent-0", CAPS, 10);
        reg.markReady("a1");
        reg.register("a2", "agent-parent-0", CAPS, 10);
        reg.markReady("a2");

        now.addAndGet(31_000L); // age a1's heartbeat past the TTL
        reg.heartbeat("a2", 0); // a2 fresh again

        assertThat(reg.readyAgents()).extracting(AgentRecord::getAgentId).containsExactly("a2");
    }

    @Test
    void atCapacityExcludedFromReady() {
        AtomicLong now = new AtomicLong(1_000_000L);
        SessionRegistry reg = new SessionRegistry(new InMemoryMetaStore(), 30_000L, now::get);
        reg.register("a1", "agent-parent-0", CAPS, 2);
        reg.markReady("a1");
        reg.heartbeat("a1", 2); // load == capacity

        assertThat(reg.readyAgents()).isEmpty();
    }

    @Test
    void unregisterRemoves() {
        SessionRegistry reg = new SessionRegistry(new InMemoryMetaStore(), 30_000L);
        reg.register("a1", "agent-parent-0", CAPS, 10);
        reg.markReady("a1");
        assertThat(reg.agent("a1")).isNotNull();

        reg.unregister("a1");
        assertThat(reg.agent("a1")).isNull();
        assertThat(reg.readyAgents()).isEmpty();
    }

    @Test
    void bindingsRoundTripAndRebind() {
        AtomicLong now = new AtomicLong(1_000_000L);
        SessionRegistry reg = new SessionRegistry(new InMemoryMetaStore(), 30_000L, now::get);

        reg.bind("c1", "aX");
        now.addAndGet(1_234L);
        reg.bind("c1", "aY"); // re-bind updates agentId + boundAt

        SessionRegistry.AgentBinding b = reg.binding("c1");
        assertThat(b.getAgentId()).isEqualTo("aY");
        assertThat(b.getBoundAt()).isEqualTo(1_001_234L);
        assertThat(reg.binding("unknown")).isNull();

        assertThat(reg.unbind("c1")).isTrue();
        assertThat(reg.binding("c1")).isNull();
        assertThat(reg.unbind("c1")).isFalse();
    }

    @Test
    void sessionsRoundTrip() {
        SessionRegistry reg = new SessionRegistry(new InMemoryMetaStore(), 30_000L);

        reg.putSession("a1:xyz", "c1", "a1");
        SessionRegistry.SessionMeta m = reg.session("a1:xyz");
        assertThat(m.getClientId()).isEqualTo("c1");
        assertThat(m.getAgentId()).isEqualTo("a1");
        assertThat(reg.session("nope")).isNull();

        assertThat(reg.removeSession("a1:xyz")).isTrue();
        assertThat(reg.session("a1:xyz")).isNull();
        assertThat(reg.removeSession("a1:xyz")).isFalse();
    }

    @Test
    void unregisterDropsBindingsPointingAtAgent() {
        SessionRegistry reg = new SessionRegistry(new InMemoryMetaStore(), 30_000L);
        reg.bind("c1", "a1");
        reg.bind("c2", "a1");
        reg.bind("c3", "a2"); // different agent — must survive

        reg.removeBindingsForAgent("a1");

        assertThat(reg.binding("c1")).isNull(); // orphaned bindings cleaned
        assertThat(reg.binding("c2")).isNull();
        assertThat(reg.binding("c3").getAgentId()).isEqualTo("a2"); // untouched
    }
}