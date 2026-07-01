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
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class EnhancedA2AProtocolAdaptorTest {

    private EnhancedA2AProtocolAdaptor adaptor;

    @BeforeEach
    public void setUp() {
        adaptor = new EnhancedA2AProtocolAdaptor();
        adaptor.initialize();
    }

    @Test
    public void testMcpRequestProcessing() throws ProtocolHandleException {
        // Standard MCP JSON-RPC Request
        String json = "{\"jsonrpc\": \"2.0\", \"method\": \"tools/call\", \"params\": {\"name\": \"weather\"}, \"id\": \"req-001\"}";
        ProtocolTransportObject obj = new MockProtocolTransportObject(json);

        CloudEvent event = adaptor.toCloudEvent(obj);
        Assertions.assertNotNull(event);
        Assertions.assertEquals("request", event.getExtension("mcptype"));
        Assertions.assertEquals("tools/call", event.getExtension("a2amethod"));
        Assertions.assertEquals("org.apache.eventmesh.a2a.tools.call.req", event.getType());
        Assertions.assertEquals("req-001", event.getId()); // ID should be preserved
    }

    @Test
    public void testMcpResponseProcessing() throws ProtocolHandleException {
        // Standard MCP JSON-RPC Response
        String json = "{\"jsonrpc\": \"2.0\", \"result\": {\"temperature\": 25}, \"id\": \"req-001\"}";
        ProtocolTransportObject obj = new MockProtocolTransportObject(json);

        CloudEvent event = adaptor.toCloudEvent(obj);
        Assertions.assertNotNull(event);
        Assertions.assertEquals("response", event.getExtension("mcptype"));
        Assertions.assertEquals("org.apache.eventmesh.a2a.common.response", event.getType());
        Assertions.assertEquals("req-001", event.getExtension("collaborationid")); // ID should be mapped to correlationId
        Assertions.assertNotEquals("req-001", event.getId()); // Event ID should be new
    }

    @Test
    public void testMcpErrorResponseProcessing() throws ProtocolHandleException {
        // Standard MCP JSON-RPC Error Response
        String json = "{\"jsonrpc\": \"2.0\", \"error\": {\"code\": -32601, \"message\": \"Method not found\"}, \"id\": \"req-error-001\"}";
        ProtocolTransportObject obj = new MockProtocolTransportObject(json);

        CloudEvent event = adaptor.toCloudEvent(obj);
        Assertions.assertNotNull(event);
        Assertions.assertEquals("response", event.getExtension("mcptype"));
        Assertions.assertEquals("org.apache.eventmesh.a2a.common.response", event.getType());
        Assertions.assertEquals("req-error-001", event.getExtension("collaborationid"));
    }

    @Test
    public void testMcpNotificationProcessing() throws ProtocolHandleException {
        // MCP Notification (no ID)
        String json = "{\"jsonrpc\": \"2.0\", \"method\": \"notifications/initialized\"}";
        ProtocolTransportObject obj = new MockProtocolTransportObject(json);

        CloudEvent event = adaptor.toCloudEvent(obj);
        Assertions.assertNotNull(event);
        Assertions.assertEquals("request", event.getExtension("mcptype")); // Treated as request/event
        Assertions.assertEquals("notifications/initialized", event.getExtension("a2amethod"));
        Assertions.assertEquals("org.apache.eventmesh.a2a.notifications.initialized.req", event.getType());
        Assertions.assertNotNull(event.getId()); // Should generate a new ID
    }

    @Test
    public void testMcpBatchRequestProcessing() throws ProtocolHandleException {
        String json = "[{\"jsonrpc\": \"2.0\", \"method\": \"ping\", \"id\": \"1\"}, {\"jsonrpc\": \"2.0\", \"method\": \"ping\", \"id\": \"2\"}]";
        ProtocolTransportObject obj = new MockProtocolTransportObject(json);

        List<CloudEvent> events = adaptor.toBatchCloudEvent(obj);
        Assertions.assertEquals(2, events.size());
        
        boolean found1 = false;
        boolean found2 = false;
        for (CloudEvent e : events) {
            if ("1".equals(e.getId())) {
                found1 = true;
            }
            if ("2".equals(e.getId())) {
                found2 = true;
            }
        }
        Assertions.assertTrue(found1, "Should contain event with ID 1");
        Assertions.assertTrue(found2, "Should contain event with ID 2");
    }

    @Test
    public void testInvalidJsonProcessing() {
        String json = "{invalid-json}";
        ProtocolTransportObject obj = new MockProtocolTransportObject(json);

        Assertions.assertThrows(ProtocolHandleException.class, () -> {
            adaptor.toCloudEvent(obj);
        });
    }

    @Test
    public void testNullProtocolObject() {
        Assertions.assertFalse(adaptor.isValid(null));
    }

    @Test
    public void testFromCloudEventMcp() throws ProtocolHandleException {
        CloudEvent event = CloudEventBuilder.v1()
            .withId("test-id")
            .withSource(URI.create("test-source"))
            .withType("org.apache.eventmesh.a2a.tools.call.req")
            .withExtension("protocol", "A2A")
            .withExtension("a2amethod", "tools/call")
            .withData("{\"some\":\"data\"}".getBytes(StandardCharsets.UTF_8))
            .build();

        ProtocolTransportObject obj = adaptor.fromCloudEvent(event);
        Assertions.assertNotNull(obj);
        Assertions.assertEquals("{\"some\":\"data\"}", obj.toString());
    }

    @Test
    public void testA2AGetTaskProcessing() throws ProtocolHandleException {
        // Test standard A2A "Get Task" operation
        String json = "{\"jsonrpc\": \"2.0\", \"method\": \"task/get\", \"params\": {\"taskId\": \"task-123\"}, \"id\": \"req-002\"}";
        ProtocolTransportObject obj = new MockProtocolTransportObject(json);

        CloudEvent event = adaptor.toCloudEvent(obj);
        Assertions.assertNotNull(event);
        Assertions.assertEquals("task/get", event.getExtension("a2amethod"));
        Assertions.assertEquals("org.apache.eventmesh.a2a.task.get.req", event.getType());
    }

    @Test
    public void testA2AStreamingMessageProcessing() throws ProtocolHandleException {
        // Test standard A2A "Send Streaming Message" operation
        // Should map to .stream suffix
        String json = "{\"jsonrpc\": \"2.0\", \"method\": \"message/sendStream\", \"params\": {\"chunk\": \"data...\"}, \"id\": \"stream-001\"}";
        ProtocolTransportObject obj = new MockProtocolTransportObject(json);

        CloudEvent event = adaptor.toCloudEvent(obj);
        Assertions.assertNotNull(event);
        Assertions.assertEquals("message/sendStream", event.getExtension("a2amethod"));
        Assertions.assertEquals("org.apache.eventmesh.a2a.message.sendStream.stream", event.getType());
    }

    @Test
    public void testMcpPubSubRouting() throws ProtocolHandleException {
        // Test Pub/Sub Broadcast routing using _topic
        String json = "{"
            + "\"jsonrpc\": \"2.0\", "
            + "\"method\": \"market/update\", "
            + "\"params\": {\"symbol\": \"BTC\", \"price\": 50000, \"_topic\": \"market.crypto.btc\"}, "
            + "\"id\": \"pub-001\""
            + "}";
        ProtocolTransportObject obj = new MockProtocolTransportObject(json);

        CloudEvent event = adaptor.toCloudEvent(obj);
        Assertions.assertNotNull(event);
        Assertions.assertEquals("market/update", event.getExtension("a2amethod"));
        // Verify Subject is set for Pub/Sub
        Assertions.assertEquals("market.crypto.btc", event.getSubject());
        // Verify Target Agent is NOT set (Broadcast)
        Assertions.assertNull(event.getExtension("targetagent"));
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
