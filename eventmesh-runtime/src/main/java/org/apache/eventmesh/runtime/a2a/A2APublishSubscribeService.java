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

package org.apache.eventmesh.runtime.a2a;

import org.apache.eventmesh.protocol.a2a.A2AProtocolConstants;
import org.apache.eventmesh.protocol.a2a.AgentCardValidator;
import org.apache.eventmesh.protocol.a2a.AgentIdentity;
import org.apache.eventmesh.protocol.a2a.model.AgentCard;
import org.apache.eventmesh.runtime.boot.EventMeshServer;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A2APublishSubscribeService: Manages A2A Agent Card Registry and processes A2A events.
 *
 * <p>Features:
 * - Agent Card CRUD (register, delete, get, list)
 * - Agent status tracking (online/offline)
 * - Event processing with status metadata augmentation
 * - Hierarchical identity (org_id/unit_id/agent_id) with wildcard queries
 */
@Slf4j
public class A2APublishSubscribeService {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final EventMeshServer eventMeshServer;
    private volatile boolean isStarted = false;

    private final ConcurrentHashMap<AgentIdentity, RegisteredCard> cardRegistry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentStatus> agentStatusMap = new ConcurrentHashMap<>();

    private AgentCardValidator cardValidator;

    public A2APublishSubscribeService(EventMeshServer eventMeshServer) {
        this.eventMeshServer = eventMeshServer;
    }

    public void init() throws Exception {
        this.cardValidator = new AgentCardValidator(true);
        log.info("A2APublishSubscribeService initialized with Agent Card Registry.");
    }

    public void start() throws Exception {
        isStarted = true;
        log.info("A2APublishSubscribeService started.");
    }

    public void shutdown() throws Exception {
        isStarted = false;
        cardRegistry.clear();
        agentStatusMap.clear();
        log.info("A2APublishSubscribeService shutdown.");
    }

    // =========================================================================
    // Agent Card Registry Operations
    // =========================================================================

    /**
     * Registers an Agent Card. Validates the card and identity before storing.
     *
     * @param identity the agent identity (org_id/unit_id/agent_id)
     * @param card     the agent card
     * @return RegistrationResult with success/failure
     */
    public RegistrationResult registerCard(AgentIdentity identity, AgentCard card) {
        if (!isStarted) {
            throw new IllegalStateException("A2APublishSubscribeService is not started");
        }

        // Validate identity
        if (!identity.isValid()) {
            String msg = String.format("Invalid agent identity: orgId=%s, unitId=%s, agentId=%s",
                identity.getOrgId(), identity.getUnitId(), identity.getAgentId());
            log.warn(msg);
            return RegistrationResult.failure(msg);
        }

        // Validate card
        try {
            String cardJson = objectMapper.writeValueAsString(card);
            AgentCardValidator.ValidationResult result = cardValidator.validate(cardJson);
            if (!result.isValid()) {
                log.warn("Agent card schema validation failed for {}: {}", identity.clientId(), result.getErrorMessage());
                return RegistrationResult.failure(result.getErrorMessage());
            }
        } catch (Exception e) {
            log.warn("Failed to serialize/validate agent card for {}: {}", identity.clientId(), e.getMessage());
            return RegistrationResult.failure("Card validation error: " + e.getMessage());
        }

        RegisteredCard existing = cardRegistry.put(identity, new RegisteredCard(card, System.currentTimeMillis()));
        if (existing != null) {
            log.info("Updated agent card for {}", identity.clientId());
        } else {
            log.info("Registered new agent card for {}", identity.clientId());
        }

        // Set agent online
        agentStatusMap.put(identity.clientId(), AgentStatus.ONLINE);

        return RegistrationResult.success();
    }

    /**
     * Deletes an Agent Card from the registry.
     */
    public boolean deleteCard(AgentIdentity identity) {
        if (!isStarted) {
            throw new IllegalStateException("A2APublishSubscribeService is not started");
        }
        RegisteredCard removed = cardRegistry.remove(identity);
        if (removed != null) {
            agentStatusMap.remove(identity.clientId());
            log.info("Deleted agent card for {}", identity.clientId());
            return true;
        }
        return false;
    }

    /**
     * Gets a specific Agent Card.
     */
    public CardEntry getCard(AgentIdentity identity) {
        if (!isStarted) {
            throw new IllegalStateException("A2APublishSubscribeService is not started");
        }
        RegisteredCard rc = cardRegistry.get(identity);
        if (rc == null) {
            return null;
        }
        return new CardEntry(identity, rc.card, lookupAgentStatus(identity));
    }

