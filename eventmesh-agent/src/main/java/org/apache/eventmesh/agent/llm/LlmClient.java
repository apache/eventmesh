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

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * SPI for chat-completion backends. {@link OpenAiLlmClient} (OpenAI-compatible SSE) is the default
 * implementation; implement this interface and hand the instance to {@code StreamingAgent} to plug
 * in any non-OpenAI-protocol LLM (Anthropic native, Bedrock, an internal inference service, ...).
 *
 * <p>Two operations: {@link #stream} for token streaming (no tools), {@link #chat} for a
 * single-shot completion that may request a function call.</p>
 */
public interface LlmClient {

    /**
     * Stream the completion for the given message list (each map holds {@code role} + {@code
     * content}); {@code chunkCb} receives token fragments in order. Blocks until the stream ends.
     *
     * @param model overrides the client default when non-null/non-empty
     */
    void stream(List<Map<String, String>> messages, String model, Consumer<String> chunkCb) throws Exception;

    /**
     * Single-shot completion with optional function-calling tools. Returns either assistant text
     * ({@link LlmCompletion#text()}) or one tool call the model wants performed first
     * ({@link LlmCompletion#toolCall()}); the caller executes the tool and loops.
     *
     * @param tools may be empty (plain completion, no tool advertisement)
     */
    LlmCompletion chat(List<Map<String, String>> messages, List<ToolSpec> tools, String model) throws Exception;
}
