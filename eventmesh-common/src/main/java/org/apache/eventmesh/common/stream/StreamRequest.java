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

package org.apache.eventmesh.common.stream;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A streaming-call request (runtime → agent over the agent's lite channel). The agent consumes this,
 * calls the LLM, and publishes {@link StreamChunk}s back to {@link #replyTo}.
 *
 * <p>v2: {@link #sessionId} is {@code <agentId>:<uuid>} — the routing key (runtime parses the
 * {@code :} prefix for zero-lookup routing) AND the agent's conversation-context key. {@link #replyTo}
 * is the reply address ({@code parent#lite}). {@link #conversationId} is v1 legacy (v2 keys multi-turn
 * by sessionId) — kept temporarily for the v1 path.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamRequest {

    /** Session id ({@code <agentId>:<uuid>} in v2); routing + conversation key. */
    private String sessionId;
    /** Reply address the agent publishes response chunks to (v2: {@code parent#lite}; v1 legacy: lite name). */
    private String replyTo;
    /** The user prompt. */
    private String prompt;
    /** The LLM model id (optional; agent default applies if null). */
    private String model;
    /**
     * Conversation id for multi-turn chat (v1 legacy, optional). v2 keys multi-turn by sessionId;
     * this field is retained only until the v1 path is removed. Null/absent = single-turn.
     */
    private String conversationId;
}
