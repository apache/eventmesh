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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OffsetStoreTest {

    @Test
    void inMemoryContract() {
        InMemoryOffsetStore store = new InMemoryOffsetStore();
        contract(store);
        store.close();
    }

    @Test
    void rocksdbContract(@TempDir Path tmp) throws Exception {
        Path dbDir = Files.createDirectory(tmp.resolve("offsets"));
        RocksDBOffsetStore store = new RocksDBOffsetStore(dbDir.toString());
        contract(store);
        store.close();
    }

    /**
     * A RocksDB store persists across close/reopen, so a restarted subscriber resumes with no replay.
     */
    @Test
    void rocksdbSurvivesRestart(@TempDir Path tmp) throws Exception {
        Path dbDir = Files.createDirectory(tmp.resolve("offsets"));

        RocksDBOffsetStore first = new RocksDBOffsetStore(dbDir.toString());
        first.writeOffset("orders", "worker-1", 0, 42L);
        first.writeOffset("orders", "worker-2", 1, 7L);
        first.flush();
        first.close();

        RocksDBOffsetStore reopened = new RocksDBOffsetStore(dbDir.toString());
        assertEquals(42L, reopened.readOffset("orders", "worker-1", 0));
        assertEquals(7L, reopened.readOffset("orders", "worker-2", 1));
        reopened.close();
    }

    /**
     * Shared contract every OffsetStore must satisfy.
     */
    private void contract(OffsetStore store) {
        // Unknown offset reads as -1.
        assertEquals(-1L, store.readOffset("orders", "worker-1", 0));

        // Write then read is consistent.
        store.writeOffset("orders", "worker-1", 0, 10L);
        store.writeOffset("orders", "worker-1", 1, 11L);
        store.writeOffset("orders", "worker-2", 0, 99L);
        // A different topic must not bleed across.
        store.writeOffset("payments", "worker-1", 0, 5L);

        assertEquals(10L, store.readOffset("orders", "worker-1", 0));
        assertEquals(11L, store.readOffset("orders", "worker-1", 1));
        assertEquals(99L, store.readOffset("orders", "worker-2", 0));
        assertEquals(-1L, store.readOffset("orders", "worker-3", 0));

        // readAllOffsets returns only this topic's entries, keyed by clientId#partition.
        java.util.Map<String, Long> all = store.readAllOffsets("orders");
        assertEquals(3, all.size());
        assertEquals(10L, all.get("worker-1#0"));
        assertEquals(11L, all.get("worker-1#1"));
        assertEquals(99L, all.get("worker-2#0"));
        assertFalse(all.containsKey("worker-1#0".replace("0", "x")));
        assertTrue(all.values().stream().allMatch(v -> v >= 0));

        // readAllTopics returns the set of topics with persisted offsets.
        java.util.Set<String> topics = store.readAllTopics();
        assertTrue(topics.contains("orders"));
        assertTrue(topics.contains("payments"));
        assertEquals(2, topics.size());
    }

    /**
     * readAllTopics on an empty store returns an empty set (first-run scenario).
     */
    @Test
    void emptyStoreReturnsEmptyTopicSet() {
        InMemoryOffsetStore store = new InMemoryOffsetStore();
        assertTrue(store.readAllTopics().isEmpty());
        store.close();
    }
}
