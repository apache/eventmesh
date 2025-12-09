package org.apache.eventmesh.protocol.a2a;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.common.utils.JsonUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Enhanced A2A Protocol Adaptor that implements MCP (Model Context Protocol) over CloudEvents.
 * 
 * This adaptor supports:
 * 1. Standard MCP JSON-RPC 2.0 messages.
 * 2. Delegation to standard CloudEvents/HTTP protocols.
 */
@Slf4j
public class EnhancedA2AProtocolAdaptor implements ProtocolAdaptor<ProtocolTransportObject> {

    private static final String PROTOCOL_TYPE = "A2A";
    private static final String PROTOCOL_VERSION = "2.0";
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Reuse existing protocol adaptors
    private ProtocolAdaptor<ProtocolTransportObject> cloudEventsAdaptor;
    private ProtocolAdaptor<ProtocolTransportObject> httpAdaptor;
    
    private volatile boolean initialized = false;

    public EnhancedA2AProtocolAdaptor() {
        // Leverage existing protocol infrastructure with null checks
        try {
            this.cloudEventsAdaptor = ProtocolPluginFactory.getProtocolAdaptor("cloudevents");
        } catch (Exception e) {
            log.warn("CloudEvents adaptor not available: {}", e.getMessage());
            this.cloudEventsAdaptor = null;
        }
        
        try {
            this.httpAdaptor = ProtocolPluginFactory.getProtocolAdaptor("http");
        } catch (Exception e) {
            log.warn("HTTP adaptor not available: {}", e.getMessage());
            this.httpAdaptor = null;
        }
    }

    @Override
    public void initialize() {
        if (!initialized) {
            log.info("Initializing Enhanced A2A Protocol Adaptor v{} (MCP Support)", PROTOCOL_VERSION);
            if (cloudEventsAdaptor != null) {
                log.info("Leveraging CloudEvents adaptor: {}", cloudEventsAdaptor.getClass().getSimpleName());
            }
            initialized = true;
        }
    }

    @Override
    public void destroy() {
        if (initialized) {
            log.info("Destroying Enhanced A2A Protocol Adaptor");
            initialized = false;
        }
    }

    @Override
    public CloudEvent toCloudEvent(ProtocolTransportObject protocol) throws ProtocolHandleException {
        try {
            String content = protocol.toString();
            JsonNode node = null;
            try {
                if (content.contains("{")) {
                     node = objectMapper.readTree(content);
                }
            } catch (Exception ignored) {
            }

            // 1. Check for MCP / JSON-RPC 2.0
            if (node != null && node.has("jsonrpc") && "2.0".equals(node.get("jsonrpc").asText())) {
                 return convertMcpToCloudEvent(node, content);
            }
            
            // 2. Delegation
            if (protocol.getClass().getName().contains("Http") && httpAdaptor != null) {
                return httpAdaptor.toCloudEvent(protocol);
            } else if (cloudEventsAdaptor != null) {
                return cloudEventsAdaptor.toCloudEvent(protocol);
            } else {
                // Last resort: if it looks like JSON but missing headers, treat as MCP Request implicitly if it has 'method'
                if (node != null && node.has("method")) {
                     return convertMcpToCloudEvent(node, content);
                }
                throw new ProtocolHandleException("Unknown protocol message format");
            }
            
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert to CloudEvent", e);
        }
    }

    @Override
    public List<CloudEvent> toBatchCloudEvent(ProtocolTransportObject protocol) throws ProtocolHandleException {
        try {
            String content = protocol.toString();
            JsonNode node = null;
            try {
                if (content.contains("[")) {
                     node = objectMapper.readTree(content);
                }
            } catch (Exception ignored) {}

            // Check if this is a Batch (JSON Array)
            if (node != null && node.isArray()) {
                 List<CloudEvent> events = new ArrayList<>();
                 for (JsonNode item : node) {
                     if (item.has("jsonrpc")) {
                         events.add(convertMcpToCloudEvent(item, item.toString()));
                     }
                 }
                 if (!events.isEmpty()) {
                     return events;
                 }
            }
            
            // Delegate
            if (cloudEventsAdaptor != null) {
                try {
                    return cloudEventsAdaptor.toBatchCloudEvent(protocol);
                } catch (Exception e) {
                    if (httpAdaptor != null) return httpAdaptor.toBatchCloudEvent(protocol);
                }
            }
            
            // Fallback
            CloudEvent single = toCloudEvent(protocol);
            return Collections.singletonList(single);
            
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert batch to CloudEvents", e);
        }
    }

