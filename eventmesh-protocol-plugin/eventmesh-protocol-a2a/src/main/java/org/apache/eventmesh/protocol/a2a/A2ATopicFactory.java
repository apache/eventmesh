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

/**
 * Factory for generating standard A2A topic paths following the convention:
 *
 * <pre>
 * {namespace}/a2a/v1/discovery/agentcards
 * {namespace}/a2a/v1/discovery/gatewaycards
 * {namespace}/a2a/v1/agent/request/{agentName}
 * {namespace}/a2a/v1/agent/status/{agentName}/{taskId}
 * {namespace}/a2a/v1/agent/response/{agentName}/{taskId}
 * {namespace}/a2a/v1/gateway/request/{gatewayId}
 * {namespace}/a2a/v1/gateway/status/{gatewayId}/{taskId}
 * {namespace}/a2a/v1/gateway/response/{gatewayId}/{taskId}
 * </pre>
 *
 * <p>When namespace is {@link AgentIdentity#GLOBAL_NAMESPACE} ("global"), the
 * namespace prefix is omitted (matching {@link AgentIdentity#discoveryTopic()}).
 */
public final class A2ATopicFactory {

    private static final String AGENT = "agent";
    private static final String GATEWAY = "gateway";
    private static final String REQUEST = "request";
    private static final String STATUS = "status";
    private static final String RESPONSE = "response";
    private static final String AGENTCARDS = "agentcards";
    private static final String GATEWAYCARDS = "gatewaycards";

    private A2ATopicFactory() {
    }

    // -------------------------------------------------------------------------
    // Discovery topics
    // -------------------------------------------------------------------------

    /**
     * Topic for publishing/subscribing AgentCard discovery messages.
     */
    public static String discoveryTopic(String namespace) {
        return buildBase(namespace) + "/" + A2AProtocolConstants.TOPIC_DISCOVERY + "/" + AGENTCARDS;
    }

    /**
     * Topic for publishing/subscribing GatewayCard discovery messages.
     */
    public static String gatewayDiscoveryTopic(String namespace) {
        return buildBase(namespace) + "/" + A2AProtocolConstants.TOPIC_DISCOVERY + "/" + GATEWAYCARDS;
    }

    // -------------------------------------------------------------------------
    // Agent topics
    // -------------------------------------------------------------------------

    /**
     * Topic for sending a task request to a specific agent.
     */
    public static String agentRequestTopic(String namespace, String agentName) {
        validateSegment(agentName, "agentName");
        return buildBase(namespace) + "/" + AGENT + "/" + REQUEST + "/" + agentName;
    }

    /**
     * Topic for an agent to publish status updates for a specific task.
     */
    public static String agentStatusTopic(String namespace, String agentName, String taskId) {
        validateSegment(agentName, "agentName");
        validateSegment(taskId, "taskId");
        return buildBase(namespace) + "/" + AGENT + "/" + STATUS + "/" + agentName + "/" + taskId;
    }

    /**
     * Topic for an agent to publish the final response for a specific task.
     */
    public static String agentResponseTopic(String namespace, String agentName, String taskId) {
        validateSegment(agentName, "agentName");
        validateSegment(taskId, "taskId");
        return buildBase(namespace) + "/" + AGENT + "/" + RESPONSE + "/" + agentName + "/" + taskId;
    }

    /**
     * Wildcard topic to subscribe to all status updates from a specific agent.
     */
    public static String agentStatusWildcardTopic(String namespace, String agentName) {
        validateSegment(agentName, "agentName");
        return buildBase(namespace) + "/" + AGENT + "/" + STATUS + "/" + agentName + "/+";
    }

    /**
     * Wildcard topic to subscribe to all responses from a specific agent.
     */
    public static String agentResponseWildcardTopic(String namespace, String agentName) {
        validateSegment(agentName, "agentName");
        return buildBase(namespace) + "/" + AGENT + "/" + RESPONSE + "/" + agentName + "/+";
    }

    // -------------------------------------------------------------------------
    // Gateway topics
    // -------------------------------------------------------------------------

