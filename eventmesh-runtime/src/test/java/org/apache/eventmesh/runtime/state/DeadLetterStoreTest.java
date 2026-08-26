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

import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;
import org.apache.eventmesh.runtime.cluster.MetaStore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sub-PR A baseline: the {@link DeadLetterStore} interface is the durable ledger of confirmed
 * dead-lettered deliveries. This test verifies the idempotent Meta-backed record (the production
 * implementation is built in Sub-PR C; this test only asserts the contract that any
 * implementation must satisfy against the in-process {@link InMemoryMetaStore} semantics).
 *
 * <p>The test uses a tiny inline implementation backed by {@code InMemoryMetaStore} so that
 * Sub-PR A does not pre-commit the production backing.</p>
 */
class DeadLetterStoreTest {

    /**
     * Minimal in-process implementation sufficient for the contract test. The production
     * implementation (Sub-PR C) replaces this with a Meta CAS-backed ledger under
     * {@code /em/dlq/<deliveryId>}.
     */
    static final class InProcessDeadLetterStore implements DeadLetterStore {
        private final MetaStore meta;
        private static final String PREFIX = "/em/dlq/";

        InProcessDeadLetterStore(MetaStore meta) {
            this.meta = meta;
        }

        private static String key(String deliveryId) {
            return PREFIX + deliveryId;
        }

        @Override
        public boolean recordDeadLetter(String deliveryId, String dlqTopic, long dlqOffset) {
            String value = dlqTopic + ":" + dlqOffset;
            // First-write-wins: try putIfAbsent; on already-present key, treat as success.
            if (meta.get(key(deliveryId)) != null) {
                return true;
            }
            return meta.putIfAbsent(key(deliveryId), value);
        }

        @Override
        public boolean isDeadLettered(String deliveryId) {
            return meta.get(key(deliveryId)) != null;
        }

        @Override
        public void flush() { /* no buffered writes */ }

        @Override
        public void close() { /* nothing to release */ }
    }

    @Test
    void recordOnceThenCheckIsDeadLettered() {
        DeadLetterStore store = new InProcessDeadLetterStore(new InMemoryMetaStore());
        assertFalse(store.isDeadLettered("d-1"));
        assertTrue(store.recordDeadLetter("d-1", "topic_DLQ", 42L));
        assertTrue(store.isDeadLettered("d-1"));
    }

    @Test
    void recordIsIdempotent() {
        DeadLetterStore store = new InProcessDeadLetterStore(new InMemoryMetaStore());
        assertTrue(store.recordDeadLetter("d-1", "topic_DLQ", 1L));
        assertTrue(store.recordDeadLetter("d-1", "topic_DLQ", 999L),
            "second recordDeadLetter for the same deliveryId must return true (idempotent)");
    }
}
