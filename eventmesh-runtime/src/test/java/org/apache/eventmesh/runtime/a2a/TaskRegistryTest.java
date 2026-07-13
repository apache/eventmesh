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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TaskRegistry}.
 */
class TaskRegistryTest {

    private TaskRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new TaskRegistry();
    }

    // =========================================================================
    // Create
    // =========================================================================

    @Test
    void testCreateTask() {
        TaskRegistry.TaskEntry entry = registry.createTask("task-1", null, "agent-a", "gw-1");
        assertNotNull(entry);
        assertEquals("task-1", entry.getTaskId());
        assertNull(entry.getParentTaskId());
        assertEquals("agent-a", entry.getTargetAgent());
        assertEquals("gw-1", entry.getGatewayId());
        assertEquals(TaskRegistry.TaskState.SUBMITTED, entry.getState());
        assertEquals(1, registry.size());
    }

    @Test
    void testCreateTaskWithParent() {
        registry.createTask("parent-1", null, "agent-a", "gw-1");
        registry.createTask("child-1", "parent-1", "agent-b", "gw-1");

        assertEquals(2, registry.size());
        assertEquals(1, registry.getChildTasks("parent-1").size());
        assertEquals("child-1", registry.getChildTasks("parent-1").get(0));
    }

    // =========================================================================
    // State transitions
    // =========================================================================

    @Test
    void testSubmittedToWorking() {
        registry.createTask("task-1", null, "agent-a", "gw-1");
        assertTrue(registry.markWorking("task-1"));
        assertEquals(TaskRegistry.TaskState.WORKING, registry.getTask("task-1").getState());
    }

    @Test
    void testWorkingToCompleted() {
        registry.createTask("task-1", null, "agent-a", "gw-1");
        registry.markWorking("task-1");
        assertTrue(registry.markCompleted("task-1", "result-data"));
        assertEquals(TaskRegistry.TaskState.COMPLETED, registry.getTask("task-1").getState());
        assertEquals("result-data", registry.getTask("task-1").getResult());
    }

    @Test
    void testWorkingToFailed() {
        registry.createTask("task-1", null, "agent-a", "gw-1");
        registry.markWorking("task-1");
        assertTrue(registry.markFailed("task-1", "something went wrong"));
        assertEquals(TaskRegistry.TaskState.FAILED, registry.getTask("task-1").getState());
        assertEquals("something went wrong", registry.getTask("task-1").getErrorMessage());
    }

    @Test
    void testSubmittedDirectlyToCompleted() {
        registry.createTask("task-1", null, "agent-a", "gw-1");
        // SUBMITTED -> COMPLETED should be allowed (skip WORKING)
        assertTrue(registry.markCompleted("task-1", "fast-result"));
        assertEquals(TaskRegistry.TaskState.COMPLETED, registry.getTask("task-1").getState());
    }

    @Test
    void testSubmittedToCancelled() {
        registry.createTask("task-1", null, "agent-a", "gw-1");
        assertTrue(registry.markCancelled("task-1"));
        assertEquals(TaskRegistry.TaskState.CANCELLED, registry.getTask("task-1").getState());
    }

    // =========================================================================
    // Invalid state transitions
    // =========================================================================

    @Test
    void testWorkingFromWorkingFails() {
        registry.createTask("task-1", null, "agent-a", "gw-1");
        registry.markWorking("task-1");
        assertFalse(registry.markWorking("task-1"));
    }

    @Test
    void testCompletedFromCompletedFails() {
        registry.createTask("task-1", null, "agent-a", "gw-1");
        registry.markCompleted("task-1", "done");
        assertFalse(registry.markCompleted("task-1", "again"));
    }

    @Test
    void testCompletedToCancelledFails() {
        registry.createTask("task-1", null, "agent-a", "gw-1");
        registry.markCompleted("task-1", "done");
        assertFalse(registry.markCancelled("task-1"));
    }

    @Test
    void testCancelledToCompletedFails() {
        registry.createTask("task-1", null, "agent-a", "gw-1");
        registry.markCancelled("task-1");
        assertFalse(registry.markCompleted("task-1", "too late"));
    }

    @Test
    void testCancelledToFailedFails() {
        registry.createTask("task-1", null, "agent-a", "gw-1");
        registry.markCancelled("task-1");
        assertFalse(registry.markFailed("task-1", "error"));
    }

    @Test
    void testWorkingOnNonExistentTaskFails() {
        assertFalse(registry.markWorking("nonexistent"));
    }

    @Test
    void testCompleteOnNonExistentTaskFails() {
        assertFalse(registry.markCompleted("nonexistent", "data"));
    }

    // =========================================================================
    // Get / List
    // =========================================================================

    @Test
    void testGetNonExistentReturnsNull() {
        assertNull(registry.getTask("nonexistent"));
    }

    @Test
    void testListTasks() {
        registry.createTask("task-1", null, "agent-a", "gw-1");
        registry.createTask("task-2", null, "agent-b", "gw-1");
        registry.createTask("task-3", null, "agent-c", "gw-1");

        assertEquals(3, registry.listTasks().size());
    }

    @Test
    void testListTasksEmpty() {
        assertEquals(0, registry.listTasks().size());
    }

    @Test
    void testGetChildTasksNoParent() {
        registry.createTask("task-1", null, "agent-a", "gw-1");
        assertEquals(0, registry.getChildTasks("task-1").size());
    }

    @Test
    void testGetChildTasksNonExistentParent() {
        assertEquals(0, registry.getChildTasks("nonexistent").size());
    }

    @Test
    void testMultipleChildren() {
        registry.createTask("parent", null, "agent-a", "gw-1");
        registry.createTask("child-1", "parent", "agent-b", "gw-1");
        registry.createTask("child-2", "parent", "agent-c", "gw-1");
        registry.createTask("child-3", "parent", "agent-d", "gw-1");

        assertEquals(3, registry.getChildTasks("parent").size());
    }

    @Test
    void testConcurrentChildTaskCreation() throws Exception {
        registry.createTask("parent", null, "agent-a", "gw-1");
        ExecutorService executor = Executors.newCachedThreadPool();
        CountDownLatch ready = new CountDownLatch(50);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            final int index = i;
            executor.execute(() -> {
                ready.countDown();
                try {
                    start.await();
                    registry.createTask("child-" + index, "parent", "agent-b", "gw-1");
                } catch (Throwable t) {
                    synchronized (failures) {
                        failures.add(t);
                    }
                }
            });
        }

        assertTrue(ready.await(30, TimeUnit.SECONDS), "Not all threads ready in time");
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "Executor did not terminate in time");
        assertEquals(0, failures.size());
        assertEquals(50, registry.getChildTasks("parent").size());
    }

    // =========================================================================
    // Remove / Clear
    // =========================================================================

    @Test
    void testRemoveTask() {
        registry.createTask("task-1", null, "agent-a", "gw-1");
        TaskRegistry.TaskEntry removed = registry.removeTask("task-1");
        assertNotNull(removed);
        assertEquals("task-1", removed.getTaskId());
        assertEquals(0, registry.size());
        assertNull(registry.getTask("task-1"));
    }

    @Test
    void testRemoveNonExistentReturnsNull() {
        assertNull(registry.removeTask("nonexistent"));
    }

    @Test
    void testRemoveChildUpdatesParentChildren() {
        registry.createTask("parent", null, "agent-a", "gw-1");
        registry.createTask("child-1", "parent", "agent-b", "gw-1");
        registry.createTask("child-2", "parent", "agent-c", "gw-1");

        registry.removeTask("child-1");
        assertEquals(1, registry.getChildTasks("parent").size());
        assertEquals("child-2", registry.getChildTasks("parent").get(0));
    }

    @Test
    void testClear() {
        registry.createTask("task-1", null, "agent-a", "gw-1");
        registry.createTask("task-2", "task-1", "agent-b", "gw-1");
        registry.clear();
        assertEquals(0, registry.size());
    }

    // =========================================================================
    // Timestamps
    // =========================================================================

    @Test
    void testTimestampsUpdate() throws InterruptedException {
        registry.createTask("task-1", null, "agent-a", "gw-1");
        long createdAt = registry.getTask("task-1").getCreatedAt();
        long updatedAtBefore = registry.getTask("task-1").getUpdatedAt();

        Thread.sleep(10);
        registry.markWorking("task-1");
        long updatedAtAfter = registry.getTask("task-1").getUpdatedAt();

        assertEquals(createdAt, updatedAtBefore);
        assertTrue(updatedAtAfter > updatedAtBefore);
    }
}
