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

package org.apache.eventmesh.runtime.core.protocol.a2a.processor;

import org.apache.eventmesh.common.protocol.http.HttpEventWrapper;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.core.protocol.a2a.A2AMessageHandler;
import org.apache.eventmesh.runtime.core.protocol.a2a.A2AProtocolAdaptor;
import org.apache.eventmesh.runtime.core.protocol.a2a.AgentRegistry;
import org.apache.eventmesh.runtime.core.protocol.a2a.CollaborationManager;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpMethod;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class A2AHttpProcessorTest {

    @Mock
    private EventMeshHTTPServer mockEventMeshHTTPServer;
    
    @Mock
    private ChannelHandlerContext mockChannelHandlerContext;
    
    @Mock
    private AsyncContext<HttpEventWrapper> mockAsyncContext;
    
    @Mock
    private A2AMessageHandler mockMessageHandler;
    
    @Mock
    private AgentRegistry mockAgentRegistry;
    
    @Mock
    private CollaborationManager mockCollaborationManager;

    private A2AHttpProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new A2AHttpProcessor(mockEventMeshHTTPServer);
    }

    @Test
    void testGetPaths() {
        String[] paths = processor.paths();
        assertNotNull(paths);
        assertEquals(12, paths.length);
        
        assertTrue(List.of(paths).contains("/a2a/agents/register"));
        assertTrue(List.of(paths).contains("/a2a/agents/unregister"));
        assertTrue(List.of(paths).contains("/a2a/agents/list"));
        assertTrue(List.of(paths).contains("/a2a/collaboration/start"));
        assertTrue(List.of(paths).contains("/a2a/workflows/register"));
    }

    @Test
    void testProcessAgentRegisterRequest() throws Exception {
        // Prepare test data
        HttpEventWrapper requestWrapper = createAgentRegisterRequest();
        when(mockAsyncContext.getRequest()).thenReturn(requestWrapper);
        
        // Mock static dependencies
        try (MockedStatic<A2AMessageHandler> mockStaticHandler = 
             Mockito.mockStatic(A2AMessageHandler.class)) {
            
            mockStaticHandler.when(A2AMessageHandler::getInstance).thenReturn(mockMessageHandler);
            doNothing().when(mockMessageHandler).handleMessage(any());
            
            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(invocation -> {
                latch.countDown();
                return null;
            }).when(mockAsyncContext).onComplete(any());
            
            // Execute test
            processor.processRequest(mockChannelHandlerContext, mockAsyncContext);
            
            // Wait for async processing
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Request processing timed out");
            
            // Verify interactions
            verify(mockMessageHandler, times(1)).handleMessage(any());
            verify(mockAsyncContext, times(1)).onComplete(any());
        }
    }

    @Test
    void testProcessAgentListRequest() throws Exception {
        // Prepare test data
        HttpEventWrapper requestWrapper = createAgentListRequest();
        when(mockAsyncContext.getRequest()).thenReturn(requestWrapper);
        
        List<A2AProtocolAdaptor.AgentInfo> mockAgents = createMockAgentList();
        
        // Mock static dependencies
        try (MockedStatic<A2AMessageHandler> mockStaticHandler = 
             Mockito.mockStatic(A2AMessageHandler.class)) {
            
            mockStaticHandler.when(A2AMessageHandler::getInstance).thenReturn(mockMessageHandler);
            when(mockMessageHandler.getAllAgents()).thenReturn(mockAgents);
            
            CountDownLatch latch = new CountDownLatch(1);
            ArgumentCaptor<HttpEventWrapper> responseCaptor = ArgumentCaptor.forClass(HttpEventWrapper.class);
            
            doAnswer(invocation -> {
                latch.countDown();
                return null;
            }).when(mockAsyncContext).onComplete(responseCaptor.capture());
            
            // Execute test
            processor.processRequest(mockChannelHandlerContext, mockAsyncContext);
            
            // Wait for async processing
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Request processing timed out");
            
            // Verify response
            verify(mockMessageHandler, times(1)).getAllAgents();
            verify(mockAsyncContext, times(1)).onComplete(any());
            
            // Additional verification would go here if HttpEventWrapper supported inspection
        }
    }

    @Test
    void testProcessCollaborationStartRequest() throws Exception {
        // Prepare test data
        HttpEventWrapper requestWrapper = createCollaborationStartRequest();
        when(mockAsyncContext.getRequest()).thenReturn(requestWrapper);
        
        String mockSessionId = "test-session-123";
        
        // Mock static dependencies
        try (MockedStatic<A2AMessageHandler> mockStaticHandler = 
             Mockito.mockStatic(A2AMessageHandler.class)) {
            
            mockStaticHandler.when(A2AMessageHandler::getInstance).thenReturn(mockMessageHandler);
            when(mockMessageHandler.startCollaboration(eq("workflow-1"), any(), any()))
                .thenReturn(mockSessionId);
            
            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(invocation -> {
                latch.countDown();
                return null;
            }).when(mockAsyncContext).onComplete(any());
            
            // Execute test
            processor.processRequest(mockChannelHandlerContext, mockAsyncContext);
            
            // Wait for async processing
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Request processing timed out");
            
            // Verify interactions
            verify(mockMessageHandler, times(1)).startCollaboration(
                eq("workflow-1"), 
                any(String[].class), 
                any(Map.class)
            );
            verify(mockAsyncContext, times(1)).onComplete(any());
        }
    }

    @Test
    void testProcessCollaborationStatusRequest() throws Exception {
        // Prepare test data
        HttpEventWrapper requestWrapper = createCollaborationStatusRequest();
        when(mockAsyncContext.getRequest()).thenReturn(requestWrapper);
        
        CollaborationManager.CollaborationStatus mockStatus = 
            CollaborationManager.CollaborationStatus.RUNNING;
        
        // Mock static dependencies
        try (MockedStatic<A2AMessageHandler> mockStaticHandler = 
             Mockito.mockStatic(A2AMessageHandler.class)) {
            
            mockStaticHandler.when(A2AMessageHandler::getInstance).thenReturn(mockMessageHandler);
            when(mockMessageHandler.getCollaborationStatus("session-123"))
                .thenReturn(mockStatus);
            
            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(invocation -> {
                latch.countDown();
                return null;
            }).when(mockAsyncContext).onComplete(any());
            
            // Execute test
            processor.processRequest(mockChannelHandlerContext, mockAsyncContext);
            
            // Wait for async processing  
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Request processing timed out");
            
            // Verify interactions
            verify(mockMessageHandler, times(1)).getCollaborationStatus("session-123");
            verify(mockAsyncContext, times(1)).onComplete(any());
        }
    }

    @Test
    void testProcessRequestWithException() throws Exception {
        // Prepare test data that will cause an exception
        HttpEventWrapper requestWrapper = createMalformedRequest();
        when(mockAsyncContext.getRequest()).thenReturn(requestWrapper);
        
        CountDownLatch latch = new CountDownLatch(1);
        ArgumentCaptor<HttpEventWrapper> responseCaptor = ArgumentCaptor.forClass(HttpEventWrapper.class);
        
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(mockAsyncContext).onComplete(responseCaptor.capture());
        
        // Execute test
        processor.processRequest(mockChannelHandlerContext, mockAsyncContext);
        
        // Wait for async processing
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Request processing timed out");
        
        // Verify error response
        verify(mockAsyncContext, times(1)).onComplete(any());
    }

    @Test
    void testProcessUnsupportedPath() throws Exception {
        // Prepare test data with unsupported path
        HttpEventWrapper requestWrapper = createUnsupportedPathRequest();
        when(mockAsyncContext.getRequest()).thenReturn(requestWrapper);
        
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(mockAsyncContext).onComplete(any());
        
        // Execute test
        processor.processRequest(mockChannelHandlerContext, mockAsyncContext);
        
        // Wait for async processing
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Request processing timed out");
        
        // Verify error response
        verify(mockAsyncContext, times(1)).onComplete(any());
    }

    // Helper methods to create test data
    private HttpEventWrapper createAgentRegisterRequest() {
        Map<String, Object> requestBody = Map.of(
            "agentId", "test-agent-001",
            "agentType", "task-executor",
            "capabilities", List.of("data-processing", "analytics")
        );
        
        return createHttpEventWrapper("/a2a/agents/register", JsonUtils.toJSONString(requestBody));
    }

    private HttpEventWrapper createAgentListRequest() {
        return createHttpEventWrapper("/a2a/agents/list", null);
    }

    private HttpEventWrapper createCollaborationStartRequest() {
        Map<String, Object> requestBody = Map.of(
            "workflowId", "workflow-1",
            "agentIds", List.of("agent-001", "agent-002"),
            "parameters", Map.of("batchSize", 100)
        );
        
        return createHttpEventWrapper("/a2a/collaboration/start", JsonUtils.toJSONString(requestBody));
    }

    private HttpEventWrapper createCollaborationStatusRequest() {
        // Simulate query parameter: ?sessionId=session-123
        return createHttpEventWrapper("/a2a/collaboration/status", null);
    }

    private HttpEventWrapper createMalformedRequest() {
        return createHttpEventWrapper("/a2a/agents/register", "invalid-json-{");
    }

    private HttpEventWrapper createUnsupportedPathRequest() {
        return createHttpEventWrapper("/a2a/unsupported/path", "{}");
    }

    private HttpEventWrapper createHttpEventWrapper(String uri, String body) {
        HttpEventWrapper wrapper = mock(HttpEventWrapper.class);
        when(wrapper.getRequestURI()).thenReturn(uri);
        when(wrapper.getBody()).thenReturn(body);
        when(wrapper.getHttpMethod()).thenReturn(HttpMethod.POST);
        return wrapper;
    }

    private List<A2AProtocolAdaptor.AgentInfo> createMockAgentList() {
        A2AProtocolAdaptor.AgentInfo agent1 = new A2AProtocolAdaptor.AgentInfo();
        agent1.setAgentId("agent-001");
        agent1.setAgentType("task-executor");
        agent1.setCapabilities(new String[]{"data-processing"});
        
        A2AProtocolAdaptor.AgentInfo agent2 = new A2AProtocolAdaptor.AgentInfo();
        agent2.setAgentId("agent-002");
        agent2.setAgentType("analytics-engine");
        agent2.setCapabilities(new String[]{"analytics", "ml-inference"});
        
        return List.of(agent1, agent2);
    }
}