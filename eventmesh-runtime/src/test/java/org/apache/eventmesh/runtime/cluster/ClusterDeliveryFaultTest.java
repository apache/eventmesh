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

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.common.wire.EventMeshFrame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * In-process fault-injection tests for the unified delivery topology (§13.2.10, #5293).
 *
 * <p>Three (or four) instances share one {@link InMemoryMetaStore}; every ownership cycle is
 * driven explicitly via {@link PartitionOwnership#refreshOnce} (no scheduler, no sleeps) against
 * a mutable clock, so failures — crash, TTL expiry, Meta network partition, membership churn —
 * are fully deterministic.</p>
 *
 * <p>Scenarios: steady-state deterministic split; crash takeover (TTL eviction forces the CAS
 * regardless of token order); scale-out churn (released partitions are re-claimed, no
 * stranding); Meta partition (lease gate stops polling → no split-brain duplicates); healed
 * partition (no failover, the share is reclaimed untouched).</p>
 */
class ClusterDeliveryFaultTest {

    private static final String TOPIC = "orders";
    private static final int PARTITIONS = 6;
    private static final long TTL_MS = 5_000L;
    private static final String ASSIGNMENT_PREFIX = "/em/assignments/";

    /** Mutable clock — tests advance time explicitly instead of sleeping. */
    static final class Clock {

        private volatile long now = 1_000L;

        long get() {
            return now;
        }

        void advance(long ms) {
            now += ms;
        }
    }

    /** In-memory storage stub recording the last {@code assignPartitions} view per topic. */
    static final class FakeStorage implements MeshStoragePlugin {

        final Map<String, List<Integer>> assigned = new ConcurrentHashMap<>();

        @Override
        public void init(Properties properties) {
        }

        @Override
        public void send(String topic, EventMeshFrame frame, SendCallback callback) {
        }

        @Override
        public List<EventMeshFrame> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            return Collections.emptyList();
        }

        @Override
        public void assignPartitions(String topic, List<Integer> partitions) {
            assigned.put(topic, new ArrayList<>(partitions));
        }

        @Override
        public void commitOffset(String topic, int partition, long offset) {
        }

        @Override
        public int partitionCount(String topic) {
            return PARTITIONS;
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void start() {
        }

        @Override
        public void shutdown() {
        }
    }

    /** Wraps the shared Meta store; {@code writesFail = true} simulates a network partition from Meta. */
    static final class PartitionedMetaStore implements MetaStore {

        final MetaStore delegate;
        volatile boolean writesFail;

        PartitionedMetaStore(MetaStore delegate) {
            this.delegate = delegate;
        }

        private void gate() {
            if (writesFail) {
                throw new RuntimeException("simulated Meta partition");
            }
        }

        @Override
        public void watch(String prefix, MetaListener listener) {
            delegate.watch(prefix, listener);
        }

        @Override
        public void put(String key, String value) {
            gate();
            delegate.put(key, value);
        }

        @Override
        public boolean putIfAbsent(String key, String value) {
            gate();
            return delegate.putIfAbsent(key, value);
        }

        @Override
        public String get(String key) {
            return delegate.get(key);
        }

        @Override
        public Map<String, String> getWithPrefix(String prefix) {
            return delegate.getWithPrefix(prefix);
        }

        @Override
        public boolean delete(String key) {
            gate();
            return delegate.delete(key);
        }

        @Override
        public boolean tryAcquire(String key, String expectedOldValue, String newValue) {
            gate();
            return delegate.tryAcquire(key, expectedOldValue, newValue);
        }
    }

    /** One simulated EventMesh instance: membership + partition ownership + storage view. */
    static final class Instance {

        final String id;
        final PartitionedMetaStore meta;
        final ClusterMembership membership;
        final PartitionOwnership ownership;
        final FakeStorage storage;

        Instance(MetaStore sharedMeta, Clock clock, String id, long bootEpoch) {
            this.id = id;
            this.meta = new PartitionedMetaStore(sharedMeta);
            FencingToken token = new FencingToken(bootEpoch, new AtomicLong(0));
            this.membership = new ClusterMembership(meta, id, id + ":8080", TTL_MS, clock::get, token);
            this.storage = new FakeStorage();
            this.ownership = new PartitionOwnership(membership, meta, storage, id, 1_000L, clock::get, token);
        }

        void refresh() {
            ownership.refreshOnce(Set.of(TOPIC));
        }

        List<Integer> owned() {
            List<Integer> ps = ownership.ownedPartitions(TOPIC);
            return ps == null ? Collections.emptyList() : ps;
        }
    }

    private final InMemoryMetaStore meta = new InMemoryMetaStore();
    private final Clock clock = new Clock();

    private Instance newInstance(String id, long bootEpoch) {
        return new Instance(meta, clock, id, bootEpoch);
    }

    private void runRounds(int rounds, Instance... instances) {
        for (int i = 0; i < rounds; i++) {
            for (Instance inst : instances) {
                inst.refresh();
            }
        }
    }

    /** partition → owner per the {@code /em/assignments/*} records (tombstones skipped). */
    private Map<Integer, String> metaAssignments() {
        Map<Integer, String> out = new HashMap<>();
        for (Map.Entry<String, String> e : meta.getWithPrefix(ASSIGNMENT_PREFIX).entrySet()) {
            String value = e.getValue();
            if (value == null || value.isEmpty()) {
                continue; // released tombstone
            }
            int sep = value.indexOf('|');
            if (sep <= 0) {
                continue;
            }
            int p = Integer.parseInt(e.getKey().substring(e.getKey().lastIndexOf('#') + 1));
            out.put(p, value.substring(sep + 1));
        }
        return out;
    }

    // ---- Scenario 1: steady state — deterministic, disjoint, full coverage ----

    @Test
    void steadyStateDeterministicSplit() {
        Instance a = newInstance("A", 1000L);
        Instance b = newInstance("B", 2000L);
        Instance c = newInstance("C", 3000L);
        runRounds(4, a, b, c);

        // sorted [A, B, C], partition % 3
        assertEquals(List.of(0, 3), a.owned());
        assertEquals(List.of(1, 4), b.owned());
        assertEquals(List.of(2, 5), c.owned());

        // the storage layer received the same ownership view
        assertEquals(List.of(0, 3), a.storage.assigned.get(TOPIC));
        assertEquals(List.of(1, 4), b.storage.assigned.get(TOPIC));
        assertEquals(List.of(2, 5), c.storage.assigned.get(TOPIC));

        // Meta agrees on every partition, and no partition has two owners
        Map<Integer, String> m = metaAssignments();
        assertEquals(6, m.size(), "every partition has an assignment record");
        assertEquals("A", m.get(0));
        assertEquals("B", m.get(1));
        assertEquals("C", m.get(2));
        assertEquals("A", m.get(3));
        assertEquals("B", m.get(4));
        assertEquals("C", m.get(5));
    }

    // ---- Scenario 2: instance crash — TTL eviction forces takeover regardless of token order ----

    @Test
    void crashedInstancePartitionsAreTakenOver() {
        Instance a = newInstance("A", 1000L);
        Instance b = newInstance("B", 2000L);
        Instance c = newInstance("C", 3000L);
        runRounds(4, a, b, c);

        // B crashes (no more heartbeat/refresh); its lease expires.
        clock.advance(TTL_MS + 1_000L);
        runRounds(4, a, c);

        // Live set is now [A, C] even though A's token (1000) is LOWER than dead B's (2000) —
        // eviction forces the CAS takeover, otherwise B's partitions would be stranded forever.
        assertEquals(List.of(0, 2, 4), a.owned());
        assertEquals(List.of(1, 3, 5), c.owned());

        Map<Integer, String> m = metaAssignments();
        assertFalse(m.containsValue("B"), "no partition may still name the crashed instance");
        for (int p = 0; p < PARTITIONS; p++) {
            assertEquals(p % 2 == 0 ? "A" : "C", m.get(p), "partition " + p + " covered after crash");
        }
    }

    // ---- Scenario 3: scale-out churn — released partitions are re-claimed, no stranding ----

    @Test
    void membershipChurnMovesOnlyTheReassignedPartitions() {
        Instance a = newInstance("A", 1000L);
        Instance b = newInstance("B", 2000L);
        Instance c = newInstance("C", 3000L);
        runRounds(4, a, b, c);

        // D joins: sorted [A, B, C, D], partition % 4 → A:0,4 B:1,5 C:2 D:3
        Instance d = newInstance("D", 4000L);
        runRounds(4, a, b, c, d);

        assertEquals(List.of(0, 4), a.owned());
        assertEquals(List.of(1, 5), b.owned());
        assertEquals(List.of(2), c.owned());
        assertEquals(List.of(3), d.owned());

        // Without the release path A (token 1000) could never claim 4 from live B (token 2000):
        // the partition would be stranded with no poller. Here B releases 4 to the tombstone and
        // A claims it on a later cycle.
        Map<Integer, String> m = metaAssignments();
        assertEquals(6, m.size(), "no stranded partition after churn");
        assertEquals("A", m.get(0));
        assertEquals("B", m.get(1));
        assertEquals("C", m.get(2));
        assertEquals("D", m.get(3));
        assertEquals("A", m.get(4));
        assertEquals("B", m.get(5));
    }

    // ---- Scenario 4: Meta network partition — lease gate stops polling (split-brain guard) ----

    @Test
    void metaPartitionStopsPollingUntilTtlExpiry() {
        Instance a = newInstance("A", 1000L);
        Instance b = newInstance("B", 2000L);
        Instance c = newInstance("C", 3000L);
        runRounds(4, a, b, c);

        // B is cut off from Meta: every write fails, heartbeat included.
        b.meta.writesFail = true;
        b.refresh();

        // Lease invalid → B polls nothing (its 1,4 would otherwise duplicate the quorum's
        // consumption once A and C take over after the TTL).
        assertTrue(b.owned().isEmpty(), "partitioned instance must stop polling");

        // While B's lease is still fresh, A and C keep their own shares only — availability is
        // sacrificed for consistency until the lease expires.
        runRounds(2, a, c);
        assertEquals(List.of(0, 3), a.owned());
        assertEquals(List.of(2, 5), c.owned());

        // After TTL expiry the quorum covers B's partitions.
        clock.advance(TTL_MS + 1_000L);
        runRounds(4, a, c);
        assertEquals(List.of(0, 2, 4), a.owned());
        assertEquals(List.of(1, 3, 5), c.owned());
        assertFalse(metaAssignments().containsValue("B"), "B's records were taken over");
    }

    // ---- Scenario 5: healed partition — no failover, share reclaimed untouched ----

    @Test
    void healedPartitionReclaimsItsPartitionsWithoutFailover() {
        Instance a = newInstance("A", 1000L);
        Instance b = newInstance("B", 2000L);
        Instance c = newInstance("C", 3000L);
        runRounds(4, a, b, c);

        // B is briefly partitioned from Meta, then heals before its lease expires.
        b.meta.writesFail = true;
        b.refresh();
        assertTrue(b.owned().isEmpty(), "while partitioned, B polls nothing");

        b.meta.writesFail = false;
        runRounds(2, a, b, c);

        // No failover happened (B never expired), so every instance ends with exactly its own
        // share — no spurious reassignment, no duplicate coverage.
        assertEquals(List.of(0, 3), a.owned());
        assertEquals(List.of(1, 4), b.owned());
        assertEquals(List.of(2, 5), c.owned());
        Map<Integer, String> m = metaAssignments();
        assertEquals(6, m.size());
        assertEquals("B", m.get(1));
        assertEquals("B", m.get(4));
    }
}
