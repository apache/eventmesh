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

import java.util.List;

/**
 * A2A task state, persisted via Meta (issue #5301 §TaskStore, Sub-PR C).
 *
 * <p>Reintroduces the A2A task model lost when #5274 reverted #5260. The store is the
 * <b>only</b> writer to {@code /em/tasks/<taskId>}; A2A handlers ({@code /a2a/tasks/send},
 * {@code /a2a/tasks/sendSubscribe}, the SSE {@code /a2a/tasks/{id}/stream} endpoint) read
 * through it.</p>
 *
 * <p>The state model: a {@code TaskRecord = { taskId, agentId, clientId, status, createdAt,
 * updatedAt, input, output? }} where {@code status ∈ {PENDING, RUNNING, COMPLETED, FAILED,
 * CANCELED}}. Status transitions are last-writer-wins on a per-task basis, with a task epoch
 * (set at {@link #createTask}) preventing stale writes from a previous Runtime instance from
 * overwriting a fresh status (issue #5291 idempotency-style).</p>
 *
 * <p>The Runtime dispatcher is the <b>sole</b> transport — A2A requests arrive via the
 * {@code a2a} distribution mode on {@code SubscriptionManager} and are routed through the
 * existing dispatcher, not a parallel gateway. This is the property the original #5259 design
 * missed and the reason #5274 reverted it.</p>
 */
public interface TaskStore {

    /** Task lifecycle states. */
    enum Status {
        PENDING, RUNNING, COMPLETED, FAILED, CANCELED
    }

    /**
     * A persisted A2A task.
     */
    final class TaskRecord {
        public final String taskId;
        public final String agentId;
        public final String clientId;
        public volatile Status status;
        public final long createdAtMs;
        public volatile long updatedAtMs;
        /** Opaque JSON or wire-format input the caller submitted. */
        public final String input;
        /** Opaque output; null until {@link #updateStatus} transitions to {@code COMPLETED} or {@code FAILED}. */
        public volatile String output;
        /** Monotonic per-task epoch; set at creation, never reset, used to reject stale writes. */
        public final long taskEpoch;

        public TaskRecord(String taskId, String agentId, String clientId, Status status,
                          long createdAtMs, long updatedAtMs, String input, String output, long taskEpoch) {
            this.taskId = taskId;
            this.agentId = agentId;
            this.clientId = clientId;
            this.status = status;
            this.createdAtMs = createdAtMs;
            this.updatedAtMs = updatedAtMs;
            this.input = input;
            this.output = output;
            this.taskEpoch = taskEpoch;
        }
    }

    /**
     * Create a new task in {@link Status#PENDING} state. {@code taskId} must be unique; a
     * duplicate returns {@code null} (caller should retry with a fresh id).
     */
    TaskRecord createTask(String taskId, String agentId, String clientId, String input);

    /**
     * @return the task record, or {@code null} if no such task
     */
    TaskRecord getTask(String taskId);

    /**
     * Transition a task to a new status. The write is rejected (returns {@code false}) if
     * the stored {@code taskEpoch} does not match {@code expectedTaskEpoch}; callers should
     * re-read and retry on epoch mismatch.
     *
     * @return true on successful status update
     */
    boolean updateStatus(String taskId, long expectedTaskEpoch, Status newStatus, String output);

    /**
     * All tasks for an agent filtered by status (any status if {@code statusFilter} is null).
     */
    List<TaskRecord> listByAgent(String agentId, Status statusFilter);

    /**
     * Remove every task idle for longer than {@code olderThanMs}. Returns the expired
     * taskIds so the caller can release any associated resources.
     */
    List<String> expireStale(long olderThanMs);

    /**
     * Force any buffered writes to durable storage.
     */
    void flush();

    /**
     * Release resources. After close the store must not be used.
     */
    void close();
}
