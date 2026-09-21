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

package org.apache.eventmesh.agent;

import java.util.List;
import java.util.Map;

/**
 * Conversation history abstraction keyed by {@code conversationId}. {@link ConversationStore} is
 * the in-memory default; implement this interface to persist history (Redis, RocksDB, a database,
 * ...) — hand the instance to {@code StreamingAgent}.
 */
public interface ConversationMemory {

    /** Snapshot of the conversation history (empty list if id null/unknown). Caller may mutate. */
    List<Map<String, String>> get(String conversationId);

    /** Append a completed turn (user prompt + assistant answer). No-op if id is null. */
    void appendTurn(String conversationId, String userPrompt, String assistantAnswer);
}
