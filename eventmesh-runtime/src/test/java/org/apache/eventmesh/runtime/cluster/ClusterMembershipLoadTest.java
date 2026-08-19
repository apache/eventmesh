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

package org.apache.eventmesh.runtime.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.eventmesh.runtime.ingress.LoadMeter;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Verifies the heartbeat value carries load, liveInstancesWithLoad parses it, and Snapshot.load
 * round-trips through the {@code <active>|<inflow>|<outflow>|<cpu>} wire format.
 */
class ClusterMembershipLoadTest {

    @Test
    void heartbeatWritesLoadAndAddress() {
        AtomicClock clock = new AtomicClock();
        InMemoryMetaStore meta = new InMemoryMetaStore();
        ClusterMembership m = new ClusterMembership(meta, "self", "self:8080", 15_000L, clock::get, new FencingToken());
        m.withLoadSupplier(() -> "3|2000|1500|0.25");
        m.heartbeat();

        String val = meta.get(ClusterMembership.INSTANCE_PREFIX + "self");
        // <ts>|<addr>|<active>|<inflow>|<outflow>|<cpu>
        assertEquals("0|self:8080|3|2000|1500|0.25", val);
    }

    @Test
    void heartbeatWithoutLoadSupplierOmitsLoadFields() {
        AtomicClock clock = new AtomicClock();
        InMemoryMetaStore meta = new InMemoryMetaStore();
        ClusterMembership m = new ClusterMembership(meta, "self", "1.2.3.4:8080", 15_000L, clock::get, new FencingToken());
        m.heartbeat();
        assertEquals("0|1.2.3.4:8080", meta.get(ClusterMembership.INSTANCE_PREFIX + "self"));
    }

    @Test
    void liveInstancesWithLoadParsesAllPeers() {
        AtomicClock clock = new AtomicClock(20_000L); // now = 20000
        InMemoryMetaStore meta = new InMemoryMetaStore();
        // Two live peers with load, one stale.
        meta.put("/em/instances/a", "19000|h1:8080|5|5000|4000|0.10"); // age 1000 < ttl
        meta.put("/em/instances/b", "19500|h2:8080|20|5000000|4000000|0.90"); // heavy, age 500 < ttl
        meta.put("/em/instances/c", "1000|h3:8080|1|100|50|0.01"); // age 19000 > ttl 15000 → pruned
        ClusterMembership m = new ClusterMembership(meta, "self", "self:8080", 15_000L, clock::get, new FencingToken());

        Map<String, ClusterMembership.InstanceInfo> live = m.liveInstancesWithLoad();
        assertEquals(2, live.size(), "stale peer c must be pruned");
        assertNotNull(live.get("a"));
        assertEquals("h1:8080", live.get("a").address);
        assertEquals(5, live.get("a").load.activeSessions);
        assertEquals(0.10, live.get("a").load.cpuLoad, 1e-9);
        assertEquals("h2:8080", live.get("b").address);
        assertEquals(20, live.get("b").load.activeSessions);
    }

    @Test
    void setSelfAddressOverridesPlaceholder() {
        ClusterMembership m = new ClusterMembership(new InMemoryMetaStore(), "self", "self", 15_000L, () -> 0, new FencingToken());
        m.setSelfAddress("10.0.0.5:8080");
        // addressOf(self) returns the overridden address.
        assertEquals("10.0.0.5:8080", m.addressOf("self"));
    }

    @Test
    void peerWithoutLoadReportsNullSnapshot() {
        AtomicClock clock = new AtomicClock(10_000L);
        InMemoryMetaStore meta = new InMemoryMetaStore();
        meta.put("/em/instances/old", "9500|h:8080"); // old-format peer, no load fields
        ClusterMembership m = new ClusterMembership(meta, "self", "self:8080", 15_000L, clock::get, new FencingToken());
        Map<String, ClusterMembership.InstanceInfo> live = m.liveInstancesWithLoad();
        assertEquals("h:8080", live.get("old").address);
        assertNull(live.get("old").load, "peer without load fields must parse to null snapshot");
    }

    @Test
    void snapshotLoadHandlesPartialFields() {
        // A peer that reports only active + inflow (older build) → outflow/cpu default 0, not null.
        AtomicClock clock = new AtomicClock(10_000L);
        InMemoryMetaStore meta = new InMemoryMetaStore();
        meta.put("/em/instances/p", "9500|h:8080|7|300");
        ClusterMembership m = new ClusterMembership(meta, "self", "self:8080", 15_000L, clock::get, new FencingToken());
        LoadMeter.Snapshot load = m.liveInstancesWithLoad().get("p").load;
        assertEquals(7, load.activeSessions);
        assertEquals(300L, load.inflowBytesPerSec);
        assertEquals(0L, load.outflowBytesPerSec);
    }

    /** Minimal controllable clock so tests don't depend on wall time. */
    static final class AtomicClock {

        private long now;

        AtomicClock() {
            this(0L);
        }

        AtomicClock(long start) {
            this.now = start;
        }

        long get() {
            return now;
        }
    }
}
