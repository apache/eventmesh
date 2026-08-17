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

package org.apache.eventmesh.runtime.session;

/**
 * Mode 1 channel strategy (§4.4): requests are multiplexed onto the agent's channel
 * {@code agent.<agentId>} (under {@code agent-parent}); replies land on a per-client lite
 * {@code client.<clientId>} (under {@code client-parent}). Multiple sessions of one client share the
 * reply lite and are demultiplexed by {@code sessionId}.
 */
public class AgentAnchoredStrategy implements ChannelStrategy {

    private final String clientParent;

    public AgentAnchoredStrategy(String clientParent) {
        this.clientParent = clientParent;
    }

    @Override
    public Address reqAddress(String sessionId, String agentId, String parent) {
        return new Address(parent, "agent." + agentId);
    }

    @Override
    public Address replyAddress(String sessionId, String clientId) {
        return new Address(clientParent, "client." + clientId);
    }
}