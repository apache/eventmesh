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

package org.apache.eventmesh.protocol.a2a.integration;

import org.apache.eventmesh.protocol.a2a.EnhancedA2AProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.EnhancedProtocolPluginFactory;
import org.apache.eventmesh.protocol.api.ProtocolRouter;
import org.apache.eventmesh.protocol.api.ProtocolMetrics;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.http.message.RequestMessage;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageRequestHeader;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for A2A protocol with existing EventMesh protocols.
 * Tests protocol interoperability, routing, and performance characteristics.
 */
public class A2AProtocolIntegrationTest {

    private EnhancedA2AProtocolAdaptor a2aAdaptor;
    private ProtocolRouter protocolRouter;
    private ProtocolMetrics protocolMetrics;

    @BeforeEach
    void setUp() {
        a2aAdaptor = new EnhancedA2AProtocolAdaptor();
        a2aAdaptor.initialize();
        
        protocolRouter = ProtocolRouter.getInstance();
        protocolMetrics = ProtocolMetrics.getInstance();
        
        // Reset metrics for clean test
        protocolMetrics.resetAllStats();
    }

    @Test
    void testA2ACloudEventsInteroperability() throws Exception {
        // Test that A2A messages can be converted to CloudEvents and back
        RequestMessage a2aMessage = createA2AMessage(
            "REGISTER",
            "agent-001",
            "task-executor",
            List.of("data-processing")
        );
        
        // Convert A2A to CloudEvent
        CloudEvent cloudEvent = a2aAdaptor.toCloudEvent(a2aMessage);
        
        // Verify CloudEvent contains A2A extensions
        assertNotNull(cloudEvent);
        assertEquals("A2A", cloudEvent.getExtension("protocol"));
        assertEquals("2.0", cloudEvent.getExtension("protocolVersion"));
        assertEquals("REGISTER", cloudEvent.getExtension("messageType"));
        assertEquals("agent-001", cloudEvent.getExtension("sourceAgent"));
        
        // Convert back to protocol transport object
        ProtocolTransportObject transportObject = a2aAdaptor.fromCloudEvent(cloudEvent);
        assertNotNull(transportObject);
    }

    @Test
    void testA2AProtocolRouting() throws Exception {
        // Test intelligent routing with A2A messages
        RequestMessage a2aMessage = createA2AMessage(
            "TASK_REQUEST",
            "agent-001",
            "task-executor",
            List.of("data-processing")
        );
        
        long startTime = System.currentTimeMillis();
        
        // Route message through protocol router
        CloudEvent routedEvent = protocolRouter.routeMessage(a2aMessage, "A2A");
        
        long routingTime = System.currentTimeMillis() - startTime;
        
        // Verify routing
        assertNotNull(routedEvent);
        assertEquals("A2A", routedEvent.getExtension("protocol"));
        assertTrue(routingTime < 100, "Routing should complete within 100ms");
        
        // Verify metrics were recorded
        ProtocolMetrics.ProtocolStats stats = protocolMetrics.getStats("A2A");
        if (stats != null) {
            assertTrue(stats.getTotalOperations() > 0);
        }
    }

    @Test
    void testA2ABatchProcessing() throws Exception {
        // Test batch processing with multiple A2A messages
        List<RequestMessage> batchMessages = IntStream.range(0, 10)
            .mapToObj(i -> createA2AMessage(
                "REGISTER",
                "agent-" + i,
                "task-executor",
                List.of("data-processing")
            ))
            .toList();
        
        long startTime = System.currentTimeMillis();
        
        // Process batch through router
        List<CloudEvent> routedEvents = protocolRouter.routeMessages(
            batchMessages.stream()
                .map(msg -> (ProtocolTransportObject) msg)
                .toList(),
            "A2A"
        );
        
        long batchTime = System.currentTimeMillis() - startTime;
        
        // Verify batch processing
        assertNotNull(routedEvents);
        assertEquals(10, routedEvents.size());
        assertTrue(batchTime < 500, "Batch processing should complete within 500ms");
        
        // Verify all events are A2A protocol
        routedEvents.forEach(event -> {
            assertEquals("A2A", event.getExtension("protocol"));
            assertNotNull(event.getExtension("sourceAgent"));
        });
    }

    @Test
    void testProtocolFallbackMechanism() throws Exception {
        // Test fallback when A2A protocol is not available
        RequestMessage nonA2aMessage = createNonA2AMessage();
        
        // Should fall back to existing protocol adaptors
        CloudEvent routedEvent = protocolRouter.routeMessage(nonA2aMessage, "A2A");
        
        assertNotNull(routedEvent);
        // Should be processed by fallback protocol
    }

