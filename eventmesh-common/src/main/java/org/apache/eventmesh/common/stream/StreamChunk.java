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

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One fragment in a streaming-call response (agent → runtime over the reply lite). {@link #seq} is
 * monotonic and strictly ordered within a single-queue lite parent; the terminal chunk carries
 * {@code done=true} ({@code error} non-null on failure).
 *
 * <p>{@link #eventType} and {@link #meta} are optional extensions for AgentScope-style
 * multi-event streaming (thought, tool-call, structured output). The default OpenAI-compatible
 * path only emits {@code eventType=text} (or {@code null} treated as text) with {@code chunk}
 * as the text delta. The agentscope-harness path maps {@code AgentEvent} subtypes to distinct
 * {@code eventType} values and serializes the full event JSON into {@code meta}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamChunk {

    /** Which session this chunk belongs to (matches the request's sessionId). */
    private String sessionId;
    /** Monotonic sequence (0,1,2,...). */
    private int seq;
    /** The token/text fragment. Empty on the terminal done chunk. */
    private String chunk;
    /** True on the terminal chunk (normal end or error). */
    private boolean done;
    /** Non-null only on a terminal error chunk. */
    private String error;
    /**
     * Optional event type for multi-event streaming: {@code null|text|thought|tool|structured}.
     * {@code null} is treated as {@code text}. Only the agentscope-harness path emits non-text
     * types; the default OpenAI-compatible path only sets {@code chunk} (text delta).
     */
    private String eventType;
    /**
     * Optional passthrough metadata for AgentScope events. The agentscope-harness path serializes
     * the full {@code AgentEvent} JSON here (thought content, tool name + arguments, structured
     * result). The default OpenAI-compatible path leaves this null.
     */
    private Map<String, Object> meta;
}
