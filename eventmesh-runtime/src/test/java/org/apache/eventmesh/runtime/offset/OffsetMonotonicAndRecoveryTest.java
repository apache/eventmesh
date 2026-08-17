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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the P2/P4 offset fixes: monotonic writeOffset (only advance, never regress), and
 * multi-client offset independence under restart/replay scenarios.
 */
class OffsetMonotonicAndRecoveryTest {

    @Test
    void inMemoryWriteOffsetIsMonotonic() {
        InMemoryOffsetStore store = new InMemoryOffsetStore();
        monotonicContract(store);
        store.close();
    }

    @Test
    void rocksdbWriteOffsetIsMonotonic(@TempDir Path tmp) throws Exception {
        Path dbDir = Files.createDirectory(tmp.resolve("offsets"));
        RocksDBOffsetStore store = new RocksDBOffsetStore(dbDir.toString());
        monotonicContract(store);
        store.close();
    }

    /**
     * P4: writeOffset must be monotonic — a lower offset never overwrites a higher one.
     * Without this, a slow group's replay (after restart) would regress a fast group's progress.
     */
    private void monotonicContract(OffsetStore store) {
        // Write offset 100.
        store.writeOffset("orders", "group-A", 0, 100L);
        assertEquals(100L, store.readOffset("orders", "group-A", 0));

        // Attempt to write offset 10 (simulating slow-group replay overwriting).
        store.writeOffset("orders", "group-A", 0, 10L);
        assertEquals(100L, store.readOffset("orders", "group-A", 0),
            "offset must NOT regress from 100 to 10");

        // Write 200 — should advance.
        store.writeOffset("orders", "group-A", 0, 200L);
        assertEquals(200L, store.readOffset("orders", "group-A", 0));

        // Write 200 again — idempotent, stays at 200.
        store.writeOffset("orders", "group-A", 0, 200L);
        assertEquals(200L, store.readOffset("orders", "group-A", 0));
    }

    @Test
    void inMemoryMultiClientIndependence() {
        InMemoryOffsetStore store = new InMemoryOffsetStore();
        multiClientContract(store);
        store.close();
    }

    @Test
    void rocksdbMultiClientIndependence(@TempDir Path tmp) throws Exception {
        Path dbDir = Files.createDirectory(tmp.resolve("offsets"));
        RocksDBOffsetStore store = new RocksDBOffsetStore(dbDir.toString());
        multiClientContract(store);
        store.close();
    }

    /**
     * Each clientId's offset is independent — one group's ACK progress never affects another's.
     */
    private void multiClientContract(OffsetStore store) {
        store.writeOffset("topic", "client-A", 0, 50L);
        store.writeOffset("topic", "client-B", 0, 80L);
        store.writeOffset("topic", "client-C", 0, 10L);

        assertEquals(50L, store.readOffset("topic", "client-A", 0));
        assertEquals(80L, store.readOffset("topic", "client-B", 0));
        assertEquals(10L, store.readOffset("topic", "client-C", 0));

        // Client-A advances to 60 — B and C unaffected.
        store.writeOffset("topic", "client-A", 0, 60L);
        assertEquals(80L, store.readOffset("topic", "client-B", 0));
        assertEquals(10L, store.readOffset("topic", "client-C", 0));
    }

    @Test
    void rocksdbMonotonicAcrossRestart(@TempDir Path tmp) throws Exception {
        Path dbDir = Files.createDirectory(tmp.resolve("offsets"));
        RocksDBOffsetStore store = new RocksDBOffsetStore(dbDir.toString());
        store.writeOffset("topic", "client-A", 0, 100L);
        store.flush();
        store.close();

        // Reopen — the stored offset should be 100, not regressed.
        RocksDBOffsetStore reopened = new RocksDBOffsetStore(dbDir.toString());
        assertEquals(100L, reopened.readOffset("topic", "client-A", 0));

        // After restart, writing a lower offset (replay) should NOT regress.
        reopened.writeOffset("topic", "client-A", 0, 50L);
        assertEquals(100L, reopened.readOffset("topic", "client-A", 0),
            "offset must NOT regress across restart + replay");
        reopened.close();
    }

    @Test
    void inMemoryUnknownOffsetReturnsNegativeOne() {
        InMemoryOffsetStore store = new InMemoryOffsetStore();
        assertEquals(-1L, store.readOffset("unknown", "nobody", 0));
        // After write, no longer -1.
        store.writeOffset("unknown", "nobody", 0, 1L);
        assertTrue(store.readOffset("unknown", "nobody", 0) >= 0);
        store.close();
    }
}
