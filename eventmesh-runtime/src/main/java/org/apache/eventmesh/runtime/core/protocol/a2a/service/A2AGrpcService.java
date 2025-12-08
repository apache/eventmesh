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
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ServiceUtils;

import java.util.concurrent.CompletableFuture;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

/**
 * A2A gRPC Service that extends EventMesh gRPC infrastructure.
 * 
 * Provides gRPC-based A2A communication while reusing existing gRPC service patterns.
 */
@Slf4j
public class A2AGrpcService {

    private final A2AMessageHandler messageHandler;
    private final A2AProtocolProcessor protocolProcessor;

    public A2AGrpcService() {
        this.messageHandler = A2AMessageHandler.getInstance();
        this.protocolProcessor = A2AProtocolProcessor.getInstance();
    }

    /**
     * Handle A2A agent registration via gRPC.
     */
    public void registerAgent(CloudEvent request, StreamObserver<Response> responseObserver) {
        try {
            log.debug("Received A2A agent registration via gRPC");
            
            // Process A2A message using existing protocol processor
            CompletableFuture<CloudEvent> processingFuture = 
                protocolProcessor.processGrpcMessage(request);
            
            processingFuture.thenAccept(responseEvent -> {
                // Convert CloudEvent response to gRPC Response
                Response response = buildSuccessResponse("Agent registered successfully");
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                
            }).exceptionally(throwable -> {
                log.error("Failed to register agent via gRPC", throwable);
                Response errorResponse = buildErrorResponse(
                    "Failed to register agent: " + throwable.getMessage());
                responseObserver.onNext(errorResponse);
                responseObserver.onCompleted();
                return null;
            });
            
        } catch (Exception e) {
            log.error("Error in agent registration gRPC call", e);
            Response errorResponse = buildErrorResponse("Agent registration error: " + e.getMessage());
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    /**
     * Handle A2A task requests via gRPC.
     */
    public void sendTaskRequest(CloudEvent request, StreamObserver<Response> responseObserver) {
        try {
            log.debug("Received A2A task request via gRPC");
            
            // Validate that this is an A2A task request
            if (!isA2ATaskRequest(request)) {
                Response errorResponse = buildErrorResponse("Invalid A2A task request");
                responseObserver.onNext(errorResponse);
                responseObserver.onCompleted();
                return;
            }
            
            // Process task request
            CompletableFuture<CloudEvent> processingFuture = 
                protocolProcessor.processGrpcMessage(request);
            
            processingFuture.thenAccept(responseEvent -> {
                Response response = buildSuccessResponse("Task request processed successfully");
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                
            }).exceptionally(throwable -> {
                log.error("Failed to process task request via gRPC", throwable);
                Response errorResponse = buildErrorResponse(
                    "Failed to process task request: " + throwable.getMessage());
                responseObserver.onNext(errorResponse);
                responseObserver.onCompleted();
                return null;
            });
            
        } catch (Exception e) {
            log.error("Error in task request gRPC call", e);
            Response errorResponse = buildErrorResponse("Task request error: " + e.getMessage());
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    /**
     * Handle A2A collaboration requests via gRPC.
     */
    public void startCollaboration(CloudEvent request, StreamObserver<Response> responseObserver) {
        try {
            log.debug("Received A2A collaboration request via gRPC");
            
            // Extract collaboration parameters from CloudEvent
            String workflowId = getCloudEventExtension(request, "workflowId");
            String agentIds = getCloudEventExtension(request, "agentIds");
            
            if (workflowId == null || agentIds == null) {
                Response errorResponse = buildErrorResponse(
                    "Missing required parameters: workflowId or agentIds");
                responseObserver.onNext(errorResponse);
                responseObserver.onCompleted();
                return;
            }
            
            // Start collaboration using message handler
            String[] agentIdArray = agentIds.split(",");
            String sessionId = messageHandler.startCollaboration(
                workflowId, 
                agentIdArray, 
                java.util.Map.of()
            );
            
            // Build success response with session ID
            Response response = Response.builder()
                .respCode(StatusCode.SUCCESS.getRetCode())
                .respMsg("Collaboration started successfully")
                .respTime(String.valueOf(System.currentTimeMillis()))
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("Error in collaboration start gRPC call", e);
            Response errorResponse = buildErrorResponse("Collaboration start error: " + e.getMessage());
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    /**
     * Handle A2A agent heartbeat via gRPC streaming.
     */
    public StreamObserver<CloudEvent> agentHeartbeat(StreamObserver<Response> responseObserver) {
        return new StreamObserver<CloudEvent>() {
            @Override
            public void onNext(CloudEvent heartbeatEvent) {
                try {
                    log.debug("Received A2A heartbeat via gRPC stream");
                    
                    // Process heartbeat using protocol processor
                    protocolProcessor.processGrpcMessage(heartbeatEvent)
                        .thenAccept(responseEvent -> {
                            Response ackResponse = buildSuccessResponse("Heartbeat acknowledged");
                            responseObserver.onNext(ackResponse);
                        })
                        .exceptionally(throwable -> {
                            log.error("Failed to process heartbeat", throwable);
                            Response errorResponse = buildErrorResponse(
                                "Heartbeat processing failed: " + throwable.getMessage());
                            responseObserver.onNext(errorResponse);
                            return null;
                        });
                        
                } catch (Exception e) {
                    log.error("Error processing heartbeat", e);
                    Response errorResponse = buildErrorResponse("Heartbeat error: " + e.getMessage());
                    responseObserver.onNext(errorResponse);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("Error in heartbeat stream", t);
                responseObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                log.debug("Heartbeat stream completed");
                responseObserver.onCompleted();
            }
        };
    }

    /**
     * Handle A2A broadcast messages via gRPC.
     */
    public void broadcast(CloudEvent request, StreamObserver<Response> responseObserver) {
        try {
            log.debug("Received A2A broadcast request via gRPC");
            
            // Process broadcast message
            CompletableFuture<CloudEvent> processingFuture = 
                protocolProcessor.processGrpcMessage(request);
            
            processingFuture.thenAccept(responseEvent -> {
                Response response = buildSuccessResponse("Broadcast message processed successfully");
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                
            }).exceptionally(throwable -> {
                log.error("Failed to process broadcast via gRPC", throwable);
                Response errorResponse = buildErrorResponse(
                    "Failed to process broadcast: " + throwable.getMessage());
                responseObserver.onNext(errorResponse);
                responseObserver.onCompleted();
                return null;
            });
            
        } catch (Exception e) {
            log.error("Error in broadcast gRPC call", e);
            Response errorResponse = buildErrorResponse("Broadcast error: " + e.getMessage());
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    /**
     * Stream A2A events to subscribed agents.
     */
    public void subscribeA2AEvents(CloudEvent subscriptionRequest, 
                                  StreamObserver<CloudEvent> responseObserver) {
        try {
            log.debug("Received A2A event subscription via gRPC");
            
            // Extract subscription parameters
            String agentId = getCloudEventExtension(subscriptionRequest, "sourceAgent");
            String eventTypes = getCloudEventExtension(subscriptionRequest, "eventTypes");
            
            if (agentId == null) {
                responseObserver.onError(new IllegalArgumentException("Missing agent ID"));
                return;
            }
            
            // Register agent for event streaming
            registerAgentForStreaming(agentId, eventTypes, responseObserver);
            
        } catch (Exception e) {
            log.error("Error in A2A event subscription", e);
            responseObserver.onError(e);
        }
    }

    /**
     * Register agent for event streaming.
     */
    private void registerAgentForStreaming(String agentId, String eventTypes, 
                                         StreamObserver<CloudEvent> responseObserver) {
        // This would integrate with the existing EventMesh event streaming infrastructure
        // For now, just acknowledge the subscription
        log.info("Agent {} subscribed to A2A events: {}", agentId, eventTypes);
        
        // Send confirmation event
        CloudEvent confirmationEvent = CloudEvent.newBuilder()
            .setId(java.util.UUID.randomUUID().toString())
            .setSource("eventmesh-a2a-service")
            .setSpecVersion("1.0")
            .setType("org.apache.eventmesh.protocol.a2a.subscription.confirmed")
            .putAttributes("agentId", 
                CloudEvent.CloudEventAttributeValue.newBuilder()
                    .setCeString(agentId).build())
            .setData(("{\"status\":\"subscribed\",\"eventTypes\":\"" + eventTypes + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .build();
        
        responseObserver.onNext(confirmationEvent);
    }

    /**
     * Check if CloudEvent is an A2A task request.
     */
    private boolean isA2ATaskRequest(CloudEvent cloudEvent) {
        String protocol = getCloudEventExtension(cloudEvent, "protocol");
        String messageType = getCloudEventExtension(cloudEvent, "messageType");
        
        return "A2A".equals(protocol) && 
               ("TASK_REQUEST".equals(messageType) || cloudEvent.getType().contains("task"));
    }

    /**
     * Get extension value from CloudEvent.
     */
    private String getCloudEventExtension(CloudEvent cloudEvent, String extensionName) {
        try {
            CloudEvent.CloudEventAttributeValue value = 
                cloudEvent.getAttributesMap().get(extensionName);
            return value != null ? value.getCeString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Build success response.
     */
    private Response buildSuccessResponse(String message) {
        return Response.builder()
            .respCode(StatusCode.SUCCESS.getRetCode())
            .respMsg(message)
            .respTime(String.valueOf(System.currentTimeMillis()))
            .build();
    }

    private Response buildErrorResponse(String errorMessage) {
        return Response.builder()
            .respCode(StatusCode.EVENTMESH_RUNTIME_ERR.getRetCode())
            .respMsg(errorMessage)
            .respTime(String.valueOf(System.currentTimeMillis()))
            .build();
    }
}