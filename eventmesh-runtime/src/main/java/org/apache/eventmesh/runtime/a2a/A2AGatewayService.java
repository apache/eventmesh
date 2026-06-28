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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * A2A Gateway Service: orchestrates task submission, response handling, and SSE streaming.
 *
 * <p>This is the core component that ties together:
 * <ul>
 *   <li>{@link A2AMessageTransport} for pub/sub</li>
 *   <li>{@link TaskRegistry} for task lifecycle</li>
 *   <li>{@link A2APublishSubscribeService} for agent discovery</li>
 *   <li>{@link A2ATopicFactory} for topic routing</li>
 * </ul>
 */
@Slf4j
public class A2AGatewayService {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String namespace;
    private final String gatewayId;
    private final A2AMessageTransport transport;
    private final TaskRegistry taskRegistry;
    private final A2APublishSubscribeService a2aService;

    // Pending tasks waiting for response
    private final ConcurrentHashMap<String, CompletableFuture<TaskResult>> pendingTasks = new ConcurrentHashMap<>();
    // SSE subscribers for status updates
    private final ConcurrentHashMap<String, List<StatusSubscriber>> statusSubscribers = new ConcurrentHashMap<>();

    private volatile boolean started = false;
    private String responseSubscriptionId;
    private String statusSubscriptionId;

    // Task timeout: tasks that don't receive a response within this duration are auto-failed
    private static final long DEFAULT_TASK_TIMEOUT_MS = 120_000L; // 2 minutes
    private final long taskTimeoutMs;
    private ScheduledExecutorService taskTimeoutScheduler;

    public A2AGatewayService(String namespace, String gatewayId,
                             A2AMessageTransport transport,
                             TaskRegistry taskRegistry,
                             A2APublishSubscribeService a2aService) {
        this(namespace, gatewayId, transport, taskRegistry, a2aService, DEFAULT_TASK_TIMEOUT_MS);
    }

    public A2AGatewayService(String namespace, String gatewayId,
                             A2AMessageTransport transport,
                             TaskRegistry taskRegistry,
                             A2APublishSubscribeService a2aService,
                             long taskTimeoutMs) {
        this.namespace = namespace;
        this.gatewayId = gatewayId;
        this.transport = transport;
        this.taskRegistry = taskRegistry;
        this.a2aService = a2aService;
        this.taskTimeoutMs = taskTimeoutMs;
    }

    public String getGatewayId() {
        return gatewayId;
    }

    public String getNamespace() {
        return namespace;
    }

    public A2APublishSubscribeService getA2aService() {
        return a2aService;
    }

    public TaskRegistry getTaskRegistry() {
        return taskRegistry;
    }

    /**
     * Starts the gateway service: subscribes to gateway response/status topics.
     */
    public synchronized void start() throws Exception {
        if (started) {
            return;
        }
        // Start task registry TTL cleanup
        taskRegistry.start();

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
        taskRegistry.shutdown();
        started = false;
        log.info("A2AGatewayService shutdown.");
    }

    // =========================================================================
    // Task Submission
    // =========================================================================

    /**
     * Submits an A2A task to a target agent.
     *
     * @param targetAgent  target agent name
     * @param message      the message content (JSON string)
     * @param parentTaskId parent task ID (null for root tasks from external clients)
     * @return CompletableFuture that will be completed with the task result
     */
    public CompletableFuture<TaskResult> submitTask(String targetAgent, String message, String parentTaskId) {
        String taskId = generateTaskId();
        return submitTask(taskId, targetAgent, message, parentTaskId);
    }

    /**
     * Submits an A2A task with a specific task ID.
     */
    public CompletableFuture<TaskResult> submitTask(String taskId, String targetAgent,
                                                     String message, String parentTaskId) {
        if (!started) {
            CompletableFuture<TaskResult> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("Gateway not started"));
            return future;
        }

