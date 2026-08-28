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

import org.apache.eventmesh.protocol.a2a.A2AMessageTransport;
import org.apache.eventmesh.protocol.a2a.A2AProtocolConstants;
import org.apache.eventmesh.protocol.a2a.A2ATopicFactory;
import org.apache.eventmesh.runtime.state.TaskStore;
import org.apache.eventmesh.runtime.state.TaskStore.Status;
import org.apache.eventmesh.runtime.state.TaskStore.TaskRecord;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * A2A Gateway Service: orchestrates task submission, response handling, and SSE streaming.
 *
 * <p>This is the core component that ties together:
 * <ul>
 *   <li>{@link A2AMessageTransport} for pub/sub (in production: the Runtime-bridged
 *       {@link EventMeshA2ATransport}, NOT a parallel in-memory transport)</li>
 *   <li>{@link TaskStore} for task lifecycle (issue #5301 Sub-PR A/C; durable across restarts)</li>
 *   <li>{@link AgentCardRegistry} for agent discovery (in-memory D1; Meta-backed in D2)</li>
 *   <li>{@link A2ATopicFactory} for topic routing</li>
 * </ul>
 *
 * <p><b>Design (issue #5302):</b> the in-memory {@code TaskRegistry} that
 * <a href="https://github.com/apache/eventmesh/pull/5260">PR #5260</a> introduced has been
 * replaced by {@link TaskStore} &mdash; the gateway no longer owns a private task table. A
 * small runtime cache ({@code parentTaskIdCache}, {@code taskEpochCache}) holds fields the
 * persistent store does not model (parent links, the per-task epoch used for stale-write
 * rejection). Both caches are rebuildable from the store on a fresh JVM.</p>
 *
 * <p><b>Status mapping (PR #5260 -&gt; Sub-PR A/C):</b>
 * {@code SUBMITTED -&gt; PENDING}, {@code WORKING -&gt; RUNNING},
 * {@code CANCELLED -&gt; CANCELED} (one L). The public state names exposed by
 * {@link A2AGatewayService.TaskState} are the legacy names (kebab-cased protocol JSON); the
 * internal store uses {@link Status}.</p>
 */
@Slf4j
public class A2AGatewayService {

    private final String namespace;
    private final String gatewayId;
    private final A2AMessageTransport transport;
    private final TaskStore taskStore;
    private final AgentCardRegistry agentCardRegistry;

    // Pending tasks waiting for response (runtime-only; not persisted)
    private final ConcurrentHashMap<String, CompletableFuture<TaskResult>> pendingTasks = new ConcurrentHashMap<>();
    // SSE subscribers for status updates (runtime-only; rebuilt on stream start)
    private final ConcurrentHashMap<String, List<StatusSubscriber>> statusSubscribers = new ConcurrentHashMap<>();
    // Parent task id cache (TaskStore does not model parent links; rebuilt on recovery)
    private final ConcurrentHashMap<String, String> parentTaskIdCache = new ConcurrentHashMap<>();
    // Per-task epoch cache (TaskStore.updateStatus requires the epoch set at createTask; we
    // remember it so transition methods don't have to re-read the record)
    private final ConcurrentHashMap<String, Long> taskEpochCache = new ConcurrentHashMap<>();

    private volatile boolean started = false;
    private String responseSubscriptionId;
    private String statusSubscriptionId;

    // Task timeout: tasks that don't receive a response within this duration are auto-failed
    private static final long DEFAULT_TASK_TIMEOUT_MS = 120_000L; // 2 minutes
    private final long taskTimeoutMs;
    private ScheduledExecutorService taskTimeoutScheduler;

    public A2AGatewayService(String namespace, String gatewayId,
                             A2AMessageTransport transport,
                             TaskStore taskStore,
                             AgentCardRegistry agentCardRegistry) {
        this(namespace, gatewayId, transport, taskStore, agentCardRegistry, DEFAULT_TASK_TIMEOUT_MS);
    }

    public A2AGatewayService(String namespace, String gatewayId,
                             A2AMessageTransport transport,
                             TaskStore taskStore,
                             AgentCardRegistry agentCardRegistry,
                             long taskTimeoutMs) {
        this.namespace = namespace;
        this.gatewayId = gatewayId;
        this.transport = transport;
        this.taskStore = taskStore;
        this.agentCardRegistry = agentCardRegistry;
        this.taskTimeoutMs = taskTimeoutMs;
    }

    public String getGatewayId() {
        return gatewayId;
    }

    public String getNamespace() {
        return namespace;
    }

    public AgentCardRegistry getAgentCardRegistry() {
        return agentCardRegistry;
    }

    public TaskStore getTaskStore() {
        return taskStore;
    }

    /**
     * Starts the gateway service: subscribes to gateway response/status topics.
     */
    public synchronized void start() throws Exception {
        if (started) {
            return;
        }

        // Start task timeout scheduler
        taskTimeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "a2a-task-timeout");
            t.setDaemon(true);
            return t;
        });

        // Subscribe to all responses for this gateway
        String responseTopic = A2ATopicFactory.gatewayResponseWildcardTopic(namespace, gatewayId);
        responseSubscriptionId = transport.subscribe(responseTopic, this::handleResponse);

        // Subscribe to all status updates for this gateway
        String statusTopic = A2ATopicFactory.gatewayStatusWildcardTopic(namespace, gatewayId);
        statusSubscriptionId = transport.subscribe(statusTopic, this::handleStatus);

        started = true;
        log.info("A2AGatewayService started: gatewayId={}, namespace={}", gatewayId, namespace);
    }

    public synchronized void shutdown() throws Exception {
        if (!started) {
            return;
        }
        if (responseSubscriptionId != null) {
            transport.unsubscribe(responseSubscriptionId);
        }
        if (statusSubscriptionId != null) {
            transport.unsubscribe(statusSubscriptionId);
        }
        if (taskTimeoutScheduler != null) {
            taskTimeoutScheduler.shutdownNow();
            taskTimeoutScheduler = null;
        }
        pendingTasks.clear();
        statusSubscribers.clear();
        parentTaskIdCache.clear();
        taskEpochCache.clear();
        started = false;
        log.info("A2AGatewayService shutdown.");
    }

    // =========================================================================
    // Task Submission
    // =========================================================================

    /**
     * Submits an A2A task to a target agent with an auto-generated task id.
     */
    public CompletableFuture<TaskResult> submitTask(String targetAgent, String message, String parentTaskId) {
        String taskId = generateTaskId();
        return submitTask(taskId, targetAgent, message, parentTaskId);
    }

    /**
     * Submits an A2A task with a specific task id. The target agent must be registered in
     * the {@link AgentCardRegistry}.
     */
    public CompletableFuture<TaskResult> submitTask(String taskId, String targetAgent,
                                                     String message, String parentTaskId) {
        if (!started) {
            CompletableFuture<TaskResult> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("Gateway not started"));
            return future;
        }

        // Validate that the target agent is registered
        if (!agentCardRegistry.isAgentRegistered(targetAgent)) {
            CompletableFuture<TaskResult> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException(
                "Target agent not registered: " + targetAgent));
            return future;
        }

        // Create task in the persistent store. createTask returns null on duplicate taskId.
        TaskRecord rec = taskStore.createTask(taskId, targetAgent, gatewayId, message);
        if (rec == null) {
            CompletableFuture<TaskResult> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("Duplicate taskId: " + taskId));
            return future;
        }
        if (parentTaskId != null) {
            parentTaskIdCache.put(taskId, parentTaskId);
        }
        taskEpochCache.put(taskId, rec.taskEpoch);

        // Build A2A CloudEvent
        CloudEvent event = buildTaskRequestEvent(taskId, targetAgent, message, parentTaskId);

        // Register pending future BEFORE publishing: a synchronous transport callback (e.g. a
        // local in-memory test) could deliver the response before submitTask returns, and we
        // would lose the future if put() ran after handleResponse().
        CompletableFuture<TaskResult> future = new CompletableFuture<>();
        pendingTasks.put(taskId, future);

        // Schedule timeout: if no response within taskTimeoutMs, auto-fail the task
        if (taskTimeoutScheduler != null) {
            taskTimeoutScheduler.schedule(() -> {
                CompletableFuture<TaskResult> pending = pendingTasks.get(taskId);
                if (pending != null && !pending.isDone()) {
                    String errMsg = "Task timed out after " + taskTimeoutMs + "ms with no response";
                    Long epoch = taskEpochCache.get(taskId);
                    if (epoch != null) {
                        taskStore.updateStatus(taskId, epoch, Status.FAILED, errMsg);
                    }
                    pendingTasks.remove(taskId);
                    pending.completeExceptionally(new java.util.concurrent.TimeoutException(errMsg));
                    notifyStatusSubscribers(taskId, "failed", errMsg);
                    log.warn("Task timed out: taskId={}, targetAgent={}", taskId, targetAgent);
                }
            }, taskTimeoutMs, TimeUnit.MILLISECONDS);
        }

        // Publish to agent request topic
        String requestTopic = A2ATopicFactory.agentRequestTopic(namespace, targetAgent);
        try {
            transport.publish(requestTopic, event);
            log.info("Task submitted: taskId={}, targetAgent={}, topic={}", taskId, targetAgent, requestTopic);
        } catch (Exception e) {
            log.error("Failed to publish task: taskId={}", taskId, e);
            Long epoch = taskEpochCache.remove(taskId);
            if (epoch != null) {
                taskStore.updateStatus(taskId, epoch, Status.FAILED, "Publish failed: " + e.getMessage());
            }
            pendingTasks.remove(taskId);
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * Cancels a task. Idempotent: cancelling a non-PENDING/RUNNING task is a no-op.
     */
    public boolean cancelTask(String taskId) {
        Long epoch = taskEpochCache.get(taskId);
        if (epoch == null) {
            return false;
        }
        TaskRecord rec = taskStore.getTask(taskId);
        if (rec == null) {
            return false;
        }
        if (rec.status == Status.COMPLETED || rec.status == Status.FAILED || rec.status == Status.CANCELED) {
            return false;
        }
        boolean ok = taskStore.updateStatus(taskId, epoch, Status.CANCELED, null);
        if (ok) {
            CompletableFuture<TaskResult> future = pendingTasks.remove(taskId);
            if (future != null) {
                future.complete(new TaskResult(TaskState.CANCELLED, null, "Task cancelled"));
            }
            notifyStatusSubscribers(taskId, "cancelled", "Task cancelled");
            log.info("Task cancelled: taskId={}", taskId);
        }
        return ok;
    }

    /**
     * Gets task status (a snapshot from the persistent store).
     */
    public TaskSnapshot getTaskStatus(String taskId) {
        TaskRecord rec = taskStore.getTask(taskId);
        if (rec == null) {
            return null;
        }
        String parentId = parentTaskIdCache.get(taskId);
        return new TaskSnapshot(rec, parentId);
    }

    /**
     * Lists child task ids of a parent task, scanning the runtime cache. The persistent
     * {@link TaskStore} does not model parent-child relations; this list reflects the
     * gateway-local view and is rebuilt on a fresh JVM by replaying pendingTasks / scanning
     * parentTaskIdCache (see issue #5302 D2 &mdash; the Meta-ized TaskStore should grow a
     * parent index in a follow-up).
     */
    public List<String> getChildTasks(String parentTaskId) {
        List<String> children = new java.util.ArrayList<>();
        for (var entry : parentTaskIdCache.entrySet()) {
            if (parentTaskId.equals(entry.getValue())) {
                children.add(entry.getKey());
            }
        }
        return children;
    }

    // =========================================================================
    // SSE Status Subscription
    // =========================================================================

    /**
     * Registers a subscriber for status updates on a specific task. The subscriber is invoked
     * on whichever thread completes the task transition (transport callback, timeout scheduler,
     * or a cancel).
     */
    public void registerStatusSubscriber(String taskId, StatusSubscriber subscriber) {
        statusSubscribers.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(subscriber);
    }

    /**
     * Removes a status subscriber. If the subscriber list becomes empty, the task entry is
     * removed entirely so the map does not grow unbounded.
     */
    public void unregisterStatusSubscriber(String taskId, StatusSubscriber subscriber) {
        List<StatusSubscriber> subs = statusSubscribers.get(taskId);
        if (subs != null) {
            subs.remove(subscriber);
            if (subs.isEmpty()) {
                statusSubscribers.remove(taskId);
            }
        }
    }

    // =========================================================================
    // Response / Status Handling
    // =========================================================================

    private void handleResponse(String topic, CloudEvent event) {
        A2ATopicFactory.ParsedTopic parsed = A2ATopicFactory.parse(topic);
        if (parsed == null || !parsed.isResponse() || parsed.getTaskId() == null) {
            return;
        }
        String taskId = parsed.getTaskId();
        log.info("Received response for task: {}", taskId);

        TaskRecord rec = taskStore.getTask(taskId);
        if (rec == null) {
            log.warn("Response received for unknown task: {}", taskId);
            return;
        }

        String resultData = extractEventData(event);
        taskStore.updateStatus(taskId, rec.taskEpoch, Status.COMPLETED, resultData);

        CompletableFuture<TaskResult> future = pendingTasks.remove(taskId);
        if (future != null) {
            future.complete(new TaskResult(TaskState.COMPLETED, resultData, null));
        }

        // Notify SSE subscribers
        notifyStatusSubscribers(taskId, "completed", resultData);
    }

    private void handleStatus(String topic, CloudEvent event) {
        A2ATopicFactory.ParsedTopic parsed = A2ATopicFactory.parse(topic);
        if (parsed == null || !parsed.isStatus() || parsed.getTaskId() == null) {
            return;
        }
        String taskId = parsed.getTaskId();
        String statusData = extractEventData(event);
        log.debug("Received status for task: {} -> {}", taskId, statusData);

        // Mark as RUNNING if currently PENDING. Idempotent: if the task is already terminal
        // or RUNNING, updateStatus with the same epoch is a no-op for status but updates
        // updatedAtMs &mdash; which we want for the timeout clock.
        TaskRecord rec = taskStore.getTask(taskId);
        if (rec != null && rec.status == Status.PENDING) {
            taskStore.updateStatus(taskId, rec.taskEpoch, Status.RUNNING, null);
        }

        // Notify SSE subscribers
        notifyStatusSubscribers(taskId, "working", statusData);
    }

    private void notifyStatusSubscribers(String taskId, String state, String data) {
        List<StatusSubscriber> subs = statusSubscribers.get(taskId);
        if (subs != null) {
            for (StatusSubscriber sub : subs) {
                try {
                    sub.onStatus(taskId, state, data);
                } catch (Exception e) {
                    log.warn("Status subscriber error for task {}: {}", taskId, e.getMessage());
                }
            }
        }
    }

    // =========================================================================
    // CloudEvent Building
    // =========================================================================

    private CloudEvent buildTaskRequestEvent(String taskId, String targetAgent,
                                              String message, String parentTaskId) {
        CloudEventBuilder builder = CloudEventBuilder.v1()
            .withId(taskId)
            .withType(A2AProtocolConstants.CE_TYPE_PREFIX + "task.request")
            .withSource(java.net.URI.create("gateway/" + gatewayId))
            .withDataContentType("application/json")
            .withData(message.getBytes(StandardCharsets.UTF_8))
            .withExtension(A2AProtocolConstants.CE_EXTENSION_A2A_METHOD, A2AProtocolConstants.OP_SEND_MESSAGE)
            .withExtension(A2AProtocolConstants.CE_EXTENSION_TARGET_AGENT, targetAgent)
            .withExtension(A2AProtocolConstants.CE_EXTENSION_PROTOCOL, "A2A")
            .withExtension(A2AProtocolConstants.CE_EXTENSION_PROTOCOL_VERSION, A2AProtocolConstants.PROTOCOL_VERSION);

        if (parentTaskId != null) {
            builder.withExtension(A2AProtocolConstants.CE_EXTENSION_COLLABORATION_ID, parentTaskId);
        }

        return builder.build();
    }

    private String extractEventData(CloudEvent event) {
        if (event.getData() == null) {
            return null;
        }
        return new String(event.getData().toBytes(), StandardCharsets.UTF_8);
    }

    private String generateTaskId() {
        return "task-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // =========================================================================
    // Result Types
    // =========================================================================

    /**
     * Legacy state names exposed to the A2A wire protocol and JSON response payloads. The
     * persistent store uses {@link Status}; this enum is the on-the-wire vocabulary.
     */
    public enum TaskState {
        SUBMITTED,
        WORKING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public static class TaskResult {

        private final TaskState state;
        private final String data;
        private final String errorMessage;

        public TaskResult(TaskState state, String data, String errorMessage) {
            this.state = state;
            this.data = data;
            this.errorMessage = errorMessage;
        }

        public TaskState getState() {
            return state;
        }

        public String getData() {
            return data;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * A snapshot of a task &mdash; the persisted record plus the runtime-only parent link.
     */
    public static class TaskSnapshot {
        private final TaskRecord record;
        private final String parentTaskId;

        public TaskSnapshot(TaskRecord record, String parentTaskId) {
            this.record = record;
            this.parentTaskId = parentTaskId;
        }

        public TaskRecord getRecord() {
            return record;
        }

        public String getParentTaskId() {
            return parentTaskId;
        }

        public TaskState getState() {
            return toLegacyState(record.status);
        }
    }

    /**
     * Maps the persistent {@link Status} to the legacy A2A wire vocabulary.
     */
    public static TaskState toLegacyState(Status s) {
        switch (s) {
            case PENDING:   return TaskState.SUBMITTED;
            case RUNNING:   return TaskState.WORKING;
            case COMPLETED: return TaskState.COMPLETED;
            case FAILED:    return TaskState.FAILED;
            case CANCELED:  return TaskState.CANCELLED;
            default:        throw new IllegalStateException("Unknown status: " + s);
        }
    }

    /**
     * Callback interface for task status change notifications.
     */
    @FunctionalInterface
    public interface StatusSubscriber {
        void onStatus(String taskId, String state, String data);
    }
}
