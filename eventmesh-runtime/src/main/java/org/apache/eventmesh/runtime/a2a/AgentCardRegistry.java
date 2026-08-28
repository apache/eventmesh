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

/**
 * Agent-card registry. Holds the discovery table that A2A clients consult before submitting
 * a task (so an unregistered agent cannot receive a message).
 *
 * <p><b>Issue #5302 D1 scope:</b> only the in-memory implementation
 * ({@link InMemoryAgentCardRegistry}) is provided. A Meta-backed implementation is the
 * subject of Sub-PR D2 &mdash; it will reuse {@code org.apache.eventmesh.runtime.state.SessionStore}
 * (Sub-PR A) for cluster-shared agent registration with prefix-watch invalidation.</p>
 */
public interface AgentCardRegistry {

    /**
     * Registers a card under the given identity. If a card is already registered for the
     * same identity, the existing entry is replaced.
     */
    void registerCard(AgentIdentity id, AgentCard card);

    /**
     * Removes a card by identity. Returns {@code true} if a card was removed.
     */
    boolean removeCard(AgentIdentity id);

    /**
     * @return {@code true} if a card is registered for the given agent name (any identity
     *         tuple whose {@code agentId} field matches).
     */
    boolean isAgentRegistered(String agentName);

    /**
     * Looks up a card by agent name.
     */
    AgentCard getCard(String agentName);
}
