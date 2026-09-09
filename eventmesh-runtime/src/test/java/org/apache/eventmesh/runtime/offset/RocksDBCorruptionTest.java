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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Issue #5364 chaos case 4, in-process form (plan #5354 Phase 3): a corrupted offset store
 * must refuse to start with the documented error instead of silently re-creating an empty
 * database (which would reset every offset and re-deliver from the beginning).
 */
class RocksDBCorruptionTest {

    @TempDir
    Path dir;

    @Test
    void corruptedDataDirRefusesToStartWithDocumentedError() throws Exception {
        // Healthy store with one durable offset.
        Path dbDir = dir.resolve("offsets");
        RocksDBOffsetStore store = new RocksDBOffsetStore(dbDir.toString());
        assertTrue(store.writeOffset("orders", "client-1", 0, 42L));
        store.flush();
        store.close();

        // Corrupt the SST file with garbage (the "overwritten SST" scenario).
        try (Stream<Path> files = Files.walk(dbDir)) {
            files.filter(f -> f.getFileName().toString().endsWith(".sst"))
                .findFirst()
                .ifPresent(sst -> {
                    try {
                        Files.write(sst, new byte[] {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF});
                    } catch (Exception ignored) {
                        // test environment issue; the assert below will surface it
                    }
                });
        }

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> new RocksDBOffsetStore(dbDir.toString()),
            "a corrupted store must refuse to start");
        assertTrue(ex.getMessage().contains("failed to open RocksDB offset store"),
            "the error names the store, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("restore from backup") || ex.getMessage().contains("wipe"),
            "the error documents the remediation, got: " + ex.getMessage());
    }

    @Test
    void healthyStoreRoundTripsOffsets() throws Exception {
        // Control case: the same flow without corruption works end to end.
        Path dbDir = dir.resolve("healthy");
        RocksDBOffsetStore store = new RocksDBOffsetStore(dbDir.toString());
        assertTrue(store.writeOffset("orders", "client-1", 0, 7L));
        store.flush();
        assertEquals(7L, store.readOffset("orders", "client-1", 0));
        store.close();

        // Reopen: the offset survives a restart (the durability baseline the corruption
        // case protects).
        RocksDBOffsetStore reopened = new RocksDBOffsetStore(dbDir.toString());
        assertEquals(7L, reopened.readOffset("orders", "client-1", 0),
            "offsets survive a clean restart");
        reopened.close();
        cleanup(dbDir);
    }

    private static void cleanup(Path p) throws Exception {
        if (Files.exists(p)) {
            try (Stream<Path> s = Files.walk(p)) {
                s.sorted(Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
            }
        }
    }
}
