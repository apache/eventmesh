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

import org.apache.eventmesh.agent.llm.OpenAiLlmClient;
import org.apache.eventmesh.client.cloudevents.CloudEventsClient;
import org.apache.eventmesh.common.stream.StreamChunk;
import org.apache.eventmesh.common.stream.StreamRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

/**
 * Consumes streaming-call requests from its own {@code agent.<agentId>} lite (mode 1), calls the
 * LLM, and publishes token chunks back to the request's {@code replyTo} (a {@code client.<clientId>}
 * lite). Each request runs on its own virtual thread. Multi-turn history is keyed by
 * {@code sessionId} (sessionId IS the conversation key).
 *
 * <p>Mode 2 (publish/subscribe) has no agent involvement — a gateway publishes chunks directly to a
 * per-session lite topic; consumers subscribe via the runtime. This class is mode-1-only.</p>
 */
@Slf4j
public class StreamingAgent {

    private final CloudEventsClient client;
    private final String agentParent;
    private final String agentId;
    private final OpenAiLlmClient llm;
    private final ConversationStore store;
    /** One virtual thread per in-flight stream. */
    private final ExecutorService streamExecutor = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("em-agent-stream-", 1).factory());
    private final AtomicInteger activeSessions = new AtomicInteger(0);

    public StreamingAgent(CloudEventsClient client, String agentParent, String agentId,
                          OpenAiLlmClient llm, ConversationStore store) {
        this.client = client;
        this.agentParent = agentParent;
        this.agentId = agentId;
        this.llm = llm;
        this.store = store;
    }

    /** Subscribe to this agent's channel; runs until {@link #shutdown()}. */
    public void start() {
        // Internal wire: the runtime publishes compact EventMeshFrame bytes (not CloudEvents) onto
        // agent.<agentId>; decode each frame to a StreamRequest.
        client.subscribeLiteBytes(agentParent, "agent." + agentId,
            frame -> streamExecutor.submit(() -> handleRequest(frame)));
        log.info("StreamingAgent subscribed (private-wire): parent={} lite=agent.{}", agentParent, agentId);
    }

    /** In-flight stream count, reported to the runtime via heartbeat. */
    public int activeSessions() {
        return activeSessions.get();
    }

    private void handleRequest(byte[] frame) {
        StreamRequest req = org.apache.eventmesh.common.wire.WireCodecs.get().decodeRequest(frame);
        String sessionId = req.getSessionId();
        String replyTo = req.getReplyTo();
        int[] seq = {0};
        activeSessions.incrementAndGet();
        log.info("stream request: sessionId={} replyTo={} model={}", sessionId, replyTo, req.getModel());
        // Multi-turn: prepend conversation history (empty for a new sessionId), then this turn's prompt.
        List<Map<String, String>> messages = store.get(sessionId);
        messages.add(message("user", req.getPrompt()));
        StringBuilder answer = new StringBuilder();
        try {
            llm.stream(messages, req.getModel(), token -> {
                answer.append(token);
                publish(replyTo, sessionId, seq, token, false, null);
            });
            publish(replyTo, sessionId, seq, "", true, null);
            log.info("stream completed: sessionId={} chunks={}", sessionId, seq[0] - 1);
            store.appendTurn(sessionId, req.getPrompt(), answer.toString());
        } catch (Exception e) {
            log.warn("stream failed: sessionId={} err={}", sessionId, e.toString());
            publish(replyTo, sessionId, seq, "", true, "llm error: " + e.getMessage());
        } finally {
            activeSessions.decrementAndGet();
        }
    }

    private void publish(String replyTo, String sessionId, int[] seq, String chunk, boolean done, String error) {
        StreamChunk c = StreamChunk.builder()
            .sessionId(sessionId).seq(seq[0]++).chunk(chunk).done(done).error(error).build();
        // replyTo = "parent#lite" (client-parent#client.<clientId>); fall back to agentParent.
        int hash = replyTo == null ? -1 : replyTo.indexOf('#');
        String parent = hash >= 0 ? replyTo.substring(0, hash) : agentParent;
        String lite = hash >= 0 ? replyTo.substring(hash + 1) : replyTo;
        try {
            // Encode the chunk via the WireCodec SPI and publish raw bytes over the internal wire.
            byte[] frame = org.apache.eventmesh.common.wire.WireCodecs.get().encode(c);
            if (!client.publishLiteBytes(parent, lite, frame)) {
                log.warn("publishLiteBytes non-202: sessionId={} seq={} (runtime not lite-capable?)",
                    sessionId, c.getSeq());
            }
        } catch (Exception e) {
            log.warn("publishLiteBytes failed: sessionId={} seq={} err={}", sessionId, c.getSeq(), e.toString());
        }
    }

    private static Map<String, String> message(String role, String content) {
        Map<String, String> m = new HashMap<>();
        m.put("role", role);
        m.put("content", content == null ? "" : content);
        return m;
    }

    public void shutdown() {
        streamExecutor.shutdownNow();
    }
}
