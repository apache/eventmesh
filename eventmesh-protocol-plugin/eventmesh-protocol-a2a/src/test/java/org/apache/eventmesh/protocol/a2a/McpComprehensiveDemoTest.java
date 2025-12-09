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
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Comprehensive Demo Suite for EventMesh A2A Protocol v2.0.
 * Demonstrates 2 Protocols (MCP, CloudEvents) x 3 Patterns (RPC, PubSub, Streaming).
 */
public class McpComprehensiveDemoTest {

    private EnhancedA2AProtocolAdaptor adaptor;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        adaptor = new EnhancedA2AProtocolAdaptor();
        adaptor.initialize();
        objectMapper = new ObjectMapper();
    }

    // ============================================================================================
    // PROTOCOL 1: JSON-RPC 2.0 (MCP Mode) - "Battery Included"
    // ============================================================================================

    /**
     * Pattern 1: RPC (Point-to-Point Request/Response)
     * Use Case: Client asks "weather-service" for data, waits for result.
     */
    @Test
    public void demo_MCP_RPC_Pattern() throws Exception {
        String reqId = "rpc-101";

        // 1. Client: Construct JSON-RPC Request
        // Note: _agentId implies Point-to-Point routing
        String requestJson = "{"
            + "\"jsonrpc\": \"2.0\","
            + "\"method\": \"tools/call\","
            + "\"params\": { \"name\": \"weather\", \"city\": \"Shanghai\", \"_agentId\": \"agent-weather\" },"
            + "\"id\": \"" + reqId + "\""
            + "}";

        // 2. EventMesh: Process Ingress
        CloudEvent reqEvent = adaptor.toCloudEvent(new MockProtocolTransportObject(requestJson));

        // 3. Verification (Routing & Semantics)
        Assertions.assertEquals("agent-weather", reqEvent.getExtension("targetagent"), "Should route to specific agent");
        Assertions.assertEquals("request", reqEvent.getExtension("mcptype"));
        Assertions.assertEquals("org.apache.eventmesh.a2a.tools.call.req", reqEvent.getType());

        // --- Simulate Server Processing ---

        // 4. Server: Construct JSON-RPC Response
        // Note: Must echo the same ID
        String responseJson = "{"
            + "\"jsonrpc\": \"2.0\","
            + "\"result\": { \"temp\": 25 },"
            + "\"id\": \"" + reqId + "\" "
            + "}";

        // 5. EventMesh: Process Response
        CloudEvent respEvent = adaptor.toCloudEvent(new MockProtocolTransportObject(responseJson));

        // 6. Verification (Correlation)
        Assertions.assertEquals("response", respEvent.getExtension("mcptype"));
        Assertions.assertEquals(reqId, respEvent.getExtension("collaborationid"), "Response must link back to Request ID");
    }

    /**
     * Pattern 2: Pub/Sub (Broadcast)
     * Use Case: Publisher broadcasts "market/update", multiple subscribers receive it.
     */
    @Test
    public void demo_MCP_PubSub_Pattern() throws Exception {
        // 1. Publisher: Construct JSON-RPC Notification (or Request)
        // Note: _topic implies Broadcast routing
        String pubJson = "{"
            + "\"jsonrpc\": \"2.0\","
            + "\"method\": \"market/update\","
            + "\"params\": { \"symbol\": \"BTC\", \"price\": 90000, \"_topic\": \"market.crypto\" }"
            + "}"; // No ID (Notification) or ID (Request) both work, usually Notifications for PubSub

        // 2. EventMesh: Process Ingress
        CloudEvent event = adaptor.toCloudEvent(new MockProtocolTransportObject(pubJson));

        // 3. Verification (Routing)
        Assertions.assertEquals("market.crypto", event.getSubject(), "Subject should match _topic");
        Assertions.assertNull(event.getExtension("targetagent"), "Target Agent should be null for Broadcast");
        Assertions.assertEquals("market/update", event.getExtension("a2amethod"));
    }

    /**
     * Pattern 3: Streaming
     * Use Case: Agent streams a large file in chunks.
     */
    @Test
    public void demo_MCP_Streaming_Pattern() throws Exception {
        String streamId = "stream-session-500";

        // 1. Sender: Send Chunk 1
        // Note: _seq implies ordering
        String chunk1Json = "{"
            + "\"jsonrpc\": \"2.0\","
            + "\"method\": \"message/sendStream\","
            + "\"params\": { \"data\": \"part1\", \"_seq\": 1, \"_agentId\": \"receiver\" },"
            + "\"id\": \"" + streamId + "\""
            + "}";

        // 2. EventMesh: Process
        CloudEvent event1 = adaptor.toCloudEvent(new MockProtocolTransportObject(chunk1Json));

        // 3. Verification
        Assertions.assertEquals("org.apache.eventmesh.a2a.message.sendStream.stream", event1.getType(), "Type should indicate streaming");
        Assertions.assertEquals("1", event1.getExtension("seq"), "Sequence number must be preserved");
        Assertions.assertEquals("receiver", event1.getExtension("targetagent"));
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
