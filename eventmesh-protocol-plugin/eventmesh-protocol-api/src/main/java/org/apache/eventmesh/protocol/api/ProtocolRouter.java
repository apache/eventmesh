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

package org.apache.eventmesh.protocol.api;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Protocol router for intelligent protocol selection and message routing.
 *
 * @since 1.3.0
 */
@Slf4j
public class ProtocolRouter {

    private static final ProtocolRouter INSTANCE = new ProtocolRouter();
    
    private final Map<String, RoutingRule> routingRules = new ConcurrentHashMap<>();
    
    private ProtocolRouter() {
        initializeDefaultRules();
    }
    
    public static ProtocolRouter getInstance() {
        return INSTANCE;
    }

    /**
     * Route message to appropriate protocol based on routing rules.
     *
     * @param message input message
     * @param preferredProtocol preferred protocol type
     * @return routed CloudEvent
     */
    public CloudEvent routeMessage(ProtocolTransportObject message, String preferredProtocol) 
        throws ProtocolHandleException {
        
        // 1. Try preferred protocol first
        if (preferredProtocol != null && EnhancedProtocolPluginFactory.isProtocolSupported(preferredProtocol)) {
            try {
                ProtocolAdaptor<ProtocolTransportObject> adaptor = 
                    EnhancedProtocolPluginFactory.getProtocolAdaptor(preferredProtocol);
                
                if (adaptor.isValid(message)) {
                    return adaptor.toCloudEvent(message);
                }
            } catch (Exception e) {
                log.warn("Failed to process message with preferred protocol {}: {}", 
                    preferredProtocol, e.getMessage());
            }
        }
        
        // 2. Apply routing rules
        String selectedProtocol = selectProtocolByRules(message);
        if (selectedProtocol != null) {
            try {
                ProtocolAdaptor<ProtocolTransportObject> adaptor = 
                    EnhancedProtocolPluginFactory.getProtocolAdaptor(selectedProtocol);
                return adaptor.toCloudEvent(message);
            } catch (Exception e) {
                log.warn("Failed to process message with rule-selected protocol {}: {}", 
                    selectedProtocol, e.getMessage());
            }
        }
        
        // 3. Try protocols by priority
        List<ProtocolAdaptor<ProtocolTransportObject>> adaptors = 
            EnhancedProtocolPluginFactory.getProtocolAdaptorsByPriority();
            
        for (ProtocolAdaptor<ProtocolTransportObject> adaptor : adaptors) {
            try {
                if (adaptor.isValid(message)) {
                    log.debug("Using protocol {} for message routing", adaptor.getProtocolType());
                    return adaptor.toCloudEvent(message);
                }
            } catch (Exception e) {
                log.debug("Protocol {} failed to process message: {}", 
                    adaptor.getProtocolType(), e.getMessage());
            }
        }
        
        throw new ProtocolHandleException(
            "No suitable protocol adaptor found for message type: " + message.getClass().getName());
    }

    /**
     * Route CloudEvent to target protocol.
     *
     * @param cloudEvent input CloudEvent
     * @param targetProtocol target protocol type
     * @return protocol transport object
     */
    public ProtocolTransportObject routeCloudEvent(CloudEvent cloudEvent, String targetProtocol) 
        throws ProtocolHandleException {
        
        if (targetProtocol == null || targetProtocol.trim().isEmpty()) {
            throw new ProtocolHandleException("Target protocol type cannot be null or empty");
        }
        
        ProtocolAdaptor<ProtocolTransportObject> adaptor = 
            EnhancedProtocolPluginFactory.getProtocolAdaptor(targetProtocol);
        
        return adaptor.fromCloudEvent(cloudEvent);
    }

    /**
     * Add routing rule.
     *
     * @param ruleName rule name
     * @param condition condition predicate
     * @param targetProtocol target protocol type
     */
    public void addRoutingRule(String ruleName, Predicate<ProtocolTransportObject> condition, 
                              String targetProtocol) {
        if (ruleName == null || condition == null || targetProtocol == null) {
            throw new IllegalArgumentException("Rule name, condition, and target protocol cannot be null");
        }
        
        routingRules.put(ruleName, new RoutingRule(condition, targetProtocol));
        log.info("Added routing rule: {} -> {}", ruleName, targetProtocol);
    }

    /**
     * Remove routing rule.
     *
     * @param ruleName rule name
     */
    public void removeRoutingRule(String ruleName) {
        if (routingRules.remove(ruleName) != null) {
            log.info("Removed routing rule: {}", ruleName);
        }
    }

