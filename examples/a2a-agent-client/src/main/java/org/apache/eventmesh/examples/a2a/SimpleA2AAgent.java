package org.apache.eventmesh.examples.a2a;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.core.protocol.a2a.A2AProtocolAdaptor.A2AMessage;
import org.apache.eventmesh.runtime.core.protocol.a2a.A2AProtocolAdaptor.AgentInfo;
import org.apache.eventmesh.runtime.core.protocol.a2a.A2AProtocolAdaptor.MessageMetadata;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/**
 * Simple A2A Agent Client Example
 * Demonstrates how to use the A2A protocol for agent-to-agent communication
 */
public class SimpleA2AAgent {
    
    private final String agentId;
    private final String agentType;
    private final String[] capabilities;
    private final A2AProtocolProcessor protocolProcessor;
    private final ScheduledExecutorService heartbeatExecutor;
    private boolean isRunning = false;
    
    public SimpleA2AAgent(String agentId, String agentType, String[] capabilities) {
        this.agentId = agentId;
        this.agentType = agentType;
        this.capabilities = capabilities;
        this.protocolProcessor = A2AProtocolProcessor.getInstance();
        this.heartbeatExecutor = Executors.newScheduledThreadPool(1);
    }
    
    /**
     * Start the agent
     */
    public void start() {
        if (isRunning) {
            System.out.println("Agent " + agentId + " is already running");
            return;
        }
        
        try {
            // Register with EventMesh
            registerAgent();
            
            // Start heartbeat
            startHeartbeat();
            
            // Start message processing
            startMessageProcessing();
            
            isRunning = true;
            System.out.println("Agent " + agentId + " started successfully");
            
        } catch (Exception e) {
            System.err.println("Failed to start agent " + agentId + ": " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Stop the agent
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        try {
            // Stop heartbeat
            heartbeatExecutor.shutdown();
            
            // Unregister from EventMesh
            unregisterAgent();
            
            isRunning = false;
            System.out.println("Agent " + agentId + " stopped successfully");
            
        } catch (Exception e) {
            System.err.println("Error stopping agent " + agentId + ": " + e.getMessage());
        }
    }
    
    /**
     * Register agent with EventMesh
     */
    private void registerAgent() {
        try {
            A2AMessage registrationMessage = protocolProcessor.createRegistrationMessage(
                agentId, agentType, capabilities);
            
            // Send registration message
            sendMessage(registrationMessage);
            
            System.out.println("Agent " + agentId + " registered with EventMesh");
            
        } catch (Exception e) {
            System.err.println("Failed to register agent " + agentId + ": " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Unregister agent from EventMesh
     */
    private void unregisterAgent() {
        try {
            A2AMessage unregisterMessage = new A2AMessage();
            unregisterMessage.setMessageType("UNREGISTER");
            
            AgentInfo sourceAgent = new AgentInfo();
            sourceAgent.setAgentId(agentId);
            unregisterMessage.setSourceAgent(sourceAgent);
            
            AgentInfo targetAgent = new AgentInfo();
            targetAgent.setAgentId("eventmesh-system");
            targetAgent.setAgentType("system");
            unregisterMessage.setTargetAgent(targetAgent);
            
            sendMessage(unregisterMessage);
            
            System.out.println("Agent " + agentId + " unregistered from EventMesh");
            
        } catch (Exception e) {
            System.err.println("Failed to unregister agent " + agentId + ": " + e.getMessage());
        }
    }
    
    /**
     * Start heartbeat mechanism
     */
    private void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                A2AMessage heartbeatMessage = protocolProcessor.createHeartbeatMessage(agentId);
                sendMessage(heartbeatMessage);
            } catch (Exception e) {
                System.err.println("Failed to send heartbeat for agent " + agentId + ": " + e.getMessage());
            }
        }, 10, 30, TimeUnit.SECONDS); // Send heartbeat every 30 seconds, starting after 10 seconds
    }
    
    /**
     * Start message processing
     */
    private void startMessageProcessing() {
        // Register message handler
        protocolProcessor.getMessageHandler().registerHandler(agentId, this::handleIncomingMessage);
    }
    
    /**
     * Handle incoming messages
     */
    private void handleIncomingMessage(A2AMessage message) {
        try {
            String messageType = message.getMessageType();
            
            switch (messageType) {
                case "TASK_REQUEST":
                    handleTaskRequest(message);
                    break;
                case "COLLABORATION_REQUEST":
                    handleCollaborationRequest(message);
                    break;
                case "BROADCAST":
                    handleBroadcast(message);
                    break;
                case "REGISTER_SUCCESS":
                case "REGISTER_FAILED":
                case "HEARTBEAT_ACK":
                    handleSystemResponse(message);
                    break;
                default:
                    System.out.println("Agent " + agentId + " received unknown message type: " + messageType);
            }
            
        } catch (Exception e) {
            System.err.println("Error handling incoming message for agent " + agentId + ": " + e.getMessage());
        }
    }
    
    /**
     * Handle task request
     */
    private void handleTaskRequest(A2AMessage message) {
        try {
            Map<String, Object> payload = (Map<String, Object>) message.getPayload();
            String taskId = (String) payload.get("taskId");
            String taskType = (String) payload.get("taskType");
            Map<String, Object> parameters = (Map<String, Object>) payload.get("parameters");
            
            System.out.println("Agent " + agentId + " received task request: " + taskId + " of type: " + taskType);
            
            // Process the task based on agent capabilities
            Object result = processTask(taskType, parameters);
            
            // Send task response
            A2AMessage response = createTaskResponse(message, taskId, result);
            sendMessage(response);
            
        } catch (Exception e) {
            System.err.println("Error handling task request for agent " + agentId + ": " + e.getMessage());
            
            // Send error response
            A2AMessage errorResponse = createTaskErrorResponse(message, e.getMessage());
            sendMessage(errorResponse);
        }
    }
    
    /**
     * Process task based on agent capabilities
     */
    private Object processTask(String taskType, Map<String, Object> parameters) {
        // Simulate task processing
        try {
            Thread.sleep(1000); // Simulate processing time
            
            switch (taskType) {
                case "data-processing":
                    return processData(parameters);
                case "image-analysis":
                    return analyzeImage(parameters);
                case "text-generation":
                    return generateText(parameters);
                default:
                    return Map.of("status", "completed", "result", "Task processed successfully");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Task processing interrupted", e);
        }
    }
    
    /**
     * Process data task
     */
    private Object processData(Map<String, Object> parameters) {
        return Map.of(
            "status", "completed",
            "result", "Data processed successfully",
            "processedRecords", 1000,
            "processingTime", "1.5s"
        );
    }
    
    /**
     * Analyze image task
     */
    private Object analyzeImage(Map<String, Object> parameters) {
        return Map.of(
            "status", "completed",
            "result", "Image analysis completed",
            "detectedObjects", 5,
            "confidence", 0.95
        );
    }
    
    /**
     * Generate text task
     */
    private Object generateText(Map<String, Object> parameters) {
        return Map.of(
            "status", "completed",
            "result", "Text generated successfully",
            "generatedText", "This is a sample generated text based on the provided parameters.",
            "wordCount", 15
        );
    }
    
    /**
     * Handle collaboration request
     */
    private void handleCollaborationRequest(A2AMessage message) {
        try {
            Map<String, Object> payload = (Map<String, Object>) message.getPayload();
            String collaborationId = (String) payload.get("collaborationId");
            
            System.out.println("Agent " + agentId + " received collaboration request: " + collaborationId);
            
            // Accept collaboration
            A2AMessage response = new A2AMessage();
            response.setMessageType("COLLABORATION_RESPONSE");
            response.setSourceAgent(message.getTargetAgent());
            response.setTargetAgent(message.getSourceAgent());
            response.setPayload(Map.of(
                "collaborationId", collaborationId,
                "status", "accepted",
                "agentId", agentId
            ));
            
            sendMessage(response);
            
        } catch (Exception e) {
            System.err.println("Error handling collaboration request for agent " + agentId + ": " + e.getMessage());
        }
    }
    
    /**
     * Handle broadcast message
     */
    private void handleBroadcast(A2AMessage message) {
        try {
            Map<String, Object> payload = (Map<String, Object>) message.getPayload();
            String broadcastId = (String) payload.get("broadcastId");
            String content = (String) payload.get("content");
            
            System.out.println("Agent " + agentId + " received broadcast: " + broadcastId + " - " + content);
            
        } catch (Exception e) {
            System.err.println("Error handling broadcast for agent " + agentId + ": " + e.getMessage());
        }
    }
    
    /**
     * Handle system response
     */
    private void handleSystemResponse(A2AMessage message) {
        String messageType = message.getMessageType();
        System.out.println("Agent " + agentId + " received system response: " + messageType);
    }
    
    /**
     * Create task response
     */
    private A2AMessage createTaskResponse(A2AMessage originalMessage, String taskId, Object result) {
        A2AMessage response = new A2AMessage();
        response.setMessageType("TASK_RESPONSE");
        response.setSourceAgent(originalMessage.getTargetAgent());
        response.setTargetAgent(originalMessage.getSourceAgent());
        
        response.setPayload(Map.of(
            "taskId", taskId,
            "status", "completed",
            "result", result,
            "completionTime", System.currentTimeMillis()
        ));
        
        // Copy correlation ID if present
        if (originalMessage.getMetadata() != null && originalMessage.getMetadata().getCorrelationId() != null) {
            MessageMetadata metadata = new MessageMetadata();
            metadata.setCorrelationId(originalMessage.getMetadata().getCorrelationId());
            response.setMetadata(metadata);
        }
        
        return response;
    }
    
    /**
     * Create task error response
     */
    private A2AMessage createTaskErrorResponse(A2AMessage originalMessage, String errorMessage) {
        A2AMessage response = new A2AMessage();
        response.setMessageType("TASK_RESPONSE");
        response.setSourceAgent(originalMessage.getTargetAgent());
        response.setTargetAgent(originalMessage.getSourceAgent());
        
        Map<String, Object> payload = (Map<String, Object>) originalMessage.getPayload();
        String taskId = (String) payload.get("taskId");
        
        response.setPayload(Map.of(
            "taskId", taskId,
            "status", "failed",
            "error", errorMessage,
            "completionTime", System.currentTimeMillis()
        ));
        
        // Copy correlation ID if present
        if (originalMessage.getMetadata() != null && originalMessage.getMetadata().getCorrelationId() != null) {
            MessageMetadata metadata = new MessageMetadata();
            metadata.setCorrelationId(originalMessage.getMetadata().getCorrelationId());
            response.setMetadata(metadata);
        }
        
        return response;
    }
    
    /**
     * Send message to EventMesh
     */
    private void sendMessage(A2AMessage message) {
        try {
            // Convert A2A message to CloudEvent and send
            // This is a simplified implementation - in practice, you would use EventMesh client
            String messageJson = JsonUtils.toJSONString(message);
            System.out.println("Agent " + agentId + " sending message: " + message.getMessageType());
            
            // Simulate sending to EventMesh
            // In real implementation, you would use EventMesh client to send the message
            
        } catch (Exception e) {
            System.err.println("Failed to send message from agent " + agentId + ": " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Send task request to another agent
     */
    public void sendTaskRequest(String targetAgentId, String taskType, Map<String, Object> parameters) {
        try {
            A2AMessage taskRequest = protocolProcessor.createTaskRequestMessage(
                agentId, targetAgentId, taskType, parameters);
            
            sendMessage(taskRequest);
            System.out.println("Agent " + agentId + " sent task request to " + targetAgentId);
            
        } catch (Exception e) {
            System.err.println("Failed to send task request from agent " + agentId + ": " + e.getMessage());
        }
    }
    
    /**
     * Send state synchronization message
     */
    public void sendStateSync(Map<String, Object> state) {
        try {
            A2AMessage stateSyncMessage = protocolProcessor.createStateSyncMessage(agentId, state);
            sendMessage(stateSyncMessage);
            
        } catch (Exception e) {
            System.err.println("Failed to send state sync from agent " + agentId + ": " + e.getMessage());
        }
    }
    
    /**
     * Get agent information
     */
    public AgentInfo getAgentInfo() {
        AgentInfo agentInfo = new AgentInfo();
        agentInfo.setAgentId(agentId);
        agentInfo.setAgentType(agentType);
        agentInfo.setCapabilities(capabilities);
        return agentInfo;
    }
    
    /**
     * Check if agent is running
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        // Create and start a sample agent
        SimpleA2AAgent agent = new SimpleA2AAgent(
            "sample-agent-001",
            "task-executor",
            new String[]{"data-processing", "image-analysis", "text-generation"}
        );
        
        try {
            agent.start();
            
            // Keep the agent running for a while
            Thread.sleep(60000); // Run for 1 minute
            
        } catch (Exception e) {
            System.err.println("Error running agent: " + e.getMessage());
        } finally {
            agent.stop();
        }
    }
}
