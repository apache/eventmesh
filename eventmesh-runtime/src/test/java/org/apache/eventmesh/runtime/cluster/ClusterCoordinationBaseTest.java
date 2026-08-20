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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class ClusterCoordinationBaseTest {

    @Test
    void metaStorePutGetWatchDelete() {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        AtomicInteger events = new AtomicInteger();
        meta.watch("/em/instances/", (k, v, del) -> events.incrementAndGet());

        meta.put("/em/instances/A", "1");
        assertEquals("1", meta.get("/em/instances/A"));
        assertTrue(meta.putIfAbsent("/em/instances/B", "2"));
        assertFalse(meta.putIfAbsent("/em/instances/B", "x"), "second putIfAbsent fails");
        assertEquals(2, meta.getWithPrefix("/em/instances/").size());
        assertTrue(meta.delete("/em/instances/A"));
        assertEquals(3, events.get(), "put x2 + delete fires the watch 3 times");
    }

    @Test
    void membershipHeartbeatAndTtlExpiry() {
        AtomicLong clock = new AtomicLong(1_000L);
        InMemoryMetaStore meta = new InMemoryMetaStore();
        ClusterMembership a = new ClusterMembership(meta, "A", "A", 5_000L, clock::get, new FencingToken());
        ClusterMembership b = new ClusterMembership(meta, "B", "B", 5_000L, clock::get, new FencingToken());

        a.heartbeat();
        b.heartbeat();
        assertEquals(List.of("A", "B"), a.liveInstances());

        // B's heartbeat goes stale (no refresh), A's stays fresh.
        clock.addAndGet(6_000L);
        a.heartbeat();
        assertFalse(a.liveInstances().contains("B"), "stale instance is evicted");
        assertEquals(List.of("A"), a.liveInstances());
    }

    @Test
    void partitionAssignerDeterministicAndBalanced() {
        // 4 partitions across [A, B]: A owns 0,2 ; B owns 1,3 — input order must not matter.
        Map<Integer, String> ab = PartitionAssigner.assign(4, Arrays.asList("B", "A"));
        assertEquals("A", ab.get(0));
        assertEquals("B", ab.get(1));
        assertEquals("A", ab.get(2));
        assertEquals("B", ab.get(3));
        assertEquals(List.of(0, 2), PartitionAssigner.ownedBy(ab, "A"));
        assertEquals(List.of(1, 3), PartitionAssigner.ownedBy(ab, "B"));

        // Adding a third instance moves partitions but keeps the set total == partitionCount.
        Map<Integer, String> abc = PartitionAssigner.assign(4, Arrays.asList("A", "B", "C"));
        assertEquals(4, abc.size());
        // No partition has two owners (implied by map), and every partition maps to a live instance.
        assertTrue(abc.values().stream().allMatch(java.util.Arrays.asList("A", "B", "C")::contains));
    }

    @Test
    void assignerDegradedToSelfWhenAlone() {
        Map<Integer, String> solo = PartitionAssigner.assign(4, java.util.Collections.singletonList("A"));
        assertTrue(solo.values().stream().allMatch("A"::equals), "single instance owns every partition");
    }
}
