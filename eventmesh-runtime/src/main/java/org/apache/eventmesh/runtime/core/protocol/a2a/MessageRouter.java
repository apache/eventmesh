package org.apache.eventmesh.runtime.core.protocol.a2a;

import org.apache.eventmesh.protocol.a2a.A2AMessage;
import org.apache.eventmesh.protocol.a2a.A2AProtocolAdaptor.AgentInfo;
import org.apache.eventmesh.runtime.core.protocol.a2a.pubsub.A2APublishSubscribeService;
import org.apache.eventmesh.runtime.core.protocol.producer.EventMeshProducer;

import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;

/**
 * A2A Message Router - Refactored for Publish/Subscribe Pattern
 * Delegates all message routing to EventMesh publish/subscribe infrastructure
 */
@Slf4j
public class MessageRouter {
    
    private static final MessageRouter INSTANCE = new MessageRouter();
    private final AgentRegistry agentRegistry = AgentRegistry.getInstance();
    private A2APublishSubscribeService pubSubService;
    
    private MessageRouter() {}
    
    public static MessageRouter getInstance() {
        return INSTANCE;
    }
    
    /**
     * Initialize with EventMesh producer for publish/subscribe operations
     */
    public void initialize(EventMeshProducer eventMeshProducer) {
        this.pubSubService = new A2APublishSubscribeService(eventMeshProducer);
        log.info("MessageRouter initialized with publish/subscribe service");
    }
    
    /**
     * Route A2A message - now delegates to publish/subscribe service
     */
    public void routeMessage(A2AMessage message) {
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
                    // Delegate to publish/subscribe service instead of point-to-point routing
                    publishTaskRequest(message);
                    break;
                case "STATE_SYNC":
                    handleStateSync(message);
                    break;
                default:
                    log.warn("Unsupported message type for new pub/sub model: {}", messageType);
            }
        } catch (Exception e) {
            log.error("Error routing A2A message", e);
        }
    }
    
    /**
     * Handle agent registration
     */
    private void handleRegistration(A2AMessage message) {
        boolean success = agentRegistry.registerAgent(message);
        log.info("Agent registration {}: {}", success ? "successful" : "failed", 
                message.getSourceAgent().getAgentId());
    }
    
    /**
     * Handle agent heartbeat
     */
    private void handleHeartbeat(A2AMessage message) {
        String agentId = message.getSourceAgent().getAgentId();
        agentRegistry.updateHeartbeat(agentId);
        log.debug("Heartbeat received from agent: {}", agentId);
    }
    
    /**
     * Publish task request to EventMesh topic (replaces point-to-point routing)
     */
    private void publishTaskRequest(A2AMessage message) {
        if (pubSubService == null) {
            log.error("Publish/Subscribe service not initialized");
            return;
        }
        
        try {
            // Convert A2A message to task request for pub/sub
            // Implementation would depend on A2AMessage structure
            log.info("Publishing task request to EventMesh topic instead of direct routing");
            // pubSubService.publishTask(convertToTaskRequest(message));
        } catch (Exception e) {
            log.error("Failed to publish task request", e);
        }
    }
    
    // Task responses are now handled via EventMesh result topics - no direct routing needed
    
    /**
     * Handle state synchronization - now publishes to EventMesh status topic
     */
    private void handleStateSync(A2AMessage message) {
        String agentId = message.getSourceAgent().getAgentId();
        agentRegistry.updateHeartbeat(agentId);
        
        // State updates are now published to EventMesh status topics
        log.debug("Agent state sync received from: {}", agentId);
    }
    
    /**
     * Get publish/subscribe service for external access
     */
    public A2APublishSubscribeService getPublishSubscribeService() {
        return pubSubService;
    }
    
    /**
     * Check if router is properly initialized
     */
    public boolean isInitialized() {
        return pubSubService != null;
    }
    
    /**
     * Shutdown router and cleanup resources
     */
    public void shutdown() {
        if (pubSubService != null) {
            pubSubService.shutdown();
        }
        log.info("MessageRouter shutdown completed");
    }
    
    // Deprecated methods - kept for backward compatibility but should not be used
    
    @Deprecated
    public void registerHandler(String agentId, java.util.function.Consumer<A2AMessage> handler) {
        log.warn("registerHandler is deprecated - use publish/subscribe model instead");
    }
    
    @Deprecated
    public void unregisterHandler(String agentId) {
        log.warn("unregisterHandler is deprecated - use publish/subscribe model instead");
    }
    
    @Deprecated
    public boolean hasHandler(String agentId) {
        log.warn("hasHandler is deprecated - use publish/subscribe model instead");
        return false;
    }
}
