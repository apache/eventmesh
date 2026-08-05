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

package org.apache.eventmesh.runtime.offset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PushOffsetStoreTest {

    @Test
    void inMemoryContract() {
        InMemoryPushOffsetStore store = new InMemoryPushOffsetStore();
        contract(store);
        store.clear();
    }

    /**
     * A PushOffsetStore tracks the MAX offset (watermark) per key, not the last written value.
     * Writing a lower offset after a higher one must NOT move the watermark back.
     */
    @Test
    void watermarkNeverGoesBack() {
        InMemoryPushOffsetStore store = new InMemoryPushOffsetStore();
        store.writePushOffset("orders", "worker-1", 0, 100L);
        assertEquals(100L, store.readPushOffset("orders", "worker-1", 0));

        // Writing a lower offset must NOT move the watermark back
        store.writePushOffset("orders", "worker-1", 0, 50L);
        assertEquals(100L, store.readPushOffset("orders", "worker-1", 0));

        // Writing a higher offset must advance the watermark
        store.writePushOffset("orders", "worker-1", 0, 200L);
        assertEquals(200L, store.readPushOffset("orders", "worker-1", 0));
    }

    /**
     * readMaxPushOffset returns the max across all partitions for a (topic, clientId).
     */
    @Test
    void maxPushOffsetAcrossPartitions() {
        InMemoryPushOffsetStore store = new InMemoryPushOffsetStore();
        store.writePushOffset("orders", "worker-1", 0, 10L);
        store.writePushOffset("orders", "worker-1", 1, 30L);
        store.writePushOffset("orders", "worker-1", 2, 20L);

        assertEquals(30L, store.readMaxPushOffset("orders", "worker-1"));
        assertEquals(-1L, store.readMaxPushOffset("orders", "worker-2"));
    }

    /**
     * removeClient removes all entries for a client across all topics.
     */
    @Test
    void removeClientCleansAllEntries() {
        InMemoryPushOffsetStore store = new InMemoryPushOffsetStore();
        store.writePushOffset("orders", "worker-1", 0, 10L);
        store.writePushOffset("payments", "worker-1", 0, 20L);
        store.writePushOffset("orders", "worker-2", 0, 30L);

        store.removeClient("worker-1");

        assertEquals(-1L, store.readPushOffset("orders", "worker-1", 0));
        assertEquals(-1L, store.readPushOffset("payments", "worker-1", 0));
        assertEquals(30L, store.readPushOffset("orders", "worker-2", 0)); // untouched
    }

    /**
     * Shared contract every PushOffsetStore must satisfy.
     */
    private void contract(PushOffsetStore store) {
        // Unknown offset reads as -1.
        assertEquals(-1L, store.readPushOffset("orders", "worker-1", 0));

        // Write then read is consistent.
        store.writePushOffset("orders", "worker-1", 0, 10L);
        store.writePushOffset("orders", "worker-1", 1, 11L);
        store.writePushOffset("orders", "worker-2", 0, 99L);
        // A different topic must not bleed across.
        store.writePushOffset("payments", "worker-1", 0, 5L);

        assertEquals(10L, store.readPushOffset("orders", "worker-1", 0));
        assertEquals(11L, store.readPushOffset("orders", "worker-1", 1));
        assertEquals(99L, store.readPushOffset("orders", "worker-2", 0));
        assertEquals(-1L, store.readPushOffset("orders", "worker-3", 0));

        // readAllPushOffsets returns only this topic's entries, keyed by clientId#partition.
        java.util.Map<String, Long> all = store.readAllPushOffsets("orders");
        assertEquals(3, all.size());
        assertEquals(10L, all.get("worker-1#0"));
        assertEquals(11L, all.get("worker-1#1"));
        assertEquals(99L, all.get("worker-2#0"));
        assertFalse(all.containsKey("worker-1#0".replace("0", "x")));
        assertTrue(all.values().stream().allMatch(v -> v >= 0));
    }
}
