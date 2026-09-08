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

import org.apache.eventmesh.protocol.a2a.AgentIdentity;
import org.apache.eventmesh.protocol.a2a.model.AgentCard;
import org.apache.eventmesh.runtime.cluster.MetaListener;
import org.apache.eventmesh.runtime.cluster.MetaStore;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Cluster-shared {@link AgentCardRegistry} backed by {@link MetaStore} (issue #5302
 * Sub-PR D2). Survives runtime restarts and is shared across all Runtime instances.
 *
 * <p>One record per {@code agentId} at key {@code /em/agent-cards/<agentId>} with value
 * the JSON-serialized {@link AgentCard} (the model is already Jackson-friendly via
 * {@code @Data @Builder}). Reads are served from a per-process cache that is kept
 * fresh by a Meta watch on the {@code /em/agent-cards/} prefix; this is the same
 * pattern used by {@code ClusterSubscriptionStore}.</p>
 *
 * <p>Wire format: UTF-8 JSON. The agentId is the cache key; the AgentIdentity is not
 * stored separately because the same agentId is the unit of addressability in the A2A
 * gateway (the discovery topic is just {@code a2a/v1/discovery/{org}/{unit}/{agent}}
 * &mdash; the org and unit are part of the card's metadata).</p>
 *
 * <p>The default Jackson {@link ObjectMapper} is used; AgentCard has no special
 * polymorphic types so the default configuration is sufficient.</p>
 */
@Slf4j
public class MetaAgentCardRegistry implements AgentCardRegistry {

    /** Meta key prefix for agent cards. Cluster-shared, namespace-stable. */
    public static final String PREFIX = "/em/agent-cards/";

    private final MetaStore meta;
    private final ObjectMapper json = new ObjectMapper();

    /** agentId -> AgentCard (rebuilt from Meta on watch). */
    private final ConcurrentHashMap<String, AgentCard> cache = new ConcurrentHashMap<>();

    public MetaAgentCardRegistry(MetaStore meta) {
        this.meta = meta;
        // Seed the cache with the current Meta state, then keep it fresh via watch.
        for (java.util.Map.Entry<String, String> e : meta.getWithPrefix(PREFIX).entrySet()) {
            applyChange(e.getKey(), e.getValue(), false);
        }
        meta.watch(PREFIX, this::applyChange);
    }

    @Override
    public void registerCard(AgentIdentity id, AgentCard card) {
        if (id == null || id.getAgentId() == null || card == null) {
            throw new IllegalArgumentException("agentId and card must be non-null");
        }
        String value;
        try {
            value = json.writeValueAsString(card);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize AgentCard for agentId=" + id.getAgentId(), e);
        }
        String key = key(id.getAgentId());
        meta.put(key, value);
        // Update the local cache immediately &mdash; the MetaStore watch is for
        // cross-instance propagation and may not fire (Nacos ConfigService is
        // per-dataId, not prefix-scan), so don't rely on it to reflect this
        // instance's own registration back.
        applyChange(key, value, false);
    }

    @Override
    public boolean removeCard(AgentIdentity id) {
        if (id == null || id.getAgentId() == null) {
            return false;
        }
        String key = key(id.getAgentId());
        boolean removed = meta.delete(key);
        applyChange(key, null, true);
        return removed;
    }

    @Override
    public boolean isAgentRegistered(String agentName) {
        return agentName != null && cache.containsKey(agentName);
    }

    @Override
    public AgentCard getCard(String agentName) {
        return agentName == null ? null : cache.get(agentName);
    }

    private void applyChange(String key, String value, boolean deleted) {
        String agentId = key.substring(PREFIX.length());
        if (deleted) {
            cache.remove(agentId);
            return;
        }
        try {
            AgentCard card = json.readValue(value, AgentCard.class);
            cache.put(agentId, card);
        } catch (Exception e) {
            log.warn("ignoring malformed agent card {}: {}", key, e.getMessage());
        }
    }

    private static String key(String agentId) {
        return PREFIX + agentId;
    }
}
