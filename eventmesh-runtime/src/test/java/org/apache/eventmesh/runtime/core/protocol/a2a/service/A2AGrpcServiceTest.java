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

package org.apache.eventmesh.runtime.core.protocol.a2a.service;

import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.common.Response;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.runtime.core.protocol.a2a.A2AMessageHandler;
import org.apache.eventmesh.runtime.core.protocol.a2a.A2AProtocolProcessor;
import org.apache.eventmesh.runtime.core.protocol.a2a.CollaborationManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.grpc.stub.StreamObserver;

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
public class A2AGrpcServiceTest {

    @Mock
    private A2AMessageHandler mockMessageHandler;
    
    @Mock
    private A2AProtocolProcessor mockProtocolProcessor;
    
    @Mock
    private StreamObserver<Response> mockResponseObserver;
    
    @Mock
    private StreamObserver<CloudEvent> mockCloudEventObserver;

    private A2AGrpcService grpcService;

    @BeforeEach
    void setUp() {
        grpcService = new A2AGrpcService();
    }

    @Test
    void testRegisterAgentSuccess() throws Exception {
        // Prepare test data
        CloudEvent registerRequest = createAgentRegisterCloudEvent();
        CloudEvent responseEvent = createSuccessCloudEvent();
        
        // Mock static dependencies
        try (MockedStatic<A2AMessageHandler> mockStaticHandler = 
             Mockito.mockStatic(A2AMessageHandler.class);
             MockedStatic<A2AProtocolProcessor> mockStaticProcessor = 
             Mockito.mockStatic(A2AProtocolProcessor.class)) {
            
            mockStaticHandler.when(A2AMessageHandler::getInstance).thenReturn(mockMessageHandler);
            mockStaticProcessor.when(A2AProtocolProcessor::getInstance).thenReturn(mockProtocolProcessor);
            
            when(mockProtocolProcessor.processGrpcMessage(registerRequest))
                .thenReturn(CompletableFuture.completedFuture(responseEvent));
            
            CountDownLatch latch = new CountDownLatch(1);
            ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
            
            doAnswer(invocation -> {
                latch.countDown();
                return null;
            }).when(mockResponseObserver).onNext(responseCaptor.capture());
            
            // Execute test
            grpcService.registerAgent(registerRequest, mockResponseObserver);
            
            // Wait for async processing
            assertTrue(latch.await(5, TimeUnit.SECONDS), "gRPC call processing timed out");
            
            // Verify interactions
            verify(mockProtocolProcessor, times(1)).processGrpcMessage(registerRequest);
            verify(mockResponseObserver, times(1)).onNext(any(Response.class));
            verify(mockResponseObserver, times(1)).onCompleted();
            
            // Verify response
            Response capturedResponse = responseCaptor.getValue();
            assertEquals(StatusCode.SUCCESS.getRetCode(), capturedResponse.getRespCode());
            assertTrue(capturedResponse.getRespMsg().contains("Agent registered successfully"));
        }
    }

