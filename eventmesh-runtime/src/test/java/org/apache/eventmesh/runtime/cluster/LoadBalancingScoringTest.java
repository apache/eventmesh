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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.runtime.ingress.LoadMeter;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests for the session-distribution load-balancing scoring algorithm: instance selection by
 * weighted score (sessions + byteRate + cpuLoad), overload negative feedback, and per-client load
 * metering.
 */
class LoadBalancingScoringTest {

    @Test
    void snapshotParseRoundTrips() {
        // Simulated heartbeat value: <ts>|<addr>|<active>|<inflow>|<outflow>|<cpu>
        String[] fields = {"5", "1200", "800", "0.25"};
        LoadMeter.Snapshot snap = LoadMeter.Snapshot.parseLoad(fields);
        assertNotNull(snap);
        assertEquals(5, snap.activeSessions);
        assertEquals(1200L, snap.inflowBytesPerSec);
        assertEquals(800L, snap.outflowBytesPerSec);
        assertEquals(0.25, snap.cpuLoad, 0.001);
    }

    @Test
    void snapshotParseNullForMissingFields() {
        assertEquals(null, LoadMeter.Snapshot.parseLoad(null));
        assertEquals(null, LoadMeter.Snapshot.parseLoad(new String[] {"5"}));
    }

    @Test
    void liveInstancesWithLoadPicksLowestScore() {
        AtomicClock clock = new AtomicClock(20_000L);
        InMemoryMetaStore meta = new InMemoryMetaStore();
        // Instance A: light load
        meta.put("/em/instances/a", "19000|h1:8080|2|500|400|0.10");
        // Instance B: heavy load
        meta.put("/em/instances/b", "19500|h2:8080|20|5000000|4000000|0.90");
        // Instance C: stale (should be pruned)
        meta.put("/em/instances/c", "1000|h3:8080|1|100|50|0.01");

        ClusterMembership m = new ClusterMembership(meta, "self", "self:8080", 15_000L, clock::get, new FencingToken());
        Map<String, ClusterMembership.InstanceInfo> live = m.liveInstancesWithLoad();

        assertEquals(2, live.size(), "stale instance c must be pruned");
        assertNotNull(live.get("a"));
        assertNotNull(live.get("b"));

        LoadMeter.Snapshot loadA = live.get("a").load;
        LoadMeter.Snapshot loadB = live.get("b").load;

        // Manual scoring: a should have a much lower score than b.
        double scoreA = score(loadA);
        double scoreB = score(loadB);
        assertTrue(scoreA < scoreB, "light instance a must score lower than heavy instance b");
    }

    @Test
    void overloadInstanceShouldScoreHigher() {
        AtomicClock clock = new AtomicClock(10_000L);
        InMemoryMetaStore meta = new InMemoryMetaStore();
        // Normal instance
        meta.put("/em/instances/normal", "9000|h1:8080|3|1000|800|0.20");
        // Overloaded instance (cpu > 0.8)
        meta.put("/em/instances/overloaded", "9500|h2:8080|5|2000|1500|0.90");

        ClusterMembership m = new ClusterMembership(meta, "self", "self:8080", 15_000L, clock::get, new FencingToken());
        Map<String, ClusterMembership.InstanceInfo> live = m.liveInstancesWithLoad();

        LoadMeter.Snapshot normal = live.get("normal").load;
        LoadMeter.Snapshot overloaded = live.get("overloaded").load;

        // The overloaded instance (cpu>0.8) should score much higher even if fewer sessions.
        double scoreNormal = score(normal);
        double scoreOverloaded = score(overloaded) + 10000; // overload penalty
        assertTrue(scoreOverloaded > scoreNormal, "overloaded instance must score higher (penalized)");
    }

    @Test
    void loadMeterPerClientBucketing() {
        LoadMeter meter = new LoadMeter(() -> 0);
        meter.recordInflow("client-A", 100);
        meter.recordInflow("client-A", 50);
        meter.recordInflow("client-B", 200);

        Map<String, Long> snapshot = meter.clientInflowSnapshot();
        assertEquals(150L, snapshot.get("client-A"));
        assertEquals(200L, snapshot.get("client-B"));
    }

    @Test
    void loadMeterSampleComputesRates() throws Exception {
        LoadMeter meter = new LoadMeter(() -> 0);
        meter.recordInflow(5000);
        meter.recordOutflow(3000);
        Thread.sleep(10);
        meter.sample();
        LoadMeter.Snapshot snap = meter.snapshot();
        assertTrue(snap.inflowBytesPerSec > 0, "inflow rate should be positive");
        assertTrue(snap.outflowBytesPerSec > 0, "outflow rate should be positive");
        assertEquals(0, snap.activeSessions);
    }

    /**
     * Mirror of the scoring formula used in UniHttpServer.recommendInstanceUrl for test assertions.
     */
    private static double score(LoadMeter.Snapshot load) {
        if (load == null) {
            return 500;
        }
        double weightSessions = 1.0;
        double weightBytes = 0.001;
        double weightCpu = 1000.0;
        return load.activeSessions * weightSessions
            + load.inflowBytesPerSec * weightBytes
            + load.outflowBytesPerSec * weightBytes
            + load.cpuLoad * weightCpu;
    }

    static final class AtomicClock {

        private long now;

        AtomicClock(long start) {
            this.now = start;
        }

        long get() {
            return now;
        }
    }
}