    /**
     * Topic for sending a task request to a specific gateway.
     */
    public static String gatewayRequestTopic(String namespace, String gatewayId) {
        validateSegment(gatewayId, "gatewayId");
        return buildBase(namespace) + "/" + GATEWAY + "/" + REQUEST + "/" + gatewayId;
    }

    /**
     * Topic for a gateway to receive status updates for a specific task.
     */
    public static String gatewayStatusTopic(String namespace, String gatewayId, String taskId) {
        validateSegment(gatewayId, "gatewayId");
        validateSegment(taskId, "taskId");
        return buildBase(namespace) + "/" + GATEWAY + "/" + STATUS + "/" + gatewayId + "/" + taskId;
    }

    /**
     * Topic for a gateway to receive the final response for a specific task.
     */
    public static String gatewayResponseTopic(String namespace, String gatewayId, String taskId) {
        validateSegment(gatewayId, "gatewayId");
        validateSegment(taskId, "taskId");
        return buildBase(namespace) + "/" + GATEWAY + "/" + RESPONSE + "/" + gatewayId + "/" + taskId;
    }

    /**
     * Wildcard topic for a gateway to subscribe to all status updates.
     */
    public static String gatewayStatusWildcardTopic(String namespace, String gatewayId) {
        validateSegment(gatewayId, "gatewayId");
        return buildBase(namespace) + "/" + GATEWAY + "/" + STATUS + "/" + gatewayId + "/+";
    }

