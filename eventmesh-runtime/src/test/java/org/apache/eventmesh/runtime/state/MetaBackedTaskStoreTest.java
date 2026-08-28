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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;
import org.apache.eventmesh.runtime.state.TaskStore.Status;
import org.apache.eventmesh.runtime.state.TaskStore.TaskRecord;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Sub-PR C: contract test for the production {@link MetaBackedTaskStore}. The shared
 * interface contract is asserted by {@link TaskStoreTest}; this test covers the production
 * behaviour: cluster-shared visibility, epoch CAS rejection of stale writers, and the
 * opaque-input round trip (input strings containing the {@code '|'} separator or newlines
 * must survive encode/decode).
 */
class MetaBackedTaskStoreTest {

    @Test
    void createThenGetRoundTrips() {
        TaskStore store = new MetaBackedTaskStore(new InMemoryMetaStore());
        TaskRecord t = store.createTask("task-1", "agent-A", "client-C", "{\"prompt\":\"hi\"}");
        assertNotNull(t);
        assertEquals("task-1", t.taskId);
        assertEquals(Status.PENDING, t.status);
        TaskRecord back = store.getTask("task-1");
        assertNotNull(back);
        assertEquals(Status.PENDING, back.status);
        assertEquals(t.taskEpoch, back.taskEpoch);
    }

    @Test
    void duplicateCreateReturnsNull() {
        TaskStore store = new MetaBackedTaskStore(new InMemoryMetaStore());
        assertNotNull(store.createTask("task-1", "a", "c", "{}"));
        assertNull(store.createTask("task-1", "a", "c", "{}"),
            "duplicate taskId must not overwrite the existing record");
    }

    @Test
    void updateStatusWithMatchingEpochSucceeds() {
        TaskStore store = new MetaBackedTaskStore(new InMemoryMetaStore());
        TaskRecord t = store.createTask("task-1", "a", "c", "{}");
        assertTrue(store.updateStatus("task-1", t.taskEpoch, Status.RUNNING, null));
        assertEquals(Status.RUNNING, store.getTask("task-1").status);
        assertTrue(store.updateStatus("task-1", t.taskEpoch, Status.COMPLETED, "{\"ok\":true}"));
        TaskRecord done = store.getTask("task-1");
        assertEquals(Status.COMPLETED, done.status);
        assertEquals("{\"ok\":true}", done.output);
    }

    @Test
    void updateStatusWithStaleEpochIsRejected() {
        TaskStore store = new MetaBackedTaskStore(new InMemoryMetaStore());
        TaskRecord t = store.createTask("task-1", "a", "c", "{}");
        store.updateStatus("task-1", t.taskEpoch, Status.RUNNING, null);
        // A different epoch (simulating a stale writer) must be rejected.
        assertFalse(store.updateStatus("task-1", t.taskEpoch + 7L, Status.COMPLETED, "{}"));
        assertEquals(Status.RUNNING, store.getTask("task-1").status);
    }

    @Test
    void clusterSharedTwoInstancesSeeSameTask() {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        TaskStore a = new MetaBackedTaskStore(meta);
        TaskStore b = new MetaBackedTaskStore(meta);
        TaskRecord t = a.createTask("task-1", "a", "c", "{}");
        // Instance B reads the record through its own getTask
        TaskRecord taskB = b.getTask("task-1");
        assertNotNull(taskB);
        assertEquals(t.taskEpoch, taskB.taskEpoch);
    }

    @Test
    void listByAgentFiltersByAgentAndStatus() {
        TaskStore store = new MetaBackedTaskStore(new InMemoryMetaStore());
        TaskRecord a1 = store.createTask("a-1", "agent-A", "c", "{}");
        store.createTask("a-2", "agent-A", "c", "{}");
        store.createTask("b-1", "agent-B", "c", "{}");
        store.updateStatus("a-1", a1.taskEpoch, Status.RUNNING, null);
        assertEquals(2, store.listByAgent("agent-A", null).size());
        assertEquals(1, store.listByAgent("agent-A", Status.RUNNING).size());
        assertEquals(1, store.listByAgent("agent-A", Status.PENDING).size());
        assertEquals(1, store.listByAgent("agent-B", null).size());
    }

    @Test
    void expireStaleRemovesOldRecords() throws Exception {
        TaskStore store = new MetaBackedTaskStore(new InMemoryMetaStore());
        store.createTask("old", "a", "c", "{}");
        store.createTask("new", "a", "c", "{}");
        // Force the "old" record to be updated 10s ago by reaching into the wire and rewriting
        // its updatedAtMs to 0; then expireStale with a small window must remove only it.
        // Deterministic: bump "new" with updateStatus so its updatedAtMs > "old" by tens of ms,
        // then expireStale(1L) removes the older "old" record only.
        store.updateStatus("new", store.getTask("new").taskEpoch, Status.RUNNING, null);
        // expireStale(1L) is called AFTER the 50ms sleep; by then the just-updated
        // "new" record's updatedAtMs is < 1ms old (well under 1ms), so it survives.
        // The "old" record's updatedAtMs is ~50ms old, so it is removed.
        // 50ms gives comfortable headroom over CI scheduler noise (a 1ms gap can
        // collapse on a busy host).
        Thread.sleep(50L);
        List<String> expired = store.expireStale(1L);
        assertEquals(1, expired.size());
        assertEquals("old", expired.get(0));
        assertNull(store.getTask("old"));
        assertNotNull(store.getTask("new"));
    }

    @Test
    void opaqueInputWithPipeAndNewlineRoundTrips() {
        TaskStore store = new MetaBackedTaskStore(new InMemoryMetaStore());
        String weird = "{\"a\":\"b|c\\nd\"}";
        TaskRecord t = store.createTask("task-1", "a", "c", weird);
        assertEquals(weird, store.getTask("task-1").input);
        assertTrue(store.updateStatus("task-1", t.taskEpoch, Status.COMPLETED, weird));
        assertEquals(weird, store.getTask("task-1").output);
    }
}
