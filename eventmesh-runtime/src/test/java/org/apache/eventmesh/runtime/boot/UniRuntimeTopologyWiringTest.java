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

package org.apache.eventmesh.runtime.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.cluster.DeliveryTopology;
import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.subscription.DistributionMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

/**
 * E2E wiring test for the {@link DeliveryTopology#PARTITION_OWNED_PULL} topology (issue #5309
 * sub-step 4). Boots real {@link UniRuntime} instances (full lifecycle: start() boots the
 * partition ownership state machine, the scheduler drives the refresh loop, shutdown() stops it)
 * against a shared in-process {@link InMemoryMetaStore} and a partition-aware storage stub.
 *
 * <p>Complements {@code ClusterDeliveryFaultTest}, which drives
 * {@code PartitionOwnership.refreshOnce} deterministically without a runtime: this class proves
 * the wiring end-to-end — two PARTITION_OWNED_PULL instances converge on a disjoint partition
 * split, the LOCAL_STICKY_PULL default never assigns partitions, and a null topology fails
 * fast.</p>
 */
class UniRuntimeTopologyWiringTest {

    private static final String TOPIC = "orders";
    private static final int PARTITIONS = 6;

    @Test
    void partitionOwnedPullSplitsPartitionsAcrossInstances() throws Exception {
        InMemoryMetaStore sharedMeta = new InMemoryMetaStore();
        RecordingStorage storageA = new RecordingStorage();
        RecordingStorage storageB = new RecordingStorage();
        UniRuntime runtimeA = new UniRuntime(storageA, new InMemoryOffsetStore(), 20L, 50L, 100, 50L,
            DeliveryTopology.PARTITION_OWNED_PULL, "A", "A:8080");
        UniRuntime runtimeB = new UniRuntime(storageB, new InMemoryOffsetStore(), 20L, 50L, 100, 50L,
            DeliveryTopology.PARTITION_OWNED_PULL, "B", "B:8080");
        runtimeA.clusterMeta = sharedMeta;
        runtimeB.clusterMeta = sharedMeta;
        try {
            runtimeA.start();
            runtimeB.start();
            runtimeA.ingress().subscribe(TOPIC, "client-a", DistributionMode.BROADCAST, null);
            runtimeB.ingress().subscribe(TOPIC, "client-b", DistributionMode.BROADCAST, null);

            // The ownership refresh loop runs on a real scheduler (5s period, first tick at t=0
            // before the subscription is visible), so convergence needs a few cycles — poll for it.
            long deadline = System.currentTimeMillis() + 30_000L;
            while (System.currentTimeMillis() < deadline) {
                if (splitConverged(storageA, storageB)) {
                    break;
                }
                Thread.sleep(200);
            }
            List<Integer> mine = storageA.assigned.get(TOPIC);
            List<Integer> theirs = storageB.assigned.get(TOPIC);
            assertTrue(mine != null && !mine.isEmpty(), "instance A should own partitions: " + mine);
            assertTrue(theirs != null && !theirs.isEmpty(), "instance B should own partitions: " + theirs);
            assertEquals(PARTITIONS, mine.size() + theirs.size(), "full coverage, no stranding");
            Set<Integer> union = new HashSet<>(mine);
            union.addAll(theirs);
            assertEquals(Set.of(0, 1, 2, 3, 4, 5), union, "union must cover every partition");
        } finally {
            runtimeA.shutdown(0);
            runtimeB.shutdown(0);
        }
    }

    @Test
    void localStickyPullDefaultNeverAssignsPartitions() throws Exception {
        RecordingStorage storage = new RecordingStorage();
        UniRuntime runtime = new UniRuntime(storage, new InMemoryOffsetStore(), 20L, 50L, 100, 50L);
        try {
            runtime.start();
            runtime.ingress().subscribe(TOPIC, "client-1", DistributionMode.BROADCAST, null);
            // The ownership loop's first tick fires at t=0 if (wrongly) started; 1.5s is enough
            // to observe a wrong assignment while keeping the test fast.
            Thread.sleep(1_500L);
            assertTrue(storage.assigned.isEmpty(),
                "LOCAL_STICKY_PULL must not assign partitions (poll-all fallback)");
        } finally {
            runtime.shutdown(0);
        }
    }

    @Test
    void nullTopologyFailsFastInConstructor() {
        assertThrows(IllegalArgumentException.class,
            () -> new UniRuntime(new RecordingStorage(), new InMemoryOffsetStore(), 20L, 50L, 100, 50L,
                null, "x", null));
    }

    private static boolean splitConverged(RecordingStorage a, RecordingStorage b) {
        List<Integer> mine = a.assigned.get(TOPIC);
        List<Integer> theirs = b.assigned.get(TOPIC);
        if (mine == null || theirs == null || mine.isEmpty() || theirs.isEmpty()) {
            return false;
        }
        if (mine.size() + theirs.size() != PARTITIONS) {
            return false;
        }
        Set<Integer> union = new HashSet<>(mine);
        union.addAll(theirs);
        return union.size() == PARTITIONS;
    }

    /** Partition-aware storage stub recording the last {@code assignPartitions} view per topic. */
    static final class RecordingStorage implements MeshStoragePlugin {

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
}
