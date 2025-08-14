package org.apache.eventmesh.runtime.core.protocol.a2a;

import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.runtime.core.protocol.a2a.A2AProtocolAdaptor.A2AMessage;
import org.apache.eventmesh.runtime.core.protocol.a2a.A2AProtocolAdaptor.AgentInfo;
import org.apache.eventmesh.runtime.core.protocol.a2a.A2AProtocolAdaptor.MessageMetadata;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.ArrayList;

/**
 * A2A Message Router
 * Routes messages between agents based on routing rules and agent capabilities
 */
public class MessageRouter {
    
    private static final MessageRouter INSTANCE = new MessageRouter();
    private final AgentRegistry agentRegistry = AgentRegistry.getInstance();
    private final Map<String, Consumer<A2AMessage>> messageHandlers = new ConcurrentHashMap<>();
    private final ExecutorService routingExecutor = Executors.newFixedThreadPool(10);
    
    private MessageRouter() {}
    
    public static MessageRouter getInstance() {
        return INSTANCE;
    }
    
    /**
     * Route A2A message to target agent(s)
     */
    public void routeMessage(A2AMessage message) {
        routingExecutor.submit(() -> {
            try {
                String messageType = message.getMessageType();
                
                switch (messageType) {
                    case "REGISTER":
                        handleRegistration(message);
                        break;
                    case "HEARTBEAT":
                        handleHeartbeat(message);
                        break;
                    case "TASK_REQUEST":
                        routeTaskRequest(message);
                        break;
                    case "TASK_RESPONSE":
                        routeTaskResponse(message);
                        break;
                    case "STATE_SYNC":
                        handleStateSync(message);
                        break;
                    case "COLLABORATION_REQUEST":
                        routeCollaborationRequest(message);
                        break;
                    case "BROADCAST":
                        broadcastMessage(message);
                        break;
                    default:
                        System.err.println("Unknown message type: " + messageType);
                }
            } catch (Exception e) {
                System.err.println("Error routing message: " + e.getMessage());
            }
        });
    }
    
    /**
     * Handle agent registration
     */
    private void handleRegistration(A2AMessage message) {
        boolean success = agentRegistry.registerAgent(message);
        
        // Send registration response
        A2AMessage response = createResponseMessage(message, success ? "REGISTER_SUCCESS" : "REGISTER_FAILED");
        response.setPayload(Map.of("success", success));
        
        // Route response back to source agent
        routeToAgent(message.getSourceAgent().getAgentId(), response);
    }
    
    /**
     * Handle agent heartbeat
     */
    private void handleHeartbeat(A2AMessage message) {
        String agentId = message.getSourceAgent().getAgentId();
        agentRegistry.updateHeartbeat(agentId);
        
        // Send heartbeat acknowledgment
        A2AMessage response = createResponseMessage(message, "HEARTBEAT_ACK");
        routeToAgent(agentId, response);
    }
    
    /**
     * Route task request to appropriate agent
     */
    private void routeTaskRequest(A2AMessage message) {
        String targetAgentId = message.getTargetAgent().getAgentId();
        
        // Check if target agent is available
        if (!agentRegistry.isAgentAlive(targetAgentId)) {
            // Try to find alternative agent with same capabilities
            AgentInfo targetAgent = message.getTargetAgent();
            List<AgentInfo> alternativeAgents = findAlternativeAgents(targetAgent);
            
            if (!alternativeAgents.isEmpty()) {
                // Route to first available alternative agent
                AgentInfo alternativeAgent = alternativeAgents.get(0);
                message.setTargetAgent(alternativeAgent);
                targetAgentId = alternativeAgent.getAgentId();
                System.out.println("Rerouting task request to alternative agent: " + targetAgentId);
            } else {
                // Send failure response
                A2AMessage response = createResponseMessage(message, "TASK_REQUEST_FAILED");
                response.setPayload(Map.of("error", "No suitable agent available"));
                routeToAgent(message.getSourceAgent().getAgentId(), response);
                return;
            }
        }
        
        // Route message to target agent
        routeToAgent(targetAgentId, message);
    }
    