    /**
     * Lists Agent Cards matching the given filters. Use null or "+" for wildcard matching.
     */
    public List<CardEntry> listCards(String orgId, String unitId, String agentId) {
        if (!isStarted) {
            throw new IllegalStateException("A2APublishSubscribeService is not started");
        }
        List<CardEntry> results = new ArrayList<>();
        for (Map.Entry<AgentIdentity, RegisteredCard> entry : cardRegistry.entrySet()) {
            AgentIdentity id = entry.getKey();
            if (id.matchesFilter(orgId, unitId, agentId)) {
                results.add(new CardEntry(id, entry.getValue().card, lookupAgentStatus(id)));
            }
        }
        return results;
    }

    /**
     * Lists all registered Agent Cards.
     */
    public List<CardEntry> listAllCards() {
        return listCards(null, null, null);
    }

    // =========================================================================
    // Agent Status
    // =========================================================================

    /**
     * Looks up the status of an agent (online/offline).
     */
    public String lookupAgentStatus(AgentIdentity identity) {
        AgentStatus status = agentStatusMap.get(identity.clientId());
        return status != null ? status.value : A2AProtocolConstants.STATUS_OFFLINE;
    }

    /**
     * Sets the status of an agent.
     */
    public void setAgentStatus(AgentIdentity identity, String status) {
        if (A2AProtocolConstants.STATUS_ONLINE.equals(status)) {
            agentStatusMap.put(identity.clientId(), AgentStatus.ONLINE);
        } else {
            agentStatusMap.put(identity.clientId(), AgentStatus.OFFLINE);
        }
    }

    // =========================================================================
    // Event Processing
    // =========================================================================

    /**
     * Processes an A2A CloudEvent. Augments events with agent status metadata.
     *
     * @param event The CloudEvent to process.
     * @return The processed (potentially modified) CloudEvent.
     */
    public CloudEvent process(CloudEvent event) {
        if (!isStarted) {
            throw new IllegalStateException("A2APublishSubscribeService is not started");
        }

        log.debug("Processing A2A event: {}", event.getId());

        // Check if this is an A2A discovery topic event
        String subject = event.getSubject();
        if (subject != null && subject.startsWith(A2AProtocolConstants.TOPIC_NAMESPACE + "/")) {
            AgentIdentity identity = AgentIdentity.fromDiscoveryTopic(subject);
            if (identity != null) {
                return augmentWithStatusMetadata(event, identity);
            }
        }

        return event;
    }

    /**
     * Augments a CloudEvent with agent status metadata (a2astatus, a2astatussource extensions).
     * Matches EMQX's on_message_delivered hook behavior.
     */
    private CloudEvent augmentWithStatusMetadata(CloudEvent event, AgentIdentity identity) {
        String status = lookupAgentStatus(identity);
        CloudEventBuilder builder = CloudEventBuilder.from(event);
        builder.withExtension(A2AProtocolConstants.CE_EXTENSION_A2A_STATUS, status);
        builder.withExtension(A2AProtocolConstants.CE_EXTENSION_A2A_STATUS_SOURCE, "eventmesh");
        return builder.build();
    }

    // =========================================================================
    // Inner Types
    // =========================================================================

    private enum AgentStatus {
        ONLINE(A2AProtocolConstants.STATUS_ONLINE),
        OFFLINE(A2AProtocolConstants.STATUS_OFFLINE);

        final String value;

        AgentStatus(String value) {
            this.value = value;
        }
    }

    private static class RegisteredCard {

        final AgentCard card;
        final long registeredAt;

        RegisteredCard(AgentCard card, long registeredAt) {
            this.card = card;
            this.registeredAt = registeredAt;
        }
    }

    /**
     * Represents a registered agent card entry with identity, card data, and status.
     */
    public static class CardEntry {

        private final AgentIdentity identity;
        private final AgentCard card;
        private final String status;

        public CardEntry(AgentIdentity identity, AgentCard card, String status) {
            this.identity = identity;
            this.card = card;
            this.status = status;
        }

        public AgentIdentity getIdentity() {
            return identity;
        }

        public AgentCard getCard() {
            return card;
        }

        public String getStatus() {
            return status;
        }

        public String getNamespace() {
            return identity.getNamespace();
        }

        public String getId() {
            return identity.clientId();
        }

        public String getName() {
            return card.getName();
        }

        public String getVersion() {
            return card.getVersion();
        }

        public String getDescription() {
            return card.getDescription();
        }
    }

    /**
     * Result of a card registration attempt.
     */
    public static class RegistrationResult {

        private final boolean success;
        private final String errorMessage;

        private RegistrationResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static RegistrationResult success() {
            return new RegistrationResult(true, null);
        }

        public static RegistrationResult failure(String errorMessage) {
            return new RegistrationResult(false, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
