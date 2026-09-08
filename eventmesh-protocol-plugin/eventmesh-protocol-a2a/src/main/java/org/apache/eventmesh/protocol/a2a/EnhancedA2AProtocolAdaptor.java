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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Enhanced A2A Protocol Adaptor that implements A2A (Agent-to-Agent) protocol over CloudEvents.
 *
 * <p>This adaptor supports:
 * 1. Standard A2A JSON-RPC 2.0 messages (messaging, task, notification, agent card operations).
 * 2. Agent Card registration and discovery via discovery topics.
 * 3. Agent status metadata augmentation.
 * 4. Delegation to standard CloudEvents/HTTP protocols.
 */
@Slf4j
public class EnhancedA2AProtocolAdaptor implements ProtocolAdaptor<ProtocolTransportObject> {

    private static final String PROTOCOL_TYPE = "A2A";
    private static final String PROTOCOL_VERSION = "2.0";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private ProtocolAdaptor<ProtocolTransportObject> cloudEventsAdaptor;
    private ProtocolAdaptor<ProtocolTransportObject> httpAdaptor;

    private volatile boolean initialized = false;

    private AgentCardValidator cardValidator;

    public EnhancedA2AProtocolAdaptor() {
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
            log.info("Initializing Enhanced A2A Protocol Adaptor v{} (Agent Card Registry Support)", PROTOCOL_VERSION);
            this.cardValidator = new AgentCardValidator(true);
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
                // ignore
            }

            if (node != null && node.has("jsonrpc") && "2.0".equals(node.get("jsonrpc").asText())) {
                return convertA2AToCloudEvent(node, content);
            }

