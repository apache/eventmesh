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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Drives {@link OpenAiLlmClient} against an in-process mock that speaks the OpenAI streaming SSE
 * wire format (data: {choices:[{delta:{content}}]} ... data: [DONE]). Asserts ordered token
 * delivery. Hermetic — no external LLM/key.
 */
class OpenAiLlmClientTest {

    private HttpServer server;
    private OpenAiLlmClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new OpenAiLlmClient(base, "test-key", "test-model");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void streamsOrderedDeltasUntilDone() throws Exception {
        server.createContext("/v1/chat/completions", this::handleOk);

        List<String> chunks = new ArrayList<>();
        client.stream("hello", null, chunks::add);

        assertThat(chunks).containsExactly("Hel", "lo", " ", "world");
    }

    @Test
    void streamsWithMessageListSendsAllMessages() throws Exception {
        List<JsonNode> captured = new ArrayList<>();
        server.createContext("/v1/chat/completions", ex -> {
            captured.add(new ObjectMapper().readTree(ex.getRequestBody().readAllBytes()));
            handleOk(ex);
        });

        // multi-turn messages list (user/assistant/user)
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("user", "hi"));
        messages.add(message("assistant", "hello"));
        messages.add(message("user", "how are you"));
        List<String> chunks = new ArrayList<>();
        client.stream(messages, null, chunks::add);

        // the full messages list was serialized into the request body, in order
        assertThat(captured).hasSize(1);
        JsonNode sent = captured.get(0).get("messages");
        assertThat(sent).hasSize(3);
        assertThat(sent.get(0).get("role").asText()).isEqualTo("user");
        assertThat(sent.get(1).get("role").asText()).isEqualTo("assistant");
        assertThat(sent.get(2).get("content").asText()).isEqualTo("how are you");
        // and chunks still stream back
        assertThat(chunks).containsExactly("Hel", "lo", " ", "world");
    }

    private static Map<String, String> message(String role, String content) {
        Map<String, String> m = new HashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    @Test
    void throwsOnNon200() {
        server.createContext("/v1/chat/completions", this::handle500);

        List<String> chunks = new ArrayList<>();
        try {
            client.stream("hello", null, chunks::add);
        } catch (Exception e) {
            assertThat(e).hasMessageContaining("LLM HTTP 500");
        }
        assertThat(chunks).isEmpty();
    }

    private void handleOk(HttpExchange exchange) throws IOException {
        String body = String.join("\n",
            "data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}",
            "",
            "data: {\"choices\":[{\"delta\":{\"content\":\"lo\"}}]}",
            "",
            "data: {\"choices\":[{\"delta\":{\"content\":\" \"}}]}",
            "",
            "data: {\"choices\":[{\"delta\":{\"content\":\"world\"}}]}",
            "",
            "data: {\"choices\":[{\"delta\":{}}]}",
            "",
            "data: [DONE]",
            "",
            "");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handle500(HttpExchange exchange) throws IOException {
        byte[] bytes = "{\"error\":\"upstream\"}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(500, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