    @Test
    @EnabledIf("isPerformanceTestEnabled")
    void testA2AProtocolPerformance() throws Exception {
        // Performance test with concurrent A2A message processing
        int messageCount = 1000;
        int concurrentThreads = 10;
        
        CompletableFuture<Void>[] futures = new CompletableFuture[concurrentThreads];
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < concurrentThreads; i++) {
            final int threadId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < messageCount / concurrentThreads; j++) {
                    try {
                        RequestMessage message = createA2AMessage(
                            "HEARTBEAT",
                            "agent-" + threadId + "-" + j,
                            "task-executor",
                            List.of("heartbeat")
                        );
                        
                        CloudEvent event = a2aAdaptor.toCloudEvent(message);
                        assertNotNull(event);
                        
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
        
        // Wait for all threads to complete
        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        
        long totalTime = System.currentTimeMillis() - startTime;
        double messagesPerSecond = (messageCount * 1000.0) / totalTime;
        
        System.out.printf("A2A Protocol Performance: %.2f messages/sec%n", messagesPerSecond);
        
        // Performance assertions
        assertTrue(messagesPerSecond > 1000, "Should process more than 1000 messages/sec");
        assertTrue(totalTime < 10000, "Should complete within 10 seconds");
        
        // Verify metrics
        ProtocolMetrics.ProtocolStats stats = protocolMetrics.getStats("A2A");
        if (stats != null) {
            assertEquals(messageCount, stats.getTotalOperations());
            assertTrue(stats.getSuccessRate() > 99.0, "Success rate should be > 99%");
        }
    }

    @Test
    void testA2AProtocolMetrics() throws Exception {
        // Test metrics collection for A2A protocol
        String protocolType = "A2A";
        
        // Process some messages to generate metrics
        for (int i = 0; i < 5; i++) {
            long start = System.currentTimeMillis();
            
            RequestMessage message = createA2AMessage(
                "STATE_SYNC",
                "agent-" + i,
                "analytics-engine",
                List.of("state-sync")
            );
            
            try {
                CloudEvent event = a2aAdaptor.toCloudEvent(message);
                assertNotNull(event);
                
                long duration = System.currentTimeMillis() - start;
                protocolMetrics.recordSuccess(protocolType, "toCloudEvent", duration);
                
            } catch (Exception e) {
                protocolMetrics.recordFailure(protocolType, "toCloudEvent", e.getMessage());
                throw e;
            }
        }
        
        // Verify metrics were collected
        ProtocolMetrics.ProtocolStats stats = protocolMetrics.getStats(protocolType);
        assertNotNull(stats);
        
        assertEquals(5, stats.getTotalOperations());
        assertEquals(0, stats.getTotalErrors());
        assertEquals(100.0, stats.getSuccessRate());
        
        ProtocolMetrics.OperationStats opStats = stats.getOperationStats("toCloudEvent");
        assertNotNull(opStats);
        assertEquals(5, opStats.getSuccessCount());
        assertTrue(opStats.getAverageDuration() >= 0);
    }

    @Test
    void testA2AProtocolCapabilities() {
        // Test protocol capabilities and metadata
        assertTrue(a2aAdaptor.isValid(createA2AMessage("TEST", "agent", "type", List.of())));
        assertTrue(a2aAdaptor.supportsBatchProcessing());
        assertEquals(90, a2aAdaptor.getPriority());
        assertEquals("A2A", a2aAdaptor.getProtocolType());
        assertEquals("2.0", a2aAdaptor.getVersion());
        
        var capabilities = a2aAdaptor.getCapabilities();
        assertTrue(capabilities.contains("agent-communication"));
        assertTrue(capabilities.contains("workflow-orchestration"));
        assertTrue(capabilities.contains("cloudevents-compatible"));
        assertTrue(capabilities.contains("multi-protocol-transport"));
    }

    @Test
    void testA2AProtocolValidation() {
        // Test message validation
        assertFalse(a2aAdaptor.isValid(null));
        
        // Valid A2A message
        RequestMessage validMessage = createA2AMessage(
            "REGISTER", "agent-001", "executor", List.of("test")
        );
        assertTrue(a2aAdaptor.isValid(validMessage));
        
        // Non-A2A message
        RequestMessage nonA2aMessage = createNonA2AMessage();
        // Should still be valid due to fallback to existing adaptors
        assertTrue(a2aAdaptor.isValid(nonA2aMessage));
    }

    @Test
    void testProtocolExtensionIntegration() throws Exception {
        // Test integration with protocol extension system
        RequestMessage a2aMessage = createA2AMessage(
            "COLLABORATION",
            "orchestrator-001",
            "orchestrator",
            List.of("workflow-management")
        );
        
        CloudEvent cloudEvent = a2aAdaptor.toCloudEvent(a2aMessage);
        
        // Verify A2A extensions are properly set
        assertNotNull(cloudEvent.getExtension("protocol"));
        assertNotNull(cloudEvent.getExtension("protocolVersion"));
        assertNotNull(cloudEvent.getExtension("messageType"));
        assertNotNull(cloudEvent.getExtension("sourceAgent"));
        
        // Verify CloudEvent can be processed by other protocols
        String eventJson = JsonUtils.toJSONString(cloudEvent);
        assertNotNull(eventJson);
        assertTrue(eventJson.contains("A2A"));
    }

    // Helper methods
    private RequestMessage createA2AMessage(String messageType, String agentId, 
                                           String agentType, List<String> capabilities) {
        SendMessageRequestHeader header = new SendMessageRequestHeader();
        header.putHeader("protocol", "A2A");
        
        SendMessageRequestBody body = new SendMessageRequestBody();
        
        Map<String, Object> a2aMessage = Map.of(
            "protocol", "A2A",
            "messageType", messageType,
            "sourceAgent", Map.of(
                "agentId", agentId,
                "agentType", agentType,
                "capabilities", capabilities
            ),
            "targetAgent", Map.of(
                "agentId", "system",
                "agentType", "system"
            ),
            "payload", Map.of("action", messageType.toLowerCase())
        );
        
        body.setContent(JsonUtils.toJSONString(a2aMessage));
        
        return new RequestMessage(header, body);
    }

    private RequestMessage createNonA2AMessage() {
        SendMessageRequestHeader header = new SendMessageRequestHeader();
        header.putHeader("protocol", "HTTP");
        
        SendMessageRequestBody body = new SendMessageRequestBody();
        body.setContent("{\"message\":\"regular http message\"}");
        
        return new RequestMessage(header, body);
    }

    private static boolean isPerformanceTestEnabled() {
        // Enable performance tests via system property
        return Boolean.getBoolean("eventmesh.test.performance.enabled");
    }
}