package org.apache.eventmesh.runtime.core.protocol.a2a;

import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.http.HttpMessage;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageResponseHeader;
import org.apache.eventmesh.common.protocol.http.message.RequestMessage;
import org.apache.eventmesh.common.protocol.http.message.ResponseMessage;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.core.protocol.a2a.A2AProtocolAdaptor.A2AMessage;
import org.apache.eventmesh.runtime.core.protocol.a2a.A2AProtocolAdaptor.AgentInfo;
import org.apache.eventmesh.runtime.core.protocol.a2a.A2AProtocolAdaptor.MessageMetadata;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A2A Protocol Processor
 * Processes A2A protocol messages in EventMesh HTTP and gRPC handlers
 */
public class A2AProtocolProcessor {
    
    private static final A2AProtocolProcessor INSTANCE = new A2AProtocolProcessor();
    private final A2AMessageHandler messageHandler = A2AMessageHandler.getInstance();
    private final A2AProtocolAdaptor protocolAdaptor = new A2AProtocolAdaptor();
    
    private A2AProtocolProcessor() {}
    
    public static A2AProtocolProcessor getInstance() {
        return INSTANCE;
    }
    
    /**
     * Process A2A message from HTTP request
     */
    public CompletableFuture<ResponseMessage> processHttpMessage(RequestMessage requestMessage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Convert HTTP message to CloudEvent
                CloudEvent cloudEvent = protocolAdaptor.toCloudEvent(requestMessage);
                
                // Process A2A message
                A2AMessage a2aMessage = parseA2AMessage(cloudEvent);
                messageHandler.handleMessage(a2aMessage);
                
                // Create success response
                return createSuccessResponse(requestMessage);
                
            } catch (Exception e) {
                System.err.println("Error processing A2A HTTP message: " + e.getMessage());
                return createErrorResponse(requestMessage, e.getMessage());
            }
        });
    }
    
    /**
     * Process A2A message from gRPC CloudEvent
     */
    public CompletableFuture<CloudEvent> processGrpcMessage(CloudEvent cloudEvent) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check if this is an A2A protocol message
                String protocolType = cloudEvent.getAttributesMap().get("protocol").getCeString();
                if (!"A2A".equals(protocolType)) {
                    throw new IllegalArgumentException("Not an A2A protocol message");
                }
                
                // Parse A2A message
                A2AMessage a2aMessage = parseA2AMessage(cloudEvent);
                
                // Process message
                messageHandler.handleMessage(a2aMessage);
                
                // Create acknowledgment CloudEvent
                return createAcknowledgmentCloudEvent(cloudEvent);
                
            } catch (Exception e) {
                System.err.println("Error processing A2A gRPC message: " + e.getMessage());
                return createErrorCloudEvent(cloudEvent, e.getMessage());
            }
        });
    }
    
    /**
     * Parse A2A message from CloudEvent
     */
    private A2AMessage parseA2AMessage(CloudEvent cloudEvent) {
        try {
            String data = new String(cloudEvent.getData().toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            return JsonUtils.parseObject(data, A2AMessage.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse A2A message from CloudEvent", e);
        }
    }
    
    /**
     * Create success response for HTTP
     */
    private ResponseMessage createSuccessResponse(RequestMessage requestMessage) {
        SendMessageResponseHeader header = new SendMessageResponseHeader();
        header.putHeader("code", "200");
        header.putHeader("desc", "A2A message processed successfully");
        header.putHeader("protocol", "A2A");
        header.putHeader("version", "1.0");
        
        SendMessageResponseBody body = new SendMessageResponseBody();
        body.setRetCode(200);
        body.setRetMsg("A2A message processed successfully");
        body.setResTime(System.currentTimeMillis());
        
        return new ResponseMessage(header, body);
    }
    
    /**
     * Create error response for HTTP
     */
    private ResponseMessage createErrorResponse(RequestMessage requestMessage, String errorMessage) {
        SendMessageResponseHeader header = new SendMessageResponseHeader();
        header.putHeader("code", "500");
        header.putHeader("desc", "Error processing A2A message: " + errorMessage);
        header.putHeader("protocol", "A2A");
        header.putHeader("version", "1.0");
        
        SendMessageResponseBody body = new SendMessageResponseBody();
        body.setRetCode(500);
        body.setRetMsg("Error processing A2A message: " + errorMessage);
        body.setResTime(System.currentTimeMillis());
        
        return new ResponseMessage(header, body);
    }
    
    /**
     * Create acknowledgment CloudEvent for gRPC
     */
    private CloudEvent createAcknowledgmentCloudEvent(CloudEvent originalEvent) {
        CloudEvent.Builder builder = CloudEvent.newBuilder()
            .setId(java.util.UUID.randomUUID().toString())
            .setSource("eventmesh-a2a-processor")
            .setSpecVersion("1.0")
            .setType("org.apache.eventmesh.protocol.a2a.ack")
            .putAttributes("protocol", 
                org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue.newBuilder()
                    .setCeString("A2A").build())
            .putAttributes("version", 
                org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue.newBuilder()
                    .setCeString("1.0").build())
            .putAttributes("status", 
                org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue.newBuilder()
                    .setCeString("SUCCESS").build());
        
        // Copy correlation ID if present
        if (originalEvent.getAttributesMap().containsKey("correlationId")) {
            builder.putAttributes("correlationId", originalEvent.getAttributesMap().get("correlationId"));
        }
        
        return builder.build();
    }
    
    /**
     * Create error CloudEvent for gRPC
     */
    private CloudEvent createErrorCloudEvent(CloudEvent originalEvent, String errorMessage) {
        CloudEvent.Builder builder = CloudEvent.newBuilder()
            .setId(java.util.UUID.randomUUID().toString())
            .setSource("eventmesh-a2a-processor")
            .setSpecVersion("1.0")
            .setType("org.apache.eventmesh.protocol.a2a.error")
            .putAttributes("protocol", 
                org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue.newBuilder()
                    .setCeString("A2A").build())
            .putAttributes("version", 
                org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue.newBuilder()
                    .setCeString("1.0").build())
            .putAttributes("status", 
                org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue.newBuilder()
                    .setCeString("ERROR").build())
            .putAttributes("error", 
                org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue.newBuilder()
                    .setCeString(errorMessage).build());
        
        // Copy correlation ID if present
        if (originalEvent.getAttributesMap().containsKey("correlationId")) {
            builder.putAttributes("correlationId", originalEvent.getAttributesMap().get("correlationId"));
        }
        
        return builder.build();
    }
    
    /**
     * Create A2A message for agent registration
     */
    public A2AMessage createRegistrationMessage(String agentId, String agentType, String[] capabilities) {
        A2AMessage message = new A2AMessage();
        message.setMessageType("REGISTER");
        
        // Create source agent info
        AgentInfo sourceAgent = new AgentInfo();
        sourceAgent.setAgentId(agentId);
        sourceAgent.setAgentType(agentType);
        sourceAgent.setCapabilities(capabilities);
        message.setSourceAgent(sourceAgent);
        
        // Create target agent (system)
        AgentInfo targetAgent = new AgentInfo();
        targetAgent.setAgentId("eventmesh-system");
        targetAgent.setAgentType("system");
        message.setTargetAgent(targetAgent);
        
        // Set payload
        message.setPayload(Map.of("agentInfo", sourceAgent));
        
        return message;
    }
    
    /**
     * Create A2A message for task request
     */
    public A2AMessage createTaskRequestMessage(String sourceAgentId, String targetAgentId, 
                                             String taskType, Map<String, Object> parameters) {
        A2AMessage message = new A2AMessage();
        message.setMessageType("TASK_REQUEST");
        
        // Create source agent info
        AgentInfo sourceAgent = new AgentInfo();
        sourceAgent.setAgentId(sourceAgentId);
        message.setSourceAgent(sourceAgent);
        
        // Create target agent info
        AgentInfo targetAgent = new AgentInfo();
        targetAgent.setAgentId(targetAgentId);
        message.setTargetAgent(targetAgent);
        
        // Set payload
        Map<String, Object> payload = Map.of(
            "taskId", java.util.UUID.randomUUID().toString(),
            "taskType", taskType,
            "parameters", parameters,
            "constraints", Map.of(
                "timeout", 300,
                "priority", "NORMAL",
                "retryCount", 3
            )
        );
        message.setPayload(payload);
        
        return message;
    }
    
    /**
     * Create A2A message for heartbeat
     */
    public A2AMessage createHeartbeatMessage(String agentId) {
        A2AMessage message = new A2AMessage();
        message.setMessageType("HEARTBEAT");
        
        // Create source agent info
        AgentInfo sourceAgent = new AgentInfo();
        sourceAgent.setAgentId(agentId);
        message.setSourceAgent(sourceAgent);
        
        // Create target agent (system)
        AgentInfo targetAgent = new AgentInfo();
        targetAgent.setAgentId("eventmesh-system");
        targetAgent.setAgentType("system");
        message.setTargetAgent(targetAgent);
        
        // Set payload
        message.setPayload(Map.of("timestamp", System.currentTimeMillis()));
        
        return message;
    }
    
    /**
     * Create A2A message for state synchronization
     */
    public A2AMessage createStateSyncMessage(String agentId, Map<String, Object> state) {
        A2AMessage message = new A2AMessage();
        message.setMessageType("STATE_SYNC");
        
        // Create source agent info
        AgentInfo sourceAgent = new AgentInfo();
        sourceAgent.setAgentId(agentId);
        message.setSourceAgent(sourceAgent);
        
        // Create target agent (broadcast)
        AgentInfo targetAgent = new AgentInfo();
        targetAgent.setAgentId("broadcast");
        message.setTargetAgent(targetAgent);
        
        // Set payload
        message.setPayload(Map.of("agentState", state));
        
        return message;
    }
    
    /**
     * Get A2A message handler
     */
    public A2AMessageHandler getMessageHandler() {
        return messageHandler;
    }
    
    /**
     * Get A2A protocol adaptor
     */
    public A2AProtocolAdaptor getProtocolAdaptor() {
        return protocolAdaptor;
    }
}
