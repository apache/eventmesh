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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AgentRegistrar} over an {@link InMemoryMetaStore} with a no-op parent ensurer. */
class AgentRegistrarTest {

    private SessionRegistry registry;
    private AgentRegistrar registrar;

    @BeforeEach
    void setUp() {
        registry = new SessionRegistry(new InMemoryMetaStore(), 30_000L);
        registrar = new AgentRegistrar(registry, p -> {
        }, "agent-parent-0", "client-parent");
    }

    @Test
    void registerReturnsParentsAndStoresPENDING() {
        AgentRegistrar.RegisterResult res = registrar.register("a1", List.of("gpt-4o-mini"), 10);

        assertThat(res.parent()).isEqualTo("agent-parent-0");
        assertThat(res.clientParent()).isEqualTo("client-parent");

        AgentRecord r = registry.agent("a1");
        assertThat(r.getStatus()).isEqualTo(AgentStatus.PENDING.name());
        assertThat(r.getParent()).isEqualTo("agent-parent-0");
        assertThat(r.getCapabilities()).containsExactly("gpt-4o-mini");
        assertThat(r.getCapacity()).isEqualTo(10);
    }

    @Test
    void readyFlipsToREADY() {
        registrar.register("a1", List.of("m"), 10);

        assertThat(registrar.ready("a1")).isTrue();
        assertThat(registry.agent("a1").getStatus()).isEqualTo(AgentStatus.READY.name());
        assertThat(registrar.ready("nope")).isFalse();
    }

    @Test
    void heartbeatRefreshesLoadThenUnregisterRemoves() {
        registrar.register("a1", List.of("m"), 10);
        registrar.ready("a1");

        assertThat(registrar.heartbeat("a1", 3)).isTrue();
        assertThat(registry.agent("a1").getLoad()).isEqualTo(3);
        assertThat(registrar.heartbeat("nope", 0)).isFalse();

        registrar.unregister("a1");
        assertThat(registry.agent("a1")).isNull();
        assertThat(registrar.ready("a1")).isFalse();
    }
}
