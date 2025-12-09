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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.cloudevents.CloudEvent;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for advanced MCP patterns: Pub/Sub and Streaming.
 */
public class McpPatternsIntegrationTest {

    private EnhancedA2AProtocolAdaptor adaptor;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        adaptor = new EnhancedA2AProtocolAdaptor();
        adaptor.initialize();
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testPubSubBroadcastPattern() throws Exception {
        // Scenario: A Market Data Publisher broadcasts price updates to a Topic.
        // Multiple Subscribers (simulated) receive it based on the Subject.

        String topic = "market.crypto.btc";
        String broadcastId = UUID.randomUUID().toString();

        // 1. Publisher constructs message
        Map<String, Object> params = new HashMap<>();
        params.put("price", 50000);
        params.put("currency", "USD");
        params.put("_topic", topic); // <--- Critical: Pub/Sub routing hint

        Map<String, Object> pubMessage = new HashMap<>();
        pubMessage.put("jsonrpc", "2.0");
        pubMessage.put("method", "market/update");
        pubMessage.put("params", params);
        pubMessage.put("id", broadcastId);

        String json = objectMapper.writeValueAsString(pubMessage);
        ProtocolTransportObject transport = new MockProtocolTransportObject(json);

        // 2. EventMesh processes the message
        CloudEvent event = adaptor.toCloudEvent(transport);

        // 3. Verify Routing Logic (Simulating EventMesh Router)
        // The router looks at the 'subject' to determine dispatch targets.
        Assertions.assertEquals(topic, event.getSubject());
        
        // Verify it is NOT a point-to-point message (no targetagent)
        Assertions.assertNull(event.getExtension("targetagent"));
        
        // Verify payload integrity
        Assertions.assertEquals("market/update", event.getExtension("a2amethod"));
        Assertions.assertEquals("request", event.getExtension("mcptype"));
    }

    @Test
    public void testStreamingPattern() throws Exception {
        // Scenario: An Agent streams a large response in chunks.
        // Client re-assembles based on Sequence ID.

        String streamId = UUID.randomUUID().toString();
        List<CloudEvent> receivedChunks = new ArrayList<>();

        // Simulate sending 3 chunks
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> params = new HashMap<>();
            params.put("chunk_data", "part-" + i);
            params.put("_seq", i); // <--- Critical: Ordering hint
            params.put("_agentId", "client-agent");

            Map<String, Object> chunkMsg = new HashMap<>();
            chunkMsg.put("jsonrpc", "2.0");
            chunkMsg.put("method", "message/sendStream");
            chunkMsg.put("params", params);
            chunkMsg.put("id", streamId); // Same ID for the stream session

            String json = objectMapper.writeValueAsString(chunkMsg);
            ProtocolTransportObject transport = new MockProtocolTransportObject(json);
            
            CloudEvent chunkEvent = adaptor.toCloudEvent(transport);
            receivedChunks.add(chunkEvent);
        }

        // Verify Chunks
        Assertions.assertEquals(3, receivedChunks.size());

        // Verify Chunk 1
        CloudEvent c1 = receivedChunks.get(0);
        Assertions.assertEquals("org.apache.eventmesh.a2a.message.sendStream.stream", c1.getType());
        Assertions.assertEquals("1", c1.getExtension("seq"));
        Assertions.assertEquals("client-agent", c1.getExtension("targetagent"));

        // Verify Chunk 3
        CloudEvent c3 = receivedChunks.get(2);
        Assertions.assertEquals("3", c3.getExtension("seq"));
        
        // In a real app, the receiver would collect these, sort by 'seq', and merge.
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