    /**
     * Route task response back to requesting agent
     */
    private void routeTaskResponse(A2AMessage message) {
        String targetAgentId = message.getTargetAgent().getAgentId();
        routeToAgent(targetAgentId, message);
    }
    
    /**
     * Handle state synchronization
     */
    private void handleStateSync(A2AMessage message) {
        String agentId = message.getSourceAgent().getAgentId();
        
        // Update agent state in registry (if needed)
        // This could be extended to maintain agent state information
        
        // Broadcast state update to interested agents
        broadcastToInterestedAgents(message, "STATE_UPDATE");
    }
    
    /**
     * Route collaboration request
     */
    private void routeCollaborationRequest(A2AMessage message) {
        String targetAgentId = message.getTargetAgent().getAgentId();
        
        if (agentRegistry.isAgentAlive(targetAgentId)) {
            routeToAgent(targetAgentId, message);
        } else {
            // Send failure response
            A2AMessage response = createResponseMessage(message, "COLLABORATION_REQUEST_FAILED");
            response.setPayload(Map.of("error", "Target agent not available"));
            routeToAgent(message.getSourceAgent().getAgentId(), response);
        }
    }
    
    /**
     * Broadcast message to all agents
     */
    private void broadcastMessage(A2AMessage message) {
        List<AgentInfo> allAgents = agentRegistry.getAllAgents();
        
        for (AgentInfo agent : allAgents) {
            if (agentRegistry.isAgentAlive(agent.getAgentId())) {
                A2AMessage broadcastMsg = cloneMessage(message);
                broadcastMsg.setTargetAgent(agent);
                routeToAgent(agent.getAgentId(), broadcastMsg);
            }
        }
    }
    
    /**
     * Broadcast to agents interested in specific topic/type
     */
    private void broadcastToInterestedAgents(A2AMessage message, String topic) {
        // This could be extended to implement topic-based routing
        // For now, broadcast to all agents
        broadcastMessage(message);
    }
    
    /**
     * Route message to specific agent
     */
    private void routeToAgent(String agentId, A2AMessage message) {
        Consumer<A2AMessage> handler = messageHandlers.get(agentId);
        if (handler != null) {
            handler.accept(message);
        } else {
            System.err.println("No handler found for agent: " + agentId);
        }
    }
    
    /**
     * Find alternative agents with similar capabilities
     */
    private List<AgentInfo> findAlternativeAgents(AgentInfo targetAgent) {
        List<AgentInfo> alternatives = new ArrayList<>();
        
        if (targetAgent.getCapabilities() != null) {
            for (String capability : targetAgent.getCapabilities()) {
                List<AgentInfo> agentsWithCapability = agentRegistry.findAgentsByCapability(capability);
                for (AgentInfo agent : agentsWithCapability) {
                    if (agentRegistry.isAgentAlive(agent.getAgentId()) && 
                        !agent.getAgentId().equals(targetAgent.getAgentId())) {
                        alternatives.add(agent);
                    }
                }
            }
        }
        
        return alternatives;
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
     * Clone message for broadcasting
     */
    private A2AMessage cloneMessage(A2AMessage original) {
        A2AMessage clone = new A2AMessage();
        clone.setMessageType(original.getMessageType());
        clone.setSourceAgent(original.getSourceAgent());
        clone.setPayload(original.getPayload());
        clone.setMetadata(original.getMetadata());
        return clone;
    }
    
    /**
     * Register message handler for agent
     */
    public void registerHandler(String agentId, Consumer<A2AMessage> handler) {
        messageHandlers.put(agentId, handler);
    }
    
    /**
     * Unregister message handler for agent
     */
    public void unregisterHandler(String agentId) {
        messageHandlers.remove(agentId);
    }
    
    /**
     * Shutdown router
     */
    public void shutdown() {
        routingExecutor.shutdown();
    }
}
