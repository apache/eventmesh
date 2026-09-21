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

package org.apache.eventmesh.agent.llm;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Minimal OpenAI-compatible chat-completion client — the default {@link LlmClient} implementation.
 * Issues {@code POST {base}/v1/chat/completions}; with {@code stream:true} it reads the SSE token
 * stream and invokes {@code chunkCb} per {@code choices[0].delta.content}; via {@link #chat} it
 * performs a single-shot completion that may return a function call. Compatible with OpenAI,
 * Azure-OpenAI, vLLM, Ollama (OpenAI mode), DeepSeek, Moonshot and most internal gateways.
 * Blocking; intended to run on a virtual thread.
 */
@Slf4j
public class OpenAiLlmClient implements LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String apiKey;
    private final String defaultModel;
    private final java.net.http.HttpClient http;

    public OpenAiLlmClient(String baseUrl, String apiKey, String defaultModel) {
        this.baseUrl = baseUrl == null || baseUrl.trim().isEmpty() ? "https://api.openai.com" : baseUrl;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.defaultModel = defaultModel;
        this.http = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /**
     * Stream the completion for {@code prompt}; {@code chunkCb} receives each token fragment in
     * order. Blocks until the server closes the stream ([DONE] or end-of-body).
     *
     * @param model overrides the default model when non-null
     */
    public void stream(String prompt, String model, Consumer<String> chunkCb) throws Exception {
        java.util.List<java.util.Map<String, String>> msgs = new java.util.ArrayList<>();
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("role", "user");
        m.put("content", prompt == null ? "" : prompt);
        msgs.add(m);
        stream(msgs, model, chunkCb);
    }

    /**
     * Stream a completion for the given message list (supports multi-turn: pass prior history +
     * the current user message). Each map must contain {@code role} + {@code content}.
     */
    @Override
    public void stream(java.util.List<java.util.Map<String, String>> messages, String model,
                       Consumer<String> chunkCb) throws Exception {
        ObjectNode body = baseBody(messages, model);
        body.put("stream", true);
        java.net.http.HttpRequest req = request(body);
        java.net.http.HttpResponse<Stream<String>> resp =
            http.send(req, java.net.http.HttpResponse.BodyHandlers.ofLines());
        int status = resp.statusCode();
        if (status != 200) {
            throw new RuntimeException("LLM HTTP " + status + " (check llm.base.url / llm.api.key / llm.model)");
        }
        try (Stream<String> lines = resp.body()) {
            lines.filter(l -> l.startsWith("data:")).forEach(line -> {
                String payload = line.substring(5).trim();
                if (payload.isEmpty() || "[DONE]".equals(payload)) {
                    return;
                }
                try {
                    JsonNode node = MAPPER.readTree(payload);
                    JsonNode choices = node.get("choices");
                    if (choices != null && choices.isArray() && choices.size() > 0) {
                        JsonNode delta = choices.get(0).get("delta");
                        if (delta != null && delta.has("content")) {
                            String content = delta.get("content").asText("");
                            if (!content.isEmpty()) {
                                chunkCb.accept(content);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("skip unparseable SSE line: {}", payload);
                }
            });
        }
    }

    /**
     * Single-shot completion with optional function-calling tools (non-streaming): parses
     * {@code choices[0].message} into either assistant text or the first requested tool call.
     */
    @Override
    public LlmCompletion chat(List<Map<String, String>> messages, List<ToolSpec> tools,
                                     String model) throws Exception {
        ObjectNode body = baseBody(messages, model);
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArr = body.putArray("tools");
            for (ToolSpec spec : tools) {
                ObjectNode fn = toolsArr.addObject().put("type", "function").putObject("function");
                fn.put("name", spec.name());
                fn.put("description", spec.description());
                fn.set("parameters", MAPPER.readTree(
                    spec.parametersJsonSchema() == null ? "{\"type\":\"object\"}" : spec.parametersJsonSchema()));
            }
        }
        java.net.http.HttpResponse<String> resp =
            http.send(request(body), java.net.http.HttpResponse.BodyHandlers.ofString());
        int status = resp.statusCode();
        if (status != 200) {
            throw new RuntimeException("LLM HTTP " + status + " (check llm.base.url / llm.api.key / llm.model)");
        }
        JsonNode message = MAPPER.readTree(resp.body()).path("choices").path(0).path("message");
        JsonNode toolCalls = message.get("tool_calls");
        if (toolCalls != null && toolCalls.isArray() && toolCalls.size() > 0) {
            JsonNode call = toolCalls.get(0).path("function");
            return LlmCompletion.ofToolCall(new ToolCall(
                toolCalls.get(0).path("id").asText(""),
                call.path("name").asText(""),
                call.path("arguments").asText("{}")));
        }
        return LlmCompletion.ofText(message.path("content").asText(""));
    }

    private ObjectNode baseBody(List<Map<String, String>> messages, String model) {
        String useModel = (model != null && !model.isEmpty()) ? model : defaultModel;
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", useModel);
        ArrayNode msgs = body.putArray("messages");
        for (Map<String, String> message : messages) {
            ObjectNode msg = msgs.addObject();
            msg.put("role", message.getOrDefault("role", "user"));
            msg.put("content", message.getOrDefault("content", ""));
        }
        return body;
    }

    private java.net.http.HttpRequest request(ObjectNode body) throws Exception {
        return java.net.http.HttpRequest.newBuilder()
            .uri(URI.create(trimSlash(this.baseUrl) + "/v1/chat/completions"))
            .timeout(Duration.ofMinutes(3))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
            .build();
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
