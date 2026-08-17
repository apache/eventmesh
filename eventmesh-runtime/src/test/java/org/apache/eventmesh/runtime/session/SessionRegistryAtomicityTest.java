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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * Tests for P1-2 (SessionRegistry immutable bean updates): markReady/heartbeat/touchSession build
 * new copies, so concurrent readers never see a half-mutated AgentRecord or SessionMeta.
 */
class SessionRegistryAtomicityTest {

    @Test
    void markReadyProducesNewImmutableBean() {
        AtomicLong clock = new AtomicLong(1000L);
        SessionRegistry reg = new SessionRegistry(new InMemoryMetaStore(), 30_000L, clock::get);

        reg.register("a1", "parent-0", List.of("model-x"), 100);
        clock.set(2000L);
        assertTrue(reg.markReady("a1"));

        AgentRecord r = reg.agent("a1");
        assertNotNull(r);
        assertEquals("READY", r.getStatus());
        assertEquals(2000L, r.getHb(), "heartbeat must be refreshed on markReady");
    }

    @Test
    void heartbeatProducesNewImmutableBean() {
        AtomicLong clock = new AtomicLong(1000L);
        SessionRegistry reg = new SessionRegistry(new InMemoryMetaStore(), 30_000L, clock::get);

        reg.register("a1", "parent-0", List.of("model-x"), 100);
        clock.set(2000L);
        assertTrue(reg.heartbeat("a1", 5));

        AgentRecord r1 = reg.agent("a1");
        assertEquals(5, r1.getLoad());
        assertEquals(2000L, r1.getHb());

        // Second heartbeat — r1 must NOT be mutated (P1-2 immutable).
        clock.set(3000L);
        assertTrue(reg.heartbeat("a1", 10));
        assertEquals(5, r1.getLoad(), "old reference must not be mutated");
        assertEquals(2000L, r1.getHb(), "old reference must not be mutated");

        AgentRecord r2 = reg.agent("a1");
        assertEquals(10, r2.getLoad());
        assertEquals(3000L, r2.getHb());
    }

    @Test
    void touchSessionProducesNewImmutableBean() {
        AtomicLong clock = new AtomicLong(1000L);
        SessionRegistry reg = new SessionRegistry(new InMemoryMetaStore(), 30_000L, clock::get);

        reg.putSession("s1", "c1", "a1");
        SessionRegistry.SessionMeta m1 = reg.session("s1");
        assertEquals(1000L, m1.getLastActiveAt());

        clock.set(5000L);
        reg.touchSession("s1");

        // Old reference must not be mutated (P1-2 immutable).
        assertEquals(1000L, m1.getLastActiveAt(), "old reference must not be mutated");

        SessionRegistry.SessionMeta m2 = reg.session("s1");
        assertEquals(5000L, m2.getLastActiveAt());
    }
}
