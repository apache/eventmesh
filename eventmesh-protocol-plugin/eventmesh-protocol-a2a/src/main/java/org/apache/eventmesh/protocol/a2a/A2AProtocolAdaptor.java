package org.apache.eventmesh.protocol.a2a;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue;
import org.apache.eventmesh.common.protocol.http.HttpMessage;
import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.body.message.SendMessageResponseBody;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.message.SendMessageResponseHeader;
import org.apache.eventmesh.common.protocol.http.message.RequestMessage;
import org.apache.eventmesh.common.protocol.http.message.ResponseMessage;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

/**
 * Enhanced A2A (Agent-to-Agent) Protocol Adaptor
 * Handles conversion between A2A protocol messages and EventMesh internal formats
 */
@Slf4j
public class A2AProtocolAdaptor implements ProtocolAdaptor<ProtocolTransportObject> {

    private static final String PROTOCOL_TYPE = "A2A";
    private static final String PROTOCOL_VERSION = "1.0";
    private volatile boolean initialized = false;


    @Override
    public void initialize() {
        if (!initialized) {
            log.info("Initializing A2A Protocol Adaptor v{}", PROTOCOL_VERSION);
            initialized = true;
        }
    }

    @Override
    public void destroy() {
        if (initialized) {
            log.info("Destroying A2A Protocol Adaptor");
            initialized = false;
        }
    }

    @Override
    public String getProtocolType() {
        return PROTOCOL_TYPE;
    }

    @Override
    public String getVersion() {
        return PROTOCOL_VERSION;
    }

    @Override
    public int getPriority() {
        return 80; // High priority for A2A protocol
    }

    @Override
    public boolean supportsBatchProcessing() {
        return true;
    }

    @Override
    public java.util.Set<String> getCapabilities() {
        return java.util.Set.of("agent-communication", "workflow-orchestration", "state-sync");
    }

