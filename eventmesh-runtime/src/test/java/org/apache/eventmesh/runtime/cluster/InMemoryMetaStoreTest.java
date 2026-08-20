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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link InMemoryMetaStore#tryAcquire(String, String, String)} — the atomic CAS
 * that backs partition fencing (§13.2.8④).
 */
class InMemoryMetaStoreTest {

    @Test
    void tryAcquireOnAbsentKeySucceeds() {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        assertTrue(meta.tryAcquire("k1", null, "v1"));
        assertEquals("v1", meta.get("k1"));
    }

    @Test
    void tryAcquireAbsentFailsWhenKeyExists() {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        meta.put("k1", "v1");
        assertFalse(meta.tryAcquire("k1", null, "v2"));
        assertEquals("v1", meta.get("k1"), "value must be unchanged on CAS failure");
    }

    @Test
    void tryAcquireReplacesOnExactMatch() {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        meta.put("k1", "v1");
        assertTrue(meta.tryAcquire("k1", "v1", "v2"));
        assertEquals("v2", meta.get("k1"));
    }

    @Test
    void tryAcquireFailsOnValueMismatch() {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        meta.put("k1", "v1");
        assertFalse(meta.tryAcquire("k1", "wrong", "v2"));
        assertEquals("v1", meta.get("k1"), "value must be unchanged on CAS failure");
    }

    @Test
    void tryAcquireFailsOnNullExpectedButKeyPresent() {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        meta.put("k1", "existing");
        assertFalse(meta.tryAcquire("k1", null, "new"));
    }

    @Test
    void tryAcquireIsAtomicUnderConcurrency() throws Exception {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        int n = 20;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        AtomicInteger winners = new AtomicInteger(0);

        for (int i = 0; i < n; i++) {
            final int idx = i;
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    if (meta.tryAcquire("race-key", null, "v" + idx)) {
                        winners.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            t.setDaemon(true);
            t.start();
        }

        start.countDown();
        done.await();

        assertEquals(1, winners.get(), "exactly one thread must win the CAS");
    }

    @Test
    void tryAcquireNotifiesListenersOnChange() {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        java.util.concurrent.atomic.AtomicReference<String> seen = new java.util.concurrent.atomic.AtomicReference<>();
        meta.watch("/em/assignments/", (key, value, deleted) -> seen.set(value));

        meta.tryAcquire("/em/assignments/test#0", null, "token1|instanceA");
        assertEquals("token1|instanceA", seen.get());
    }
}
