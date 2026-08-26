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

import org.apache.eventmesh.runtime.state.TaskStore.Status;
import org.apache.eventmesh.runtime.state.TaskStore.TaskRecord;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Sub-PR A baseline: the {@link TaskStore} interface is the contract for A2A task state. This
 * test asserts the data-model semantics (idempotent create, epoch-protected status update,
 * list/expire filters) that any backing implementation must satisfy.
 *
 * <p>The test uses an in-process implementation that satisfies the contract from a
 * {@code ConcurrentHashMap} — the production Meta-backed implementation lands in Sub-PR C.</p>
 */
class TaskStoreTest {

    static final class InProcessTaskStore implements TaskStore {
        private final java.util.concurrent.ConcurrentHashMap<String, TaskRecord> table =
            new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.concurrent.atomic.AtomicLong epoch = new java.util.concurrent.atomic.AtomicLong();

        @Override
        public TaskRecord createTask(String taskId, String agentId, String clientId, String input) {
            long now = System.currentTimeMillis();
            long e = epoch.incrementAndGet();
            TaskRecord rec = new TaskRecord(taskId, agentId, clientId, Status.PENDING, now, now, input, null, e);
            TaskRecord prior = table.putIfAbsent(taskId, rec);
            return prior == null ? rec : null;
        }

        @Override
        public TaskRecord getTask(String taskId) {
            return table.get(taskId);
        }

        @Override
        public boolean updateStatus(String taskId, long expectedTaskEpoch, Status newStatus, String output) {
            TaskRecord rec = table.get(taskId);
            if (rec == null || rec.taskEpoch != expectedTaskEpoch) {
                return false;
            }
            rec.status = newStatus;
            rec.updatedAtMs = System.currentTimeMillis();
            rec.output = output;
            return true;
        }

        @Override
        public List<TaskRecord> listByAgent(String agentId, Status statusFilter) {
            return table.values().stream()
                .filter(r -> r.agentId.equals(agentId))
                .filter(r -> statusFilter == null || r.status == statusFilter)
                .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public List<String> expireStale(long olderThanMs) {
            long deadline = System.currentTimeMillis() - olderThanMs;
            List<String> expired = new java.util.ArrayList<>();
            for (TaskRecord r : table.values()) {
                if (r.updatedAtMs < deadline) {
                    expired.add(r.taskId);
                }
            }
            for (String id : expired) {
                table.remove(id);
            }
            return expired;
        }

        @Override
        public void flush() {
            /* no buffered writes */
        }

        @Override
        public void close() {
            table.clear();
        }
    }

    @Test
    void createTaskIsIdempotent() {
        TaskStore store = new InProcessTaskStore();
        TaskRecord t1 = store.createTask("task-1", "agent-A", "client-C", "{}");
        assertNotNull(t1);
        assertEquals("task-1", t1.taskId);
        assertEquals(Status.PENDING, t1.status);

        TaskRecord dup = store.createTask("task-1", "agent-A", "client-C", "{}");
        assertNull(dup, "duplicate taskId must return null");
    }

    @Test
    void updateStatusRequiresMatchingEpoch() {
        TaskStore store = new InProcessTaskStore();
        TaskRecord t1 = store.createTask("task-1", "agent-A", "client-C", "{}");

        assertTrue(store.updateStatus("task-1", t1.taskEpoch, Status.RUNNING, null));
        TaskRecord after = store.getTask("task-1");
        assertEquals(Status.RUNNING, after.status);

        // Wrong epoch must be rejected (taskEpoch is set at creation and never reset,
        // so any value other than t1.taskEpoch simulates a stale writer).
        long wrongEpoch = t1.taskEpoch + 1L;
        assertFalse(store.updateStatus("task-1", wrongEpoch, Status.COMPLETED, "{\"ok\":true}"),
            "stale epoch must not overwrite");
        assertEquals(Status.RUNNING, store.getTask("task-1").status);
    }

    @Test
    void listByAgentFiltersByStatus() {
        TaskStore store = new InProcessTaskStore();
        TaskRecord t1 = store.createTask("a-1", "agent-A", "c", "{}");
        store.createTask("a-2", "agent-A", "c", "{}");
        store.createTask("b-1", "agent-B", "c", "{}");
        store.updateStatus("a-1", t1.taskEpoch, Status.RUNNING, null);

        List<TaskRecord> allTasks = store.listByAgent("agent-A", null);
        assertEquals(2, allTasks.size());

        List<TaskRecord> runningTasks = store.listByAgent("agent-A", Status.RUNNING);
        assertEquals(1, runningTasks.size());
        assertEquals("a-1", runningTasks.get(0).taskId);

        List<TaskRecord> pendingTasks = store.listByAgent("agent-A", Status.PENDING);
        assertEquals(1, pendingTasks.size());
        assertEquals("a-2", pendingTasks.get(0).taskId);
    }
}
