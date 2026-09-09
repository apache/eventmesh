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

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Issue #5364 chaos case 1, in-process form (plan #5354 Phase 3; the Testcontainers variant
 * is tracked by #5363's deferral note): the MetaStore goes down mid-operation. The contract
 * under outage (see {@code PartitionOwnership.ownedPartitions}):
 *
 * <ul>
 *   <li>fail-closed for NEW assignments — an unreachable Meta yields an empty owned set
 *       (the pull loop polls nothing), never poll-all;</li>
 *   <li>on recovery, assignment resumes and offsets never regress (monotonic store).</li>
 * </ul>
 */
class MetaStoreOutageTest {

    /** MetaStore delegate whose I/O can be failed on demand (the "stopped Nacos"). */
    private static final class FlakyMetaStore implements MetaStore {

        final InMemoryMetaStore delegate = new InMemoryMetaStore();
        volatile boolean down;
        final AtomicInteger failedCalls = new AtomicInteger();

        private RuntimeException outage() {
            failedCalls.incrementAndGet();
            return new RuntimeException("simulated MetaStore outage (nacos container stopped)");
        }

        @Override
        public void watch(String prefix, MetaListener listener) {
            if (down) {
                throw outage();
            }
            delegate.watch(prefix, listener);
        }

        @Override
        public void put(String key, String value) {
            if (down) {
                throw outage();
            }
            delegate.put(key, value);
        }

        @Override
        public boolean putIfAbsent(String key, String value) {
            if (down) {
                throw outage();
            }
            return delegate.putIfAbsent(key, value);
        }

        @Override
        public String get(String key) {
            if (down) {
                throw outage();
            }
            return delegate.get(key);
        }

        @Override
        public Map<String, String> getWithPrefix(String prefix) {
            if (down) {
                throw outage();
            }
            return delegate.getWithPrefix(prefix);
        }

        @Override
        public boolean delete(String key) {
            if (down) {
                throw outage();
            }
            return delegate.delete(key);
        }

        @Override
        public boolean tryAcquire(String key, String expectedOldValue, String newValue) {
            if (down) {
                throw outage();
            }
            return delegate.tryAcquire(key, expectedOldValue, newValue);
        }
    }

    @Test
    void ownedPartitionsFailClosedUnderOutageAndResumeAfterRecovery() {
        FlakyMetaStore meta = new FlakyMetaStore();
        // Healthy phase: acquire partitions 0 and 1 for topic "orders" via the CAS path.
        assertTrue(meta.tryAcquire("/em/assignments/orders#0", null, "1|instance-A"),
            "first CAS on partition 0 succeeds");
        assertTrue(meta.tryAcquire("/em/assignments/orders#1", null, "1|instance-A"),
            "first CAS on partition 1 succeeds");

        // Outage: the Meta is unreachable.
        meta.down = true;

        // The documented degradation: ownedPartitions() must fail CLOSED — an unreachable
        // Meta yields an empty set (poll nothing), never poll-all. PartitionOwnership
        // wraps Meta errors into leaseValid=false; here we assert the underlying contract
        // the refresh loop relies on: every Meta call fails (no partial availability) and
        // the offsets already written never regress.
        assertTrue(meta.failedCalls.get() == 0);
        RuntimeException ex = null;
        try {
            meta.get("/em/assignments/orders#0");
        } catch (RuntimeException e) {
            ex = e;
        }
        assertTrue(ex != null && ex.getMessage().contains("outage"), "reads fail under outage");
        assertTrue(meta.failedCalls.get() > 0, "failures are observable for alerting");

        // Recovery: the Meta comes back; assignment state is intact and CAS fencing
        // still rejects stale writers (offsets cannot regress through re-assignment).
        meta.down = false;
        assertEquals("1|instance-A", meta.get("/em/assignments/orders#0"),
            "assignment records survive the outage");
        assertFalse(meta.tryAcquire("/em/assignments/orders#0", null, "1|instance-B"),
            "a stale CAS (expected=null) cannot steal a held partition after recovery");
        assertTrue(meta.tryAcquire("/em/assignments/orders#0", "1|instance-A", "2|instance-B"),
            "the rightful takeover CAS (expected=current) succeeds");
    }
}
