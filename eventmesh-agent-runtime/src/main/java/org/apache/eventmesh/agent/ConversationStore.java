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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory multi-turn conversation history keyed by {@code conversationId}. Each completed turn
 * appends a {@code {user, assistant}} message pair; history is trimmed to the most recent
 * {@code maxMessages} entries (sliding window) to bound LLM context size, and bounds the
 * number of conversations themselves ({@code maxConversations}, access-order eviction) —
 * without the outer bound a long-lived agent leaks memory one session at a time (#5405).
 *
 * <p>Lost on agent restart (persistence to Redis/DB is a TODO — implement {@link ConversationMemory}
 * and inject it into {@code StreamingAgent} to add one). Thread-safe per conversation
 * (synchronized on the per-id list).</p>
 */
public class ConversationStore implements ConversationMemory {

    private final int maxMessages;
    private final ConcurrentHashMap<String, List<Map<String, String>>> history = new ConcurrentHashMap<>();
    /** Access-order LRU over the live conversations; guards the outer bound. */
    private final LinkedHashMap<String, Void> lru = new LinkedHashMap<>(16, 0.75f, true);

    public ConversationStore(int maxMessages) {
        this(maxMessages, Integer.MAX_VALUE);
    }

    public ConversationStore(int maxMessages, int maxConversations) {
        // keep at least one full turn (user + assistant)
        this.maxMessages = Math.max(2, maxMessages);
        this.maxConversations = Math.max(1, maxConversations);
    }

    private final int maxConversations;

    /** Snapshot of the conversation history (empty list if convId null/unknown). Caller may mutate. */
    @Override
    public List<Map<String, String>> get(String conversationId) {
        if (conversationId == null) {
            return new ArrayList<>();
        }
        touch(conversationId, false);
        List<Map<String, String>> msgs = history.get(conversationId);
        return msgs == null ? new ArrayList<>() : new ArrayList<>(msgs);
    }

    /** Number of live conversations (visible for tests/metrics). */
    public int conversationCount() {
        return history.size();
    }

    /**
     * Mark {@code id} most-recently-used and admit it ({@code admit=true}) or only refresh it when
     * already live ({@code admit=false} — a get() of a dead id must not resurrect the key and
     * push a live conversation out). Evicts the least-recently-used conversation past the bound.
     */
    private synchronized void touch(String id, boolean admit) {
        if (admit) {
            lru.put(id, null);
        } else if (!lru.containsKey(id)) {
            return;
        } else {
            lru.get(id); // access-order side effect: refresh recency
        }
        while (lru.size() > maxConversations) {
            java.util.Iterator<String> it = lru.keySet().iterator();
            if (!it.hasNext()) {
                break;
            }
            String eldest = it.next();
            it.remove();
            history.remove(eldest);
        }
    }

    /** Append a completed turn (user prompt + assistant answer). No-op if convId is null. */
    @Override
    public void appendTurn(String conversationId, String userPrompt, String assistantAnswer) {
        if (conversationId == null) {
            return;
        }
        touch(conversationId, true);
        List<Map<String, String>> msgs = history.computeIfAbsent(conversationId,
            k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (msgs) {
            msgs.add(message("user", userPrompt));
            msgs.add(message("assistant", assistantAnswer));
            while (msgs.size() > maxMessages) {
                msgs.remove(0); // drop oldest → sliding window
            }
        }
    }

    private static Map<String, String> message(String role, String content) {
        Map<String, String> m = new HashMap<>();
        m.put("role", role);
        m.put("content", content == null ? "" : content);
        return m;
    }
}
