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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link DefaultStreamingResponse}'s {@code forEach} posture, using a canned SSE
 * byte stream and a stub {@link HttpURLConnection} (no real HTTP). The SSE read happens on a
 * virtual thread; {@code forEach} synchronizes via the returned future.
 */
class DefaultStreamingResponseTest {

    private static final String EV_ENT_MESH =
        data(0, "Ev", false, null) + data(1, "ent", false, null) + data(2, "Mesh", false, null)
            + data(3, "", true, null);

    private final ObjectMapper mapper = new ObjectMapper();

    private static String data(int seq, String chunk, boolean done, String error) {
        return "data: {\"sessionId\":\"s1\",\"seq\":" + seq + ",\"chunk\":\"" + chunk + "\","
            + "\"done\":" + done + ",\"error\":" + (error == null ? "null" : "\"" + error + "\"") + "}\n\n";
    }

    private DefaultStreamingResponse response(String sse) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        return new DefaultStreamingResponse("s1", "agent1", reader, new StubConn(),
            mapper, 1000, () -> {
            });
    }

    /**
     * Minimal {@link HttpURLConnection} stub: {@code DefaultStreamingResponse} only ever calls
     * {@code disconnect()} on it, so every method is a no-op.
     */
    private static final class StubConn extends HttpURLConnection {

        StubConn() {
            super(null);
        }

        @Override
        public void disconnect() {
            // no-op
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
            // no-op
        }
    }

    @Test
    void forEachDeliversAllChunksAndCompletes() throws Exception {
        DefaultStreamingResponse resp = response(EV_ENT_MESH);
        List<String> texts = new ArrayList<>();

        CompletableFuture<Void> done = resp.forEach(c -> texts.add(c.getChunk()));

        done.get(5, TimeUnit.SECONDS);
        assertThat(texts).containsExactly("Ev", "ent", "Mesh");
    }

    @Test
    void forEachPropagatesTerminalError() throws Exception {
        String sse = data(0, "Hi", false, null) + data(1, "", true, "llm 503");
        DefaultStreamingResponse resp = response(sse);

        CompletableFuture<Void> fut = resp.forEach(c -> { });
        assertThatThrownBy(() -> fut.get(5, TimeUnit.SECONDS))
            .hasCauseInstanceOf(StreamException.class);
    }

    @Test
    void onlyOneConsumerMayBeActive() {
        DefaultStreamingResponse resp = response(EV_ENT_MESH);
        resp.forEach(c -> {
        });
        assertThatThrownBy(() -> resp.forEach(c -> { })).isInstanceOf(IllegalStateException.class);
    }
}