    @Override
    public boolean isValid(ProtocolTransportObject protocol) {
        if (protocol == null) {
            return false;
        }
        
        // Check if it's an A2A message
        try {
            if (protocol instanceof RequestMessage) {
                String content = protocol.toString();
                return content.contains("\"protocol\":\"A2A\"") || 
                       content.contains("A2A") ||
                       content.contains("agent");
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public CloudEvent toCloudEvent(ProtocolTransportObject protocolTransportObject) 
        throws ProtocolHandleException {
        if (protocolTransportObject instanceof RequestMessage) {
            return toCloudEvent((RequestMessage) protocolTransportObject);
        } else if (protocolTransportObject instanceof ResponseMessage) {
            return toCloudEvent((ResponseMessage) protocolTransportObject);
        }
        throw new ProtocolHandleException("Unsupported protocol transport object type: " + 
            protocolTransportObject.getClass().getName());
    }

    @Override
    public java.util.List<CloudEvent> toBatchCloudEvent(ProtocolTransportObject protocol) 
        throws ProtocolHandleException {
        // For A2A protocol, we can process multiple agent messages in batch
        if (protocol instanceof RequestMessage) {
            try {
                String body = protocol.toString();
                if (body.contains("batchMessages")) {
                    // Parse batch A2A messages
                    java.util.List<A2AMessage> batchMessages = parseBatchA2AMessages(body);
                    return batchMessages.stream()
                        .map(this::convertA2AMessageToCloudEvent)
                        .collect(java.util.stream.Collectors.toList());
                }
            } catch (Exception e) {
                throw new ProtocolHandleException("Failed to process batch A2A messages", e);
            }
        }
        
        // Fall back to single message processing
        return java.util.List.of(toCloudEvent(protocol));
    }

    @Override
    public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) 
        throws ProtocolHandleException {
        String protocolType = cloudEvent.getAttributesMap().get("protocol").getCeString();
        if (!PROTOCOL_TYPE.equals(protocolType)) {
            throw new ProtocolHandleException("Unsupported protocol type: " + protocolType);
        }

        String messageType = cloudEvent.getAttributesMap().get("messageType").getCeString();
        
        // Convert CloudEvent to appropriate A2A message format
        A2AMessage a2aMessage = parseA2AMessage(cloudEvent);
        
        // Create HTTP message for transport
        return createHttpMessage(a2aMessage);
    }

    private CloudEvent toCloudEvent(RequestMessage requestMessage) {
        try {
            String body = requestMessage.getBody().toMap().get("content").toString();
            A2AMessage a2aMessage = JsonUtils.parseObject(body, A2AMessage.class);
            
            CloudEvent.Builder cloudEventBuilder = CloudEvent.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setSource("eventmesh")
                .setSpecVersion("1.0")
                .setType("org.apache.eventmesh.protocol.a2a")
                .setData(cloudEvent.getData())
                .putAttributes("protocol", CloudEventAttributeValue.newBuilder().setCeString(PROTOCOL_TYPE).build())
                .putAttributes("version", CloudEventAttributeValue.newBuilder().setCeString(PROTOCOL_VERSION).build())
                .putAttributes("messageType", CloudEventAttributeValue.newBuilder().setCeString(a2aMessage.getMessageType()).build())
                .putAttributes("sourceAgent", CloudEventAttributeValue.newBuilder().setCeString(a2aMessage.getSourceAgent().getAgentId()).build())
                .putAttributes("targetAgent", CloudEventAttributeValue.newBuilder().setCeString(a2aMessage.getTargetAgent().getAgentId()).build());

            if (a2aMessage.getMetadata() != null && a2aMessage.getMetadata().getCorrelationId() != null) {
                cloudEventBuilder.putAttributes("correlationId", 
                    CloudEventAttributeValue.newBuilder().setCeString(a2aMessage.getMetadata().getCorrelationId()).build());
            }

            return cloudEventBuilder.build();
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert RequestMessage to CloudEvent", e);
        }
    }

    private CloudEvent toCloudEvent(ResponseMessage responseMessage) {
        try {
            String body = responseMessage.getBody().toMap().get("content").toString();
            A2AMessage a2aMessage = JsonUtils.parseObject(body, A2AMessage.class);
            
            CloudEvent.Builder cloudEventBuilder = CloudEvent.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setSource("eventmesh")
                .setSpecVersion("1.0")
                .setType("org.apache.eventmesh.protocol.a2a")
                .setData(cloudEvent.getData())
                .putAttributes("protocol", CloudEventAttributeValue.newBuilder().setCeString(PROTOCOL_TYPE).build())
                .putAttributes("version", CloudEventAttributeValue.newBuilder().setCeString(PROTOCOL_VERSION).build())
                .putAttributes("messageType", CloudEventAttributeValue.newBuilder().setCeString(a2aMessage.getMessageType()).build())
                .putAttributes("sourceAgent", CloudEventAttributeValue.newBuilder().setCeString(a2aMessage.getSourceAgent().getAgentId()).build())
                .putAttributes("targetAgent", CloudEventAttributeValue.newBuilder().setCeString(a2aMessage.getTargetAgent().getAgentId()).build());

            return cloudEventBuilder.build();
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert ResponseMessage to CloudEvent", e);
        }
    }

    private A2AMessage parseA2AMessage(CloudEvent cloudEvent) {
        try {
            String data = new String(cloudEvent.getData().toByteArray(), StandardCharsets.UTF_8);
            return JsonUtils.parseObject(data, A2AMessage.class);
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to parse A2A message from CloudEvent", e);
        }
    }

    private ProtocolTransportObject createHttpMessage(A2AMessage a2aMessage) {
        try {
            // Create HTTP request message
            SendMessageRequestHeader header = new SendMessageRequestHeader();
            header.putHeader(ProtocolKey.REQUEST_CODE, "200");
            header.putHeader(ProtocolKey.LANGUAGE, "JAVA");
            header.putHeader(ProtocolKey.PROTOCOL_TYPE, PROTOCOL_TYPE);
            header.putHeader(ProtocolKey.PROTOCOL_VERSION, PROTOCOL_VERSION);
            header.putHeader(ProtocolKey.PROTOCOL_DESC, "Agent-to-Agent Communication Protocol");

            SendMessageRequestBody body = new SendMessageRequestBody();
            body.setContent(JsonUtils.toJSONString(a2aMessage));
            body.setTtl(300);
            body.setUniqueId(UUID.randomUUID().toString());

            RequestMessage requestMessage = new RequestMessage(header, body);
            return requestMessage;
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to create HTTP message from A2A message", e);
        }
    }

    /**
     * Parse batch A2A messages from request body.
     */
    private List<A2AMessage> parseBatchA2AMessages(String body) throws ProtocolHandleException {
        try {
            // Parse JSON to extract batch messages
            Map<String, Object> bodyMap = JsonUtils.parseTypeReferenceObject(body, 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            
            if (bodyMap.containsKey("batchMessages")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> messagesData = (List<Map<String, Object>>) bodyMap.get("batchMessages");
                
                return messagesData.stream()
                    .map(data -> JsonUtils.mapToObject(data, A2AMessage.class))
                    .collect(java.util.stream.Collectors.toList());
            }
            
            return List.of();
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to parse batch A2A messages", e);
        }
    }

    /**
     * Convert A2A message to CloudEvent.
     */
    private CloudEvent convertA2AMessageToCloudEvent(A2AMessage a2aMessage) {
        try {
            CloudEvent.Builder cloudEventBuilder = CloudEvent.newBuilder()
                .setId(a2aMessage.getMessageId() != null ? a2aMessage.getMessageId() : UUID.randomUUID().toString())
                .setSource("eventmesh-a2a")
                .setSpecVersion("1.0")
                .setType("org.apache.eventmesh.protocol.a2a." + a2aMessage.getMessageType().toLowerCase())
                .setData(com.google.protobuf.ByteString.copyFromUtf8(JsonUtils.toJSONString(a2aMessage)))
                .putAttributes("protocol", 
                    CloudEvent.CloudEventAttributeValue.newBuilder().setCeString(PROTOCOL_TYPE).build())
                .putAttributes("version", 
                    CloudEvent.CloudEventAttributeValue.newBuilder().setCeString(PROTOCOL_VERSION).build())
                .putAttributes("messageType", 
                    CloudEvent.CloudEventAttributeValue.newBuilder().setCeString(a2aMessage.getMessageType()).build());

            if (a2aMessage.getSourceAgent() != null) {
                cloudEventBuilder.putAttributes("sourceAgent", 
                    CloudEvent.CloudEventAttributeValue.newBuilder()
                        .setCeString(a2aMessage.getSourceAgent().getAgentId()).build());
            }

            if (a2aMessage.getTargetAgent() != null) {
                cloudEventBuilder.putAttributes("targetAgent", 
                    CloudEvent.CloudEventAttributeValue.newBuilder()
                        .setCeString(a2aMessage.getTargetAgent().getAgentId()).build());
            }

            if (a2aMessage.getMetadata() != null && a2aMessage.getMetadata().getCorrelationId() != null) {
                cloudEventBuilder.putAttributes("correlationId", 
                    CloudEvent.CloudEventAttributeValue.newBuilder()
                        .setCeString(a2aMessage.getMetadata().getCorrelationId()).build());
            }

            return cloudEventBuilder.build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert A2A message to CloudEvent", e);
        }
    }

    /**
     * A2A Message structure
     */
    public static class A2AMessage {
        private String protocol = PROTOCOL_TYPE;
        private String version = PROTOCOL_VERSION;
        private String messageId;
        private String timestamp;
        private AgentInfo sourceAgent;
        private AgentInfo targetAgent;
        private String messageType;
        private Object payload;
        private MessageMetadata metadata;

        // Constructors, getters, and setters
        public A2AMessage() {
            this.messageId = UUID.randomUUID().toString();
            this.timestamp = java.time.Instant.now().toString();
        }

        // Getters and setters
        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public String getMessageId() { return messageId; }
        public void setMessageId(String messageId) { this.messageId = messageId; }
        
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        
        public AgentInfo getSourceAgent() { return sourceAgent; }
        public void setSourceAgent(AgentInfo sourceAgent) { this.sourceAgent = sourceAgent; }
        
        public AgentInfo getTargetAgent() { return targetAgent; }
        public void setTargetAgent(AgentInfo targetAgent) { this.targetAgent = targetAgent; }
        
        public String getMessageType() { return messageType; }
        public void setMessageType(String messageType) { this.messageType = messageType; }
        
        public Object getPayload() { return payload; }
        public void setPayload(Object payload) { this.payload = payload; }
        
        public MessageMetadata getMetadata() { return metadata; }
        public void setMetadata(MessageMetadata metadata) { this.metadata = metadata; }
    }

    /**
     * Agent Information
     */
    public static class AgentInfo {
        private String agentId;
        private String agentType;
        private String[] capabilities;
        private Map<String, String> endpoints;
        private Map<String, String> resources;

        // Constructors, getters, and setters
        public AgentInfo() {}

        public String getAgentId() { return agentId; }
        public void setAgentId(String agentId) { this.agentId = agentId; }
        
        public String getAgentType() { return agentType; }
        public void setAgentType(String agentType) { this.agentType = agentType; }
        
        public String[] getCapabilities() { return capabilities; }
        public void setCapabilities(String[] capabilities) { this.capabilities = capabilities; }
        
        public Map<String, String> getEndpoints() { return endpoints; }
        public void setEndpoints(Map<String, String> endpoints) { this.endpoints = endpoints; }
        
        public Map<String, String> getResources() { return resources; }
        public void setResources(Map<String, String> resources) { this.resources = resources; }
    }

    /**
     * Message Metadata
     */
    public static class MessageMetadata {
        private String priority = "NORMAL";
        private int ttl = 300;
        private String correlationId;

        // Constructors, getters, and setters
        public MessageMetadata() {}

        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        
        public int getTtl() { return ttl; }
        public void setTtl(int ttl) { this.ttl = ttl; }
        
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    }
}