    @Test
    void testRegisterAgentFailure() throws Exception {
        // Prepare test data
        CloudEvent registerRequest = createAgentRegisterCloudEvent();
        
        // Mock static dependencies
        try (MockedStatic<A2AMessageHandler> mockStaticHandler = 
             Mockito.mockStatic(A2AMessageHandler.class);
             MockedStatic<A2AProtocolProcessor> mockStaticProcessor = 
             Mockito.mockStatic(A2AProtocolProcessor.class)) {
            
            mockStaticHandler.when(A2AMessageHandler::getInstance).thenReturn(mockMessageHandler);
            mockStaticProcessor.when(A2AProtocolProcessor::getInstance).thenReturn(mockProtocolProcessor);
            
            CompletableFuture<CloudEvent> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("Processing failed"));
            when(mockProtocolProcessor.processGrpcMessage(registerRequest)).thenReturn(failedFuture);
            
            CountDownLatch latch = new CountDownLatch(1);
            ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
            
            doAnswer(invocation -> {
                latch.countDown();
                return null;
            }).when(mockResponseObserver).onNext(responseCaptor.capture());
            
            // Execute test
            grpcService.registerAgent(registerRequest, mockResponseObserver);
            
            // Wait for async processing
            assertTrue(latch.await(5, TimeUnit.SECONDS), "gRPC call processing timed out");
            
            // Verify error response
            verify(mockResponseObserver, times(1)).onNext(any(Response.class));
            verify(mockResponseObserver, times(1)).onCompleted();
            
            Response capturedResponse = responseCaptor.getValue();
            assertEquals(StatusCode.EVENTMESH_RUNTIME_ERR.getRetCode(), capturedResponse.getRespCode());
            assertTrue(capturedResponse.getRespMsg().contains("Failed to register agent"));
        }
    }

    @Test
    void testSendTaskRequestSuccess() throws Exception {
        // Prepare test data
        CloudEvent taskRequest = createTaskRequestCloudEvent();
        CloudEvent responseEvent = createSuccessCloudEvent();
        
        // Mock static dependencies
        try (MockedStatic<A2AMessageHandler> mockStaticHandler = 
             Mockito.mockStatic(A2AMessageHandler.class);
             MockedStatic<A2AProtocolProcessor> mockStaticProcessor = 
             Mockito.mockStatic(A2AProtocolProcessor.class)) {
            
            mockStaticHandler.when(A2AMessageHandler::getInstance).thenReturn(mockMessageHandler);
            mockStaticProcessor.when(A2AProtocolProcessor::getInstance).thenReturn(mockProtocolProcessor);
            
            when(mockProtocolProcessor.processGrpcMessage(taskRequest))
                .thenReturn(CompletableFuture.completedFuture(responseEvent));
            
            CountDownLatch latch = new CountDownLatch(1);
            ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
            
            doAnswer(invocation -> {
                latch.countDown();
                return null;
            }).when(mockResponseObserver).onNext(responseCaptor.capture());
            
            // Execute test
            grpcService.sendTaskRequest(taskRequest, mockResponseObserver);
            
            // Wait for async processing
            assertTrue(latch.await(5, TimeUnit.SECONDS), "gRPC call processing timed out");
            
            // Verify interactions
            verify(mockProtocolProcessor, times(1)).processGrpcMessage(taskRequest);
            verify(mockResponseObserver, times(1)).onNext(any(Response.class));
            verify(mockResponseObserver, times(1)).onCompleted();
            
            // Verify response
            Response capturedResponse = responseCaptor.getValue();
            assertEquals(StatusCode.SUCCESS.getRetCode(), capturedResponse.getRespCode());
            assertTrue(capturedResponse.getRespMsg().contains("Task request processed successfully"));
        }
    }

    @Test
    void testSendTaskRequestInvalidMessage() throws Exception {
        // Prepare invalid task request (missing A2A attributes)
        CloudEvent invalidRequest = CloudEvent.newBuilder()
            .setId("invalid-id")
            .setSource("test-source")
            .setSpecVersion("1.0")
            .setType("invalid.type")
            .build();
        
        CountDownLatch latch = new CountDownLatch(1);
        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(mockResponseObserver).onNext(responseCaptor.capture());
        
        // Execute test
        grpcService.sendTaskRequest(invalidRequest, mockResponseObserver);
        
        // Wait for processing
        assertTrue(latch.await(5, TimeUnit.SECONDS), "gRPC call processing timed out");
        
        // Verify error response
        verify(mockResponseObserver, times(1)).onNext(any(Response.class));
        verify(mockResponseObserver, times(1)).onCompleted();
        
        Response capturedResponse = responseCaptor.getValue();
        assertEquals(StatusCode.EVENTMESH_RUNTIME_ERR.getRetCode(), capturedResponse.getRespCode());
        assertTrue(capturedResponse.getRespMsg().contains("Invalid A2A task request"));
    }

    @Test
    void testStartCollaborationSuccess() throws Exception {
        // Prepare test data
        CloudEvent collaborationRequest = createCollaborationCloudEvent();
        String mockSessionId = "session-12345";
        
        // Mock static dependencies
        try (MockedStatic<A2AMessageHandler> mockStaticHandler = 
             Mockito.mockStatic(A2AMessageHandler.class)) {
            
            mockStaticHandler.when(A2AMessageHandler::getInstance).thenReturn(mockMessageHandler);
            when(mockMessageHandler.startCollaboration(eq("workflow-1"), any(String[].class), any()))
                .thenReturn(mockSessionId);
            
            CountDownLatch latch = new CountDownLatch(1);
            ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
            
            doAnswer(invocation -> {
                latch.countDown();
                return null;
            }).when(mockResponseObserver).onNext(responseCaptor.capture());
            
            // Execute test
            grpcService.startCollaboration(collaborationRequest, mockResponseObserver);
            
            // Wait for processing
            assertTrue(latch.await(5, TimeUnit.SECONDS), "gRPC call processing timed out");
            
            // Verify interactions
            verify(mockMessageHandler, times(1)).startCollaboration(
                eq("workflow-1"), 
                any(String[].class), 
                any()
            );
            verify(mockResponseObserver, times(1)).onNext(any(Response.class));
            verify(mockResponseObserver, times(1)).onCompleted();
            
            // Verify response
            Response capturedResponse = responseCaptor.getValue();
            assertEquals(StatusCode.SUCCESS.getRetCode(), capturedResponse.getRespCode());
            assertTrue(capturedResponse.getRespMsg().contains("Collaboration started successfully"));
        }
    }

    @Test
    void testStartCollaborationMissingParameters() throws Exception {
        // Prepare collaboration request missing required parameters
        CloudEvent invalidRequest = CloudEvent.newBuilder()
            .setId("collab-id")
            .setSource("test-source")
            .setSpecVersion("1.0")
            .setType("collaboration.start")
            .build(); // Missing workflowId and agentIds extensions
        
        CountDownLatch latch = new CountDownLatch(1);
        ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
        
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(mockResponseObserver).onNext(responseCaptor.capture());
        
        // Execute test
        grpcService.startCollaboration(invalidRequest, mockResponseObserver);
        
        // Wait for processing
        assertTrue(latch.await(5, TimeUnit.SECONDS), "gRPC call processing timed out");
        
        // Verify error response
        verify(mockResponseObserver, times(1)).onNext(any(Response.class));
        verify(mockResponseObserver, times(1)).onCompleted();
        
        Response capturedResponse = responseCaptor.getValue();
        assertEquals(StatusCode.EVENTMESH_RUNTIME_ERR.getRetCode(), capturedResponse.getRespCode());
        assertTrue(capturedResponse.getRespMsg().contains("Missing required parameters"));
    }

    @Test
    void testAgentHeartbeatStream() throws Exception {
        // Prepare heartbeat event
        CloudEvent heartbeatEvent = createHeartbeatCloudEvent();
        CloudEvent responseEvent = createSuccessCloudEvent();
        
        // Mock static dependencies
        try (MockedStatic<A2AProtocolProcessor> mockStaticProcessor = 
             Mockito.mockStatic(A2AProtocolProcessor.class)) {
            
            mockStaticProcessor.when(A2AProtocolProcessor::getInstance).thenReturn(mockProtocolProcessor);
            when(mockProtocolProcessor.processGrpcMessage(heartbeatEvent))
                .thenReturn(CompletableFuture.completedFuture(responseEvent));
            
            CountDownLatch latch = new CountDownLatch(1);
            doAnswer(invocation -> {
                latch.countDown();
                return null;
            }).when(mockResponseObserver).onNext(any(Response.class));
            
            // Execute test
            StreamObserver<CloudEvent> heartbeatObserver = grpcService.agentHeartbeat(mockResponseObserver);
            heartbeatObserver.onNext(heartbeatEvent);
            heartbeatObserver.onCompleted();
            
            // Wait for processing
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Heartbeat processing timed out");
            
            // Verify interactions
            verify(mockProtocolProcessor, times(1)).processGrpcMessage(heartbeatEvent);
            verify(mockResponseObserver, times(1)).onNext(any(Response.class));
            verify(mockResponseObserver, times(1)).onCompleted();
        }
    }

    @Test
    void testAgentHeartbeatStreamError() throws Exception {
        // Prepare heartbeat event that will cause error
        CloudEvent heartbeatEvent = createHeartbeatCloudEvent();
        
        // Mock static dependencies
        try (MockedStatic<A2AProtocolProcessor> mockStaticProcessor = 
             Mockito.mockStatic(A2AProtocolProcessor.class)) {
            
            mockStaticProcessor.when(A2AProtocolProcessor::getInstance).thenReturn(mockProtocolProcessor);
            
            CompletableFuture<CloudEvent> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("Heartbeat processing failed"));
            when(mockProtocolProcessor.processGrpcMessage(heartbeatEvent)).thenReturn(failedFuture);
            
            CountDownLatch latch = new CountDownLatch(1);
            ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);
            
            doAnswer(invocation -> {
                latch.countDown();
                return null;
            }).when(mockResponseObserver).onNext(responseCaptor.capture());
            
            // Execute test
            StreamObserver<CloudEvent> heartbeatObserver = grpcService.agentHeartbeat(mockResponseObserver);
            heartbeatObserver.onNext(heartbeatEvent);
            
            // Wait for processing
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Heartbeat processing timed out");
            
            // Verify error response
            verify(mockResponseObserver, times(1)).onNext(any(Response.class));
            
            Response capturedResponse = responseCaptor.getValue();
            assertEquals(StatusCode.EVENTMESH_RUNTIME_ERR.getRetCode(), capturedResponse.getRespCode());
            assertTrue(capturedResponse.getRespMsg().contains("Heartbeat processing failed"));
        }
    }

    @Test
    void testSubscribeA2AEvents() throws Exception {
        // Prepare subscription request
        CloudEvent subscriptionRequest = createSubscriptionCloudEvent();
        
        CountDownLatch latch = new CountDownLatch(1);
        ArgumentCaptor<CloudEvent> eventCaptor = ArgumentCaptor.forClass(CloudEvent.class);
        
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(mockCloudEventObserver).onNext(eventCaptor.capture());
        
        // Execute test
        grpcService.subscribeA2AEvents(subscriptionRequest, mockCloudEventObserver);
        
        // Wait for processing
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Subscription processing timed out");
        
        // Verify subscription confirmation
        verify(mockCloudEventObserver, times(1)).onNext(any(CloudEvent.class));
        
        CloudEvent confirmationEvent = eventCaptor.getValue();
        assertEquals("org.apache.eventmesh.protocol.a2a.subscription.confirmed", confirmationEvent.getType());
        assertTrue(confirmationEvent.getAttributesMap().containsKey("agentId"));
    }

    @Test
    void testSubscribeA2AEventsWithoutAgentId() throws Exception {
        // Prepare invalid subscription request (missing agentId)
        CloudEvent invalidRequest = CloudEvent.newBuilder()
            .setId("sub-id")
            .setSource("test-source")
            .setSpecVersion("1.0")
            .setType("subscription.request")
            .build();
        
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(mockCloudEventObserver).onError(any(Throwable.class));
        
        // Execute test
        grpcService.subscribeA2AEvents(invalidRequest, mockCloudEventObserver);
        
        // Wait for processing
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Subscription processing timed out");
        
        // Verify error
        verify(mockCloudEventObserver, times(1)).onError(any(IllegalArgumentException.class));
    }

    // Helper methods to create test CloudEvents
    private CloudEvent createAgentRegisterCloudEvent() {
        return CloudEvent.newBuilder()
            .setId("register-1")
            .setSource("test-agent")
            .setSpecVersion("1.0")
            .setType("org.apache.eventmesh.protocol.a2a.register")
            .putAttributes("protocol", 
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("A2A").build())
            .putAttributes("messageType", 
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("REGISTER").build())
            .putAttributes("sourceAgent", 
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("agent-001").build())
            .setData(com.google.protobuf.ByteString.copyFromUtf8(
                "{\"agentId\":\"agent-001\",\"agentType\":\"task-executor\"}"))
            .build();
    }

    private CloudEvent createTaskRequestCloudEvent() {
        return CloudEvent.newBuilder()
            .setId("task-1")
            .setSource("test-agent")
            .setSpecVersion("1.0")
            .setType("org.apache.eventmesh.protocol.a2a.task")
            .putAttributes("protocol", 
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("A2A").build())
            .putAttributes("messageType", 
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("TASK_REQUEST").build())
            .putAttributes("sourceAgent", 
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("agent-001").build())
            .putAttributes("targetAgent", 
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("agent-002").build())
            .setData(com.google.protobuf.ByteString.copyFromUtf8(
                "{\"taskType\":\"data-processing\",\"parameters\":{}}"))
            .build();
    }

    private CloudEvent createCollaborationCloudEvent() {
        return CloudEvent.newBuilder()
            .setId("collab-1")
            .setSource("test-agent")
            .setSpecVersion("1.0")
            .setType("org.apache.eventmesh.protocol.a2a.collaboration")
            .putAttributes("workflowId", 
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("workflow-1").build())
            .putAttributes("agentIds", 
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("agent-001,agent-002").build())
            .build();
    }

    private CloudEvent createHeartbeatCloudEvent() {
        return CloudEvent.newBuilder()
            .setId("heartbeat-1")
            .setSource("test-agent")
            .setSpecVersion("1.0")
            .setType("org.apache.eventmesh.protocol.a2a.heartbeat")
            .putAttributes("protocol", 
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("A2A").build())
            .putAttributes("sourceAgent", 
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("agent-001").build())
            .build();
    }

    private CloudEvent createSubscriptionCloudEvent() {
        return CloudEvent.newBuilder()
            .setId("sub-1")
            .setSource("test-agent")
            .setSpecVersion("1.0")
            .setType("org.apache.eventmesh.protocol.a2a.subscription")
            .putAttributes("sourceAgent", 
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("agent-001").build())
            .putAttributes("eventTypes", 
                CloudEvent.CloudEventAttributeValue.newBuilder().setCeString("task,heartbeat").build())
            .build();
    }

    private CloudEvent createSuccessCloudEvent() {
        return CloudEvent.newBuilder()
            .setId("success-1")
            .setSource("eventmesh-a2a")
            .setSpecVersion("1.0")
            .setType("org.apache.eventmesh.protocol.a2a.success")
            .setData(com.google.protobuf.ByteString.copyFromUtf8("{\"status\":\"success\"}"))
            .build();
    }
}