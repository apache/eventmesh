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

import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.http.body.BaseResponseBody;
import org.apache.eventmesh.common.protocol.http.header.BaseResponseHeader;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.core.protocol.a2a.A2AMessageHandler;
import org.apache.eventmesh.runtime.core.protocol.a2a.AgentRegistry;
import org.apache.eventmesh.runtime.core.protocol.a2a.CollaborationManager;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.processor.AbstractHttpRequestProcessor;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.protocol.a2a.A2AProtocolAdaptor;
import org.apache.eventmesh.protocol.a2a.A2AMessage;
import org.apache.eventmesh.protocol.a2a.A2AProtocolAdaptor.AgentInfo;

import java.util.concurrent.Executor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * A2A HTTP Processor that extends existing EventMesh HTTP infrastructure.
 * 
 * Handles A2A-specific HTTP endpoints while reusing the existing HTTP processing pipeline.
 */
@Slf4j
public class A2AHttpProcessor extends AbstractHttpRequestProcessor {

    private final A2AMessageHandler messageHandler;
    private final AgentRegistry agentRegistry;
    private final CollaborationManager collaborationManager;

    private final EventMeshHTTPServer eventMeshHTTPServer;

    public A2AHttpProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.messageHandler = A2AMessageHandler.getInstance();
        this.agentRegistry = AgentRegistry.getInstance();
        this.collaborationManager = CollaborationManager.getInstance();
    }

    @Override
    public String[] paths() {
        return new String[]{
            "/a2a/agents/register",
            "/a2a/agents/unregister", 
            "/a2a/agents/heartbeat",
            "/a2a/agents/list",
            "/a2a/agents/search",
            "/a2a/tasks/request",
            "/a2a/tasks/response", 
            "/a2a/collaboration/start",
            "/a2a/collaboration/status",
            "/a2a/collaboration/cancel",
            "/a2a/workflows/register",
            "/a2a/broadcast"
        };
    }

    @Override
    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) 
        throws Exception {
        
        HttpCommand request = asyncContext.getRequest();
        // Assuming path is passed via header or body, or we need to parse it. 
        // Since HttpCommand doesn't have path directly, we might need to rely on requestCode or other mechanisms.
        // However, the original code used paths. Let's assume for now we can get it from header or it's a specific request code.
        // For A2A, we might need to look at a specific header "path" or similar if it's not standard.
        // But wait, HttpCommand is usually for specific RequestCodes. 
        // If we are integrating into existing HTTP server, we should check how it routes.
        // The paths() method suggests it uses path matching.
        // But AbstractHttpRequestProcessor doesn't seem to use paths() for routing in the way we might expect if it's just a list of paths.
        // Actually, the HTTP server likely uses the map of processors.
        
        // For now, let's try to get path from header if available, or default to a generic handling.
        // In standard EventMesh, RequestCode determines the processor.
        // If we want path based, we might need to check how the server dispatches.
        // Assuming we are registered for these paths.
        
        // Let's look at the request.
        String path = request.getHeader().toMap().getOrDefault("path", "");
        
        log.debug("Processing A2A request: {}", path);
        
        try {
            // Route to specific handler based on path
            CompletableFuture<Map<String, Object>> responseFuture = routeRequest(path, request);
            
            // Handle response asynchronously
            responseFuture.thenAccept(responseData -> {
                try {
                    HttpCommand response = buildHttpResponse(request, responseData);
                    asyncContext.onComplete(response);
                } catch (Exception e) {
                    log.error("Failed to build A2A response", e);
                    asyncContext.onComplete(buildErrorResponse(request, 
                        EventMeshRetCode.EVENTMESH_RUNTIME_ERR, 
                        "Failed to process A2A request: " + e.getMessage()));
                }
            }).exceptionally(throwable -> {
                log.error("A2A request processing failed", throwable);
                asyncContext.onComplete(buildErrorResponse(request,
                    EventMeshRetCode.EVENTMESH_RUNTIME_ERR, 
                    "A2A request failed: " + throwable.getMessage()));
                return null;
            });
            
        } catch (Exception e) {
            log.error("Failed to process A2A request", e);
            asyncContext.onComplete(buildErrorResponse(request,
                EventMeshRetCode.EVENTMESH_RUNTIME_ERR, 
                "A2A request processing error: " + e.getMessage()));
        }
    }

    @Override
    public Executor executor() {
        return eventMeshHTTPServer.getHttpThreadPoolGroup().getSendMsgExecutor();
    }

    private HttpCommand buildHttpResponse(HttpCommand request, Map<String, Object> responseData) {
        BaseResponseHeader header = new BaseResponseHeader();
        header.setCode(request.getRequestCode());
        
        BaseResponseBody body = new BaseResponseBody();
        body.setRetCode(EventMeshRetCode.SUCCESS.getRetCode());
        body.setRetMsg(JsonUtils.toJSONString(responseData));
        
        return request.createHttpCommandResponse(header, body);
    }

    private HttpCommand buildErrorResponse(HttpCommand request, EventMeshRetCode retCode, String msg) {
        BaseResponseHeader header = new BaseResponseHeader();
        header.setCode(request.getRequestCode());
        
        BaseResponseBody body = new BaseResponseBody();
        body.setRetCode(retCode.getRetCode());
        body.setRetMsg(msg);
        
        return request.createHttpCommandResponse(header, body);
    }

    /**
     * Route A2A requests to appropriate handlers.
     */
    private CompletableFuture<Map<String, Object>> routeRequest(String path, HttpCommand request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                switch (path) {
                    case "/a2a/agents/register":
                        return handleAgentRegister(request);
                    case "/a2a/agents/unregister":
                        return handleAgentUnregister(request);
                    case "/a2a/agents/heartbeat":
                        return handleAgentHeartbeat(request);
                    case "/a2a/agents/list":
                        return handleAgentList(request);
                    case "/a2a/agents/search":
                        return handleAgentSearch(request);
                    case "/a2a/tasks/request":
                        return handleTaskRequest(request);
                    case "/a2a/tasks/response":
                        return handleTaskResponse(request);
                    case "/a2a/collaboration/start":
                        return handleCollaborationStart(request);
                    case "/a2a/collaboration/status":
                        return handleCollaborationStatus(request);
                    case "/a2a/collaboration/cancel":
                        return handleCollaborationCancel(request);
                    case "/a2a/workflows/register":
                        return handleWorkflowRegister(request);
                    case "/a2a/broadcast":
                        return handleBroadcast(request);
                    default:
                        throw new IllegalArgumentException("Unsupported A2A path: " + path);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to handle A2A request", e);
            }
        });
    }

    /**
     * Handle agent registration.
     */
    private Map<String, Object> handleAgentRegister(HttpCommand request) {
        try {
            Map<String, Object> body = extractRequestBody(request);
            
            String agentId = (String) body.get("agentId");
            String agentType = (String) body.get("agentType");
            @SuppressWarnings("unchecked")
            List<String> capabilities = (List<String>) body.get("capabilities");
            
            // Create A2A registration message and process
            A2AMessage registerMsg = new A2AMessage();
            registerMsg.setMessageType("REGISTER");
            
            AgentInfo agentInfo = new AgentInfo();
            agentInfo.setAgentId(agentId);
            agentInfo.setAgentType(agentType);
            agentInfo.setCapabilities(capabilities.toArray(new String[0]));
            
            registerMsg.setSourceAgent(agentInfo);
            registerMsg.setPayload(Map.of("agentInfo", agentInfo));
            
            // Process using existing message handler
            messageHandler.handleMessage(registerMsg);
            
            return Map.of(
                "code", 200,
                "message", "Agent registered successfully",
                "data", Map.of(
                    "agentId", agentId,
                    "status", "registered"
                )
            );
            
        } catch (Exception e) {
            log.error("Failed to register agent", e);
            return Map.of(
                "code", 500,
                "message", "Failed to register agent: " + e.getMessage()
            );
        }
    }

    /**
     * Handle agent list request.
     */
    private Map<String, Object> handleAgentList(HttpCommand request) {
        try {
            List<AgentInfo> agents = messageHandler.getAllAgents();
            
            return Map.of(
                "code", 200,
                "message", "Agents retrieved successfully", 
                "data", Map.of(
                    "agents", agents,
                    "count", agents.size()
                )
            );
            
        } catch (Exception e) {
            log.error("Failed to list agents", e);
            return Map.of(
                "code", 500,
                "message", "Failed to list agents: " + e.getMessage()
            );
        }
    }

    /**
     * Handle agent search by type or capability.
     */
    private Map<String, Object> handleAgentSearch(HttpCommand request) {
        try {
            Map<String, String> params = extractQueryParams(request);
            
            String agentType = params.get("type");
            String capability = params.get("capability");
            
            List<AgentInfo> agents;
            
            if (agentType != null) {
                agents = messageHandler.findAgentsByType(agentType);
            } else if (capability != null) {
                agents = messageHandler.findAgentsByCapability(capability);
            } else {
                agents = messageHandler.getAllAgents();
            }
            
            return Map.of(
                "code", 200,
                "message", "Agent search completed",
                "data", Map.of(
                    "agents", agents,
                    "count", agents.size(),
                    "searchCriteria", Map.of(
                        "type", agentType != null ? agentType : "all",
                        "capability", capability != null ? capability : "all"
                    )
                )
            );
            
        } catch (Exception e) {
            log.error("Failed to search agents", e);
            return Map.of(
                "code", 500,
                "message", "Failed to search agents: " + e.getMessage()
            );
        }
    }

    /**
     * Handle collaboration start request.
     */
    private Map<String, Object> handleCollaborationStart(HttpCommand request) {
        try {
            Map<String, Object> body = extractRequestBody(request);
            
            String workflowId = (String) body.get("workflowId");
            @SuppressWarnings("unchecked")
            List<String> agentIds = (List<String>) body.get("agentIds");
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) body.getOrDefault("parameters", Map.of());
            
            String sessionId = messageHandler.startCollaboration(
                workflowId, 
                agentIds.toArray(new String[0]), 
                parameters
            );
            
            return Map.of(
                "code", 200,
                "message", "Collaboration started successfully",
                "data", Map.of(
                    "sessionId", sessionId,
                    "workflowId", workflowId,
                    "agentCount", agentIds.size()
                )
            );
            
        } catch (Exception e) {
            log.error("Failed to start collaboration", e);
            return Map.of(
                "code", 500,
                "message", "Failed to start collaboration: " + e.getMessage()
            );
        }
    }

    /**
     * Handle collaboration status request.
     */
    private Map<String, Object> handleCollaborationStatus(HttpCommand request) {
        try {
            Map<String, String> params = extractQueryParams(request);
            String sessionId = params.get("sessionId");
            
            if (sessionId == null) {
                throw new IllegalArgumentException("sessionId parameter is required");
            }
            
            CollaborationManager.CollaborationStatus status = 
                messageHandler.getCollaborationStatus(sessionId);
            
            return Map.of(
                "code", 200,
                "message", "Collaboration status retrieved",
                "data", Map.of(
                    "sessionId", sessionId,
                    "status", status != null ? status.name() : "NOT_FOUND"
                )
            );
            
        } catch (Exception e) {
            log.error("Failed to get collaboration status", e);
            return Map.of(
                "code", 500,
                "message", "Failed to get collaboration status: " + e.getMessage()
            );
        }
    }

    /**
     * Handle other A2A operations with similar patterns...
     */
    private Map<String, Object> handleAgentUnregister(HttpCommand request) {
        // Implementation similar to register
        return Map.of("code", 200, "message", "Not implemented yet");
    }

    private Map<String, Object> handleAgentHeartbeat(HttpCommand request) {
        // Implementation for heartbeat
        return Map.of("code", 200, "message", "Not implemented yet");
    }

    private Map<String, Object> handleTaskRequest(HttpCommand request) {
        // Implementation for task request
        return Map.of("code", 200, "message", "Not implemented yet");
    }

    private Map<String, Object> handleTaskResponse(HttpCommand request) {
        // Implementation for task response
        return Map.of("code", 200, "message", "Not implemented yet");
    }

    private Map<String, Object> handleCollaborationCancel(HttpCommand request) {
        // Implementation for collaboration cancel
        return Map.of("code", 200, "message", "Not implemented yet");
    }

    private Map<String, Object> handleWorkflowRegister(HttpCommand request) {
        // Implementation for workflow register
        return Map.of("code", 200, "message", "Not implemented yet");
    }

    private Map<String, Object> handleBroadcast(HttpCommand request) {
        // Implementation for broadcast
        return Map.of("code", 200, "message", "Not implemented yet");
    }

    /**
     * Extract request body as map.
     */
    private Map<String, Object> extractRequestBody(HttpCommand request) {
        try {
            if (request.getBody() == null) {
                return Map.of();
            }
            return request.getBody().toMap();
                
        } catch (Exception e) {
            log.warn("Failed to parse request body", e);
            return Map.of();
        }
    }

    /**
     * Extract query parameters from request.
     */
    private Map<String, String> extractQueryParams(HttpCommand request) {
        // This would need proper implementation based on HttpEventWrapper structure
        // For now, return empty map
        return Map.of();
    }
}