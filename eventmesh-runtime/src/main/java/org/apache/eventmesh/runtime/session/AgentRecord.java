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

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A registered agent's record, stored under {@code /em/agents/<agentId>} in the {@link
 * org.apache.eventmesh.runtime.cluster.MetaStore}. JSON-serialized by {@link SessionRegistry}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRecord {

    /** Agent id (also the prefix of sessionId {@code <agentId>:<uuid>}). */
    private String agentId;
    /** Assigned agent-parent shard ({@code agent-parent-<i>}). */
    private String parent;
    /** Declared capabilities (model ids) used for matchmaking. */
    private List<String> capabilities;
    /** Max concurrent sessions the agent accepts. */
    private int capacity;
    /** Current active sessions (reported via heartbeat). */
    private int load;
    /** {@link AgentStatus} name. */
    private String status;
    /** Last heartbeat epoch millis. */
    private long hb;
}
