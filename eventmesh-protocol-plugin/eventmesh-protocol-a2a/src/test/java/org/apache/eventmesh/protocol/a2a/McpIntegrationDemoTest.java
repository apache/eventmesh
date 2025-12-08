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

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.cloudevents.CloudEvent;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration Demo for MCP over EventMesh A2A.
 * Simulates a full interaction cycle between a Client Agent and a Tool Provider Agent.
 */
public class McpIntegrationDemoTest {

    private EnhancedA2AProtocolAdaptor adaptor;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        adaptor = new EnhancedA2AProtocolAdaptor();
        adaptor.initialize();
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testWeatherServiceInteraction() throws Exception {
        // ==========================================
        // 1. Client Side: Construct and Send Request
        // ==========================================
        String requestId = UUID.randomUUID().toString();
        String targetAgent = "weather-service-01";
        
        // Construct MCP JSON-RPC Request
        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put("name", "get_weather");
        requestParams.put("city", "Beijing");
        requestParams.put("_agentId", targetAgent); // Routing hint

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("jsonrpc", "2.0");
        requestMap.put("method", "tools/call");
        requestMap.put("params", requestParams);
        requestMap.put("id", requestId);

        String requestJson = objectMapper.writeValueAsString(requestMap);
        System.out.println("[Client] Sending Request: " + requestJson);

        // Client uses Adaptor to wrap into CloudEvent
        ProtocolTransportObject clientTransport = new MockProtocolTransportObject(requestJson);
        CloudEvent requestEvent = adaptor.toCloudEvent(clientTransport);

        // Verify Client Event properties
        Assertions.assertEquals("org.apache.eventmesh.a2a.tools.call.req", requestEvent.getType());
        Assertions.assertEquals("request", requestEvent.getExtension("mcptype"));
        Assertions.assertEquals(targetAgent, requestEvent.getExtension("targetagent"));
        Assertions.assertEquals(requestId, requestEvent.getId());

        // ==========================================
        // 2. EventMesh Transport (Simulation)
        // ==========================================
        // In a real scenario, EventMesh receives requestEvent and routes it to Server
        CloudEvent transportedEvent = requestEvent; // Simulate transport

        // ==========================================
        // 3. Server Side: Receive and Process
        // ==========================================
        // Server unpacks the event
        ProtocolTransportObject serverReceivedObj = adaptor.fromCloudEvent(transportedEvent);
        String receivedContent = serverReceivedObj.toString();
        JsonNode receivedNode = objectMapper.readTree(receivedContent);

        System.out.println("[Server] Received: " + receivedContent);
        
        // Verify content matches
        Assertions.assertEquals("tools/call", receivedNode.get("method").asText());
        Assertions.assertEquals(requestId, receivedNode.get("id").asText());

        // Execute Logic (Mocking weather service)
        String city = receivedNode.get("params").get("city").asText();
        String weatherResult = "Sunny, 25C in " + city;

        // Construct MCP JSON-RPC Response
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("text", weatherResult);

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("jsonrpc", "2.0");
        responseMap.put("result", resultData);
        responseMap.put("id", receivedNode.get("id").asText()); // Must echo ID

        String responseJson = objectMapper.writeValueAsString(responseMap);
        System.out.println("[Server] Sending Response: " + responseJson);

        // Server uses Adaptor to wrap Response
        ProtocolTransportObject serverResponseTransport = new MockProtocolTransportObject(responseJson);
        CloudEvent responseEvent = adaptor.toCloudEvent(serverResponseTransport);

        // Verify Server Event properties
        Assertions.assertEquals("org.apache.eventmesh.a2a.common.response", responseEvent.getType());
        Assertions.assertEquals("response", responseEvent.getExtension("mcptype"));
        // The critical part: Correlation ID must match Request ID
        Assertions.assertEquals(requestId, responseEvent.getExtension("collaborationid"));

        // ==========================================
        // 4. Client Side: Receive Response
        // ==========================================
        // Client receives responseEvent
        ProtocolTransportObject clientReceivedObj = adaptor.fromCloudEvent(responseEvent);
        JsonNode clientResponseNode = objectMapper.readTree(clientReceivedObj.toString());

        System.out.println("[Client] Received Response: " + clientReceivedObj.toString());

        // Verify final result
        Assertions.assertEquals(requestId, clientResponseNode.get("id").asText());
        Assertions.assertEquals("Sunny, 25C in Beijing", clientResponseNode.get("result").get("text").asText());
    }

    private static class MockProtocolTransportObject implements ProtocolTransportObject {
        private final String content;

        public MockProtocolTransportObject(String content) {
            this.content = content;
        }

        @Override
        public String toString() {
            return content;
        }
    }
}
