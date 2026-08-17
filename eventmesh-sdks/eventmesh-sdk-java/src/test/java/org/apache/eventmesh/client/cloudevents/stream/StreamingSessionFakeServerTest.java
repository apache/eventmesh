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

package org.apache.eventmesh.client.cloudevents.stream;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.eventmesh.client.cloudevents.CloudEventsClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Integration test for the {@code streaming()} facade ({@link StreamingSession}) against a fake HTTP
 * server that emits the exact v2 SSE wire format. Validates the SDK's HTTP client +
 * openSession/call wiring end-to-end without needing a broker or the full runtime.
 */
class StreamingSessionFakeServerTest {

    private static final String AGENT_ID = "agent1";
    private static final String SESSION_ID = AGENT_ID + ":abc123";

    private HttpServer server;
    private CloudEventsClient client;

    private void boot() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/session/open", this::handleOpen);
        server.createContext("/session/stream", this::handleStream);
        server.createContext("/session/close", this::handleClose);
        server.start();
        int port = server.getAddress().getPort();
        client = CloudEventsClient.builder().runtimeUrl("http://127.0.0.1:" + port)
            .clientId("c1").pollIntervalMs(100L).build();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.shutdown();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    private void handleOpen(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        String body = "{\"sessionId\":\"" + SESSION_ID + "\",\"agentId\":\"" + AGENT_ID + "\"}";
        sendJson(exchange, 200, body);
    }

    private void handleStream(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            writeSse(out, 0, "Ev", false, null);
            writeSse(out, 1, "ent", false, null);
            writeSse(out, 2, "Mesh", false, null);
            writeSse(out, 3, "", true, null);
        }
    }

    private void handleClose(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        sendJson(exchange, 200, "{\"status\":\"ok\"}");
    }

    private static void writeSse(OutputStream out, int seq, String chunk, boolean done, String error)
        throws IOException {
        String err = error == null ? "null" : "\"" + error + "\"";
        String frame = "data: {\"sessionId\":\"" + SESSION_ID + "\",\"seq\":" + seq
            + ",\"chunk\":\"" + chunk + "\",\"done\":" + done + ",\"error\":" + err + "}\n\n";
        out.write(frame.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    void openSessionThenCallForEach() throws Exception {
        boot();
        StreamingSession session = client.streaming()
            .openSession(OpenSession.builder().clientId("c1").build());

        assertThat(session.sessionId()).isEqualTo(SESSION_ID);
        assertThat(session.agentId()).isEqualTo(AGENT_ID);

        List<String> texts = new ArrayList<>();
        try (StreamingResponse r = session.call("hello")) {
            r.forEach(c -> texts.add(c.getChunk())).get(10, TimeUnit.SECONDS);
        }
        assertThat(texts).containsExactly("Ev", "ent", "Mesh");
        session.close();
    }

    @Test
    void secondTurnReusesSameSession() throws Exception {
        boot();
        StreamingSession session = client.streaming()
            .openSession(OpenSession.builder().clientId("c1").build());
        try {
            List<String> turn1 = new ArrayList<>();
            try (StreamingResponse r = session.call("first")) {
                r.forEach(c -> turn1.add(c.getChunk())).get(10, TimeUnit.SECONDS);
            }
            assertThat(turn1).containsExactly("Ev", "ent", "Mesh");

            // closing turn 1's response must NOT have closed the session → turn 2 works
            List<String> turn2 = new ArrayList<>();
            try (StreamingResponse r = session.call("second")) {
                r.forEach(c -> turn2.add(c.getChunk())).get(10, TimeUnit.SECONDS);
            }
            assertThat(turn2).containsExactly("Ev", "ent", "Mesh");
        } finally {
            session.close();
        }
    }
}