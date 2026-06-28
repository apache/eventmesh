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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.protocol.a2a.A2AProtocolConstants;
import org.apache.eventmesh.protocol.a2a.A2ATopicFactory;
import org.apache.eventmesh.protocol.a2a.AgentIdentity;
import org.apache.eventmesh.protocol.a2a.model.AgentCard;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end test for A2A Gateway: task submission, agent response, and task registry.
 */
class A2AGatewayEndToEndTest {

    private InMemoryA2AMessageTransport transport;
    private TaskRegistry taskRegistry;
    private A2APublishSubscribeService a2aService;
    private A2AGatewayService gateway;

    private static final String NAMESPACE = "global";
    private static final String GATEWAY_ID = "test-gateway";
    private static final String AGENT_NAME = "echo-agent";

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

    @Test
    void testTaskSubmissionAndResponse() throws Exception {
        // Register agent
        AgentCard card = AgentCardTestUtils.createValidCard(AGENT_NAME);
        AgentIdentity identity = AgentIdentity.builder()
            .orgId("default").unitId("default").agentId(AGENT_NAME).build();
        a2aService.registerCard(identity, card);

        // Agent subscribes to request topic
        String requestTopic = A2ATopicFactory.agentRequestTopic(NAMESPACE, AGENT_NAME);
        transport.subscribe(requestTopic, (topic, event) -> {
            String taskId = event.getId();
            String responseTopic = A2ATopicFactory.gatewayResponseTopic(NAMESPACE, GATEWAY_ID, taskId);
            CloudEvent response = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withType(A2AProtocolConstants.CE_TYPE_PREFIX + "task.response")
                .withSource(java.net.URI.create("agent/" + AGENT_NAME))
                .withData("echo: hello".getBytes(StandardCharsets.UTF_8))
                .build();
            try {
                transport.publish(responseTopic, response);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Submit task
        java.util.concurrent.CompletableFuture<A2AGatewayService.TaskResult> future =
            gateway.submitTask(AGENT_NAME, "hello", null);

        A2AGatewayService.TaskResult result = future.get(10, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals(TaskRegistry.TaskState.COMPLETED, result.getState());
        assertEquals("echo: hello", result.getData());
    }

    @Test
    void testTaskRegistryLifecycle() {
        String taskId = "test-task-001";
        taskRegistry.createTask(taskId, null, "test-agent", "test-gateway");

        assertEquals(TaskRegistry.TaskState.SUBMITTED, taskRegistry.getTask(taskId).getState());

        assertTrue(taskRegistry.markWorking(taskId));
        assertEquals(TaskRegistry.TaskState.WORKING, taskRegistry.getTask(taskId).getState());

        assertTrue(taskRegistry.markCompleted(taskId, "done"));
        assertEquals(TaskRegistry.TaskState.COMPLETED, taskRegistry.getTask(taskId).getState());
        assertEquals("done", taskRegistry.getTask(taskId).getResult());
    }

    @Test
    void testTaskCancellation() {
        String taskId = "test-task-cancel";
        taskRegistry.createTask(taskId, null, "test-agent", "test-gateway");

        assertTrue(taskRegistry.markCancelled(taskId));
        assertEquals(TaskRegistry.TaskState.CANCELLED, taskRegistry.getTask(taskId).getState());
    }

    @Test
    void testParentChildTaskRelationship() {
        String parentId = "parent-task";
        String childId = "child-task";

        taskRegistry.createTask(parentId, null, "agent-a", "gateway-1");
        taskRegistry.createTask(childId, parentId, "agent-b", "gateway-1");

        java.util.List<String> children = taskRegistry.getChildTasks(parentId);
        assertEquals(1, children.size());
        assertEquals(childId, children.get(0));
    }

    @Test
    void testAgentCardTtlExpiry() throws Exception {
        // Use a very short TTL for testing
        a2aService.shutdown();
        a2aService = new A2APublishSubscribeService(null, 100L, 50L);
        a2aService.init();
        a2aService.start();

        AgentCard card = AgentCardTestUtils.createValidCard("ttl-agent");
        AgentIdentity identity = AgentIdentity.builder()
            .orgId("default").unitId("default").agentId("ttl-agent").build();

        a2aService.registerCard(identity, card);
        assertNotNull(a2aService.getCard(identity));

        // Wait for TTL to expire
        Thread.sleep(300);

        // Force cleanup
        a2aService.cleanupExpiredCards();

        // Card should be removed
        assertEquals(null, a2aService.getCard(identity));
    }

    @Test
    void testHeartbeatRefreshesTtl() throws Exception {
        a2aService.shutdown();
        a2aService = new A2APublishSubscribeService(null, 200L, 100L);
        a2aService.init();
        a2aService.start();

        AgentCard card = AgentCardTestUtils.createValidCard("hb-agent");
        AgentIdentity identity = AgentIdentity.builder()
            .orgId("default").unitId("default").agentId("hb-agent").build();

        a2aService.registerCard(identity, card);

        // Heartbeat before TTL expires
        Thread.sleep(100);
        assertTrue(a2aService.heartbeat(identity));

        // Should still be registered
        Thread.sleep(100);
        assertNotNull(a2aService.getCard(identity));
    }

    @Test
    void testTopicFactoryGeneratesCorrectTopics() {
        // Agent topics
        assertEquals("a2a/v1/agent/request/weather-agent",
            A2ATopicFactory.agentRequestTopic("global", "weather-agent"));
        assertEquals("a2a/v1/agent/status/weather-agent/task-123",
            A2ATopicFactory.agentStatusTopic("global", "weather-agent", "task-123"));
        assertEquals("a2a/v1/agent/response/weather-agent/task-123",
            A2ATopicFactory.agentResponseTopic("global", "weather-agent", "task-123"));

        // Gateway topics
        assertEquals("a2a/v1/gateway/status/gw-1/task-456",
            A2ATopicFactory.gatewayStatusTopic("global", "gw-1", "task-456"));
        assertEquals("a2a/v1/gateway/response/gw-1/task-456",
            A2ATopicFactory.gatewayResponseTopic("global", "gw-1", "task-456"));

        // Discovery topics
        assertEquals("a2a/v1/discovery/agentcards",
            A2ATopicFactory.discoveryTopic("global"));

        // Namespaced topics
        assertEquals("myorg/a2a/v1/agent/request/weather-agent",
            A2ATopicFactory.agentRequestTopic("myorg", "weather-agent"));
    }

    @Test
    void testTopicFactoryParsing() {
        A2ATopicFactory.ParsedTopic parsed = A2ATopicFactory.parse("a2a/v1/agent/request/weather-agent");
        assertNotNull(parsed);
        assertEquals(A2ATopicFactory.EntityType.AGENT_REQUEST, parsed.getEntityType());
        assertEquals("weather-agent", parsed.getAgentName());

        parsed = A2ATopicFactory.parse("a2a/v1/gateway/response/gw-1/task-789");
        assertNotNull(parsed);
        assertEquals(A2ATopicFactory.EntityType.GATEWAY_RESPONSE, parsed.getEntityType());
        assertEquals("gw-1", parsed.getGatewayId());
        assertEquals("task-789", parsed.getTaskId());

        parsed = A2ATopicFactory.parse("a2a/v1/discovery/agentcards");
        assertNotNull(parsed);
        assertEquals(A2ATopicFactory.EntityType.DISCOVERY_AGENT, parsed.getEntityType());
    }
}
