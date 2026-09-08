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

package org.apache.eventmesh.runtime.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;
import org.apache.eventmesh.runtime.state.MetaBackedTaskStore;
import org.apache.eventmesh.runtime.state.TaskStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * Issue #5340 D2a: TaskExpirer reaper unit tests. Covers the four acceptance
 * properties from the issue body:
 *
 * <ol>
 *   <li>idle tasks past the TTL are evicted;</li>
 *   <li>fresh tasks are NOT evicted;</li>
 *   <li>the listener is invoked with the evicted taskIds;</li>
 *   <li>multiple scans accumulate the totalExpired counter.</li>
 * </ol>
 */
class TaskExpirerTest {

    /** Wait long enough for {@link TaskStore.TaskRecord#updatedAtMs} to be older than the TTL. */
    private static void advanceClock(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }

    @Test
    void evictsIdleTasksPastTtl() throws Exception {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        TaskStore store = new MetaBackedTaskStore(meta);
        store.createTask("stale-1", "agent-A", "client-X", "{}");
        store.createTask("stale-2", "agent-A", "client-X", "{}");
        store.createTask("fresh-1", "agent-B", "client-Y", "{}");
        // Use a 200ms TTL so the test does not need to sleep for 24h.
        TaskExpirer reaper = new TaskExpirer(store, 200L, 60_000L, null);
        // No task is past the TTL yet.
        assertEquals(0, reaper.scan().size(),
            "no task is past the 200ms TTL immediately after creation");
        advanceClock(250);
        // The reaper is configured with a 60s scan interval; we drive scan() by
        // hand so the test does not have to wait. The two stale-* tasks are now
        // past the TTL; fresh-1 is too (the TTL is from createdAtMs/updateAtMs,
        // which the production MetaBackedTaskStore stamps with the current
        // wall clock at createTask). All three should be evicted in this case
        // because the test's TTL is shorter than the wait.
        List<String> evicted = reaper.scan();
        assertEquals(3, evicted.size(),
            "all three tasks are past the 200ms TTL after 250ms");
        assertNull(store.getTask("stale-1"));
        assertNull(store.getTask("stale-2"));
        assertNull(store.getTask("fresh-1"));
        assertEquals(3L, reaper.getTotalExpired());
    }

    @Test
    void freshTasksAreNotEvicted() throws Exception {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        TaskStore store = new MetaBackedTaskStore(meta);
        // TTL of 10 seconds; the test runs in milliseconds, so nothing is past the TTL.
        TaskExpirer reaper = new TaskExpirer(store, 10_000L, 60_000L, null);
        store.createTask("t-1", "agent-A", "client-X", "{}");
        store.createTask("t-2", "agent-A", "client-X", "{}");
        assertEquals(0, reaper.scan().size(),
            "no task is past the 10s TTL immediately after creation");
        assertNotNull(store.getTask("t-1"));
        assertNotNull(store.getTask("t-2"));
        assertEquals(0L, reaper.getTotalExpired());
    }

    @Test
    void listenerReceivesEvictedTaskIds() throws Exception {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        TaskStore store = new MetaBackedTaskStore(meta);
        AtomicReference<List<String>> lastEvicted = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        store.createTask("a", "agent-A", "client-X", "{}");
        store.createTask("b", "agent-A", "client-X", "{}");
        TaskExpirer reaper = new TaskExpirer(store, 100L, 60_000L, ids -> {
            lastEvicted.set(new ArrayList<>(ids));
            calls.incrementAndGet();
        });
        advanceClock(150);
        reaper.scan();
        assertEquals(1, calls.get(), "listener was called exactly once");
        assertNotNull(lastEvicted.get());
        assertEquals(2, lastEvicted.get().size(),
            "listener received the two evicted taskIds");
        assertTrue(lastEvicted.get().contains("a"));
        assertTrue(lastEvicted.get().contains("b"));
    }

    @Test
    void startAndShutdownAreIdempotent() throws Exception {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        TaskStore store = new MetaBackedTaskStore(meta);
        TaskExpirer reaper = new TaskExpirer(store, 200L, 50L, null);
        assertFalse(reaper.isRunning());
        reaper.start();
        assertTrue(reaper.isRunning());
        // A second start is a no-op (idempotent).
        reaper.start();
        assertTrue(reaper.isRunning());
        reaper.shutdown();
        assertFalse(reaper.isRunning());
        // Shutdown when already stopped is a no-op.
        reaper.shutdown();
        assertFalse(reaper.isRunning());
    }

    @Test
    void backgroundScanFiresOnSchedule() throws Exception {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        TaskStore store = new MetaBackedTaskStore(meta);
        CountDownLatch evictionLatch = new CountDownLatch(1);
        TaskExpirer reaper = new TaskExpirer(store, 100L, 50L, ids -> {
            if (!ids.isEmpty()) {
                evictionLatch.countDown();
            }
        });
        store.createTask("bg-1", "agent-A", "client-X", "{}");
        reaper.start();
        try {
            // The reaper schedules the first scan after one interval (50ms), then
            // every 50ms. After ~150ms the task is past the TTL and the scan
            // evicts it. We give the scheduler a generous 2s budget.
            assertTrue(evictionLatch.await(2, TimeUnit.SECONDS),
                "background scan fired within 2s and evicted bg-1");
            assertNull(store.getTask("bg-1"));
        } finally {
            reaper.shutdown();
        }
    }

    @Test
    void invalidTtlThrows() {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        TaskStore store = new MetaBackedTaskStore(meta);
        assertThrows(IllegalArgumentException.class,
            () -> new TaskExpirer(store, 0L, 1000L, null));
        assertThrows(IllegalArgumentException.class,
            () -> new TaskExpirer(store, -1L, 1000L, null));
        assertThrows(IllegalArgumentException.class,
            () -> new TaskExpirer(store, 1000L, 0L, null));
        assertThrows(NullPointerException.class,
            () -> new TaskExpirer(null));
    }
}
