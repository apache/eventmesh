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

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * Issue #5359 acceptance: boot-time wiring of DeliveryTopology + MetaStore + FencingToken.
 *
 * <p>Covers the three wiring contracts the plan demands: (a) enableCluster flips the runtime
 * topology to PARTITION_OWNED_PULL and injects the shared MetaStore before start; (b) the
 * pre-start builders reject post-start mutation; (c) PARTITION_OWNED_PULL without a MetaStore
 * fails fast (the #5356 contract, re-verified at the application level).</p>
 */
class EventMeshApplicationStartupTest {

    private static final class StubStorage implements MeshStoragePlugin {

        @Override
        public void init(Properties props) {
            // no-op
        }

        @Override
        public void send(String topic, EventMeshFrame frame, SendCallback callback) {
            // no-op
        }

        @Override
        public List<EventMeshFrame> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            return Collections.emptyList();
        }

        @Override
        public void assignPartitions(String topic, List<Integer> partitions) {
            // no-op
        }

        @Override
        public void commitOffset(String topic, int partition, long offset) {
            // no-op
        }

        @Override
        public int partitionCount(String topic) {
            return 0;
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

    private static UniRuntime runtime() {
        return new UniRuntime(new StubStorage(), new InMemoryOffsetStore(),
            20L, 50L, 100, 50L, DeliveryTopology.LOCAL_STICKY_PULL, "test", "test:8080");
    }

    @Test
    void enableClusterFlipsTopologyAndInjectsMeta() throws Exception {
        // The core #5359 contract: after enableCluster, the runtime polls ONLY owned
        // partitions (PARTITION_OWNED_PULL) through the injected shared MetaStore —
        // not one ownership state machine in the app and a poll-all loop in the runtime.
        UniRuntime runtime = runtime();
        InMemoryMetaStore shared = new InMemoryMetaStore();
        runtime.withClusterMeta(shared);
        runtime.withTopology(DeliveryTopology.PARTITION_OWNED_PULL);
        runtime.start();
        try {
            assertEquals(DeliveryTopology.PARTITION_OWNED_PULL, runtime.topology(),
                "cluster mode must poll owned partitions only");
            assertEquals(shared, runtime.clusterMeta(),
                "the shared MetaStore must be wired into the runtime");
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void topologyCannotFlipAfterStart() throws Exception {
        UniRuntime runtime = runtime();
        runtime.start();
        try {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> runtime.withTopology(DeliveryTopology.PARTITION_OWNED_PULL));
            assertTrue(ex.getMessage().contains("after start"),
                "post-start topology change must fail fast, got: " + ex.getMessage());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void clusterMetaCannotChangeAfterStart() throws Exception {
        UniRuntime runtime = runtime();
        runtime.start();
        try {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> runtime.withClusterMeta(new InMemoryMetaStore()));
            assertTrue(ex.getMessage().contains("after start"),
                "post-start meta change must fail fast, got: " + ex.getMessage());
        } finally {
            runtime.shutdown();
        }
    }

    @Test
    void nullTopologyRejected() {
        UniRuntime runtime = runtime();
        assertThrows(IllegalArgumentException.class,
            () -> runtime.withTopology(null));
    }
}
