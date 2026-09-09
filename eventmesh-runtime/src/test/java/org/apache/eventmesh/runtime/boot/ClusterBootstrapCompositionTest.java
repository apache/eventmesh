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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.runtime.cluster.ClusterCoordinator;
import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;
import org.apache.eventmesh.runtime.cluster.PartitionOwnership;
import org.apache.eventmesh.runtime.delivery.ReliableDispatcher;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.state.InMemoryDeliveryStateStore;
import org.apache.eventmesh.runtime.state.MetaBackedDeadLetterStore;

import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Issues #5376 / #5377 / #5378 acceptance: the cluster bootstrap composes ONE ownership
 * lifecycle, a cluster-wide subscription route, and durable delivery/DLQ stores.
 */
class ClusterBootstrapCompositionTest {

    @TempDir
    java.nio.file.Path dir;

    private UniRuntime runtime() {
        return new UniRuntime(new StubStorage(), new InMemoryOffsetStore(),
            20L, 50L, 100, 50L,
            org.apache.eventmesh.runtime.cluster.DeliveryTopology.LOCAL_STICKY_PULL, "test", "test:8080");
    }

    /** #5377: with the app-layer ownership pre-installed, UniRuntime reuses it — one lifecycle. */
    @Test
    void runtimeReusesPreInstalledOwnership() throws Exception {
        UniRuntime runtime = runtime();
        InMemoryMetaStore meta = new InMemoryMetaStore();
        // what enableCluster does: build + start ownership, install into ingress
        org.apache.eventmesh.runtime.cluster.FencingToken token =
            new org.apache.eventmesh.runtime.cluster.FencingToken();
        org.apache.eventmesh.runtime.cluster.ClusterMembership membership =
            new org.apache.eventmesh.runtime.cluster.ClusterMembership(meta, "test", "test:8080",
                15_000L, System::currentTimeMillis, token);
        PartitionOwnership installed = new PartitionOwnership(
            membership, meta, runtime.storage(), "test", 5_000L, System::currentTimeMillis, token);
        runtime.ingress().withPartitionOwnership(installed);
        runtime.withClusterMeta(meta);
        runtime.withTopology(org.apache.eventmesh.runtime.cluster.DeliveryTopology.PARTITION_OWNED_PULL);
        runtime.start();
        try {
            assertSame(installed, runtime.ingress().partitionOwnership(),
                "the pre-installed ownership must survive start (no second state machine)");
            assertSame(installed, runtime.partitionOwnershipRefForTest(),
                "UniRuntime must hold the SAME instance it stops on shutdown");
        } finally {
            runtime.shutdown();
        }
    }

    /** #5376: the coordinator wired by enableCluster routes remote subscribers via forward. */
    @Test
    void coordinatorRoutesRemoteSubscriber() {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        Map<String, AtomicInteger> forwards = new ConcurrentHashMap<>();
        // instance B's subscription lands in the shared Meta (what ClusterSubscriptionStore.put does)
        org.apache.eventmesh.runtime.cluster.ClusterSubscriptionStore storeB =
            new org.apache.eventmesh.runtime.cluster.ClusterSubscriptionStore(meta);
        storeB.put("orders", "client-B", "B",
            org.apache.eventmesh.runtime.subscription.DistributionMode.BROADCAST, null);

        // instance A (the partition owner) sees it through its own store view and forwards
        org.apache.eventmesh.runtime.cluster.ClusterSubscriptionStore storeA =
            new org.apache.eventmesh.runtime.cluster.ClusterSubscriptionStore(meta);
        ClusterCoordinator a = new ClusterCoordinator("A", storeA,
            (topic, clientId, event) -> false, // no local targets on A
            (targetInstance, clientId, topic, event) -> {
                forwards.computeIfAbsent(targetInstance + ":" + clientId, k -> new AtomicInteger())
                    .incrementAndGet();
                return true;
            });
        org.apache.eventmesh.common.wire.EventMeshFrame frame =
            org.apache.eventmesh.common.wire.EventMeshFrame.event(
                new java.util.LinkedHashMap<>(Map.of("id", "e-1")), new byte[] {1});
        int delivered = a.dispatch("orders", frame);
        assertEquals(1, delivered, "the remote subscriber receives the event via forward");
        assertEquals(1, forwards.get("B:client-B").get(),
            "exactly one forward to B for client-B");
    }

