package org.apache.eventmesh.protocol.a2a;

import org.apache.eventmesh.common.protocol.ProtocolAdaptor;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
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
import java.util.Map;
import java.util.UUID;

/**
 * A2A (Agent-to-Agent) Protocol Adaptor
 * Handles conversion between A2A protocol messages and EventMesh internal formats
 */
public class A2AProtocolAdaptor implements ProtocolAdaptor<ProtocolTransportObject> {

    public static final String PROTOCOL_TYPE = "A2A";
    public static final String PROTOCOL_VERSION = "1.0";

    @Override
    public CloudEvent toCloudEvent(ProtocolTransportObject protocolTransportObject) {
        if (protocolTransportObject instanceof RequestMessage) {
            return toCloudEvent((RequestMessage) protocolTransportObject);
        } else if (protocolTransportObject instanceof ResponseMessage) {
            return toCloudEvent((ResponseMessage) protocolTransportObject);
        }
        throw new IllegalArgumentException("Unsupported protocol transport object type");
    }

    @Override
    public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) {
        String protocolType = cloudEvent.getAttributesMap().get("protocol").getCeString();
        if (!PROTOCOL_TYPE.equals(protocolType)) {
            throw new IllegalArgumentException("Unsupported protocol type: " + protocolType);
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
            throw new RuntimeException("Failed to convert RequestMessage to CloudEvent", e);
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
            throw new RuntimeException("Failed to convert ResponseMessage to CloudEvent", e);
        }
    }

    private A2AMessage parseA2AMessage(CloudEvent cloudEvent) {
        try {
            String data = new String(cloudEvent.getData().toByteArray(), StandardCharsets.UTF_8);
            return JsonUtils.parseObject(data, A2AMessage.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse A2A message from CloudEvent", e);
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
            throw new RuntimeException("Failed to create HTTP message from A2A message", e);
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
