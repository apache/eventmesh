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

import org.apache.eventmesh.agent.llm.LlmClient;
import org.apache.eventmesh.agent.llm.LlmCompletion;
import org.apache.eventmesh.agent.llm.ToolCall;
import org.apache.eventmesh.agent.tool.ToolRegistry;
import org.apache.eventmesh.client.cloudevents.CloudEventsClient;
import org.apache.eventmesh.common.stream.StreamChunk;
import org.apache.eventmesh.common.stream.StreamRequest;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Consumes streaming-call requests from its own {@code agent.<agentId>} lite (mode 1), calls the
 * LLM, and publishes token chunks back to the request's {@code replyTo} (a {@code client.<clientId>}
 * lite). Each request runs on its own virtual thread. Multi-turn history is keyed by
 * {@code sessionId} (sessionId IS the conversation key).
 *
 * <p>Extension points (constructor-injected): {@link LlmClient} (any chat backend — the default is
 * the OpenAI-compatible client), {@link ConversationMemory} (history persistence), and an optional
 * {@link ToolRegistry} of {@code AgentTool}s. With tools registered the agent runs a
 * function-calling loop: the model may request tools (e.g. connector-backed write/read tools) and
 * receives their results before producing the final answer; the answer is then published as one
 * chunk (per-token streaming of the tool-loop answer is a follow-up).</p>
 *
 * <p>{@link #onEvent(CloudEvent, String)} is the event-driven trigger path: a subscribed external
 * event is turned into a prompt, answered (with tools if registered), and the answer is published
 * as a CloudEvent onto an output topic — where sink connectors can deliver it to external systems.</p>
 *
 * <p>Mode 2 (publish/subscribe) has no agent involvement — a gateway publishes chunks directly to a
 * per-session lite topic; consumers subscribe via the runtime. This class is mode-1-only.</p>
 */
@Slf4j
public class StreamingAgent {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int MAX_TOOL_ITERATIONS = 5;

    private final CloudEventsClient client;
    private final String agentParent;
    private final String agentId;
    private final LlmClient llm;
    private final ConversationMemory store;
    private final ToolRegistry tools;
    /** One virtual thread per in-flight stream. */
    private final ExecutorService streamExecutor = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("em-agent-stream-", 1).factory());
    private final AtomicInteger activeSessions = new AtomicInteger(0);

    public StreamingAgent(CloudEventsClient client, String agentParent, String agentId,
                          LlmClient llm, ConversationMemory store) {
        this(client, agentParent, agentId, llm, store, new ToolRegistry());
    }

    public StreamingAgent(CloudEventsClient client, String agentParent, String agentId,
                          LlmClient llm, ConversationMemory store, ToolRegistry tools) {
        this.client = client;
        this.agentParent = agentParent;
        this.agentId = agentId;
        this.llm = llm;
        this.store = store;
        this.tools = tools == null ? new ToolRegistry() : tools;
    }

    /** Subscribe to this agent's channel; runs until {@link #shutdown()}. */
    public void start() {
        // Internal wire: the runtime publishes compact EventMeshFrame bytes (not CloudEvents) onto
        // agent.<agentId>; decode each frame to a StreamRequest.
        client.subscribeLiteBytes(agentParent, "agent." + agentId,
            frame -> streamExecutor.submit(() -> handleRequest(frame)));
        log.info("StreamingAgent subscribed (private-wire): parent={} lite=agent.{} tools={}",
            agentParent, agentId, tools.size());
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
        log.info("stream request: sessionId={} replyTo={} model={} tools={}", sessionId, replyTo, req.getModel(),
            tools.size());
        // Multi-turn: prepend conversation history (empty for a new sessionId), then this turn's prompt.
        List<Map<String, String>> messages = store.get(sessionId);
        messages.add(message("user", req.getPrompt()));
        StringBuilder answer = new StringBuilder();
        try {
            if (tools.isEmpty()) {
                llm.stream(messages, req.getModel(), token -> {
                    answer.append(token);
                    publish(replyTo, sessionId, seq, token, false, null);
                });
            } else {
                answer.append(runToolLoop(messages, req.getModel()));
                publish(replyTo, sessionId, seq, answer.toString(), false, null);
            }
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

    /**
     * Event-driven trigger path: render the incoming CloudEvent as a prompt, answer it (with the
     * registered tools, if any), and publish the answer as a CloudEvent onto {@code outputTopic} —
     * the topic sink connectors subscribe to for external delivery. Each event is its own
     * conversation ({@code trigger:<eventId>}) so triggers never bleed into user sessions.
     */
    public void onEvent(CloudEvent event, String outputTopic) {
        streamExecutor.submit(() -> {
            String data = event.getData() == null ? "{}"
                : new String(event.getData().toBytes(), StandardCharsets.UTF_8);
            String prompt = "Incoming event (type=" + event.getType() + "):\n" + data
                + "\n\nProcess this event and decide the follow-up action.";
            String conversationId = "trigger:" + event.getId();
            List<Map<String, String>> messages = store.get(conversationId);
            messages.add(message("user", prompt));
            try {
                String answer = tools.isEmpty() ? aggregate(messages) : runToolLoop(messages, null);
                store.appendTurn(conversationId, prompt, answer);
                CloudEvent out = CloudEventBuilder.v1()
                    .withId(UUID.randomUUID().toString())
                    .withSource(java.net.URI.create("urn:eventmesh:agent:" + agentId))
                    .withType("org.apache.eventmesh.agent.trigger.output")
                    .withDataContentType("application/json")
                    .withData(MAPPER.writeValueAsBytes(Map.of("agentId", agentId, "answer", answer)))
                    .build();
                client.publish(outputTopic, out);
                log.info("trigger processed: eventId={} -> topic={} answerChars={}", event.getId(), outputTopic,
                    answer.length());
            } catch (Exception e) {
                log.warn("trigger failed: eventId={} err={}", event.getId(), e.toString());
            }
        });
    }

    /** Function-calling loop: chat → (tool → feed result back)* → final answer text. */
    private String runToolLoop(List<Map<String, String>> messages, String model) throws Exception {
        for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
            LlmCompletion completion = llm.chat(messages, tools.specs(), model);
            if (completion.text() != null) {
                return completion.text();
            }
            ToolCall call = completion.toolCall();
            String result;
            try {
                result = tools.invoke(call.function(), parseArgs(call.argumentsJson()));
            } catch (Exception e) {
                result = "tool error: " + e;
            }
            // Tool results are fed back as user messages: keeps the LlmClient message shape
            // (role+content maps) provider-neutral.
            messages.add(message("user", "Tool result for `" + call.function() + "`: " + result));
            log.debug("tool loop: iter={} tool={} resultChars={}", i, call.function(), result.length());
        }
        return "(max tool iterations reached without a final answer)";
    }

    private String aggregate(List<Map<String, String>> messages) throws Exception {
        StringBuilder sb = new StringBuilder();
        llm.stream(messages, null, sb::append);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArgs(String argumentsJson) throws Exception {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        return MAPPER.readValue(argumentsJson, Map.class);
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
