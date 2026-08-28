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

import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * In-memory {@link AgentCardRegistry}. Suitable for tests and single-process demos; not
 * cluster-safe (a fresh JVM starts with an empty registry).
 *
 * <p>Sub-PR D2 will add a Meta-backed implementation that survives restarts and is shared
 * across Runtime instances.</p>
 */
@Slf4j
public class InMemoryAgentCardRegistry implements AgentCardRegistry {

    private final ConcurrentHashMap<String, AgentCard> cardsByAgentId = new ConcurrentHashMap<>();

    @Override
    public void registerCard(AgentIdentity id, AgentCard card) {
        cardsByAgentId.put(id.getAgentId(), card);
        log.info("Registered agent card: agentId={}", id.getAgentId());
    }

    @Override
    public boolean removeCard(AgentIdentity id) {
        return cardsByAgentId.remove(id.getAgentId()) != null;
    }

    @Override
    public boolean isAgentRegistered(String agentName) {
        return cardsByAgentId.containsKey(agentName);
    }

    @Override
    public AgentCard getCard(String agentName) {
        return cardsByAgentId.get(agentName);
    }
}
