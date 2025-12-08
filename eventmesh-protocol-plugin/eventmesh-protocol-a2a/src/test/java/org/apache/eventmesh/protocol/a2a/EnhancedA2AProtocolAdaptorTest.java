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

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        Assertions.assertEquals("1", events.get(0).getId());
        Assertions.assertEquals("2", events.get(1).getId());
    }
    
    @Test
    public void testLegacyA2AMessageProcessing() throws ProtocolHandleException {
        String json = "{\"protocol\":\"A2A\",\"messageType\":\"PROPOSE\",\"sourceAgent\":{\"agentId\":\"agent1\"}}";
        ProtocolTransportObject obj = new MockProtocolTransportObject(json);

        CloudEvent event = adaptor.toCloudEvent(obj);
        Assertions.assertEquals("A2A", event.getExtension("protocol"));
        Assertions.assertEquals("PROPOSE", event.getExtension("messagetype"));
        Assertions.assertEquals("org.apache.eventmesh.a2a.legacy.propose", event.getType());
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