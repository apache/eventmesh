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

package org.apache.eventmesh.cloudevents.demo.stream;

import org.apache.eventmesh.client.cloudevents.CloudEventsClient;
import org.apache.eventmesh.client.cloudevents.stream.OpenSession;
import org.apache.eventmesh.client.cloudevents.stream.StreamRequest;
import org.apache.eventmesh.client.cloudevents.stream.StreamingResponse;
import org.apache.eventmesh.client.cloudevents.stream.StreamingSession;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * Streaming-call client demo for the uni-architecture (mode 1: client↔agent, runtime-mediated).
 *
 * <p>This is a pure HTTP client demo that connects to a running EventMesh Runtime + at least one
 * registered streaming Agent. It does NOT depend on any runtime server-side classes.
 *
 * <p>Prerequisites:
 * <ol>
 *   <li>A running EventMesh Runtime on {@code runtimeUrl} (default {@code http://localhost:8080}),
 *       wired with a {@code SessionRouter} (see docs/sdk-streaming-call-guide.md §9).</li>
 *   <li>At least one streaming Agent registered + heartbeating against that runtime.</li>
 * </ol>
 *
 * <p>Run:
 * <pre>
 *   java org.apache.eventmesh.cloudevents.demo.stream.StreamingCallDemo [runtimeUrl] [prompt]
 *   # defaults: runtimeUrl=http://localhost:8080  prompt="Introduce Apache EventMesh in three sentences."
 * </pre>
 *
 * <p>The demo exercises {@code forEach}-only consumption (the sole posture) on a single-turn call,
 * then a multi-turn session that proves context is carried across turns.
 * See docs/sdk-streaming-call-guide.md for the full API reference.
 */
@Slf4j
public class StreamingCallDemo {

    private static final String DEFAULT_RUNTIME_URL = "http://localhost:8080";
    private static final String DEFAULT_PROMPT = "Introduce Apache EventMesh in three sentences.";

    public static void main(String[] args) throws Exception {
        String runtimeUrl = args.length > 0 ? args[0] : DEFAULT_RUNTIME_URL;
        String prompt = args.length > 1 ? args[1] : DEFAULT_PROMPT;

        log.info("=== Streaming Call Demo ===");
        log.info("Runtime: {}", runtimeUrl);

        // Each session uses a FRESH clientId. The matchmaker binds clientId→agent, so a fresh clientId
        // per session lets it load-balance across all registered agents (random within the
        // least-loaded tier). The multi-turn session keeps a single client on purpose: sticky binding
        // is what carries context across turns, so it stays on whichever agent it first binds to.

        // 1) Single-turn session: openSession → call → forEach → close
        withClient(runtimeUrl, c -> singleTurnForEach(c, prompt));

        // 2) Multi-turn session: context is carried across turns (single client → sticky to one agent)
        withClient(runtimeUrl, StreamingCallDemo::multiTurnSession);

        log.info("=== Demo Complete ===");
    }

    /** Build a client with a unique clientId, run an action, always shut it down. */
    private static void withClient(String runtimeUrl, ThrowingClientAction action) throws Exception {
        CloudEventsClient client = CloudEventsClient.builder()
                .runtimeUrl(runtimeUrl)
                .clientId("streaming-demo-" + System.nanoTime())
                .build();
        try {
            action.run(client);
        } finally {
            client.shutdown();
        }
    }

    @FunctionalInterface
    private interface ThrowingClientAction {
        void run(CloudEventsClient client) throws Exception;
    }

    // ------------------------------------------------------------------
    // Single-turn (openSession → call → forEach)
    // ------------------------------------------------------------------

    /** Open a session, make one call, print tokens as they arrive, close. */
    private static void singleTurnForEach(CloudEventsClient client, String prompt) throws Exception {
        log.info("");
        log.info("--- [1] single-turn / forEach ---  prompt=\"{}\"", prompt);
        StreamingSession session = client.streaming()
                .openSession(OpenSession.builder().clientId(client.clientId()).build());
        log.info("sessionId={}, agentId={}", session.sessionId(), session.agentId());
        try {
            try (StreamingResponse r = session.call(
                    StreamRequest.builder().prompt(prompt).timeout(Duration.ofMinutes(2)).build())) {
                StringBuilder sb = new StringBuilder();
                r.forEach(chunk -> {
                    sb.append(chunk.getChunk());
                    System.out.print(chunk.getChunk());   // stream tokens as they arrive
                })
                        .orTimeout(2, TimeUnit.MINUTES)
                        .join();
                log.info("");
                log.info("full reply: {}", sb);
            }
        } finally {
            session.close();
        }
    }

    // ------------------------------------------------------------------
    // Multi-turn session
    // ------------------------------------------------------------------

    /**
     * Open one session and run two turns. Closing a turn's {@link StreamingResponse} cancels only
     * that turn's read; the same {@code sessionId} is reused so the agent accumulates context.
     */
    private static void multiTurnSession(CloudEventsClient client) throws Exception {
        log.info("");
        log.info("--- [2] multi-turn session (context carried across turns) ---");
        StreamingSession session = client.streaming()
                .openSession(OpenSession.builder().clientId(client.clientId()).build());
        log.info("sessionId={}", session.sessionId());
        try {
            // Turn 1
            try (StreamingResponse r = session.call("My name is Zhang San and I am a Java engineer.")) {
                log.info("[turn 1] prompt=\"My name is Zhang San and I am a Java engineer.\"");
                r.forEach(chunk -> System.out.print(chunk.getChunk())).join();
                log.info("");
            }

            // Turn 2 — same session: the agent remembers turn 1
            try (StreamingResponse r = session.call("What is my name and what do I do?")) {
                log.info("[turn 2] prompt=\"What is my name and what do I do?\"");
                r.forEach(chunk -> System.out.print(chunk.getChunk())).join();
                log.info("");
            }
        } finally {
            session.close();   // POST /session/close/{sessionId} — idempotent
        }
    }
}