    /** #5378: durable delivery-state + DLQ ledger injection takes effect on the dispatcher. */
    @Test
    void durableStoresInjectIntoDispatcher() throws Exception {
        UniRuntime runtime = runtime();
        runtime.start();
        try {
            ReliableDispatcher dispatcher = runtime.ingress().dispatcher();
            assertNotNull(dispatcher, "ingress exposes its dispatcher for boot wiring");

            // in-flight guard: swapping with pending deliveries must fail closed
            String id = dispatcher.deliver("orders", 0, 1L,
                org.apache.eventmesh.common.wire.EventMeshFrame.event(
                    new java.util.LinkedHashMap<>(), new byte[] {1}),
                "client", (deliveryId, event, cb) -> { }, null);
            IllegalStateException ex = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> dispatcher.withStateStore(new InMemoryDeliveryStateStore()));
            assertTrue(ex.getMessage().contains("in-flight"), ex.getMessage());
            // clear the pending delivery, then the swap succeeds
            dispatcher.ack(id);
            InMemoryDeliveryStateStore replacement = new InMemoryDeliveryStateStore();
            dispatcher.withStateStore(replacement);
            assertSame(replacement, dispatcher.stateStoreForTest());

            // DLQ ledger attach is a plain setter
            MetaBackedDeadLetterStore ledger = new MetaBackedDeadLetterStore(new InMemoryMetaStore());
            dispatcher.withDeadLetterStore(ledger);
            assertSame(ledger, dispatcher.deadLetterStoreForTest());
        } finally {
            runtime.shutdown();
        }
    }

    /** #5378: the RocksDB delivery-state store round-trips records under the data path. */
    @Test
    void rocksDbDeliveryStateStoreRoundTrips() throws Exception {
        java.nio.file.Path path = dir.resolve("delivery-state");
        org.apache.eventmesh.runtime.state.RocksDBDeliveryStateStore store =
            new org.apache.eventmesh.runtime.state.RocksDBDeliveryStateStore(path.toString());
        org.apache.eventmesh.runtime.state.DeliveryStateStore.Record rec =
            new org.apache.eventmesh.runtime.state.DeliveryStateStore.Record(
                "d-1", "orders", 0, 5L, "client-1", 1, 0L, new byte[] {1, 2, 3});
        store.put(rec);
        store.flush();
        org.apache.eventmesh.runtime.state.DeliveryStateStore.Record read = store.get("d-1");
        assertNotNull(read, "the record survives the flush");
        assertEquals("orders", read.topic);
        store.remove("d-1");
        store.close();
        assertTrue(Files.exists(path), "the RocksDB dir persists after close");
    }

    /** Minimal storage stub (mirrors EventMeshApplicationStartupTest). */
    private static final class StubStorage implements org.apache.eventmesh.api.storage.MeshStoragePlugin {

        @Override
        public void init(java.util.Properties props) {
            // no-op
        }

        @Override
        public void send(String topic, org.apache.eventmesh.common.wire.EventMeshFrame frame,
            org.apache.eventmesh.api.SendCallback callback) {
            // no-op
        }

        @Override
        public java.util.List<org.apache.eventmesh.common.wire.EventMeshFrame> poll(
            String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            return java.util.Collections.emptyList();
        }

        @Override
        public void assignPartitions(String topic, java.util.List<Integer> partitions) {
            // no-op
        }

        @Override
        public void commitOffset(String topic, int partition, long offset) {
            // no-op
        }

        @Override
        public int partitionCount(String topic) {
            return 1;
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
            // no-op
        }

        @Override
        public void shutdown() {
            // no-op
        }
    }
}
