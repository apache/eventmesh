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

package org.apache.eventmesh.runtime.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.state.DeliveryStateStore.Record;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Sub-PR B baseline: the {@link DeliveryStateStore} interface is the contract for crash-recovery
 * state, and any backing implementation must satisfy these contract tests. The in-memory
 * implementation is the reference; the RocksDB implementation is verified in
 * {@code rocksDbContract} (the same harness).
 */
class DeliveryStateStoreTest {

    @Test
    void inMemoryContract() {
        InMemoryDeliveryStateStore store = new InMemoryDeliveryStateStore();
        contract(store);
        store.close();
    }

    @Test
    void rocksDbContract(@TempDir Path tmp) throws Exception {
        Path dbDir = Files.createDirectory(tmp.resolve("delivery-state"));
        RocksDBDeliveryStateStore store = new RocksDBDeliveryStateStore(dbDir.toString());
        contract(store);
        store.close();
    }

    @Test
    void rocksDbPersistsAcrossClose(@TempDir Path tmp) throws Exception {
        Path dbDir = Files.createDirectory(tmp.resolve("delivery-state-persist"));
        String deliveryId = "persist-1";

        // Open, write, close
        RocksDBDeliveryStateStore first = new RocksDBDeliveryStateStore(dbDir.toString());
        Record rec = newRecord(deliveryId, "topic-A", 0, 100L, "client-X", 3, 12345L);
        first.put(rec);
        first.flush();
        first.close();

        // Reopen and verify the record is still there
        RocksDBDeliveryStateStore second = new RocksDBDeliveryStateStore(dbDir.toString());
        Record got = second.get(deliveryId);
        assertNotNull(got, "record must survive close+reopen");
        assertEquals("topic-A", got.topic);
        assertEquals(0, got.partition);
        assertEquals(100L, got.offset);
        assertEquals("client-X", got.clientId);
        assertEquals(3, got.attempt);
        assertEquals(12345L, got.nextAttemptAtMs);
        assertEquals(1, second.count());
        second.close();
    }

    private static void contract(DeliveryStateStore store) {
        // put + get round-trip
        Record a = newRecord("d-1", "topic-A", 0, 100L, "client-1", 1, 1000L);
        store.put(a);
        Record got = store.get("d-1");
        assertNotNull(got, "put then get must return the same record");
        assertEquals("d-1", got.deliveryId);
        assertEquals("topic-A", got.topic);
        assertEquals(0, got.partition);
        assertEquals(100L, got.offset);
        assertEquals("client-1", got.clientId);
        assertEquals(1, got.attempt);
        assertEquals(1000L, got.nextAttemptAtMs);

        // overwrite (last-writer-wins)
        Record aUpdated = newRecord("d-1", "topic-A", 0, 200L, "client-1", 2, 2000L);
        store.put(aUpdated);
        Record gotUpdated = store.get("d-1");
        assertEquals(2, gotUpdated.attempt, "put overwrites; dispatcher uses putIfAbsent to guard races");

        // missing
        assertNull(store.get("not-here"), "get on unknown id must return null");

        // count
        store.put(newRecord("d-2", "topic-B", 1, 50L, "client-2", 1, 500L));
        assertEquals(2, store.count());

        // remove
        assertTrue(store.remove("d-1"));
        assertNull(store.get("d-1"), "removed record must be gone");
        assertEquals(1, store.count());

        // remove is idempotent
        assertTrue(store.remove("d-1"), "second remove of same id is a no-op and returns true");
        assertTrue(store.remove("never-existed"), "remove of never-existed id returns true");

        // iterate
        store.put(newRecord("d-3", "topic-C", 2, 10L, "client-3", 1, 100L));
        List<String> seen = new ArrayList<>();
        store.iterate(r -> seen.add(r.deliveryId));
        assertTrue(seen.contains("d-2"), "iterate must visit d-2");
        assertTrue(seen.contains("d-3"), "iterate must visit d-3");
        assertFalse(seen.contains("d-1"), "iterate must NOT visit removed d-1");

        // iterate over a snapshot can remove via the visitor (recovery path)
        AtomicInteger removed = new AtomicInteger();
        store.iterate(r -> {
            if (store.remove(r.deliveryId)) {
                removed.incrementAndGet();
            }
        });
        assertEquals(2, removed.get(), "visitor-driven remove must work for all records");
        assertEquals(0, store.count());
    }

    @Test
    void closedStoreRejectsMutations() {
        InMemoryDeliveryStateStore store = new InMemoryDeliveryStateStore();
        store.close();
        assertThrows(IllegalStateException.class, () -> store.put(newRecord("x", "t", 0, 0L, "c", 1, 0L)));
        assertThrows(IllegalStateException.class, () -> store.remove("x"));
    }

    @Test
    void recordToDeliveryRoundTrip() {
        // The record.toDelivery() helper is what recover() uses to walk the ledger
        EventMeshFrame event = EventMeshFrame.event(java.util.Map.of("k", "v"), "hello".getBytes());
        Record rec = new Record("d-X", "topic", 3, 999L, "client", 4, 5555L, event.encode());
        // The Record constructor used here is the public one (encodedEvent). Mirror the production path:
        assertEquals("d-X", rec.deliveryId);
        assertEquals(3, rec.partition);
        // toDelivery rebuilds the Delivery
        org.apache.eventmesh.runtime.delivery.Delivery d = rec.toDelivery();
        assertEquals("d-X", d.getDeliveryId());
        assertEquals(3, d.getPartition());
        assertEquals(999L, d.getOffset());
        assertEquals("client", d.getClientId());
        assertEquals(4, d.getAttempt());
        assertEquals(5555L, d.getNextAttemptAtMs());
        assertNull(d.getChannel(), "recovered record has no live channel");
    }

    private static Record newRecord(String deliveryId, String topic, int partition, long offset,
        String clientId, int attempt, long nextAttemptAtMs) {
        EventMeshFrame event = EventMeshFrame.event(java.util.Map.of(), null);
        return new Record(deliveryId, topic, partition, offset, clientId, attempt, nextAttemptAtMs,
            event.encode());
    }
}
