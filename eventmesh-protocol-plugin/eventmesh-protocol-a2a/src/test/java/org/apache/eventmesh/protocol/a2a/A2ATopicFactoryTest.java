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

package org.apache.eventmesh.protocol.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link A2ATopicFactory}.
 */
class A2ATopicFactoryTest {

    private static final String GLOBAL = "global";
    private static final String NS = "myorg";

    // =========================================================================
    // Discovery topics
    // =========================================================================

    @Test
    void testDiscoveryTopicGlobal() {
        assertEquals("a2a/v1/discovery/agentcards", A2ATopicFactory.discoveryTopic(GLOBAL));
    }

    @Test
    void testDiscoveryTopicNamespaced() {
        assertEquals("myorg/a2a/v1/discovery/agentcards", A2ATopicFactory.discoveryTopic(NS));
    }

    @Test
    void testGatewayDiscoveryTopicGlobal() {
        assertEquals("a2a/v1/discovery/gatewaycards", A2ATopicFactory.gatewayDiscoveryTopic(GLOBAL));
    }

    @Test
    void testGatewayDiscoveryTopicNamespaced() {
        assertEquals("myorg/a2a/v1/discovery/gatewaycards", A2ATopicFactory.gatewayDiscoveryTopic(NS));
    }

    // =========================================================================
    // Agent topics
    // =========================================================================

    @Test
    void testAgentRequestTopicGlobal() {
        assertEquals("a2a/v1/agent/request/weather-agent",
            A2ATopicFactory.agentRequestTopic(GLOBAL, "weather-agent"));
    }

    @Test
    void testAgentRequestTopicNamespaced() {
        assertEquals("myorg/a2a/v1/agent/request/weather-agent",
            A2ATopicFactory.agentRequestTopic(NS, "weather-agent"));
    }

    @Test
    void testAgentStatusTopic() {
        assertEquals("a2a/v1/agent/status/weather-agent/task-123",
            A2ATopicFactory.agentStatusTopic(GLOBAL, "weather-agent", "task-123"));
    }

    @Test
    void testAgentResponseTopic() {
        assertEquals("a2a/v1/agent/response/weather-agent/task-123",
            A2ATopicFactory.agentResponseTopic(GLOBAL, "weather-agent", "task-123"));
    }

    @Test
    void testAgentStatusWildcardTopic() {
        assertEquals("a2a/v1/agent/status/weather-agent/+",
            A2ATopicFactory.agentStatusWildcardTopic(GLOBAL, "weather-agent"));
    }

    @Test
    void testAgentResponseWildcardTopic() {
        assertEquals("a2a/v1/agent/response/weather-agent/+",
            A2ATopicFactory.agentResponseWildcardTopic(GLOBAL, "weather-agent"));
    }

    // =========================================================================
    // Gateway topics
    // =========================================================================

    @Test
    void testGatewayRequestTopic() {
        assertEquals("a2a/v1/gateway/request/gw-1",
            A2ATopicFactory.gatewayRequestTopic(GLOBAL, "gw-1"));
    }

    @Test
    void testGatewayStatusTopic() {
        assertEquals("a2a/v1/gateway/status/gw-1/task-456",
            A2ATopicFactory.gatewayStatusTopic(GLOBAL, "gw-1", "task-456"));
    }

    @Test
    void testGatewayResponseTopic() {
        assertEquals("a2a/v1/gateway/response/gw-1/task-456",
            A2ATopicFactory.gatewayResponseTopic(GLOBAL, "gw-1", "task-456"));
    }

    @Test
    void testGatewayStatusWildcardTopic() {
        assertEquals("a2a/v1/gateway/status/gw-1/+",
            A2ATopicFactory.gatewayStatusWildcardTopic(GLOBAL, "gw-1"));
    }

    @Test
    void testGatewayResponseWildcardTopic() {
        assertEquals("a2a/v1/gateway/response/gw-1/+",
            A2ATopicFactory.gatewayResponseWildcardTopic(GLOBAL, "gw-1"));
    }

    // =========================================================================
    // Validation
    // =========================================================================