        // Validate that the target agent is registered
        if (!a2aService.isAgentRegistered(targetAgent)) {
            CompletableFuture<TaskResult> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException(
                "Target agent not registered: " + targetAgent));
            return future;
        }

        // Create task entry
        TaskRegistry.TaskEntry taskEntry = taskRegistry.createTask(taskId, parentTaskId, targetAgent, gatewayId);

        // Build A2A CloudEvent
        CloudEvent event = buildTaskRequestEvent(taskId, targetAgent, message, parentTaskId);

        // Register pending future BEFORE publishing to avoid race condition:
        // InMemoryTransport delivers synchronously, so if we publish first,
        // handleResponse() could run before put() and the future would never complete.
        CompletableFuture<TaskResult> future = new CompletableFuture<>();
        pendingTasks.put(taskId, future);

        // Schedule timeout: if no response within taskTimeoutMs, auto-fail the task
        if (taskTimeoutScheduler != null) {
            taskTimeoutScheduler.schedule(() -> {
                CompletableFuture<TaskResult> pending = pendingTasks.get(taskId);
                if (pending != null && !pending.isDone()) {
                    taskRegistry.markFailed(taskId, "Task timed out after " + taskTimeoutMs + "ms with no response");
                    pendingTasks.remove(taskId);
                    pending.completeExceptionally(new java.util.concurrent.TimeoutException(
                        "Task " + taskId + " timed out after " + taskTimeoutMs + "ms"));
                    notifyStatusSubscribers(taskId, "failed", "Task timed out");
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
            taskRegistry.markFailed(taskId, "Publish failed: " + e.getMessage());
            pendingTasks.remove(taskId);
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * Cancels a task.
     */
    public boolean cancelTask(String taskId) {
        boolean cancelled = taskRegistry.markCancelled(taskId);
        if (cancelled) {
            CompletableFuture<TaskResult> future = pendingTasks.remove(taskId);
            if (future != null) {
                future.complete(new TaskResult(TaskRegistry.TaskState.CANCELLED, null, "Task cancelled"));
            }
            notifyStatusSubscribers(taskId, "cancelled", "Task cancelled");
            return true;
        }
        return false;
    }

    /**
     * Gets task status.
     */
    public TaskRegistry.TaskEntry getTaskStatus(String taskId) {
        return taskRegistry.getTask(taskId);
    }

    // =========================================================================
    // SSE Status Subscription
    // =========================================================================

    /**
     * Registers a subscriber for status updates on a specific task.
     */
    public void registerStatusSubscriber(String taskId, StatusSubscriber subscriber) {
        statusSubscribers.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(subscriber);
    }

    /**
     * Removes a status subscriber.
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

        TaskRegistry.TaskEntry entry = taskRegistry.getTask(taskId);
        if (entry == null) {
            log.warn("Response received for unknown task: {}", taskId);
            return;
        }

        String resultData = extractEventData(event);
        taskRegistry.markCompleted(taskId, resultData);

        CompletableFuture<TaskResult> future = pendingTasks.remove(taskId);
        if (future != null) {
            future.complete(new TaskResult(TaskRegistry.TaskState.COMPLETED, resultData, null));
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

        // Mark as working if currently submitted
        TaskRegistry.TaskEntry entry = taskRegistry.getTask(taskId);
        if (entry != null && entry.getState() == TaskRegistry.TaskState.SUBMITTED) {
            taskRegistry.markWorking(taskId);
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

    public static class TaskResult {

        private final TaskRegistry.TaskState state;
        private final String data;
        private final String errorMessage;

        public TaskResult(TaskRegistry.TaskState state, String data, String errorMessage) {
            this.state = state;
            this.data = data;
            this.errorMessage = errorMessage;
        }

        public TaskRegistry.TaskState getState() {
            return state;
        }

        public String getData() {
            return data;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    @FunctionalInterface
    public interface StatusSubscriber {
        void onStatus(String taskId, String state, String data);
    }
}
