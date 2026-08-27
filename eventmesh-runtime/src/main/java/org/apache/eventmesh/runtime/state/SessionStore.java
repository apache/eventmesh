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

package org.apache.eventmesh.runtime.state;

import java.util.List;

/**
 * Cluster-shared session control-plane (issue #5301 §SessionStore).
 *
 * <p>Wraps the streaming-session design from {@code org.apache.eventmesh.runtime.session}. It
 * holds three independent collections on the underlying {@code MetaStore}:</p>
 *
 * <ul>
 *   <li><b>agent registry</b> — {@code /em/agents/<agentId>} (capabilities, capacity, parent, load, status, heartbeat)</li>
 *   <li><b>client bindings</b> — {@code /em/bindings/<clientId>} (client→agent affinity)</li>
 *   <li><b>session metadata</b> — {@code /em/sessions/<sessionId>}</li>
 * </ul>
 *
 * <p>The default implementation is {@code SessionRegistry} (Meta-backed with local cache). This
 * interface extracts the contract so future implementations (in-process test fake, an alternate
 * store) can be swapped without changing the matchmaker, router, or admin callers.</p>
 *
 * <p>Liveness is enforced by a heartbeat TTL window: callers that have not refreshed their
 * heartbeat within the window are excluded from {@link #readyAgents()}.</p>
 *
 * <p>The record types {@code AgentRecord}, {@code AgentBinding}, {@code SessionMeta} live in
 * {@code org.apache.eventmesh.runtime.session} (the implementation package) and are referenced
 * by the interface so that {@code SessionRegistry} can {@code implement} it directly without an
 * adapter. Test fakes in other packages can supply their own record types if needed.</p>
 */
public interface SessionStore {

    /**
     * Register a new agent in {@code PENDING} state (will be flipped to {@code READY} after
     * the agent subscribes to its channel).
     */
    void registerAgent(String agentId, String parent, java.util.List<String> capabilities, int capacity);

    /**
     * Flip a {@code PENDING} agent to {@code READY} (after it has subscribed to its channel).
     *
     * @return false if the agent is not registered
     */
    boolean markAgentReady(String agentId);

    /**
     * Refresh heartbeat and reported load. {@code false} if the agent isn't registered.
     */
    boolean heartbeat(String agentId, int activeSessions);

    /**
     * Remove an agent and every client binding pointing at it (so a dying agent doesn't leave
     * orphaned client bindings).
     */
    void unregisterAgent(String agentId);

    /**
     * @return the agent record, or {@code null} if not registered
     */
    org.apache.eventmesh.runtime.session.AgentRecord agent(String agentId);

    /**
     * All agents eligible for matchmaking: {@code READY} status, fresh heartbeat, not at capacity.
     */
    List<org.apache.eventmesh.runtime.session.AgentRecord> readyAgents();

    /**
     * Bind a client to an agent (affinity).
     */
    void bind(String clientId, String agentId);

    /**
     * @return the binding record, or {@code null} if no binding
     */
    org.apache.eventmesh.runtime.session.SessionRegistry.AgentBinding binding(String clientId);

    /**
     * @return true if a binding was removed
     */
    boolean unbind(String clientId);

    /**
     * Record a mode-1 streaming-call session.
     */
    void putSession(String sessionId, String clientId, String agentId);

    /**
     * @return the session record, or {@code null} if no session
     */
    org.apache.eventmesh.runtime.session.SessionRegistry.SessionMeta session(String sessionId);

    /**
     * Refresh a session's last-active timestamp (called on each {@code STREAM_REQ}). No-op if
     * the session is gone.
     */
    void touchSession(String sessionId);

    /**
     * Remove every session idle for longer than {@code sessionTtlMs}. Returns the expired
     * sessionIds so the caller can tear down data-path state.
     */
    List<String> expireStaleSessions(long sessionTtlMs);

    /**
     * @return true if the session existed and was removed
     */
    boolean removeSession(String sessionId);
}
