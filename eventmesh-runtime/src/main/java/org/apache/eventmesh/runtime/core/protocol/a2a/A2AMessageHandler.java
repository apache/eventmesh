package org.apache.eventmesh.runtime.core.protocol.a2a;

import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.protocol.a2a.A2AProtocolAdaptor.A2AMessage;
import org.apache.eventmesh.protocol.a2a.A2AProtocolAdaptor.AgentInfo;
import org.apache.eventmesh.protocol.a2a.A2AProtocolAdaptor.MessageMetadata;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * A2A Message Handler
 * Handles A2A protocol messages in EventMesh runtime
 */
public class A2AMessageHandler {
    
    private static final A2AMessageHandler INSTANCE = new A2AMessageHandler();
    private final AgentRegistry agentRegistry = AgentRegistry.getInstance();
    private final MessageRouter messageRouter = MessageRouter.getInstance();
    private final CollaborationManager collaborationManager = CollaborationManager.getInstance();
    private final Map<String, Consumer<A2AMessage>> messageProcessors = new ConcurrentHashMap<>();
    
    private A2AMessageHandler() {
        initializeMessageProcessors();
    }
    
    public static A2AMessageHandler getInstance() {
        return INSTANCE;
    }
    
    /**
     * Initialize message processors for different message types
     */
    private void initializeMessageProcessors() {
        // Register message processors
        messageProcessors.put("REGISTER", this::processRegistration);
        messageProcessors.put("HEARTBEAT", this::processHeartbeat);
        messageProcessors.put("TASK_REQUEST", this::processTaskRequest);
        messageProcessors.put("TASK_RESPONSE", this::processTaskResponse);
        messageProcessors.put("STATE_SYNC", this::processStateSync);
        messageProcessors.put("COLLABORATION_REQUEST", this::processCollaborationRequest);
        messageProcessors.put("BROADCAST", this::processBroadcast);
    }
    
    /**
     * Handle incoming A2A message
     */
    public void handleMessage(A2AMessage message) {
        try {
            String messageType = message.getMessageType();
            Consumer<A2AMessage> processor = messageProcessors.get(messageType);
            
            if (processor != null) {
                processor.accept(message);
            } else {
                System.err.println("No processor found for message type: " + messageType);
            }
        } catch (Exception e) {
            System.err.println("Error handling A2A message: " + e.getMessage());
        }
    }
    
    /**
     * Process agent registration
     */
    private void processRegistration(A2AMessage message) {
        try {
            // Extract agent info from payload
            Map<String, Object> payload = (Map<String, Object>) message.getPayload();
            AgentInfo agentInfo = JsonUtils.parseObject(JsonUtils.toJSONString(payload.get("agentInfo")), AgentInfo.class);
            
            // Create registration message with agent info
            A2AMessage registrationMsg = new A2AMessage();
            registrationMsg.setMessageType("REGISTER");
            registrationMsg.setSourceAgent(message.getSourceAgent());
            registrationMsg.setTargetAgent(message.getTargetAgent());
            registrationMsg.setPayload(agentInfo);
            registrationMsg.setMetadata(message.getMetadata());
            
            // Route to message router
            messageRouter.routeMessage(registrationMsg);
            
        } catch (Exception e) {
            System.err.println("Error processing registration: " + e.getMessage());
        }
    }
    
    /**
     * Process agent heartbeat
     */
    private void processHeartbeat(A2AMessage message) {
        try {
            String agentId = message.getSourceAgent().getAgentId();
            agentRegistry.updateHeartbeat(agentId);
            
            // Create heartbeat acknowledgment
            A2AMessage response = createResponseMessage(message, "HEARTBEAT_ACK");
            response.setPayload(Map.of("timestamp", System.currentTimeMillis()));
            
            // Send response back to agent
            sendResponseToAgent(agentId, response);
            
        } catch (Exception e) {
            System.err.println("Error processing heartbeat: " + e.getMessage());
        }
    }
    
    /**
     * Process task request
     */
    private void processTaskRequest(A2AMessage message) {
        try {
            // Route task request to appropriate agent
            messageRouter.routeMessage(message);
            
        } catch (Exception e) {
            System.err.println("Error processing task request: " + e.getMessage());
            
            // Send error response
            A2AMessage errorResponse = createResponseMessage(message, "TASK_REQUEST_FAILED");
            errorResponse.setPayload(Map.of("error", e.getMessage()));
            sendResponseToAgent(message.getSourceAgent().getAgentId(), errorResponse);
        }
    }
    
    /**
     * Process task response
     */
    private void processTaskResponse(A2AMessage message) {
        try {
            // Check if this is part of a collaboration session
            if (message.getMetadata() != null && message.getMetadata().getCorrelationId() != null) {
                collaborationManager.handleTaskResponse(message);
            }
            
            // Route response to requesting agent
            messageRouter.routeMessage(message);
            
        } catch (Exception e) {
            System.err.println("Error processing task response: " + e.getMessage());
        }
    }
    
