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
import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageRequestHeader;
import org.apache.eventmesh.common.protocol.http.message.RequestMessage;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EnhancedA2AProtocolAdaptorTest {

    @Mock
    private ProtocolTransportObject mockCloudEventsAdaptor;
    
    @Mock
    private ProtocolTransportObject mockHttpAdaptor;

    private EnhancedA2AProtocolAdaptor adaptor;

    @BeforeEach
    void setUp() {
        adaptor = new EnhancedA2AProtocolAdaptor();
        adaptor.initialize();
    }

    @Test
    void testGetProtocolType() {
        assertEquals("A2A", adaptor.getProtocolType());
    }

    @Test
    void testGetVersion() {
        assertEquals("2.0", adaptor.getVersion());
    }

    @Test
    void testGetPriority() {
        assertEquals(90, adaptor.getPriority());
    }

    @Test
    void testSupportsBatchProcessing() {
        assertTrue(adaptor.supportsBatchProcessing());
    }

    @Test
    void testGetCapabilities() {
        Set<String> capabilities = adaptor.getCapabilities();
        assertNotNull(capabilities);
        assertTrue(capabilities.contains("agent-communication"));
        assertTrue(capabilities.contains("workflow-orchestration"));
        assertTrue(capabilities.contains("state-sync"));
        assertTrue(capabilities.contains("collaboration"));
        assertTrue(capabilities.contains("cloudevents-compatible"));
        assertTrue(capabilities.contains("multi-protocol-transport"));
    }

    @Test
    void testInitializeAndDestroy() {
        EnhancedA2AProtocolAdaptor newAdaptor = new EnhancedA2AProtocolAdaptor();
        
        // Test multiple initialize calls (should be idempotent)
        assertDoesNotThrow(() -> {
            newAdaptor.initialize();
            newAdaptor.initialize();
        });
        
        // Test destroy
        assertDoesNotThrow(() -> {
            newAdaptor.destroy();
            newAdaptor.destroy(); // Should be safe to call multiple times
        });
    }

    @Test
    void testIsValidWithNullProtocol() {
        assertFalse(adaptor.isValid(null));
    }

    @Test
    void testIsValidWithA2AMessage() {
        ProtocolTransportObject mockProtocol = mock(ProtocolTransportObject.class);
        when(mockProtocol.toString()).thenReturn("{\"protocol\":\"A2A\",\"messageType\":\"REGISTER\"}");
        
        assertTrue(adaptor.isValid(mockProtocol));
    }

    @Test
    void testIsValidWithAgentMessage() {
        ProtocolTransportObject mockProtocol = mock(ProtocolTransportObject.class);
        when(mockProtocol.toString()).thenReturn("{\"sourceAgent\":{\"agentId\":\"test-agent\"}}");
        
        assertTrue(adaptor.isValid(mockProtocol));
    }

    @Test
    void testIsValidWithCollaborationMessage() {
        ProtocolTransportObject mockProtocol = mock(ProtocolTransportObject.class);
        when(mockProtocol.toString()).thenReturn("{\"collaboration\":{\"sessionId\":\"test-session\"}}");
        
        assertTrue(adaptor.isValid(mockProtocol));
    }

    @Test
    void testToCloudEventWithA2AMessage() throws Exception {
        // Create A2A message
        RequestMessage requestMessage = createA2ARequestMessage();
        
        // Mock CloudEvents adaptor behavior
        try (MockedStatic<org.apache.eventmesh.protocol.api.ProtocolPluginFactory> mockFactory = 
             Mockito.mockStatic(org.apache.eventmesh.protocol.api.ProtocolPluginFactory.class)) {
            
            // Create base CloudEvent
            CloudEvent baseEvent = CloudEventBuilder.v1()
                .withId("test-id")
                .withSource("test-source")
                .withType("test-type")
                .withData("test-data".getBytes())
                .build();
                
            org.apache.eventmesh.protocol.api.ProtocolAdaptor mockCloudEventsAdaptor = 
                mock(org.apache.eventmesh.protocol.api.ProtocolAdaptor.class);
            when(mockCloudEventsAdaptor.toCloudEvent(any())).thenReturn(baseEvent);
            
            mockFactory.when(() -> org.apache.eventmesh.protocol.api.ProtocolPluginFactory
                .getProtocolAdaptor("cloudevents")).thenReturn(mockCloudEventsAdaptor);
            mockFactory.when(() -> org.apache.eventmesh.protocol.api.ProtocolPluginFactory
                .getProtocolAdaptor("http")).thenReturn(mockCloudEventsAdaptor);
            
            EnhancedA2AProtocolAdaptor testAdaptor = new EnhancedA2AProtocolAdaptor();
            testAdaptor.initialize();
            
            CloudEvent result = testAdaptor.toCloudEvent(requestMessage);
            
            assertNotNull(result);
            assertEquals("A2A", result.getExtension("protocol"));
            assertEquals("2.0", result.getExtension("protocolVersion"));
            assertNotNull(result.getExtension("messageType"));
        }
    }

    @Test
    void testToCloudEventWithException() throws Exception {
        RequestMessage requestMessage = createA2ARequestMessage();
        
        try (MockedStatic<org.apache.eventmesh.protocol.api.ProtocolPluginFactory> mockFactory = 
             Mockito.mockStatic(org.apache.eventmesh.protocol.api.ProtocolPluginFactory.class)) {
            
            org.apache.eventmesh.protocol.api.ProtocolAdaptor mockCloudEventsAdaptor = 
                mock(org.apache.eventmesh.protocol.api.ProtocolAdaptor.class);
            when(mockCloudEventsAdaptor.toCloudEvent(any())).thenThrow(new RuntimeException("Test exception"));
            
            mockFactory.when(() -> org.apache.eventmesh.protocol.api.ProtocolPluginFactory
                .getProtocolAdaptor("cloudevents")).thenReturn(mockCloudEventsAdaptor);
            mockFactory.when(() -> org.apache.eventmesh.protocol.api.ProtocolPluginFactory
                .getProtocolAdaptor("http")).thenReturn(mockCloudEventsAdaptor);
            
            EnhancedA2AProtocolAdaptor testAdaptor = new EnhancedA2AProtocolAdaptor();
            testAdaptor.initialize();
            
            assertThrows(ProtocolHandleException.class, () -> {
                testAdaptor.toCloudEvent(requestMessage);
            });
        }
    }

    @Test
    void testToBatchCloudEventWithA2ABatch() throws Exception {
        RequestMessage batchMessage = createA2ABatchRequestMessage();
        
        try (MockedStatic<org.apache.eventmesh.protocol.api.ProtocolPluginFactory> mockFactory = 
             Mockito.mockStatic(org.apache.eventmesh.protocol.api.ProtocolPluginFactory.class)) {
            
            CloudEvent mockEvent = CloudEventBuilder.v1()
                .withId("batch-1")
                .withSource("test")
                .withType("test")
                .build();
            
            org.apache.eventmesh.protocol.api.ProtocolAdaptor mockCloudEventsAdaptor = 
                mock(org.apache.eventmesh.protocol.api.ProtocolAdaptor.class);
            when(mockCloudEventsAdaptor.toBatchCloudEvent(any())).thenReturn(List.of(mockEvent));
            
            mockFactory.when(() -> org.apache.eventmesh.protocol.api.ProtocolPluginFactory
                .getProtocolAdaptor("cloudevents")).thenReturn(mockCloudEventsAdaptor);
            mockFactory.when(() -> org.apache.eventmesh.protocol.api.ProtocolPluginFactory
                .getProtocolAdaptor("http")).thenReturn(mockCloudEventsAdaptor);
            
            EnhancedA2AProtocolAdaptor testAdaptor = new EnhancedA2AProtocolAdaptor();
            testAdaptor.initialize();
            
            List<CloudEvent> result = testAdaptor.toBatchCloudEvent(batchMessage);
            
            assertNotNull(result);
            assertFalse(result.isEmpty());
        }
    }

    @Test
    void testFromCloudEventWithA2ACloudEvent() throws Exception {
        CloudEvent a2aCloudEvent = CloudEventBuilder.v1()
            .withId("test-id")
            .withSource("test-source")
            .withType("org.apache.eventmesh.protocol.a2a.register")
            .withExtension("protocol", "A2A")
            .withExtension("sourceAgent", "agent-001")
            .withExtension("targetAgent", "agent-002")
            .build();
        
        try (MockedStatic<org.apache.eventmesh.protocol.api.ProtocolPluginFactory> mockFactory = 
             Mockito.mockStatic(org.apache.eventmesh.protocol.api.ProtocolPluginFactory.class)) {
            
            ProtocolTransportObject mockTransport = mock(ProtocolTransportObject.class);
            
            org.apache.eventmesh.protocol.api.ProtocolAdaptor mockCloudEventsAdaptor = 
                mock(org.apache.eventmesh.protocol.api.ProtocolAdaptor.class);
            when(mockCloudEventsAdaptor.fromCloudEvent(any())).thenReturn(mockTransport);
            
            mockFactory.when(() -> org.apache.eventmesh.protocol.api.ProtocolPluginFactory
                .getProtocolAdaptor("cloudevents")).thenReturn(mockCloudEventsAdaptor);
            mockFactory.when(() -> org.apache.eventmesh.protocol.api.ProtocolPluginFactory
                .getProtocolAdaptor("http")).thenReturn(mockCloudEventsAdaptor);
            
            EnhancedA2AProtocolAdaptor testAdaptor = new EnhancedA2AProtocolAdaptor();
            testAdaptor.initialize();
            
            ProtocolTransportObject result = testAdaptor.fromCloudEvent(a2aCloudEvent);
            
            assertNotNull(result);
            verify(mockCloudEventsAdaptor).fromCloudEvent(a2aCloudEvent);
        }
    }

    @Test
    void testFromCloudEventWithHttpProtocol() throws Exception {
        CloudEvent httpCloudEvent = CloudEventBuilder.v1()
            .withId("test-id")
            .withSource("test-source")
            .withType("test.http")
            .withExtension("protocolDesc", "http")
            .build();
        
        try (MockedStatic<org.apache.eventmesh.protocol.api.ProtocolPluginFactory> mockFactory = 
             Mockito.mockStatic(org.apache.eventmesh.protocol.api.ProtocolPluginFactory.class)) {
            
            ProtocolTransportObject mockTransport = mock(ProtocolTransportObject.class);
            
            org.apache.eventmesh.protocol.api.ProtocolAdaptor mockHttpAdaptor = 
                mock(org.apache.eventmesh.protocol.api.ProtocolAdaptor.class);
            when(mockHttpAdaptor.fromCloudEvent(any())).thenReturn(mockTransport);
            
            org.apache.eventmesh.protocol.api.ProtocolAdaptor mockCloudEventsAdaptor = 
                mock(org.apache.eventmesh.protocol.api.ProtocolAdaptor.class);
            
            mockFactory.when(() -> org.apache.eventmesh.protocol.api.ProtocolPluginFactory
                .getProtocolAdaptor("http")).thenReturn(mockHttpAdaptor);
            mockFactory.when(() -> org.apache.eventmesh.protocol.api.ProtocolPluginFactory
                .getProtocolAdaptor("cloudevents")).thenReturn(mockCloudEventsAdaptor);
            
            EnhancedA2AProtocolAdaptor testAdaptor = new EnhancedA2AProtocolAdaptor();
            testAdaptor.initialize();
            
            ProtocolTransportObject result = testAdaptor.fromCloudEvent(httpCloudEvent);
            
            assertNotNull(result);
            verify(mockHttpAdaptor).fromCloudEvent(httpCloudEvent);
        }
    }

    // Helper methods to create test data
    private RequestMessage createA2ARequestMessage() {
        SendMessageRequestHeader header = new SendMessageRequestHeader();
        header.putHeader("protocol", "A2A");
        
        SendMessageRequestBody body = new SendMessageRequestBody();
        
        Map<String, Object> a2aMessage = Map.of(
            "protocol", "A2A",
            "messageType", "REGISTER",
            "sourceAgent", Map.of(
                "agentId", "test-agent-001",
                "agentType", "task-executor",
                "capabilities", List.of("data-processing", "analytics")
            ),
            "targetAgent", Map.of(
                "agentId", "system",
                "agentType", "system"
            ),
            "payload", Map.of("action", "register")
        );
        
        body.setContent(JsonUtils.toJSONString(a2aMessage));
        
        return new RequestMessage(header, body);
    }

    private RequestMessage createA2ABatchRequestMessage() {
        SendMessageRequestHeader header = new SendMessageRequestHeader();
        header.putHeader("protocol", "A2A");
        
        SendMessageRequestBody body = new SendMessageRequestBody();
        
        Map<String, Object> batchMessage = Map.of(
            "protocol", "A2A",
            "messageType", "BATCH",
            "batchMessages", List.of(
                Map.of("messageType", "REGISTER", "agentId", "agent-001"),
                Map.of("messageType", "REGISTER", "agentId", "agent-002")
            )
        );
        
        body.setContent(JsonUtils.toJSONString(batchMessage));
        
        return new RequestMessage(header, body);
    }
}