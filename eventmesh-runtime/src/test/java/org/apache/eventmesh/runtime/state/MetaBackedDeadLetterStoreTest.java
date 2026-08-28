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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;

import org.junit.jupiter.api.Test;

/**
 * Sub-PR C: contract test for the production {@link MetaBackedDeadLetterStore}. The shared
 * interface contract is asserted by {@link DeadLetterStoreTest}; this test covers the
 * production behaviour: namespace prefix, value format, cluster-shared visibility, and
 * idempotency under concurrent writers.
 */
class MetaBackedDeadLetterStoreTest {

    @Test
    void recordThenIsDeadLetteredRoundTrips() {
        DeadLetterStore store = new MetaBackedDeadLetterStore(new InMemoryMetaStore());
        assertFalse(store.isDeadLettered("d-1"));
        assertTrue(store.recordDeadLetter("d-1", "topic_DLQ", 42L));
        assertTrue(store.isDeadLettered("d-1"));
    }

    @Test
    void recordIsIdempotentAcrossWriters() {
        // Two store instances against the same Meta simulate two Runtime instances racing
        // on the same deliveryId. The first writer wins; the second sees an already-present
        // key and returns true.
        InMemoryMetaStore meta = new InMemoryMetaStore();
        DeadLetterStore a = new MetaBackedDeadLetterStore(meta);
        DeadLetterStore b = new MetaBackedDeadLetterStore(meta);
        assertTrue(a.recordDeadLetter("d-1", "topic_DLQ", 1L));
        assertTrue(b.recordDeadLetter("d-1", "topic_DLQ", 999L),
            "second writer must observe the first record and return true");
        // The first writer's value (1L) is preserved; subsequent attempts do not overwrite.
        assertEquals("topic_DLQ:1", meta.get(MetaBackedDeadLetterStore.PREFIX + "d-1"));
    }

    @Test
    void keysAreNamespacedUnderEmDlq() {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        DeadLetterStore store = new MetaBackedDeadLetterStore(meta);
        store.recordDeadLetter("d-1", "topic_DLQ", 1L);
        assertNotNull(meta.get("/em/dlq/d-1"));
    }
}
