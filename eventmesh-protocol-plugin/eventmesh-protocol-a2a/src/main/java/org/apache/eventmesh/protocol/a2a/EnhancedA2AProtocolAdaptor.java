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

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventBuilder;
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
    private final ProtocolAdaptor<ProtocolTransportObject> cloudEventsAdaptor;
    private final ProtocolAdaptor<ProtocolTransportObject> httpAdaptor;
    
    private volatile boolean initialized = false;

    public EnhancedA2AProtocolAdaptor() {
        // Leverage existing protocol infrastructure
        this.cloudEventsAdaptor = ProtocolPluginFactory.getProtocolAdaptor("cloudevents");
        this.httpAdaptor = ProtocolPluginFactory.getProtocolAdaptor("http");
    }

    @Override
    public void initialize() {
        if (!initialized) {
            log.info("Initializing Enhanced A2A Protocol Adaptor v{}", PROTOCOL_VERSION);
            log.info("Leveraging CloudEvents adaptor: {}", cloudEventsAdaptor.getClass().getSimpleName());
            log.info("Leveraging HTTP adaptor: {}", httpAdaptor.getClass().getSimpleName());
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
            
            // Fall back to existing protocol adaptors
            if (protocol.getClass().getName().contains("Http")) {
                return httpAdaptor.toCloudEvent(protocol);
            } else {
                return cloudEventsAdaptor.toCloudEvent(protocol);
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
            
            // Delegate to existing adaptors
            try {
                return cloudEventsAdaptor.toBatchCloudEvent(protocol);
            } catch (Exception e) {
                log.debug("CloudEvents adaptor failed, trying HTTP adaptor", e);
                return httpAdaptor.toBatchCloudEvent(protocol);
            }
            
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
                    return httpAdaptor.fromCloudEvent(cloudEvent);
                case "cloudevents":
                default:
                    return cloudEventsAdaptor.fromCloudEvent(cloudEvent);
            }
            
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
        return Set.of(
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
        return isA2AMessage(protocol) || 
               cloudEventsAdaptor.isValid(protocol) || 
               httpAdaptor.isValid(protocol);
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
               cloudEvent.getExtension("sourceAgent") != null ||
               cloudEvent.getExtension("targetAgent") != null;
    }

    /**
     * Convert A2A message to CloudEvent using existing CloudEvents infrastructure.
     */
    private CloudEvent convertA2AToCloudEvent(ProtocolTransportObject protocol) throws ProtocolHandleException {
        try {
            // First convert using existing adaptor to get base CloudEvent
            CloudEvent baseEvent = cloudEventsAdaptor.toCloudEvent(protocol);
            
            // Extract A2A-specific information
            A2AMessageInfo a2aInfo = extractA2AInfo(protocol);
            
            // Enhance CloudEvent with A2A extensions
            return CloudEventBuilder.from(baseEvent)
                .withExtension("protocol", PROTOCOL_TYPE)
                .withExtension("protocolVersion", PROTOCOL_VERSION)
                .withExtension("messageType", a2aInfo.messageType)
                .withExtension("sourceAgent", a2aInfo.sourceAgentId)
                .withExtension("targetAgent", a2aInfo.targetAgentId)
                .withExtension("agentCapabilities", String.join(",", a2aInfo.capabilities))
                .withExtension("collaborationId", a2aInfo.collaborationId)
                .withType("org.apache.eventmesh.protocol.a2a." + a2aInfo.messageType.toLowerCase())
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
            // Use existing adaptor to get base transport object
            ProtocolTransportObject baseTransport = cloudEventsAdaptor.fromCloudEvent(cloudEvent);
            
            // This would be enhanced with A2A-specific transport object creation
            // For now, delegate to existing implementation
            return baseTransport;
            
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
            
            info.messageType = (String) messageMap.getOrDefault("messageType", "UNKNOWN");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> sourceAgent = (Map<String, Object>) messageMap.get("sourceAgent");
            if (sourceAgent != null) {
                info.sourceAgentId = (String) sourceAgent.get("agentId");
                @SuppressWarnings("unchecked")
                List<String> caps = (List<String>) sourceAgent.get("capabilities");
                info.capabilities = caps != null ? caps : List.of();
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> targetAgent = (Map<String, Object>) messageMap.get("targetAgent");
            if (targetAgent != null) {
                info.targetAgentId = (String) targetAgent.get("agentId");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) messageMap.get("metadata");
            if (metadata != null) {
                info.collaborationId = (String) metadata.get("correlationId");
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
            return messages != null ? messages : List.of();
            
        } catch (Exception e) {
            log.warn("Failed to extract batch messages", e);
            return List.of();
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
        String messageType = "UNKNOWN";
        String sourceAgentId;
        String targetAgentId;
        List<String> capabilities = List.of();
        String collaborationId;
    }
}