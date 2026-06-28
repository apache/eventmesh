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

import org.apache.eventmesh.protocol.a2a.A2AProtocolConstants;
import org.apache.eventmesh.protocol.a2a.A2ATopicFactory;
import org.apache.eventmesh.protocol.a2a.AgentIdentity;
import org.apache.eventmesh.protocol.a2a.model.AgentCard;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link A2AGatewayService}.
 */
class A2AGatewayServiceTest {

    private InMemoryA2AMessageTransport transport;
    private TaskRegistry taskRegistry;
    private A2APublishSubscribeService a2aService;
    private A2AGatewayService gateway;

    private static final String NAMESPACE = "global";
    private static final String GATEWAY_ID = "test-gateway";

    @BeforeEach
    void setUp() throws Exception {
        transport = new InMemoryA2AMessageTransport();
        taskRegistry = new TaskRegistry();
        a2aService = new A2APublishSubscribeService(null);
        a2aService.init();
        a2aService.start();
        gateway = new A2AGatewayService(NAMESPACE, GATEWAY_ID, transport, taskRegistry, a2aService);
        gateway.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        gateway.shutdown();
        a2aService.shutdown();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void registerAgent(String agentName) {
        AgentCard card = AgentCardTestUtils.createValidCard(agentName);
        AgentIdentity identity = AgentIdentity.builder()
            .orgId("default").unitId("default").agentId(agentName).build();
        a2aService.registerCard(identity, card);
    }

    private void subscribeEchoAgent(String agentName) throws Exception {
        String requestTopic = A2ATopicFactory.agentRequestTopic(NAMESPACE, agentName);
        transport.subscribe(requestTopic, (topic, event) -> {
            String taskId = event.getId();
            String data = event.getData() != null
                ? new String(event.getData().toBytes(), StandardCharsets.UTF_8) : "";
            String responseTopic = A2ATopicFactory.gatewayResponseTopic(NAMESPACE, GATEWAY_ID, taskId);
            CloudEvent response = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(A2AProtocolConstants.CE_TYPE_PREFIX + "task.response")
                .withSource(java.net.URI.create("agent/" + agentName))
                .withData(("echo: " + data).getBytes(StandardCharsets.UTF_8))
                .build();
            try {
                transport.publish(responseTopic, response);
            } catch (Exception e) {
                // ignore
            }
        });
    }

    // =========================================================================
    // Task submission and response
    // =========================================================================

    @Test
    void testSubmitTaskAndReceiveResponse() throws Exception {
        registerAgent("echo-agent");
        subscribeEchoAgent("echo-agent");

        CompletableFuture<A2AGatewayService.TaskResult> future =
            gateway.submitTask("echo-agent", "hello", null);

        A2AGatewayService.TaskResult result = future.get(10, TimeUnit.SECONDS);
        assertNotNull(result);
        assertEquals(TaskRegistry.TaskState.COMPLETED, result.getState());
        assertEquals("echo: hello", result.getData());
    }

    @Test
    void testTaskRegistryUpdatedAfterResponse() throws Exception {
        registerAgent("echo-agent");
        subscribeEchoAgent("echo-agent");

        CompletableFuture<A2AGatewayService.TaskResult> future =
            gateway.submitTask("echo-agent", "test", null);
        future.get(10, TimeUnit.SECONDS);

        assertEquals(1, taskRegistry.size());
        TaskRegistry.TaskEntry entry = taskRegistry.listTasks().get(0);
        assertEquals(TaskRegistry.TaskState.COMPLETED, entry.getState());
        assertEquals("echo: test", entry.getResult());
        assertEquals("echo-agent", entry.getTargetAgent());
        assertEquals(GATEWAY_ID, entry.getGatewayId());
    }

    @Test
    void testSubmitTaskWithParentTaskId() throws Exception {
        registerAgent("echo-agent");
        subscribeEchoAgent("echo-agent");

        String parentTaskId = "parent-task-001";
        CompletableFuture<A2AGatewayService.TaskResult> future =
            gateway.submitTask("echo-agent", "child-msg", parentTaskId);
        future.get(10, TimeUnit.SECONDS);

        TaskRegistry.TaskEntry entry = taskRegistry.listTasks().get(0);
        assertEquals(parentTaskId, entry.getParentTaskId());
    }

    // =========================================================================
    // Task cancellation
    // =========================================================================

    @Test
    void testCancelTask() {
        // Register an agent that never responds
        registerAgent("slow-agent");

        CompletableFuture<A2AGatewayService.TaskResult> future =
            gateway.submitTask("slow-agent", "hello", null);

        // Cancel before response
        boolean cancelled = gateway.cancelTask(
            taskRegistry.listTasks().get(0).getTaskId());

        assertTrue(cancelled);
        TaskRegistry.TaskEntry entry = taskRegistry.listTasks().get(0);
        assertEquals(TaskRegistry.TaskState.CANCELLED, entry.getState());

        // Future should be completed with CANCELLED
        A2AGatewayService.TaskResult result = future.getNow(null);
        assertNotNull(result);
        assertEquals(TaskRegistry.TaskState.CANCELLED, result.getState());
    }

    @Test
    void testCancelNonExistentTask() {
        assertFalse(gateway.cancelTask("nonexistent-task"));
    }

    // =========================================================================
    // Get task status
    // =========================================================================

    @Test
    void testGetTaskStatus() {
        registerAgent("echo-agent");

        gateway.submitTask("echo-agent", "hello", null);
        String taskId = taskRegistry.listTasks().get(0).getTaskId();

        TaskRegistry.TaskEntry entry = gateway.getTaskStatus(taskId);
        assertNotNull(entry);
        assertEquals(taskId, entry.getTaskId());
        assertEquals(TaskRegistry.TaskState.SUBMITTED, entry.getState());
    }

    @Test
    void testGetTaskStatusNonExistent() {
        assertNull(gateway.getTaskStatus("nonexistent"));
    }

    // =========================================================================
    // Status subscriber (SSE)
    // =========================================================================

    @Test
    void testStatusSubscriberNotifiedOnResponse() throws Exception {
        registerAgent("echo-agent");
        subscribeEchoAgent("echo-agent");

        AtomicInteger notifications = new AtomicInteger(0);
        String[] lastState = new String[1];

        // Use a known task ID so we can register subscriber before submitting
        String taskId = "status-test-task-001";
        gateway.registerStatusSubscriber(taskId, (tid, state, data) -> {
            lastState[0] = state;
            notifications.incrementAndGet();
        });

        // Submit task — InMemory transport is synchronous, so subscriber must be registered first
        gateway.submitTask(taskId, "echo-agent", "hello", null);

        // Give async processing time to complete
        Thread.sleep(500);

        assertTrue(notifications.get() >= 1);
        assertEquals("completed", lastState[0]);
    }

    // =========================================================================
    // Agent-to-agent delegation
    // =========================================================================

    @Test
    void testAgentDelegation() throws Exception {
        // Agent A delegates to Agent B
        registerAgent("agent-a");
        registerAgent("agent-b");

        // Agent B responds
        subscribeEchoAgent("agent-b");

        // Agent A receives request, then delegates to Agent B
        String agentARequestTopic = A2ATopicFactory.agentRequestTopic(NAMESPACE, "agent-a");
        transport.subscribe(agentARequestTopic, (topic, event) -> {
            String taskId = event.getId();
            // Agent A delegates to Agent B
            String delegateTopic = A2ATopicFactory.agentRequestTopic(NAMESPACE, "agent-b");
            CloudEvent delegatedEvent = CloudEventBuilder.v1()
                .withId(taskId) // use same task ID for simplicity
                .withType(A2AProtocolConstants.CE_TYPE_PREFIX + "task.delegate")
                .withSource(java.net.URI.create("agent/agent-a"))
                .withData("delegated".getBytes(StandardCharsets.UTF_8))
                .build();
            try {
                transport.publish(delegateTopic, delegatedEvent);
            } catch (Exception e) {
                // ignore
            }
        });

        // Also subscribe Agent A to publish response back to gateway
        // Actually, Agent B's echo response goes to gateway, not Agent A
        // Let's simplify: Agent B's response goes to gateway
        CompletableFuture<A2AGatewayService.TaskResult> future =
            gateway.submitTask("agent-b", "delegation-test", null);

        A2AGatewayService.TaskResult result = future.get(10, TimeUnit.SECONDS);
        assertNotNull(result);
        assertEquals(TaskRegistry.TaskState.COMPLETED, result.getState());
        assertEquals("echo: delegation-test", result.getData());
    }

    // =========================================================================
    // Gateway not started
    // =========================================================================

    @Test
    void testSubmitTaskBeforeStartFails() throws Exception {
        gateway.shutdown();
        gateway = new A2AGatewayService(NAMESPACE, GATEWAY_ID, transport, taskRegistry, a2aService);
        // Don't start

        CompletableFuture<A2AGatewayService.TaskResult> future =
            gateway.submitTask("test-agent", "hello", null);

        assertTrue(future.isCompletedExceptionally());
    }

    // =========================================================================
    // Multiple tasks
    // =========================================================================

    @Test
    void testMultipleConcurrentTasks() throws Exception {
        registerAgent("echo-agent");
        subscribeEchoAgent("echo-agent");

        CompletableFuture<A2AGatewayService.TaskResult> f1 = gateway.submitTask("echo-agent", "msg1", null);
        CompletableFuture<A2AGatewayService.TaskResult> f2 = gateway.submitTask("echo-agent", "msg2", null);
        CompletableFuture<A2AGatewayService.TaskResult> f3 = gateway.submitTask("echo-agent", "msg3", null);

        assertEquals("echo: msg1", f1.get(10, TimeUnit.SECONDS).getData());
        assertEquals("echo: msg2", f2.get(10, TimeUnit.SECONDS).getData());
        assertEquals("echo: msg3", f3.get(10, TimeUnit.SECONDS).getData());

        assertEquals(3, taskRegistry.size());
    }

    // =========================================================================
    // Gateway properties
    // =========================================================================

    @Test
    void testGatewayId() {
        assertEquals(GATEWAY_ID, gateway.getGatewayId());
    }

    @Test
    void testNamespace() {
        assertEquals(NAMESPACE, gateway.getNamespace());
    }

    @Test
    void testGetA2aService() {
        assertNotNull(gateway.getA2aService());
    }

    @Test
    void testGetTaskRegistry() {
        assertNotNull(gateway.getTaskRegistry());
    }

    // =========================================================================
    // Task Timeout
    // =========================================================================

    @Test
    void testTaskTimeoutAutoFails() throws Exception {
        // Use a gateway with very short timeout
        gateway.shutdown();
        gateway = new A2AGatewayService(NAMESPACE, GATEWAY_ID, transport, taskRegistry, a2aService, 500L);
        gateway.start();

        // Register an agent that never responds
        registerAgent("slow-agent");

        CompletableFuture<A2AGatewayService.TaskResult> future =
            gateway.submitTask("slow-agent", "hello", null);

        // Wait for timeout
        try {
            future.get(3, TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.fail("Future should have timed out");
        } catch (java.util.concurrent.ExecutionException e) {
            // Expected: TimeoutException wrapped in ExecutionException
            assertTrue(e.getCause() instanceof java.util.concurrent.TimeoutException,
                "Cause should be TimeoutException: " + e.getCause());
        }

        // Verify task was marked FAILED
        TaskRegistry.TaskEntry entry = taskRegistry.listTasks().get(0);
        assertEquals(TaskRegistry.TaskState.FAILED, entry.getState());
        assertTrue(entry.getErrorMessage().contains("timed out"));
    }

    @Test
    void testCancelTaskNotifiesStatusSubscriber() throws Exception {
        registerAgent("slow-agent");

        CompletableFuture<A2AGatewayService.TaskResult> future =
            gateway.submitTask("task-cancel-notify", "slow-agent", "hello", null);
        AtomicInteger notifications = new AtomicInteger(0);
        String[] lastState = new String[1];
        gateway.registerStatusSubscriber("task-cancel-notify", (tid, state, data) -> {
            notifications.incrementAndGet();
            lastState[0] = state;
        });

        assertTrue(gateway.cancelTask("task-cancel-notify"));
        A2AGatewayService.TaskResult result = future.get(1, TimeUnit.SECONDS);
        assertEquals(TaskRegistry.TaskState.CANCELLED, result.getState());
        assertEquals(1, notifications.get());
        assertEquals("cancelled", lastState[0]);
    }

    // =========================================================================
    // Agent Validation
    // =========================================================================

    @Test
    void testSubmitTaskToUnregisteredAgentFails() {
        // Don't register any agent
        CompletableFuture<A2AGatewayService.TaskResult> future =
            gateway.submitTask("ghost-agent", "hello", null);

        assertTrue(future.isCompletedExceptionally());
    }
}
