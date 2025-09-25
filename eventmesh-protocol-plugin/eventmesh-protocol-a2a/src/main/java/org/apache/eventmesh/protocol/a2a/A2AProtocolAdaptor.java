package org.apache.eventmesh.protocol.a2a;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * A2A (Agent-to-Agent) Protocol Adaptor
 * Handles agent-to-agent communication protocol for EventMesh
 */
@Slf4j
public class A2AProtocolAdaptor implements ProtocolAdaptor<ProtocolTransportObject> {

    private static final String PROTOCOL_TYPE = "A2A";
    private static final String PROTOCOL_VERSION = "2.0";
    
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
            return content.contains("A2A") || 
                   content.contains("agent") || 
                   content.contains("collaboration");
        } catch (Exception e) {
            log.warn("Failed to validate A2A message: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public CloudEvent toCloudEvent(ProtocolTransportObject protocolTransportObject) throws ProtocolHandleException {
        try {
            if (!isValid(protocolTransportObject)) {
                throw new ProtocolHandleException("Invalid A2A protocol message");
            }

            String content = protocolTransportObject.toString();
            Map<String, Object> messageData = parseA2AMessage(content);
            
            CloudEventBuilder builder = CloudEventBuilder.v1()
                .withId(generateMessageId())
                .withSource(URI.create("eventmesh-a2a"))
                .withType("org.apache.eventmesh.protocol.a2a.message")
                .withData(content.getBytes(StandardCharsets.UTF_8));

            // Add A2A specific extensions (must follow CloudEvent extension naming rules)
            builder.withExtension("protocol", PROTOCOL_TYPE);
            builder.withExtension("protocolversion", PROTOCOL_VERSION);
            
            if (messageData.containsKey("messageType")) {
                builder.withExtension("messagetype", messageData.get("messageType").toString());
            }
            
            if (messageData.containsKey("sourceAgent")) {
                builder.withExtension("sourceagent", messageData.get("sourceAgent").toString());
            }

            return builder.build();
            
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert A2A message to CloudEvent", e);
        }
    }

    @Override
    public List<CloudEvent> toBatchCloudEvent(ProtocolTransportObject protocolTransportObject) throws ProtocolHandleException {
        try {
            CloudEvent singleEvent = toCloudEvent(protocolTransportObject);
            return Collections.singletonList(singleEvent);
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert A2A batch message to CloudEvents", e);
        }
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

    private Map<String, Object> parseA2AMessage(String content) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = JsonUtils.parseObject(content, Map.class);
            return result;
        } catch (Exception e) {
            log.debug("Failed to parse as JSON, treating as plain text: {}", e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("content", content);
            result.put("messageType", "TEXT");
            return result;
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