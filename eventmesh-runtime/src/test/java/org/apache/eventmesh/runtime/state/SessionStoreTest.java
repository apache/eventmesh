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

package org.apache.eventmesh.runtime.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;
import org.apache.eventmesh.runtime.session.AgentRecord;
import org.apache.eventmesh.runtime.session.SessionRegistry;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Sub-PR A baseline: the {@link SessionStore} interface is the contract for the agent / binding /
 * session control-plane. This test verifies that {@link SessionRegistry} implements it and that
 * the round-trip + heartbeat-TTL semantics are preserved against an in-process Meta.
 */
class SessionStoreTest {

    @Test
    void sessionRegistryImplementsInterface() {
        SessionStore store = new SessionRegistry(new InMemoryMetaStore(), 60_000L);
        assertNotNull(store, "Meta-backed SessionStore must construct");
    }

    @Test
    void registerAgentMarkReadyAndBind() {
        SessionStore store = new SessionRegistry(new InMemoryMetaStore(), 60_000L);
        store.registerAgent("a1", "parent-x", Arrays.asList("cap-1", "cap-2"), 4);
        assertFalse(store.readyAgents().stream().anyMatch(a ->
            ((AgentRecord) a).getAgentId().equals("a1")),
            "PENDING agent must not appear in readyAgents()");

        assertTrue(store.markAgentReady("a1"));
        List<AgentRecord> ready = store.readyAgents();
        assertEquals(1, ready.size());
        assertEquals("a1", ready.get(0).getAgentId());

        store.bind("client-c", "a1");
        SessionRegistry.AgentBinding binding = store.binding("client-c");
        assertNotNull(binding);
        assertEquals("a1", binding.getAgentId());
    }

    @Test
    void markAgentReadyReturnsFalseForUnknownAgent() {
        SessionStore store = new SessionRegistry(new InMemoryMetaStore(), 60_000L);
        assertFalse(store.markAgentReady("nope"));
    }

    @Test
    void heartbeatRefreshesLiveness() {
        // Mutable clock so we can simulate heartbeat TTL expiry without sleeping.
        long[] now = new long[]{1_000L};
        SessionStore store = new SessionRegistry(new InMemoryMetaStore(), 5_000L, () -> now[0]);
        store.registerAgent("a1", null, List.of(), 4);
        store.markAgentReady("a1");
        assertEquals(1, store.readyAgents().size(), "fresh heartbeat must keep the agent ready");

        // Jump the clock past the TTL window.
        now[0] += 10_000;
        assertEquals(0, store.readyAgents().size(), "stale heartbeat must drop the agent");

        // Heartbeat refreshes liveness.
        assertTrue(store.heartbeat("a1", 0));
        assertEquals(1, store.readyAgents().size());
    }

    @Test
    void putSessionAndTouch() {
        SessionStore store = new SessionRegistry(new InMemoryMetaStore(), 60_000L);
        store.putSession("s1", "c1", "a1");
        assertNotNull(store.session("s1"));
        store.touchSession("s1"); // no-op; should not throw
        assertNotNull(store.session("s1"));
        assertNull(store.session("nope"));
    }

    @Test
    void expireStaleSessionsRemovesIdle() {
        long[] now = new long[]{1_000L};
        SessionStore store = new SessionRegistry(new InMemoryMetaStore(), 60_000L, () -> now[0]);
        store.putSession("old", "c1", "a1");
        // Move the clock forward and force the session to be idle past the TTL.
        now[0] += 120_000;
        List<String> expired = store.expireStaleSessions(60_000L);
        assertEquals(1, expired.size());
        assertEquals("old", expired.get(0));
        assertNull(store.session("old"));
    }
}
