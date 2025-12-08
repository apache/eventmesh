package org.apache.eventmesh.protocol.a2a;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * A2A (Agent-to-Agent) Protocol Adaptor
 * Handles agent-to-agent communication protocol for EventMesh
 */
@Slf4j
public class A2AProtocolAdaptor implements ProtocolAdaptor<ProtocolTransportObject> {

    private static final String PROTOCOL_TYPE = "A2A";
    private static final String PROTOCOL_VERSION = "2.0";
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private volatile boolean initialized = false;

    @Override
    public void initialize() {
        if (!initialized) {
            log.info("Initializing A2A Protocol Adaptor");
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
    public Set<String> getCapabilities() {
        Set<String> capabilities = new HashSet<>();
        capabilities.add("agent-communication");
        capabilities.add("workflow-orchestration");
        capabilities.add("state-sync");
        return capabilities;
    }

    @Override
    public boolean isValid(ProtocolTransportObject cloudEvent) {
        if (cloudEvent == null) {
            return false;
        }
        
        try {
            String content = cloudEvent.toString();
            // Fast fail
            if (!content.contains("{")) {
                return false;
            }
            
            JsonNode node = objectMapper.readTree(content);
            if (node.has("protocol") && PROTOCOL_TYPE.equals(node.get("protocol").asText())) {
                return true;
            }
            
            // Allow array for batch processing
            if (node.isArray() && node.size() > 0) {
                JsonNode first = node.get(0);
                return first.has("protocol") && PROTOCOL_TYPE.equals(first.get("protocol").asText());
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public CloudEvent toCloudEvent(ProtocolTransportObject protocolTransportObject) throws ProtocolHandleException {
        try {
            String content = protocolTransportObject.toString();
            JsonNode rootNode = objectMapper.readTree(content);
            
            if (rootNode.isArray()) {
                 throw new ProtocolHandleException("Single message expected but got array. Use toBatchCloudEvent instead.");
            }

            return buildCloudEvent(rootNode, content);
            
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert A2A message to CloudEvent", e);
        }
    }

    @Override
    public List<CloudEvent> toBatchCloudEvent(ProtocolTransportObject protocolTransportObject) throws ProtocolHandleException {
        try {
            String content = protocolTransportObject.toString();
            JsonNode rootNode = objectMapper.readTree(content);
            
            List<CloudEvent> events = new ArrayList<>();
            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    events.add(buildCloudEvent(node, node.toString()));
                }
            } else {
                events.add(buildCloudEvent(rootNode, content));
            }
            return events;
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert A2A batch message to CloudEvents", e);
        }
    }

    private CloudEvent buildCloudEvent(JsonNode node, String content) throws Exception {
        if (!node.has("protocol") || !PROTOCOL_TYPE.equals(node.get("protocol").asText())) {
             // If implicit A2A, we might want to allow it, but strictly it should have the protocol field.
             // For now, enforcing the check if it's passed through valid().
             // However, for robustness, if missing, we proceed if it looks like an agent message.
        }

        String messageType = node.has("messageType") ? node.get("messageType").asText() : "UNKNOWN";
        // Validate against A2APerformatives if needed, but allow extensibility
        
        String sourceAgentId = "unknown";
        if (node.has("sourceAgent")) {
            JsonNode source = node.get("sourceAgent");
            if (source.has("agentId")) {
                sourceAgentId = source.get("agentId").asText();
            }
        }

        String targetAgentId = null;
        if (node.has("targetAgent")) {
            JsonNode target = node.get("targetAgent");
            if (target.has("agentId")) {
                targetAgentId = target.get("agentId").asText();
            }
        }
        
        CloudEventBuilder builder = CloudEventBuilder.v1()
            .withId(generateMessageId())
            .withSource(URI.create("eventmesh-a2a"))
            .withType("org.apache.eventmesh.protocol.a2a." + messageType.toLowerCase())
            .withData(content.getBytes(StandardCharsets.UTF_8));

        // Add A2A specific extensions
        builder.withExtension("protocol", PROTOCOL_TYPE);
        builder.withExtension("protocolversion", PROTOCOL_VERSION);
        builder.withExtension("messagetype", messageType);
        builder.withExtension("sourceagent", sourceAgentId);
        
        if (targetAgentId != null) {
            builder.withExtension("targetagent", targetAgentId);
        }

        return builder.build();
    }

    @Override
    public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) throws ProtocolHandleException {
        try {
            if (cloudEvent == null) {
                throw new ProtocolHandleException("CloudEvent cannot be null");
            }

            // Extract A2A message data from CloudEvent
            byte[] data = cloudEvent.getData() != null ? cloudEvent.getData().toBytes() : new byte[0];
            String content = new String(data, StandardCharsets.UTF_8);

            // Create a simple protocol transport object wrapper
            return new A2AProtocolTransportObject(content, cloudEvent);
            
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert CloudEvent to A2A message", e);
        }
    }

    private String generateMessageId() {
        return "a2a-" + System.currentTimeMillis() + "-" + Math.random();
    }

    /**
     * Simple wrapper for A2A protocol transport object
     */
    public static class A2AProtocolTransportObject implements ProtocolTransportObject {
        private final String content;
        private final CloudEvent sourceCloudEvent;

        public A2AProtocolTransportObject(String content, CloudEvent sourceCloudEvent) {
            this.content = content;
            this.sourceCloudEvent = sourceCloudEvent;
        }

        @Override
        public String toString() {
            return content;
        }

        public CloudEvent getSourceCloudEvent() {
            return sourceCloudEvent;
        }
    }

    /**
     * Agent information container
     */
    public static class AgentInfo {
        private String agentId;
        private String agentType;
        private String[] capabilities;
        private Map<String, Object> metadata;

        public String getAgentId() {
            return agentId;
        }

        public void setAgentId(String agentId) {
            this.agentId = agentId;
        }

        public String getAgentType() {
            return agentType;
        }

        public void setAgentType(String agentType) {
            this.agentType = agentType;
        }

        public String[] getCapabilities() {
            return capabilities;
        }

        public void setCapabilities(String[] capabilities) {
            this.capabilities = capabilities;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }
}