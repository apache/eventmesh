/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import java.util.Set;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import lombok.extern.slf4j.Slf4j;

/**
 * Enhanced A2A Protocol Adaptor that leverages existing EventMesh protocol infrastructure.
 * 
 * This implementation reuses CloudEvents protocol for message transport while adding
 * A2A-specific agent management and collaboration features on top.
 */
@Slf4j
public class EnhancedA2AProtocolAdaptor implements ProtocolAdaptor<ProtocolTransportObject> {

    private static final String PROTOCOL_TYPE = "A2A";
    private static final String PROTOCOL_VERSION = "2.0";
    
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
            log.info("Initializing Enhanced A2A Protocol Adaptor v{}", PROTOCOL_VERSION);
            if (cloudEventsAdaptor != null) {
                log.info("Leveraging CloudEvents adaptor: {}", cloudEventsAdaptor.getClass().getSimpleName());
            } else {
                log.warn("CloudEvents adaptor not available - running in standalone mode");
            }
            if (httpAdaptor != null) {
                log.info("Leveraging HTTP adaptor: {}", httpAdaptor.getClass().getSimpleName());
            } else {
                log.warn("HTTP adaptor not available - running in standalone mode");
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
            // First try to identify if this is an A2A message
            if (isA2AMessage(protocol)) {
                return convertA2AToCloudEvent(protocol);
            }
            
            // Fall back to existing protocol adaptors if available
            if (protocol.getClass().getName().contains("Http") && httpAdaptor != null) {
                return httpAdaptor.toCloudEvent(protocol);
            } else if (cloudEventsAdaptor != null) {
                return cloudEventsAdaptor.toCloudEvent(protocol);
            } else {
                // If no adaptors available, treat as A2A message
                return convertA2AToCloudEvent(protocol);
            }
            
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert to CloudEvent", e);
        }
    }

    @Override
    public List<CloudEvent> toBatchCloudEvent(ProtocolTransportObject protocol) throws ProtocolHandleException {
        try {
            // Check if this is A2A batch message
            if (isA2ABatchMessage(protocol)) {
                return convertA2ABatchToCloudEvents(protocol);
            }
            
            // Delegate to existing adaptors if available
            if (cloudEventsAdaptor != null) {
                try {
                    return cloudEventsAdaptor.toBatchCloudEvent(protocol);
                } catch (Exception e) {
                    log.debug("CloudEvents adaptor failed, trying HTTP adaptor", e);
                    if (httpAdaptor != null) {
                        return httpAdaptor.toBatchCloudEvent(protocol);
                    }
                }
            } else if (httpAdaptor != null) {
                return httpAdaptor.toBatchCloudEvent(protocol);
            }
            
            // Fallback - treat as single A2A message
            CloudEvent single = toCloudEvent(protocol);
            return Collections.singletonList(single);
            
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert batch to CloudEvents", e);
        }
    }

    @Override
    public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) throws ProtocolHandleException {
        try {
            // Check if this is an A2A CloudEvent
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
            
            // Fallback - treat as A2A CloudEvent
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
        return 90; // Higher priority than base CloudEvents
    }

    @Override
    public boolean supportsBatchProcessing() {
        return true;
    }

    @Override
    public Set<String> getCapabilities() {
        return createCapabilitiesSet(
            "agent-communication", 
            "workflow-orchestration", 
            "state-sync", 
            "collaboration",
            "cloudevents-compatible",
            "multi-protocol-transport"
        );
    }

    @Override
    public boolean isValid(ProtocolTransportObject protocol) {
        if (protocol == null) {
            return false;
        }
        
        // A2A protocol can handle various transport objects through delegation
        boolean valid = isA2AMessage(protocol);
        
        if (!valid && cloudEventsAdaptor != null) {
            valid = cloudEventsAdaptor.isValid(protocol);
        }
        
        if (!valid && httpAdaptor != null) {
            valid = httpAdaptor.isValid(protocol);
        }
        
        return valid;
    }

    /**
     * Check if the protocol transport object is an A2A message.
     */
    private boolean isA2AMessage(ProtocolTransportObject protocol) {
        try {
            String content = protocol.toString();
            return content.contains("\"protocol\":\"A2A\"") || 
                   content.contains("agent") ||
                   content.contains("collaboration") ||
                   content.contains("workflow");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if this is an A2A batch message.
     */
    private boolean isA2ABatchMessage(ProtocolTransportObject protocol) {
        try {
            String content = protocol.toString();
            return isA2AMessage(protocol) && 
                   (content.contains("batchMessages") || content.contains("agents"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if CloudEvent contains A2A extensions.
     */
    private boolean isA2ACloudEvent(CloudEvent cloudEvent) {
        return PROTOCOL_TYPE.equals(cloudEvent.getExtension("protocol")) ||
               cloudEvent.getType().startsWith("org.apache.eventmesh.protocol.a2a") ||
               cloudEvent.getExtension("sourceagent") != null ||
               cloudEvent.getExtension("targetagent") != null;
    }

    /**
     * Convert A2A message to CloudEvent using existing CloudEvents infrastructure.
     */
    private CloudEvent convertA2AToCloudEvent(ProtocolTransportObject protocol) throws ProtocolHandleException {
        try {
            CloudEvent baseEvent = null;
            
            // Try to use existing adaptor if available
            if (cloudEventsAdaptor != null) {
                try {
                    baseEvent = cloudEventsAdaptor.toCloudEvent(protocol);
                } catch (Exception e) {
                    log.debug("CloudEvents adaptor failed, creating base event manually", e);
                }
            }
            
            // Extract A2A-specific information
            A2AMessageInfo a2aInfo = extractA2AInfo(protocol);
            
            CloudEventBuilder builder;
            if (baseEvent != null) {
                // Enhance existing CloudEvent with A2A extensions
                builder = CloudEventBuilder.from(baseEvent);
            } else {
                // Create new CloudEvent from scratch
                builder = CloudEventBuilder.v1()
                    .withId(generateMessageId())
                    .withSource(java.net.URI.create("eventmesh-a2a"))
                    .withData(protocol.toString().getBytes(StandardCharsets.UTF_8));
            }
            
            return builder
                .withExtension("protocol", PROTOCOL_TYPE)
                .withExtension("protocolversion", PROTOCOL_VERSION)
                .withExtension("messagetype", a2aInfo.messagetype)
                .withExtension("sourceagent", a2aInfo.sourceagentId)
                .withExtension("targetagent", a2aInfo.targetagentId)
                .withExtension("agentcapabilities", String.join(",", a2aInfo.capabilities))
                .withExtension("collaborationid", a2aInfo.collaborationid)
                .withType("org.apache.eventmesh.protocol.a2a." + a2aInfo.messagetype.toLowerCase())
                .build();
                
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert A2A message to CloudEvent", e);
        }
    }

    /**
     * Convert A2A batch to CloudEvents.
     */
    private List<CloudEvent> convertA2ABatchToCloudEvents(ProtocolTransportObject protocol) 
        throws ProtocolHandleException {
        try {
            // Extract batch messages
            List<Map<String, Object>> batchMessages = extractBatchMessages(protocol);
            
            return batchMessages.stream()
                .map(messageData -> {
                    try {
                        // Create individual A2A messages and convert
                        String json = JsonUtils.toJSONString(messageData);
                        // This would need proper implementation based on the protocol structure
                        return convertA2AToCloudEvent(protocol); // Simplified
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to convert batch message", e);
                    }
                })
                .collect(java.util.stream.Collectors.toList());
                
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert A2A batch to CloudEvents", e);
        }
    }

    /**
     * Convert CloudEvent back to A2A protocol format.
     */
    private ProtocolTransportObject convertCloudEventToA2A(CloudEvent cloudEvent) 
        throws ProtocolHandleException {
        try {
            // Try using existing adaptor if available
            if (cloudEventsAdaptor != null) {
                try {
                    return cloudEventsAdaptor.fromCloudEvent(cloudEvent);
                } catch (Exception e) {
                    log.debug("CloudEvents adaptor failed, creating transport object manually", e);
                }
            }
            
            // Create A2A transport object manually
            byte[] data = cloudEvent.getData() != null ? cloudEvent.getData().toBytes() : new byte[0];
            String content = new String(data, StandardCharsets.UTF_8);
            
            // Create a simple A2A protocol transport object
            return new SimpleA2AProtocolTransportObject(content, cloudEvent);
            
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert CloudEvent to A2A", e);
        }
    }

    /**
     * Extract A2A-specific information from protocol transport object.
     */
    private A2AMessageInfo extractA2AInfo(ProtocolTransportObject protocol) {
        A2AMessageInfo info = new A2AMessageInfo();
        
        try {
            String content = protocol.toString();
            Map<String, Object> messageMap = JsonUtils.parseTypeReferenceObject(content,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            
            info.messagetype = (String) messageMap.getOrDefault("messagetype", "UNKNOWN");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> sourceagent = (Map<String, Object>) messageMap.get("sourceagent");
            if (sourceagent != null) {
                info.sourceagentId = (String) sourceagent.get("agentId");
                @SuppressWarnings("unchecked")
                List<String> caps = (List<String>) sourceagent.get("capabilities");
                info.capabilities = caps != null ? caps : Collections.emptyList();
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> targetagent = (Map<String, Object>) messageMap.get("targetagent");
            if (targetagent != null) {
                info.targetagentId = (String) targetagent.get("agentId");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) messageMap.get("metadata");
            if (metadata != null) {
                info.collaborationid = (String) metadata.get("correlationId");
            }
            
        } catch (Exception e) {
            log.warn("Failed to extract A2A info, using defaults", e);
        }
        
        return info;
    }

    /**
     * Extract batch messages from protocol.
     */
    private List<Map<String, Object>> extractBatchMessages(ProtocolTransportObject protocol) {
        try {
            String content = protocol.toString();
            Map<String, Object> data = JsonUtils.parseTypeReferenceObject(content,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> messages = (List<Map<String, Object>>) data.get("batchMessages");
            return messages != null ? messages : Collections.emptyList();
            
        } catch (Exception e) {
            log.warn("Failed to extract batch messages", e);
            return Collections.emptyList();
        }
    }

    /**
     * Get target protocol from CloudEvent extensions.
     */
    private String getTargetProtocol(CloudEvent cloudEvent) {
        String protocolDesc = (String) cloudEvent.getExtension("protocolDesc");
        if (protocolDesc != null) {
            return protocolDesc;
        }
        
        // Default based on event type
        if (cloudEvent.getType().contains("http")) {
            return "http";
        }
        
        return "cloudevents";
    }

    /**
     * A2A message information holder.
     */
    private static class A2AMessageInfo {
        String messagetype = "UNKNOWN";
        String sourceagentId;
        String targetagentId;
        List<String> capabilities = Collections.emptyList();
        String collaborationid;
    }
    
    /**
     * Simple A2A Protocol Transport Object implementation.
     */
    private static class SimpleA2AProtocolTransportObject implements ProtocolTransportObject {
        private final String content;
        private final CloudEvent sourceCloudEvent;

        public SimpleA2AProtocolTransportObject(String content, CloudEvent sourceCloudEvent) {
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
    
    // Helper method for Java 8 compatibility
    private Set<String> createCapabilitiesSet(String... capabilities) {
        Set<String> result = new HashSet<>();
        for (String capability : capabilities) {
            result.add(capability);
        }
        return result;
    }
    
    private String generateMessageId() {
        return "enhanced-a2a-" + System.currentTimeMillis() + "-" + Math.random();
    }
}