    /**
     * Get best protocol for specific capability.
     *
     * @param capability required capability
     * @return best protocol adaptor or null if none found
     */
    public ProtocolAdaptor<ProtocolTransportObject> getBestProtocolForCapability(String capability) {
        List<ProtocolAdaptor<ProtocolTransportObject>> adaptors = 
            EnhancedProtocolPluginFactory.getProtocolAdaptorsByCapability(capability);
        
        return adaptors.isEmpty() ? null : adaptors.get(0);
    }

    /**
     * Batch route messages.
     *
     * @param messages list of messages
     * @param preferredProtocol preferred protocol type
     * @return list of CloudEvents
     */
    public List<CloudEvent> routeMessages(List<ProtocolTransportObject> messages, String preferredProtocol) 
        throws ProtocolHandleException {
        
        if (messages == null || messages.isEmpty()) {
            throw new ProtocolHandleException("Messages list cannot be null or empty");
        }
        
        // Check if preferred protocol supports batch processing
        if (preferredProtocol != null) {
            try {
                ProtocolAdaptor<ProtocolTransportObject> adaptor = 
                    EnhancedProtocolPluginFactory.getProtocolAdaptor(preferredProtocol);
                
                if (adaptor.supportsBatchProcessing() && messages.size() > 1) {
                    // Try batch processing if supported
                    ProtocolTransportObject batchMessage = createBatchMessage(messages);
                    if (batchMessage != null && adaptor.isValid(batchMessage)) {
                        return adaptor.toBatchCloudEvent(batchMessage);
                    }
                }
            } catch (Exception e) {
                log.warn("Batch processing failed with preferred protocol {}: {}", 
                    preferredProtocol, e.getMessage());
            }
        }
        
        // Fall back to individual message routing
        return messages.stream()
            .map(message -> {
                try {
                    return routeMessage(message, preferredProtocol);
                } catch (ProtocolHandleException e) {
                    log.error("Failed to route individual message", e);
                    throw new RuntimeException(e);
                }
            })
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Select protocol based on routing rules.
     */
    private String selectProtocolByRules(ProtocolTransportObject message) {
        for (Map.Entry<String, RoutingRule> entry : routingRules.entrySet()) {
            try {
                if (entry.getValue().condition.test(message)) {
                    log.debug("Message matched routing rule: {} -> {}", 
                        entry.getKey(), entry.getValue().targetProtocol);
                    return entry.getValue().targetProtocol;
                }
            } catch (Exception e) {
                log.warn("Error evaluating routing rule {}: {}", entry.getKey(), e.getMessage());
            }
        }
        return null;
    }

    /**
     * Initialize default routing rules.
     */
    private void initializeDefaultRules() {
        // HTTP messages
        addRoutingRule("http-messages", 
            message -> message.getClass().getName().contains("Http"), 
            "cloudevents");
        
        // gRPC messages  
        addRoutingRule("grpc-messages",
            message -> message.getClass().getName().contains("Grpc")
                || message.getClass().getName().contains("CloudEvent"),
            "cloudevents");
        
        // TCP messages
        addRoutingRule("tcp-messages",
            message -> message.getClass().getName().contains("Package"),
            "cloudevents");
        
        // A2A messages
        addRoutingRule("a2a-messages",
            message -> {
                if (message != null && message.getClass().getSimpleName().equals("RequestMessage")) {
                    try {
                        String content = message.toString();
                        return content.contains("\"protocol\":\"A2A\"") || content.contains("A2A");
                    } catch (Exception e) {
                        return false;
                    }
                }
                return false;
            },
            "A2A");
        
        log.info("Initialized {} default routing rules", routingRules.size());
    }

    /**
     * Create batch message from individual messages.
     * Override this method for specific batch message implementations.
     */
    protected ProtocolTransportObject createBatchMessage(List<ProtocolTransportObject> messages) {
        // Default implementation returns null, indicating no batch support
        // Subclasses can override this for specific batch message creation
        return null;
    }

    /**
     * Routing rule definition.
     */
    private static class RoutingRule {
        final Predicate<ProtocolTransportObject> condition;
        final String targetProtocol;
        
        RoutingRule(Predicate<ProtocolTransportObject> condition, String targetProtocol) {
            this.condition = condition;
            this.targetProtocol = targetProtocol;
        }
    }
}