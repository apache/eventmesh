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

import org.apache.eventmesh.protocol.a2a.A2AClient;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * True client-server HTTP integration test.
 *
 * <p>Starts {@link A2AGatewayServer} on a random port, then uses {@link A2AClient}
 * to exercise all A2A functionality over real HTTP:
 * <ul>
 *   <li>Agent card registration</li>
 *   <li>Agent listing</li>
 *   <li>Sync task submission + response</li>
 *   <li>Task status query</li>
 *   <li>Async task submission + cancellation</li>
 *   <li>Heartbeat</li>
 * </ul>
 *
 * <p>This test covers the full network path: A2AClient → HTTP → Netty → A2AGatewayHttpHandler → A2AGatewayService.
 */
class A2AClientServerIntegrationTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private A2AGatewayServer server;
    private String gatewayUrl;
    private A2AClient client;
    private CloseableHttpClient rawHttpClient;

    @BeforeEach
    void setUp() throws Exception {
        // Start server on random port
        server = new A2AGatewayServer(0);
        server.start();
        int port = server.getBoundPort();
        gatewayUrl = "http://localhost:" + port;

        // Create and start client (registers agent card + starts heartbeat)
        client = A2AClient.builder()
            .gatewayUrl(gatewayUrl)
            .namespace("global")
            .agentName("default/default/test-client-agent")
            .agentCard(AgentCardTestUtils.createValidCard("test-client-agent"))
            .heartbeatInterval(60_000L) // long interval so it doesn't interfere with tests
            .socketTimeoutMs(30_000)
            .build();
        client.start();

        rawHttpClient = HttpClients.createDefault();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.shutdown();
        }
        if (rawHttpClient != null) {
            rawHttpClient.close();
        }
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Registers an agent card via HTTP without subscribing to request topics.
     * The agent is "registered" but never responds — useful for testing async/cancel flows.
     */
    private void registerSlowAgent(String agentName) throws Exception {
        Map<String, Object> card = new HashMap<>();
        card.put("name", agentName);
        card.put("version", "1.0.0");
        card.put("description", "A slow agent for testing");

        // Required fields for schema validation
        java.util.List<Map<String, Object>> interfaces = new java.util.ArrayList<>();
        Map<String, Object> iface = new HashMap<>();
        iface.put("url", "http://localhost/a2a");
        iface.put("protocolBinding", "JSONRPC");
        iface.put("protocolVersion", "0.3");
        interfaces.add(iface);
        card.put("supportedInterfaces", interfaces);

        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("streaming", false);
        capabilities.put("pushNotifications", false);
        card.put("capabilities", capabilities);

        card.put("defaultInputModes", java.util.Collections.singletonList("text/plain"));
        card.put("defaultOutputModes", java.util.Collections.singletonList("text/plain"));

        java.util.List<Map<String, Object>> skills = new java.util.ArrayList<>();
        Map<String, Object> skill = new HashMap<>();
        skill.put("id", "slow-skill");
        skill.put("name", "Slow Skill");
        skill.put("description", "Does nothing");
        skill.put("tags", java.util.Collections.singletonList("test"));
        skills.add(skill);
        card.put("skills", skills);

        HttpPost post = new HttpPost(gatewayUrl + "/a2a/cards/card/default/default/" + agentName);
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(objectMapper.writeValueAsString(card), StandardCharsets.UTF_8));
        HttpResponse response = rawHttpClient.execute(post);
        int status = response.getStatusLine().getStatusCode();
        if (status >= 400) {
            String body = response.getEntity() != null
                ? EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8) : "";
            throw new RuntimeException("Failed to register slow agent: " + status + " " + body);
        }
    }

    // =========================================================================
    // Agent Card Registration & Listing
    // =========================================================================

    @Test
    void testPreRegisteredWeatherAgentIsListed() throws Exception {
        List<String> agents = client.listAgents();
        assertNotNull(agents);
        assertTrue(agents.contains("weather-agent"),
            "Pre-registered weather-agent should appear in agent list: " + agents);
    }

    @Test
    void testClientAgentRegistrationAppearsInList() throws Exception {
        List<String> agents = client.listAgents();
        assertNotNull(agents);
        assertTrue(agents.contains("test-client-agent"),
            "Client-registered test-client-agent should appear in agent list: " + agents);
    }

    // =========================================================================
    // Sync Task Submission
    // =========================================================================

    @Test
    void testSubmitTaskSyncToWeatherAgent() throws Exception {
        A2AClient.TaskResult result = client.sendTaskSync("weather-agent", "Beijing", null);

        assertNotNull(result);
        assertNotNull(result.getTaskId(), "taskId should be returned in sync response");
        assertEquals("COMPLETED", result.getState());
        assertNotNull(result.getData());
        assertTrue(result.getData().contains("Beijing"), "Response should mention Beijing: " + result.getData());
        assertTrue(result.getData().contains("sunny"), "Response should mention sunny: " + result.getData());
    }

    @Test
    void testSubmitTaskSyncReturnsValidTaskId() throws Exception {
        A2AClient.TaskResult result = client.sendTaskSync("weather-agent", "Shanghai", null);

        assertNotNull(result.getTaskId());
        assertTrue(result.getTaskId().startsWith("task-"),
            "Task ID should start with 'task-': " + result.getTaskId());
    }

    // =========================================================================
    // Task Status Query
    // =========================================================================

    @Test
    void testGetTaskStatusAfterCompletion() throws Exception {
        A2AClient.TaskResult result = client.sendTaskSync("weather-agent", "Guangzhou", null);
        String taskId = result.getTaskId();
        assertNotNull(taskId);

        // Query status via HTTP GET
        A2AClient.TaskResult status = client.getTaskStatus(taskId);
        assertNotNull(status);
        assertEquals(taskId, status.getTaskId());
        assertEquals("COMPLETED", status.getState());
        assertEquals("weather-agent", status.getTargetAgent());
        assertNotNull(status.getData());
        assertTrue(status.getData().contains("Guangzhou"));
    }

    @Test
    void testGetTaskStatusNotFound() throws Exception {
        A2AClient.TaskResult status = client.getTaskStatus("non-existent-task-id");
        assertNotNull(status);
        // Server returns 404 with error JSON; Jackson maps it to TaskResult.error
        assertTrue(status.getError() != null || status.getState() == null,
            "Should return error for non-existent task");
    }

    // =========================================================================
    // Async Task + Cancellation
    // =========================================================================

    @Test
    void testSubmitAsyncTaskAndGetStatus() throws Exception {
        // Register a slow agent that doesn't respond (card registered but no request handler)
        registerSlowAgent("slow-agent");

        String taskId = client.sendTaskAsync("slow-agent", "test message", null);
        assertNotNull(taskId);
        assertTrue(taskId.startsWith("task-"));

        // Query status — should be SUBMITTED
        A2AClient.TaskResult status = client.getTaskStatus(taskId);
        assertNotNull(status);
        assertEquals(taskId, status.getTaskId());
        assertEquals("SUBMITTED", status.getState());
    }

    @Test
    void testCancelTask() throws Exception {
        // Register a slow agent that doesn't respond
        registerSlowAgent("slow-agent");

        String taskId = client.sendTaskAsync("slow-agent", "cancel me", null);
        assertNotNull(taskId);

        // Cancel the task
        boolean cancelled = client.cancelTask(taskId);
        assertTrue(cancelled, "Task should be cancellable");

        // Verify state is CANCELLED
        A2AClient.TaskResult status = client.getTaskStatus(taskId);
        assertNotNull(status);
        assertEquals("CANCELLED", status.getState());
    }

    @Test
    void testCancelCompletedTaskFails() throws Exception {
        // Submit sync to weather-agent (completes immediately)
        A2AClient.TaskResult result = client.sendTaskSync("weather-agent", "Shenzhen", null);
        String taskId = result.getTaskId();

        // Try to cancel a completed task — should fail
        boolean cancelled = client.cancelTask(taskId);
        assertFalse(cancelled, "Completed task should not be cancellable");
    }

    @Test
    void testCancelNonExistentTaskFails() throws Exception {
        boolean cancelled = client.cancelTask("fake-task-id-99999");
        assertFalse(cancelled, "Non-existent task should not be cancellable");
    }

    // =========================================================================
    // Agent Validation
    // =========================================================================

    @Test
    void testSubmitTaskToUnregisteredAgentFails() throws Exception {
        // Attempt to submit a task to an agent that is not registered
        try {
            client.sendTaskSync("ghost-agent-not-registered", "hello", null);
            org.junit.jupiter.api.Assertions.fail(
                "Should throw exception for unregistered agent");
        } catch (Exception e) {
            // Expected: server returns 400 because agent is not registered
            assertTrue(e.getMessage().contains("not registered")
                || e.getMessage().contains("400")
                || e.getMessage().contains("Task submission failed"),
                "Error should mention unregistered agent: " + e.getMessage());
        }
    }

    // =========================================================================
    // Task Listing
    // =========================================================================

    @Test
    void testListTasksEndpoint() throws Exception {
        // Submit a task that completes
        A2AClient.TaskResult result = client.sendTaskSync("weather-agent", "ListTest", null);
        assertNotNull(result.getTaskId());

        // List all tasks via HTTP GET /a2a/tasks
        HttpGet get = new HttpGet(gatewayUrl + "/a2a/tasks");
        HttpResponse response = rawHttpClient.execute(get);
        assertEquals(200, response.getStatusLine().getStatusCode());

        String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        Map<?, ?> listResponse = objectMapper.readValue(body, Map.class);
        assertNotNull(listResponse.get("total"));
        assertNotNull(listResponse.get("count"));
        assertEquals(100, listResponse.get("limit"));
        assertEquals(0, listResponse.get("offset"));
        assertNotNull(listResponse.get("hasMore"));
        assertNotNull(listResponse.get("tasks"));
    }

    @Test
    void testListTasksEndpointWithPagination() throws Exception {
        client.sendTaskSync("weather-agent", "PageTest-1", null);
        client.sendTaskSync("weather-agent", "PageTest-2", null);
        client.sendTaskSync("weather-agent", "PageTest-3", null);

        HttpGet get = new HttpGet(gatewayUrl + "/a2a/tasks?state=COMPLETED&limit=2&offset=1");
        HttpResponse response = rawHttpClient.execute(get);
        assertEquals(200, response.getStatusLine().getStatusCode());

        String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        Map<?, ?> listResponse = objectMapper.readValue(body, Map.class);
        assertEquals(2, listResponse.get("limit"));
        assertEquals(1, listResponse.get("offset"));
        assertTrue(((Number) listResponse.get("count")).intValue() <= 2);
        assertNotNull(listResponse.get("tasks"));
    }

    @Test
    void testHealthEndpoint() throws Exception {
        HttpGet get = new HttpGet(gatewayUrl + "/a2a/health");
        HttpResponse response = rawHttpClient.execute(get);
        assertEquals(200, response.getStatusLine().getStatusCode());

        String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        Map<?, ?> health = objectMapper.readValue(body, Map.class);
        assertEquals("UP", health.get("status"));
        assertNotNull(health.get("gatewayId"));
        assertNotNull(health.get("namespace"));
        assertNotNull(health.get("taskCount"));
        assertNotNull(health.get("agentCount"));
        assertNotNull(health.get("timestamp"));
    }

    @Test
    void testCorsPreflightEndpoint() throws Exception {
        HttpOptions options = new HttpOptions(gatewayUrl + "/a2a/tasks");
        HttpResponse response = rawHttpClient.execute(options);
        assertEquals(200, response.getStatusLine().getStatusCode());
        assertEquals("*", response.getFirstHeader("Access-Control-Allow-Origin").getValue());
        assertTrue(response.getFirstHeader("Access-Control-Allow-Methods").getValue().contains("OPTIONS"));
        assertTrue(response.getFirstHeader("Access-Control-Allow-Headers").getValue().contains("Content-Type"));
    }

    @Test
    void testSseStreamThroughClientSdk() throws Exception {
        A2AClient.TaskResult result = client.sendTaskSync("weather-agent", "SSE-City", null);
        AtomicInteger eventCount = new AtomicInteger(0);
        String[] lastState = new String[1];
        String[] lastData = new String[1];

        // Build a short-timeout client for this bounded terminal-state SSE test.
        try (A2AClient sseClient = A2AClient.builder()
            .gatewayUrl(gatewayUrl)
            .namespace("global")
            .agentName("default/default/test-sse-client-agent")
            .agentCard(AgentCardTestUtils.createValidCard("test-sse-client-agent"))
            .heartbeatInterval(60_000L)
            .socketTimeoutMs(1_000)
            .build()) {
            sseClient.streamTaskStatus(result.getTaskId(), (taskId, state, data) -> {
                eventCount.incrementAndGet();
                lastState[0] = state;
                lastData[0] = data;
                return true;
            });
        }

        assertTrue(eventCount.get() >= 1);
        assertEquals("COMPLETED", lastState[0]);
        assertTrue(lastData[0].contains("SSE-City"));
    }

    // =========================================================================
    // Heartbeat
    // =========================================================================

    @Test
    void testHeartbeatForRegisteredAgent() throws Exception {
        // Send heartbeat for the pre-registered weather-agent
        Map<String, String> body = new HashMap<>();
        body.put("orgId", "default");
        body.put("unitId", "default");
        body.put("agentId", "weather-agent");

        HttpPost post = new HttpPost(gatewayUrl + "/a2a/heartbeat");
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));

        HttpResponse response = rawHttpClient.execute(post);
        assertEquals(200, response.getStatusLine().getStatusCode());

        String respBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        Map<?, ?> result = objectMapper.readValue(respBody, Map.class);
        assertEquals(true, result.get("ok"), "Heartbeat for registered agent should succeed: " + respBody);
    }

    @Test
    void testHeartbeatForUnregisteredAgentFails() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("orgId", "default");
        body.put("unitId", "default");
        body.put("agentId", "ghost-agent");

        HttpPost post = new HttpPost(gatewayUrl + "/a2a/heartbeat");
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));

        HttpResponse response = rawHttpClient.execute(post);
        assertEquals(200, response.getStatusLine().getStatusCode());

        String respBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        Map<?, ?> result = objectMapper.readValue(respBody, Map.class);
        assertEquals(false, result.get("ok"), "Heartbeat for unregistered agent should fail: " + respBody);
    }

    // =========================================================================
    // Multiple Tasks / Concurrency
    // =========================================================================

    @Test
    void testMultipleSyncTasks() throws Exception {
        String[] cities = {"Beijing", "Shanghai", "Guangzhou"};
        for (String city : cities) {
            A2AClient.TaskResult result = client.sendTaskSync("weather-agent", city, null);
            assertEquals("COMPLETED", result.getState());
            assertTrue(result.getData().contains(city),
                "Response for " + city + " should mention the city: " + result.getData());
        }
    }

    @Test
    void testParentChildTaskViaHttp() throws Exception {
        // Submit parent task (sync)
        A2AClient.TaskResult parentResult = client.sendTaskSync("weather-agent", "parent", null);
        String parentTaskId = parentResult.getTaskId();

        // Submit child task with parentTaskId (sync)
        A2AClient.TaskResult childResult = client.sendTaskSync("weather-agent", "child", parentTaskId);
        String childTaskId = childResult.getTaskId();

        assertNotNull(parentTaskId);
        assertNotNull(childTaskId);
        assertFalse(parentTaskId.equals(childTaskId), "Parent and child task IDs should differ");

        // Verify child task has parentTaskId set
        A2AClient.TaskResult childStatus = client.getTaskStatus(childTaskId);
        assertNotNull(childStatus);
        assertEquals(parentTaskId, childStatus.getParentTaskId(),
            "Child task should reference parent task ID");
    }
}