            if (protocol.getClass().getName().contains("Http") && httpAdaptor != null) {
                return httpAdaptor.toCloudEvent(protocol);
            } else if (cloudEventsAdaptor != null) {
                return cloudEventsAdaptor.toCloudEvent(protocol);
            } else {
                if (node != null && node.has("method")) {
                    return convertA2AToCloudEvent(node, content);
                }
                throw new ProtocolHandleException("Unknown protocol message format");
            }

        } catch (ProtocolHandleException e) {
            throw e;
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
            } catch (Exception ignored) {
                // ignore
            }

            if (node != null && node.isArray()) {
                List<CloudEvent> events = new ArrayList<>();
                for (JsonNode item : node) {
                    if (item.has("jsonrpc")) {
                        events.add(convertA2AToCloudEvent(item, item.toString()));
                    }
                }
                if (!events.isEmpty()) {
                    return events;
                }
            }

            if (cloudEventsAdaptor != null) {
                try {
                    return cloudEventsAdaptor.toBatchCloudEvent(protocol);
                } catch (Exception e) {
                    if (httpAdaptor != null) {
                        return httpAdaptor.toBatchCloudEvent(protocol);
                    }
                }
            }

            CloudEvent single = toCloudEvent(protocol);
            return Collections.singletonList(single);

        } catch (ProtocolHandleException e) {
            throw e;
        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert batch to CloudEvents", e);
        }
    }

    @Override
    public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) throws ProtocolHandleException {
        try {
            if (isA2ACloudEvent(cloudEvent)) {
                return convertCloudEventToA2A(cloudEvent);
            }

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
            "collaboration",
            "agent-discovery",
            "agent-card-registry",
            "agent-status"
        );
    }

    @Override
    public boolean isValid(ProtocolTransportObject protocol) {
        if (protocol == null) {
            return false;
        }

        try {
            String content = protocol.toString();
            if (!content.contains("{")) {
                return false;
            }

            JsonNode node = objectMapper.readTree(content);
            if (node.has("jsonrpc")) {
                return true;
            }
        } catch (Exception e) {
            // ignore
        }

        if (cloudEventsAdaptor != null && cloudEventsAdaptor.isValid(protocol)) {
            return true;
        }
        if (httpAdaptor != null && httpAdaptor.isValid(protocol)) {
            return true;
        }

        return false;
    }

    private boolean isA2ACloudEvent(CloudEvent cloudEvent) {
        return PROTOCOL_TYPE.equals(cloudEvent.getExtension(A2AProtocolConstants.CE_EXTENSION_PROTOCOL))
            || cloudEvent.getType().startsWith(A2AProtocolConstants.CE_TYPE_PREFIX)
            || cloudEvent.getExtension(A2AProtocolConstants.CE_EXTENSION_A2A_METHOD) != null;
    }

    /**
     * Converts an A2A JSON-RPC message to CloudEvent.
     * Handles Agent Card operations with discovery topic routing.
     */
    private CloudEvent convertA2AToCloudEvent(JsonNode node, String content) throws ProtocolHandleException {
        try {
            boolean isRequest = node.has("method");
            boolean isResponse = node.has("result") || node.has("error");

            String id = node.has("id") ? node.get("id").asText() : generateMessageId();
            String ceType;
            String mcpType;
            String correlationId = null;
            String eventId = isRequest ? id : generateMessageId();

            CloudEventBuilder builder = CloudEventBuilder.v1()
                .withSource(java.net.URI.create("eventmesh-a2a"))
                .withData(content.getBytes(StandardCharsets.UTF_8))
                .withExtension(A2AProtocolConstants.CE_EXTENSION_PROTOCOL, PROTOCOL_TYPE)
                .withExtension(A2AProtocolConstants.CE_EXTENSION_PROTOCOL_VERSION, PROTOCOL_VERSION);

            if (isRequest) {
                String method = node.get("method").asText();
                mcpType = "request";

                if (A2AProtocolConstants.isAgentCardOperation(method)) {
                    ceType = buildAgentCardCloudEventType(method);
                    builder.withExtension(A2AProtocolConstants.CE_EXTENSION_A2A_METHOD, method);
                    extractAgentCardRouting(node, builder, method);
                } else if (A2AProtocolConstants.OP_SEND_STREAMING_MESSAGE.equals(method)) {
                    ceType = A2AProtocolConstants.CE_TYPE_PREFIX + method.replace("/", ".") + ".stream";
                    builder.withExtension(A2AProtocolConstants.CE_EXTENSION_A2A_METHOD, method);
                    extractStandardRouting(node, builder);
                } else {
                    ceType = A2AProtocolConstants.CE_TYPE_PREFIX + method.replace("/", ".") + ".req";
                    builder.withExtension(A2AProtocolConstants.CE_EXTENSION_A2A_METHOD, method);
                    extractStandardRouting(node, builder);
                }
            } else if (isResponse) {
                ceType = A2AProtocolConstants.CE_TYPE_PREFIX + "common.response";
                mcpType = "response";
                correlationId = id;
                builder.withExtension(A2AProtocolConstants.CE_EXTENSION_COLLABORATION_ID, correlationId);
            } else {
                ceType = A2AProtocolConstants.CE_TYPE_PREFIX + "unknown";
                mcpType = "unknown";
            }

            builder.withId(eventId)
                .withType(ceType)
                .withExtension(A2AProtocolConstants.CE_EXTENSION_MCP_TYPE, mcpType);

            return builder.build();

        } catch (Exception e) {
            throw new ProtocolHandleException("Failed to convert A2A message to CloudEvent", e);
        }
    }

    private String buildAgentCardCloudEventType(String method) {
        return A2AProtocolConstants.CE_TYPE_PREFIX + method.replace("/", ".") + ".req";
    }

    /**
     * Extracts routing for Agent Card operations.
     * For register/update: route to discovery topic with org_id/unit_id/agent_id from params.
     * For get/delete: route to discovery topic for lookup.
     * For list: route to discovery topic with wildcard.
     */
    private void extractAgentCardRouting(JsonNode node, CloudEventBuilder builder, String method) {
        if (!node.has("params")) {
            return;
        }
        JsonNode params = node.get("params");

        String orgId = getTextParam(params, "org_id");
        String unitId = getTextParam(params, "unit_id");
        String agentId = getTextParam(params, "agent_id");

        if (orgId != null && unitId != null && agentId != null) {
            AgentIdentity identity = new AgentIdentity(orgId, unitId, agentId);
            builder.withSubject(identity.discoveryTopic());
        }

        if (params.has("card")) {
            // Validate card if validator is available
            if (cardValidator != null) {
                String cardJson = params.get("card").toString();
                AgentCardValidator.ValidationResult result = cardValidator.validate(cardJson);
                if (!result.isValid()) {
                    log.warn("Agent card validation failed: {}", result.getErrorMessage());
                }
            }
        }
    }

    private void extractStandardRouting(JsonNode node, CloudEventBuilder builder) {
        if (!node.has("params")) {
            return;
        }
        JsonNode params = node.get("params");

        if (params.has("_topic")) {
            builder.withSubject(params.get("_topic").asText());
        } else if (params.has("_agentId")) {
            builder.withExtension(A2AProtocolConstants.CE_EXTENSION_TARGET_AGENT, params.get("_agentId").asText());
        }

        if (params.has("_seq")) {
            builder.withExtension(A2AProtocolConstants.CE_EXTENSION_SEQ, params.get("_seq").asText());
        }
    }

    private String getTextParam(JsonNode params, String key) {
        if (params.has(key)) {
            JsonNode val = params.get(key);
            return val.isTextual() ? val.asText() : null;
        }
        return null;
    }

    private ProtocolTransportObject convertCloudEventToA2A(CloudEvent cloudEvent) {
        if (cloudEventsAdaptor != null) {
            try {
                return cloudEventsAdaptor.fromCloudEvent(cloudEvent);
            } catch (Exception ignored) {
                // ignore
            }
        }

        byte[] data = cloudEvent.getData() != null ? cloudEvent.getData().toBytes() : new byte[0];
        String content = new String(data, StandardCharsets.UTF_8);
        return new SimpleA2AProtocolTransportObject(content, cloudEvent);
    }

    private String getTargetProtocol(CloudEvent cloudEvent) {
        if (cloudEvent == null) {
            return "cloudevents";
        }
        Object protocolDescObj = cloudEvent.getExtension("protocolDesc");
        if (protocolDescObj instanceof String) {
            return (String) protocolDescObj;
        }
        String type = cloudEvent.getType();
        if (type != null && type.contains("http")) {
            return "http";
        }
        return "cloudevents";
    }

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

    private Set<String> createCapabilitiesSet(String... capabilities) {
        Set<String> result = new HashSet<>();
        Collections.addAll(result, capabilities);
        return result;
    }

    private String generateMessageId() {
        return "a2a-" + System.currentTimeMillis() + "-" + Math.random();
    }
}