    @Override
    public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) throws ProtocolHandleException {
        try {
            // Check if this is an A2A/MCP CloudEvent
            if (isA2ACloudEvent(cloudEvent)) {
                return convertCloudEventToA2A(cloudEvent);
            }
            
            // Determine target protocol from CloudEvent extensions
            String targetProtocol = getTargetProtocol(cloudEvent);
            
            switch (targetProtocol.toLowerCase()) {
                case "http":
                    if (httpAdaptor != null) {
                        return httpAdaptor.fromCloudEvent(cloudEvent);
                    }
                    break;
                case "cloudevents":
                default:
                    if (cloudEventsAdaptor != null) {
                        return cloudEventsAdaptor.fromCloudEvent(cloudEvent);
                    }
                    break;
            }
            
            return convertCloudEventToA2A(cloudEvent);
            
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert from CloudEvent", e);
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
        return 90; 
    }

    @Override
    public boolean supportsBatchProcessing() {
        return true;
    }

    @Override
    public Set<String> getCapabilities() {
        return createCapabilitiesSet(
            "mcp-jsonrpc",
            "agent-communication", 
            "workflow-orchestration", 
            "collaboration"
        );
    }

    @Override
    public boolean isValid(ProtocolTransportObject protocol) {
        if (protocol == null) return false;
        
        try {
            String content = protocol.toString();
            // Fast fail
            if (!content.contains("{")) return false;
            
            JsonNode node = objectMapper.readTree(content);
            // Valid if JSON-RPC
            if (node.has("jsonrpc")) {
                return true;
            }
        } catch (Exception e) {
            // ignore
        }
        
        if (cloudEventsAdaptor != null && cloudEventsAdaptor.isValid(protocol)) return true;
        if (httpAdaptor != null && httpAdaptor.isValid(protocol)) return true;
        
        return false;
    }

    private boolean isA2ACloudEvent(CloudEvent cloudEvent) {
        return PROTOCOL_TYPE.equals(cloudEvent.getExtension("protocol")) ||
               cloudEvent.getType().startsWith("org.apache.eventmesh.a2a") ||
               cloudEvent.getExtension("a2amethod") != null;
    }

    /**
     * Converts a modern MCP / A2A JSON-RPC message to CloudEvent.
     * Distinguishes between Requests and Responses for Event-Driven Async RPC pattern.
     */
    private CloudEvent convertMcpToCloudEvent(JsonNode node, String content) throws ProtocolHandleException {
        try {
            boolean isRequest = node.has("method");
            boolean isResponse = node.has("result") || node.has("error");
            
            String id = node.has("id") ? node.get("id").asText() : generateMessageId();
            String ceType;
            String mcpType;
            String correlationId = null;
            String eventId = isRequest ? id : generateMessageId(); // For request, CE id = RPC id. For response, CE id is new.

            CloudEventBuilder builder = CloudEventBuilder.v1()
                .withSource(java.net.URI.create("eventmesh-a2a"))
                .withData(content.getBytes(StandardCharsets.UTF_8))
                .withExtension("protocol", PROTOCOL_TYPE)
                .withExtension("protocolversion", PROTOCOL_VERSION);

            if (isRequest) {
                // JSON-RPC Request -> Event
                String method = node.get("method").asText();
                
                // Determine suffix based on operation type
                String suffix = ".req";
                if (A2AProtocolConstants.OP_SEND_STREAMING_MESSAGE.equals(method)) {
                    suffix = ".stream";
                }
                
                ceType = "org.apache.eventmesh.a2a." + method.replace("/", ".") + suffix;
                mcpType = "request";
                
                builder.withExtension("a2amethod", method);
                
                // Extract optional params for routing and sequencing
                if (node.has("params")) {
                    JsonNode params = node.get("params");
                    
                    // 1. Pub/Sub Routing (Priority): Broadcast to a Topic
                    if (params.has("_topic")) {
                        builder.withSubject(params.get("_topic").asText());
                    }
                    // 2. P2P Routing (Fallback): Unicast to specific Agent
                    else if (params.has("_agentId")) {
                        builder.withExtension("targetagent", params.get("_agentId").asText());
                    }
                    
                    // 3. Sequencing for Streaming
                    if (params.has("_seq")) {
                        builder.withExtension("seq", params.get("_seq").asText());
                    }
                }
            } else if (isResponse) {
                // JSON-RPC Response -> Event
                // We map the RPC ID to correlationId so the requester can match it
                ceType = "org.apache.eventmesh.a2a.common.response";
                mcpType = "response";
                correlationId = id;
                
                builder.withExtension("collaborationid", correlationId);
            } else {
                // Notification or invalid
                ceType = "org.apache.eventmesh.a2a.unknown";
                mcpType = "unknown";
            }

            builder.withId(eventId)
                   .withType(ceType)
                   .withExtension("mcptype", mcpType);
            
            return builder.build();
            
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert MCP/A2A message to CloudEvent", e);
        }
    }

    private ProtocolTransportObject convertCloudEventToA2A(CloudEvent cloudEvent) 
        throws ProtocolHandleException {
        try {
            if (cloudEventsAdaptor != null) {
                try {
                    return cloudEventsAdaptor.fromCloudEvent(cloudEvent);
                } catch (Exception ignored) {}
            }
            
            byte[] data = cloudEvent.getData() != null ? cloudEvent.getData().toBytes() : new byte[0];
            String content = new String(data, StandardCharsets.UTF_8);
            return new SimpleA2AProtocolTransportObject(content, cloudEvent);
            
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert CloudEvent to A2A", e);
        }
    }

    private String getTargetProtocol(CloudEvent cloudEvent) {
        String protocolDesc = (String) cloudEvent.getExtension("protocolDesc");
        if (protocolDesc != null) return protocolDesc;
        if (cloudEvent.getType().contains("http")) return "http";
        return "cloudevents";
    }
    
    private static class SimpleA2AProtocolTransportObject implements ProtocolTransportObject {
        private final String content;
        private final CloudEvent sourceCloudEvent;
        public SimpleA2AProtocolTransportObject(String content, CloudEvent sourceCloudEvent) {
            this.content = content;
            this.sourceCloudEvent = sourceCloudEvent;
        }
        @Override public String toString() { return content; }
        public CloudEvent getSourceCloudEvent() { return sourceCloudEvent; }
    }
    
    private Set<String> createCapabilitiesSet(String... capabilities) {
        Set<String> result = new HashSet<>();
        Collections.addAll(result, capabilities);
        return result;
    }
    
    private String generateMessageId() {
        return "a2a-mcp-" + System.currentTimeMillis() + "-" + Math.random();
    }
}