    /**
     * Wildcard topic for a gateway to subscribe to all responses.
     */
    public static String gatewayResponseWildcardTopic(String namespace, String gatewayId) {
        validateSegment(gatewayId, "gatewayId");
        return buildBase(namespace) + "/" + GATEWAY + "/" + RESPONSE + "/" + gatewayId + "/+";
    }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    /**
     * Parses an A2A topic and returns its components.
     *
     * @return parsed topic info, or {@code null} if not a valid A2A topic
     */
    public static ParsedTopic parse(String topic) {
        if (topic == null || topic.isEmpty()) {
            return null;
        }
        String[] parts = topic.split("/");
        // Global: a2a/v1/{entity}/{action}/... = parts starting at index 0
        // Namespaced: {ns}/a2a/v1/{entity}/{action}/... = parts starting at index 1
        int offset;
        String namespace;
        if (parts.length >= 3 && A2AProtocolConstants.TOPIC_NAMESPACE.equals(parts[0])
            && A2AProtocolConstants.TOPIC_VERSION.equals(parts[1])) {
            offset = 0;
            namespace = AgentIdentity.GLOBAL_NAMESPACE;
        } else if (parts.length >= 4 && A2AProtocolConstants.TOPIC_NAMESPACE.equals(parts[1])
            && A2AProtocolConstants.TOPIC_VERSION.equals(parts[2])) {
            offset = 1;
            namespace = parts[0];
        } else {
            return null;
        }

        // parts[offset] = "a2a", parts[offset+1] = "v1"
        if (parts.length <= offset + 2) {
            return null;
        }
        String segment3 = parts[offset + 2]; // discovery | agent | gateway

        if (A2AProtocolConstants.TOPIC_DISCOVERY.equals(segment3)) {
            // a2a/v1/discovery/{agentcards|gatewaycards}
            if (parts.length == offset + 4) {
                String cardType = parts[offset + 3];
                if (AGENTCARDS.equals(cardType)) {
                    return new ParsedTopic(namespace, EntityType.DISCOVERY_AGENT, null, null, null);
                } else if (GATEWAYCARDS.equals(cardType)) {
                    return new ParsedTopic(namespace, EntityType.DISCOVERY_GATEWAY, null, null, null);
                }
            }
            return null;
        }

        if (AGENT.equals(segment3)) {
            // a2a/v1/agent/{request|status|response}/{agentName}[/{taskId}]
            if (parts.length < offset + 5) {
                return null;
            }
            String action = parts[offset + 3];
            String agentName = parts[offset + 4];
            String taskId = (parts.length >= offset + 6) ? parts[offset + 5] : null;
            EntityType entityType;
            if (REQUEST.equals(action)) {
                entityType = EntityType.AGENT_REQUEST;
            } else if (STATUS.equals(action)) {
                entityType = EntityType.AGENT_STATUS;
            } else if (RESPONSE.equals(action)) {
                entityType = EntityType.AGENT_RESPONSE;
            } else {
                return null;
            }
            return new ParsedTopic(namespace, entityType, agentName, null, taskId);
        }

        if (GATEWAY.equals(segment3)) {
            // a2a/v1/gateway/{request|status|response}/{gatewayId}[/{taskId}]
            if (parts.length < offset + 5) {
                return null;
            }
            String action = parts[offset + 3];
            String gatewayId = parts[offset + 4];
            String taskId = (parts.length >= offset + 6) ? parts[offset + 5] : null;
            EntityType entityType;
            if (REQUEST.equals(action)) {
                entityType = EntityType.GATEWAY_REQUEST;
            } else if (STATUS.equals(action)) {
                entityType = EntityType.GATEWAY_STATUS;
            } else if (RESPONSE.equals(action)) {
                entityType = EntityType.GATEWAY_RESPONSE;
            } else {
                return null;
            }
            return new ParsedTopic(namespace, entityType, null, gatewayId, taskId);
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static String buildBase(String namespace) {
        if (namespace == null || AgentIdentity.GLOBAL_NAMESPACE.equals(namespace)) {
            return A2AProtocolConstants.TOPIC_NAMESPACE + "/" + A2AProtocolConstants.TOPIC_VERSION;
        }
        return namespace + "/" + A2AProtocolConstants.TOPIC_NAMESPACE + "/" + A2AProtocolConstants.TOPIC_VERSION;
    }

    private static void validateSegment(String value, String name) {
        if (value == null || !value.matches(A2AProtocolConstants.SEGMENT_ID_PATTERN)) {
            throw new IllegalArgumentException(
                "Invalid " + name + ": '" + value + "'. Must match " + A2AProtocolConstants.SEGMENT_ID_PATTERN);
        }
    }

    // -------------------------------------------------------------------------
    // Parsed topic result
    // -------------------------------------------------------------------------

    public enum EntityType {
        DISCOVERY_AGENT,
        DISCOVERY_GATEWAY,
        AGENT_REQUEST,
        AGENT_STATUS,
        AGENT_RESPONSE,
        GATEWAY_REQUEST,
        GATEWAY_STATUS,
        GATEWAY_RESPONSE
    }

    public static class ParsedTopic {

        private final String namespace;
        private final EntityType entityType;
        private final String agentName;
        private final String gatewayId;
        private final String taskId;

        public ParsedTopic(String namespace, EntityType entityType, String agentName,
                           String gatewayId, String taskId) {
            this.namespace = namespace;
            this.entityType = entityType;
            this.agentName = agentName;
            this.gatewayId = gatewayId;
            this.taskId = taskId;
        }

        public String getNamespace() {
            return namespace;
        }

        public EntityType getEntityType() {
            return entityType;
        }

        public String getAgentName() {
            return agentName;
        }

        public String getGatewayId() {
            return gatewayId;
        }

        public String getTaskId() {
            return taskId;
        }

        public boolean isRequest() {
            return entityType == EntityType.AGENT_REQUEST || entityType == EntityType.GATEWAY_REQUEST;
        }

        public boolean isStatus() {
            return entityType == EntityType.AGENT_STATUS || entityType == EntityType.GATEWAY_STATUS;
        }

        public boolean isResponse() {
            return entityType == EntityType.AGENT_RESPONSE || entityType == EntityType.GATEWAY_RESPONSE;
        }

        public boolean isDiscovery() {
            return entityType == EntityType.DISCOVERY_AGENT || entityType == EntityType.DISCOVERY_GATEWAY;
        }

        @Override
        public String toString() {
            return "ParsedTopic{namespace='" + namespace + "', entityType=" + entityType
                + ", agentName='" + agentName + "', gatewayId='" + gatewayId
                + "', taskId='" + taskId + "'}";
        }
    }
}