    @Test
    void testInvalidAgentNameThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> A2ATopicFactory.agentRequestTopic(GLOBAL, "agent with spaces"));
    }

    @Test
    void testInvalidTaskIdThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> A2ATopicFactory.agentStatusTopic(GLOBAL, "agent-1", "task/with/slashes"));
    }

    @Test
    void testInvalidGatewayIdThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> A2ATopicFactory.gatewayResponseTopic(GLOBAL, "gw#1", "task-1"));
    }

    @Test
    void testNullAgentNameThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> A2ATopicFactory.agentRequestTopic(GLOBAL, null));
    }

    @Test
    void testValidSpecialChars() {
        // These should not throw: letters, digits, dots, underscores, hyphens
        assertEquals("a2a/v1/agent/request/agent_1.v2-3",
            A2ATopicFactory.agentRequestTopic(GLOBAL, "agent_1.v2-3"));
    }

    // =========================================================================
    // Parsing
    // =========================================================================

    @Test
    void testParseAgentRequestGlobal() {
        A2ATopicFactory.ParsedTopic parsed = A2ATopicFactory.parse("a2a/v1/agent/request/weather-agent");
        assertNotNull(parsed);
        assertEquals(A2ATopicFactory.EntityType.AGENT_REQUEST, parsed.getEntityType());
        assertEquals("weather-agent", parsed.getAgentName());
        assertEquals(GLOBAL, parsed.getNamespace());
        assertNull(parsed.getTaskId());
        assertTrue(parsed.isRequest());
        assertFalse(parsed.isStatus());
        assertFalse(parsed.isResponse());
        assertFalse(parsed.isDiscovery());
    }

    @Test
    void testParseAgentStatusWithTaskId() {
        A2ATopicFactory.ParsedTopic parsed = A2ATopicFactory.parse("a2a/v1/agent/status/weather-agent/task-789");
        assertNotNull(parsed);
        assertEquals(A2ATopicFactory.EntityType.AGENT_STATUS, parsed.getEntityType());
        assertEquals("weather-agent", parsed.getAgentName());
        assertEquals("task-789", parsed.getTaskId());
        assertTrue(parsed.isStatus());
    }

    @Test
    void testParseAgentResponse() {
        A2ATopicFactory.ParsedTopic parsed = A2ATopicFactory.parse("a2a/v1/agent/response/agent-a/task-001");
        assertNotNull(parsed);
        assertEquals(A2ATopicFactory.EntityType.AGENT_RESPONSE, parsed.getEntityType());
        assertTrue(parsed.isResponse());
    }

    @Test
    void testParseGatewayResponse() {
        A2ATopicFactory.ParsedTopic parsed = A2ATopicFactory.parse("a2a/v1/gateway/response/gw-1/task-789");
        assertNotNull(parsed);
        assertEquals(A2ATopicFactory.EntityType.GATEWAY_RESPONSE, parsed.getEntityType());
        assertEquals("gw-1", parsed.getGatewayId());
        assertEquals("task-789", parsed.getTaskId());
        assertTrue(parsed.isResponse());
    }

    @Test
    void testParseGatewayStatus() {
        A2ATopicFactory.ParsedTopic parsed = A2ATopicFactory.parse("a2a/v1/gateway/status/gw-1/task-789");
        assertNotNull(parsed);
        assertEquals(A2ATopicFactory.EntityType.GATEWAY_STATUS, parsed.getEntityType());
        assertTrue(parsed.isStatus());
    }

    @Test
    void testParseGatewayRequest() {
        A2ATopicFactory.ParsedTopic parsed = A2ATopicFactory.parse("a2a/v1/gateway/request/gw-1");
        assertNotNull(parsed);
        assertEquals(A2ATopicFactory.EntityType.GATEWAY_REQUEST, parsed.getEntityType());
        assertEquals("gw-1", parsed.getGatewayId());
        assertTrue(parsed.isRequest());
    }

    @Test
    void testParseDiscoveryAgent() {
        A2ATopicFactory.ParsedTopic parsed = A2ATopicFactory.parse("a2a/v1/discovery/agentcards");
        assertNotNull(parsed);
        assertEquals(A2ATopicFactory.EntityType.DISCOVERY_AGENT, parsed.getEntityType());
        assertTrue(parsed.isDiscovery());
    }

    @Test
    void testParseDiscoveryGateway() {
        A2ATopicFactory.ParsedTopic parsed = A2ATopicFactory.parse("a2a/v1/discovery/gatewaycards");
        assertNotNull(parsed);
        assertEquals(A2ATopicFactory.EntityType.DISCOVERY_GATEWAY, parsed.getEntityType());
        assertTrue(parsed.isDiscovery());
    }

    @Test
    void testParseNamespacedTopic() {
        A2ATopicFactory.ParsedTopic parsed = A2ATopicFactory.parse("myorg/a2a/v1/agent/request/weather-agent");
        assertNotNull(parsed);
        assertEquals("myorg", parsed.getNamespace());
        assertEquals(A2ATopicFactory.EntityType.AGENT_REQUEST, parsed.getEntityType());
        assertEquals("weather-agent", parsed.getAgentName());
    }

    @Test
    void testParseNamespacedGatewayResponse() {
        A2ATopicFactory.ParsedTopic parsed = A2ATopicFactory.parse("myorg/a2a/v1/gateway/response/gw-1/task-1");
        assertNotNull(parsed);
        assertEquals("myorg", parsed.getNamespace());
        assertEquals(A2ATopicFactory.EntityType.GATEWAY_RESPONSE, parsed.getEntityType());
        assertEquals("gw-1", parsed.getGatewayId());
        assertEquals("task-1", parsed.getTaskId());
    }

    // =========================================================================
    // Invalid parsing
    // =========================================================================

    @Test
    void testParseNullReturnsNull() {
        assertNull(A2ATopicFactory.parse(null));
    }

    @Test
    void testParseEmptyReturnsNull() {
        assertNull(A2ATopicFactory.parse(""));
    }

    @Test
    void testParseNonA2ATopicReturnsNull() {
        assertNull(A2ATopicFactory.parse("some/random/topic"));
    }

    @Test
    void testParseInvalidDiscoveryReturnsNull() {
        assertNull(A2ATopicFactory.parse("a2a/v1/discovery/unknown"));
    }

    @Test
    void testParseInvalidActionReturnsNull() {
        assertNull(A2ATopicFactory.parse("a2a/v1/agent/unknown/agent-1"));
    }

    @Test
    void testParseTooShortReturnsNull() {
        assertNull(A2ATopicFactory.parse("a2a/v1"));
    }

    // =========================================================================
    // Round-trip: generate then parse
    // =========================================================================

    @Test
    void testRoundTripAgentRequest() {
        String topic = A2ATopicFactory.agentRequestTopic(NS, "my-agent");
        A2ATopicFactory.ParsedTopic parsed = A2ATopicFactory.parse(topic);
        assertNotNull(parsed);
        assertEquals(NS, parsed.getNamespace());
        assertEquals("my-agent", parsed.getAgentName());
        assertEquals(A2ATopicFactory.EntityType.AGENT_REQUEST, parsed.getEntityType());
    }

    @Test
    void testRoundTripGatewayResponse() {
        String topic = A2ATopicFactory.gatewayResponseTopic(NS, "gw-1", "task-abc");
        A2ATopicFactory.ParsedTopic parsed = A2ATopicFactory.parse(topic);
        assertNotNull(parsed);
        assertEquals(NS, parsed.getNamespace());
        assertEquals("gw-1", parsed.getGatewayId());
        assertEquals("task-abc", parsed.getTaskId());
        assertEquals(A2ATopicFactory.EntityType.GATEWAY_RESPONSE, parsed.getEntityType());
    }

    @Test
    void testParsedTopicToString() {
        A2ATopicFactory.ParsedTopic parsed = A2ATopicFactory.parse("a2a/v1/agent/request/test-agent");
        assertNotNull(parsed);
        String str = parsed.toString();
        assertTrue(str.contains("agentName='test-agent'"));
        assertTrue(str.contains("AGENT_REQUEST"));
    }
}
