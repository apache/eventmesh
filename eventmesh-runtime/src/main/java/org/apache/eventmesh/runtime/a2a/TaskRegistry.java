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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * In-memory registry for A2A task lifecycle management.
 *
 * <p>Maintains task state transitions and parent-child task relationships.
 *
 * <p>State machine:
 * <pre>
 *   SUBMITTED -> WORKING -> COMPLETED
 *                       \-> FAILED
 *                       \-> CANCELLED
 * </pre>
 */
@Slf4j
public class TaskRegistry {

    public enum TaskState {
        SUBMITTED,
        WORKING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public static class TaskEntry {

        private final String taskId;
        private final String parentTaskId;
        private final String targetAgent;
        private final String gatewayId;
        private final long createdAt;
        private volatile long updatedAt;
        private volatile TaskState state;
        private volatile String result;
        private volatile String errorMessage;

        public TaskEntry(String taskId, String parentTaskId, String targetAgent, String gatewayId) {
            this.taskId = taskId;
            this.parentTaskId = parentTaskId;
            this.targetAgent = targetAgent;
            this.gatewayId = gatewayId;
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = this.createdAt;
            this.state = TaskState.SUBMITTED;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getParentTaskId() {
            return parentTaskId;
        }

        public String getTargetAgent() {
            return targetAgent;
        }

        public String getGatewayId() {
            return gatewayId;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public long getUpdatedAt() {
            return updatedAt;
        }

        public TaskState getState() {
            return state;
        }

        public String getResult() {
            return result;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    private final ConcurrentHashMap<String, TaskEntry> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>> parentToChildren = new ConcurrentHashMap<>();

    // TTL cleanup configuration
    private static final long DEFAULT_TASK_TTL_MS = 300_000L; // 5 minutes
    private static final long DEFAULT_TASK_CLEANUP_INTERVAL_MS = 60_000L; // 60 seconds
    private final long taskTtlMs;
    private final long cleanupIntervalMs;
    private ScheduledExecutorService cleanupExecutor;
    private volatile boolean started = false;

    public TaskRegistry() {
        this(DEFAULT_TASK_TTL_MS, DEFAULT_TASK_CLEANUP_INTERVAL_MS);
    }

    public TaskRegistry(long taskTtlMs, long cleanupIntervalMs) {
        this.taskTtlMs = taskTtlMs;
        this.cleanupIntervalMs = cleanupIntervalMs;
    }

    /**
     * Starts the TTL cleanup scheduler. Terminal-state tasks older than TTL are automatically removed.
     */
    public void start() {
        if (started) {
            return;
        }
        started = true;
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "a2a-task-ttl-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredTasks,
            cleanupIntervalMs, cleanupIntervalMs, TimeUnit.MILLISECONDS);
        log.info("TaskRegistry started (TTL={}ms, cleanup={}ms)", taskTtlMs, cleanupIntervalMs);
    }

    /**
     * Shuts down the TTL cleanup scheduler.
     */
    public void shutdown() {
        if (!started) {
            return;
        }
        started = false;
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdownNow();
            cleanupExecutor = null;
        }
        log.info("TaskRegistry shut down.");
    }

    /**
     * Removes terminal-state tasks whose updatedAt timestamp is older than TTL.
     */
    void cleanupExpiredTasks() {
        if (!started) {
            return;
        }
        long now = System.currentTimeMillis();
        int removed = 0;
        Iterator<Map.Entry<String, TaskEntry>> it = tasks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TaskEntry> entry = it.next();
            TaskEntry te = entry.getValue();
            if (isTerminalState(te.getState()) && (now - te.getUpdatedAt()) > taskTtlMs) {
                it.remove();
                if (te.getParentTaskId() != null) {
                    List<String> siblings = parentToChildren.get(te.getParentTaskId());
                    if (siblings != null) {
                        siblings.remove(te.getTaskId());
                    }
                }
                removed++;
            }
        }
        if (removed > 0) {
            log.info("TTL cleanup removed {} expired task(s).", removed);
        }
    }

    private boolean isTerminalState(TaskState state) {
        return state == TaskState.COMPLETED || state == TaskState.FAILED || state == TaskState.CANCELLED;
    }

    /**
     * Creates a new task entry.
     *
     * @param taskId        unique task ID
     * @param parentTaskId  parent task ID (null for root tasks)
     * @param targetAgent   target agent name
     * @param gatewayId     gateway ID (null for agent-to-agent tasks)
     * @return the created task entry
     */
    public TaskEntry createTask(String taskId, String parentTaskId, String targetAgent, String gatewayId) {
        TaskEntry entry = new TaskEntry(taskId, parentTaskId, targetAgent, gatewayId);
        tasks.put(taskId, entry);
        if (parentTaskId != null) {
            parentToChildren.computeIfAbsent(parentTaskId, k -> new CopyOnWriteArrayList<>()).add(taskId);
        }
        log.info("Task created: taskId={}, parentTaskId={}, targetAgent={}", taskId, parentTaskId, targetAgent);
        return entry;
    }

    /**
     * Transitions a task to WORKING state.
     */
    public boolean markWorking(String taskId) {
        TaskEntry entry = tasks.get(taskId);
        if (entry == null) {
            return false;
        }
        synchronized (entry) {
            if (entry.state != TaskState.SUBMITTED) {
                log.warn("Cannot mark task {} as WORKING from state {}", taskId, entry.state);
                return false;
            }
            entry.state = TaskState.WORKING;
            entry.updatedAt = System.currentTimeMillis();
        }
        log.info("Task working: taskId={}", taskId);
        return true;
    }

    /**
     * Transitions a task to COMPLETED state with result.
     */
    public boolean markCompleted(String taskId, String result) {
        TaskEntry entry = tasks.get(taskId);
        if (entry == null) {
            return false;
        }
        synchronized (entry) {
            if (entry.state == TaskState.COMPLETED || entry.state == TaskState.CANCELLED) {
                return false;
            }
            entry.state = TaskState.COMPLETED;
            entry.result = result;
            entry.updatedAt = System.currentTimeMillis();
        }
        log.info("Task completed: taskId={}", taskId);
        return true;
    }

    /**
     * Transitions a task to FAILED state with error message.
     */
    public boolean markFailed(String taskId, String errorMessage) {
        TaskEntry entry = tasks.get(taskId);
        if (entry == null) {
            return false;
        }
        synchronized (entry) {
            if (entry.state == TaskState.COMPLETED || entry.state == TaskState.CANCELLED) {
                return false;
            }
            entry.state = TaskState.FAILED;
            entry.errorMessage = errorMessage;
            entry.updatedAt = System.currentTimeMillis();
        }
        log.info("Task failed: taskId={}, error={}", taskId, errorMessage);
        return true;
    }

    /**
     * Transitions a task to CANCELLED state.
     */
    public boolean markCancelled(String taskId) {
        TaskEntry entry = tasks.get(taskId);
        if (entry == null) {
            return false;
        }
        synchronized (entry) {
            if (entry.state == TaskState.COMPLETED || entry.state == TaskState.CANCELLED) {
                return false;
            }
            entry.state = TaskState.CANCELLED;
            entry.updatedAt = System.currentTimeMillis();
        }
        log.info("Task cancelled: taskId={}", taskId);
        return true;
    }

    /**
     * Gets a task entry by ID.
     */
    public TaskEntry getTask(String taskId) {
        return tasks.get(taskId);
    }

    /**
     * Lists all tasks.
     */
    public List<TaskEntry> listTasks() {
        return new ArrayList<>(tasks.values());
    }

    /**
     * Lists child tasks of a parent task.
     */
    public List<String> getChildTasks(String parentTaskId) {
        List<String> children = parentToChildren.get(parentTaskId);
        return children != null ? new ArrayList<>(children) : new ArrayList<>();
    }

    /**
     * Removes a task from the registry.
     */
    public TaskEntry removeTask(String taskId) {
        TaskEntry removed = tasks.remove(taskId);
        if (removed != null && removed.parentTaskId != null) {
            List<String> siblings = parentToChildren.get(removed.parentTaskId);
            if (siblings != null) {
                siblings.remove(taskId);
            }
        }
        return removed;
    }

    /**
     * Returns the current size of the registry.
     */
    public int size() {
        return tasks.size();
    }

    /**
     * Clears all tasks.
     */
    public void clear() {
        tasks.clear();
        parentToChildren.clear();
    }
}