    /**
     * Process state synchronization
     */
    private void processStateSync(A2AMessage message) {
        try {
            // Update agent state in registry
            String agentId = message.getSourceAgent().getAgentId();
            agentRegistry.updateHeartbeat(agentId);
            
            // Route state sync message
            messageRouter.routeMessage(message);
            
        } catch (Exception e) {
            System.err.println("Error processing state sync: " + e.getMessage());
        }
    }
    
    /**
     * Process collaboration request
     */
    private void processCollaborationRequest(A2AMessage message) {
        try {
            // Route collaboration request
            messageRouter.routeMessage(message);
            
        } catch (Exception e) {
            System.err.println("Error processing collaboration request: " + e.getMessage());
            
            // Send error response
            A2AMessage errorResponse = createResponseMessage(message, "COLLABORATION_REQUEST_FAILED");
            errorResponse.setPayload(Map.of("error", e.getMessage()));
            sendResponseToAgent(message.getSourceAgent().getAgentId(), errorResponse);
        }
    }
    
    /**
     * Process broadcast message
     */
    private void processBroadcast(A2AMessage message) {
        try {
            // Route broadcast message to all agents
            messageRouter.routeMessage(message);
            
        } catch (Exception e) {
            System.err.println("Error processing broadcast: " + e.getMessage());
        }
    }
    
    /**
     * Create response message
     */
    private A2AMessage createResponseMessage(A2AMessage originalMessage, String responseType) {
        A2AMessage response = new A2AMessage();
        response.setMessageType(responseType);
        response.setSourceAgent(originalMessage.getTargetAgent());
        response.setTargetAgent(originalMessage.getSourceAgent());
        
        // Copy correlation ID if present
        if (originalMessage.getMetadata() != null && originalMessage.getMetadata().getCorrelationId() != null) {
            MessageMetadata metadata = new MessageMetadata();
            metadata.setCorrelationId(originalMessage.getMetadata().getCorrelationId());
            response.setMetadata(metadata);
        }
        
        return response;
    }
    
    /**
     * Send response to specific agent
     */
    private void sendResponseToAgent(String agentId, A2AMessage response) {
        try {
            // Register temporary handler for this agent if not exists
            if (!messageRouter.hasHandler(agentId)) {
                messageRouter.registerHandler(agentId, this::handleAgentResponse);
            }
            
            // Route response
            messageRouter.routeMessage(response);
            
        } catch (Exception e) {
            System.err.println("Error sending response to agent " + agentId + ": " + e.getMessage());
        }
    }
    
    /**
     * Handle response from agent (placeholder for actual agent communication)
     */
    private void handleAgentResponse(A2AMessage response) {
        // This would typically involve sending the response to the actual agent
        // For now, just log the response
        System.out.println("Response from agent " + response.getSourceAgent().getAgentId() + 
                          ": " + response.getMessageType());
    }
    
    /**
     * Start collaboration workflow
     */
    public String startCollaboration(String workflowId, String[] agentIds, Map<String, Object> parameters) {
        try {
            return collaborationManager.startCollaboration(workflowId, java.util.Arrays.asList(agentIds), parameters);
        } catch (Exception e) {
            System.err.println("Error starting collaboration: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Get collaboration status
     */
    public CollaborationManager.CollaborationStatus getCollaborationStatus(String sessionId) {
        return collaborationManager.getSessionStatus(sessionId);
    }
    
    /**
     * Cancel collaboration
     */
    public boolean cancelCollaboration(String sessionId) {
        return collaborationManager.cancelSession(sessionId);
    }
    
    /**
     * Get all registered agents
     */
    public java.util.List<AgentInfo> getAllAgents() {
        return agentRegistry.getAllAgents();
    }
    
    /**
     * Find agents by type
     */
    public java.util.List<AgentInfo> findAgentsByType(String agentType) {
        return agentRegistry.findAgentsByType(agentType);
    }
    
    /**
     * Find agents by capability
     */
    public java.util.List<AgentInfo> findAgentsByCapability(String capability) {
        return agentRegistry.findAgentsByCapability(capability);
    }
    
    /**
     * Check if agent is alive
     */
    public boolean isAgentAlive(String agentId) {
        return agentRegistry.isAgentAlive(agentId);
    }
    
    /**
     * Register workflow definition
     */
    public void registerWorkflow(CollaborationManager.WorkflowDefinition workflow) {
        collaborationManager.registerWorkflow(workflow);
    }
    
    /**
     * Shutdown message handler
     */
    public void shutdown() {
        // Shutdown all components
        agentRegistry.shutdown();
        messageRouter.shutdown();
        collaborationManager.shutdown();
    }
}
