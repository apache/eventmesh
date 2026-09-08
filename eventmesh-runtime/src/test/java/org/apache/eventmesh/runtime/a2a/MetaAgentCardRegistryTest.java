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

package org.apache.eventmesh.runtime.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.protocol.a2a.AgentIdentity;
import org.apache.eventmesh.protocol.a2a.model.AgentCard;
import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;

import org.junit.jupiter.api.Test;

/**
 * Issue #5340 D2a: MetaAgentCardRegistry unit tests. Verifies that:
 * <ol>
 *   <li>register / get / remove are wired through Meta;</li>
 *   <li>the registry survives a "restart" (re-open over the same Meta);</li>
 *   <li>two instances sharing the same Meta see each other's writes via watch;</li>
 *   <li>malformed JSON in Meta is logged and ignored (no cache corruption).</li>
 * </ol>
 */
class MetaAgentCardRegistryTest {

    private static AgentCard sampleCard(String name) {
        return AgentCard.builder()
            .name(name)
            .description("sample agent: " + name)
            .version("1.0")
            .build();
    }

    @Test
    void registerAndLookupArePersistedToMeta() {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        MetaAgentCardRegistry reg = new MetaAgentCardRegistry(meta);
        AgentIdentity id = new AgentIdentity("org-1", "unit-1", "agent-A");
        reg.registerCard(id, sampleCard("agent-A"));
        assertTrue(reg.isAgentRegistered("agent-A"));
        AgentCard got = reg.getCard("agent-A");
        assertNotNull(got);
        assertEquals("agent-A", got.getName());
        // The raw Meta value is a JSON object containing the card's fields.
        String raw = meta.get("/em/agent-cards/agent-A");
        assertNotNull(raw);
        assertTrue(raw.contains("\"name\":\"agent-A\""),
            "Meta value is the JSON-serialized AgentCard: " + raw);
    }

    @Test
    void registrySurvivesWrapperRestart() {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        MetaAgentCardRegistry first = new MetaAgentCardRegistry(meta);
        first.registerCard(new AgentIdentity("org-1", "unit-1", "agent-A"),
            sampleCard("agent-A"));
        first.registerCard(new AgentIdentity("org-1", "unit-1", "agent-B"),
            sampleCard("agent-B"));
        // The runtime restarts; the registry wrapper is re-opened over the same Meta.
        MetaAgentCardRegistry second = new MetaAgentCardRegistry(meta);
        assertTrue(second.isAgentRegistered("agent-A"));
        assertTrue(second.isAgentRegistered("agent-B"));
        assertEquals("agent-A", second.getCard("agent-A").getName());
        assertEquals("agent-B", second.getCard("agent-B").getName());
    }

    @Test
    void twoInstancesShareStateViaMeta() {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        MetaAgentCardRegistry a = new MetaAgentCardRegistry(meta);
        MetaAgentCardRegistry b = new MetaAgentCardRegistry(meta);
        a.registerCard(new AgentIdentity("org-1", "unit-1", "agent-A"),
            sampleCard("agent-A"));
        // b observes a's write via the watch prefix.
        assertTrue(b.isAgentRegistered("agent-A"));
        assertEquals("agent-A", b.getCard("agent-A").getName());
        b.registerCard(new AgentIdentity("org-1", "unit-1", "agent-B"),
            sampleCard("agent-B"));
        assertTrue(a.isAgentRegistered("agent-B"));
        // a removes; b observes.
        assertTrue(a.removeCard(new AgentIdentity("org-1", "unit-1", "agent-A")));
        assertFalse(b.isAgentRegistered("agent-A"));
    }

    @Test
    void malformedJsonInMetaIsLoggedAndIgnored() {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        // Pre-seed Meta with a malformed value (simulates a peer that wrote garbage).
        meta.put("/em/agent-cards/agent-X", "this is not valid JSON");
        // Construction must not throw; the bad entry is logged + ignored.
        MetaAgentCardRegistry reg = new MetaAgentCardRegistry(meta);
        assertFalse(reg.isAgentRegistered("agent-X"),
            "malformed JSON does not produce a cache entry");
        assertNull(reg.getCard("agent-X"));
        // After the malformed value, legitimate register still works.
        reg.registerCard(new AgentIdentity("org-1", "unit-1", "agent-Y"),
            sampleCard("agent-Y"));
        assertTrue(reg.isAgentRegistered("agent-Y"));
    }

    @Test
    void nullArgumentsAreRejected() {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        MetaAgentCardRegistry reg = new MetaAgentCardRegistry(meta);
        assertThrows(IllegalArgumentException.class,
            () -> reg.registerCard(null, sampleCard("x")));
        assertThrows(IllegalArgumentException.class,
            () -> reg.registerCard(new AgentIdentity("o", "u", "a"), null));
        assertThrows(IllegalArgumentException.class,
            () -> reg.registerCard(
                new AgentIdentity(null, null, null), sampleCard("x")));
    }
}
