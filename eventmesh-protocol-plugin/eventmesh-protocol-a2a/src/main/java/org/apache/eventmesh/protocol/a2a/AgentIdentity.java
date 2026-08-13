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

import java.io.Serializable;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the hierarchical identity of an A2A agent: org_id / unit_id / agent_id.
 * Also provides discovery topic construction and parsing per the A2A protocol.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentIdentity implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String GLOBAL_NAMESPACE = "global";
    public static final String TOPIC_NAMESPACE = "a2a";
    public static final String TOPIC_VERSION = "v1";
    public static final String TOPIC_DISCOVERY = "discovery";

    private String orgId;

    private String unitId;

    private String agentId;

    private String namespace;

    public AgentIdentity(String orgId, String unitId, String agentId) {
        this.orgId = orgId;
        this.unitId = unitId;
        this.agentId = agentId;
        this.namespace = GLOBAL_NAMESPACE;
    }

    /**
     * Builds the discovery topic for this agent identity.
     * Global namespace: a2a/v1/discovery/{orgId}/{unitId}/{agentId}
     * Custom namespace: {namespace}/a2a/v1/discovery/{orgId}/{unitId}/{agentId}
     */
    public String discoveryTopic() {
        if (GLOBAL_NAMESPACE.equals(namespace)) {
            return String.join("/", TOPIC_NAMESPACE, TOPIC_VERSION, TOPIC_DISCOVERY, orgId, unitId, agentId);
        }
        return String.join("/", namespace, TOPIC_NAMESPACE, TOPIC_VERSION, TOPIC_DISCOVERY, orgId, unitId, agentId);
    }

    /**
     * Parses a discovery topic string into an AgentIdentity.
     *
     * @param topic the discovery topic
     * @return parsed AgentIdentity, or null if the topic does not match the expected pattern
     */
    public static AgentIdentity fromDiscoveryTopic(String topic) {
        if (topic == null) {
            return null;
        }
        String[] parts = topic.split("/");
        // Global: a2a/v1/discovery/{org}/{unit}/{agent} = 6 parts
        // Namespaced: {ns}/a2a/v1/discovery/{org}/{unit}/{agent} = 7 parts
        if (parts.length == 6
            && TOPIC_NAMESPACE.equals(parts[0])
            && TOPIC_VERSION.equals(parts[1])
            && TOPIC_DISCOVERY.equals(parts[2])) {
            return AgentIdentity.builder()
                .namespace(GLOBAL_NAMESPACE)
                .orgId(parts[3])
                .unitId(parts[4])
                .agentId(parts[5])
                .build();
        }
        if (parts.length == 7
            && TOPIC_NAMESPACE.equals(parts[1])
            && TOPIC_VERSION.equals(parts[2])
            && TOPIC_DISCOVERY.equals(parts[3])) {
            return AgentIdentity.builder()
                .namespace(parts[0])
                .orgId(parts[4])
                .unitId(parts[5])
                .agentId(parts[6])
                .build();
        }
        return null;
    }

    /**
     * Returns the composite client ID: orgId/unitId/agentId (matching EMQX's agent_card_clientid).
     */
    public String clientId() {
        return String.join("/", orgId, unitId, agentId);
    }

    /**
     * Validates that all ID segments match the allowed pattern: ^[A-Za-z0-9._-]+$
     */
    public boolean isValid() {
        return isValidId(orgId) && isValidId(unitId) && isValidId(agentId);
    }

    private static boolean isValidId(String id) {
        return id != null && id.matches(A2AProtocolConstants.SEGMENT_ID_PATTERN);
    }

    /**
     * Checks if this identity matches a wildcard filter.
     * Wildcard is represented by "+" (matching EMQX's MQTT wildcard convention).
     */
    public boolean matchesFilter(String filterOrgId, String filterUnitId, String filterAgentId) {
        return (filterOrgId == null || "+".equals(filterOrgId) || filterOrgId.equals(orgId))
            && (filterUnitId == null || "+".equals(filterUnitId) || filterUnitId.equals(unitId))
            && (filterAgentId == null || "+".equals(filterAgentId) || filterAgentId.equals(agentId));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AgentIdentity that = (AgentIdentity) o;
        return Objects.equals(orgId, that.orgId)
            && Objects.equals(unitId, that.unitId)
            && Objects.equals(agentId, that.agentId)
            && Objects.equals(namespace, that.namespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, unitId, agentId, namespace);
    }
}
