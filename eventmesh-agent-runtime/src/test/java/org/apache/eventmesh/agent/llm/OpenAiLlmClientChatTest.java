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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Drives {@link OpenAiLlmClient#chat} (function calling) against an in-process mock. Hermetic. */
class OpenAiLlmClientChatTest {

    private HttpServer server;
    private OpenAiLlmClient client;
    private final List<JsonNode> capturedBodies = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        client = new OpenAiLlmClient("http://127.0.0.1:" + server.getAddress().getPort(), "k", "m");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void chatReturnsToolCallWhenModelRequestsOne() throws Exception {
        server.createContext("/v1/chat/completions", ex -> {
            capture(ex);
            respond(ex, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,"
                + "\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\","
                + "\"function\":{\"name\":\"notify\",\"arguments\":\"{\\\"text\\\":\\\"hi\\\"}\"}}]}}]}");
        });
        LlmCompletion completion = client.chat(List.of(Map.of("role", "user", "content", "go")),
            List.of(new ToolSpec("notify", "send a notification", "{\"type\":\"object\"}")), null);
        assertThat(completion.text()).isNull();
        assertThat(completion.toolCall().id()).isEqualTo("call-1");
        assertThat(completion.toolCall().function()).isEqualTo("notify");
        assertThat(completion.toolCall().argumentsJson()).isEqualTo("{\"text\":\"hi\"}");
    }

    @Test
    void chatReturnsTextWhenNoToolCall() throws Exception {
        server.createContext("/v1/chat/completions", ex -> {
            capture(ex);
            respond(ex, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"all done\"}}]}");
        });
        LlmCompletion completion = client.chat(List.of(Map.of("role", "user", "content", "go")),
            List.of(), null);
        assertThat(completion.toolCall()).isNull();
        assertThat(completion.text()).isEqualTo("all done");
    }

    @Test
    void chatAdvertisesToolsInTheRequestBody() throws Exception {
        server.createContext("/v1/chat/completions", ex -> {
            capture(ex);
            respond(ex, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}");
        });
        client.chat(List.of(Map.of("role", "user", "content", "go")),
            List.of(new ToolSpec("poll-orders", "poll a batch", "{\"type\":\"object\"}")), "override-m");
        assertThat(capturedBodies).hasSize(1);
        JsonNode body = capturedBodies.get(0);
        assertThat(body.get("model").asText()).isEqualTo("override-m");
        assertThat(body.get("stream")).isNull();
        JsonNode fn = body.path("tools").path(0).path("function");
        assertThat(fn.get("name").asText()).isEqualTo("poll-orders");
        assertThat(fn.get("description").asText()).isEqualTo("poll a batch");
        assertThat(fn.path("parameters").get("type").asText()).isEqualTo("object");
    }

    private void capture(HttpExchange exchange) throws IOException {
        capturedBodies.add(new ObjectMapper().readTree(exchange.getRequestBody().readAllBytes()));
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
