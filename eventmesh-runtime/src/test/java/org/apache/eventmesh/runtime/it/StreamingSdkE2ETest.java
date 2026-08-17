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

package org.apache.eventmesh.runtime.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.eventmesh.agent.AgentControlClient;
import org.apache.eventmesh.agent.ConversationStore;
import org.apache.eventmesh.agent.StreamingAgent;
import org.apache.eventmesh.agent.llm.OpenAiLlmClient;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.client.cloudevents.CloudEventsClient;
import org.apache.eventmesh.client.cloudevents.stream.OpenSession;
import org.apache.eventmesh.client.cloudevents.stream.SessionPublisher;
import org.apache.eventmesh.client.cloudevents.stream.StreamException;
import org.apache.eventmesh.client.cloudevents.stream.StreamRequest;
import org.apache.eventmesh.client.cloudevents.stream.StreamingResponse;
import org.apache.eventmesh.client.cloudevents.stream.StreamingSession;
import org.apache.eventmesh.cloudevents.demo.stream.StreamingCallDemo;
import org.apache.eventmesh.runtime.boot.EventMeshApplication;
import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.session.AgentAnchoredStrategy;
import org.apache.eventmesh.runtime.session.AgentRegistrar;
import org.apache.eventmesh.runtime.session.BrokerGroupHealth;
import org.apache.eventmesh.runtime.session.ChannelStrategy;
import org.apache.eventmesh.runtime.session.Matchmaker;
import org.apache.eventmesh.runtime.session.SessionRegistry;
import org.apache.eventmesh.runtime.session.SessionRouter;
import org.apache.eventmesh.storage.rocketmq5.storage.RocketMQ5RemotingStoragePlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Real end-to-end test of the <b>SDK streaming API</b> ({@code CloudEventsClient.streaming()} →
 * {@link StreamingResponse}/{@link StreamingSession}, mode 1: client↔agent, runtime-mediated) over a
 * full {@link EventMeshApplication} + real RocketMQ 5.x lite-topic broker + an in-process
 * {@link StreamingAgent} backed by either a mock OpenAI SSE server (deterministic) or the real LLM
 * gateway.
 *
 * <p>Unlike {@code LiteStreamCallIntegrationTest} (raw {@code HttpURLConnection}), this test drives
 * the entire client side through the first-class SDK types. Coverage:</p>
 * <ul>
 *   <li><b>Mock mode</b> ({@link Mode1}): {@code forEach} consumption, multi-turn session reuse,
 *   multi-turn context carry-over, per-call model pass-through, session-close idempotency,
 *   terminal-error propagation — deterministic.</li>
 *   <li><b>Real-LLM mode</b> ({@link Mode1RealLlm}): {@code forEach} against the real gateway
 *   (lenient, non-deterministic).</li>
 * </ul>
 *
 * <p>Split into {@link AbstractMockMode}/{@link AbstractRealLlmMode} so mock tests don't boot a real
 * broker for real-LLM modes. Gated on broker/LLM reachability ({@link E2EConfig}, auto-detected). Run:</p>
 * <pre>
 *   gradle :eventmesh-runtime:e2eTest5x --tests "org.apache.eventmesh.runtime.it.StreamingSdkE2ETest"
 *   # real-LLM: add -Dllm.api.key=...
 * </pre>
 */
class StreamingSdkE2ETest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Sentinel prompt that makes the mock LLM return HTTP 500 → agent publishes an error chunk. */
    private static final String FORCE_ERROR_PROMPT = "FORCE_ERROR";

    /** Model the agent advertises AND the per-call-model test requests (matchmaker filters by it). */
    private static final String TEST_MODEL = "mini-3b";

    /**
     * Shared lifecycle + helpers for one runtime boot (mode-1 streaming call). {@link AbstractMockMode}
     * and {@link AbstractRealLlmMode} carry the {@code @Test} methods. JUnit creates a fresh instance
     * per test method, so every test boots exactly one app + agent + LLM and tears it down.
     */
    abstract static class AbstractMode {

        static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AbstractMode.class);

        static final long AWAIT_SEC = 30L;

        final String agentParent = "em5-agent-sdk-" + System.nanoTime();
        final String clientParent = "em5-client-sdk-" + System.nanoTime();
        /** Captures each mock-LLM request body (for model/context assertions). */
        final List<JsonNode> llmRequests = new CopyOnWriteArrayList<>();

        EventMeshApplication app;
        int trafficPort;
        StreamingAgent agent;
        AgentControlClient control;
        String agentId;
        HttpServer mockLlm;
        Thread heartbeatThread;
        CloudEventsClient sdkClient;
        CloudEventsClient agentClient;
        ConversationStore conversations;

        /** true = real LLM gateway ({@link E2EConfig}); false = in-process mock OpenAI SSE server. */
        boolean useRealLlm() {
            return false;
        }

        /**
         * Mode 2 (pub/sub) parent topic for per-session lites. Non-null enables mode 2 on this runtime
         * (6-arg {@code SessionRouter} constructor + the parent is pre-created). Default null = mode 2
         * disabled (mode-1 streaming-call only).
         */
        String sessionStreamParent() {
            return null;
        }

        @BeforeEach
        void boot() throws Exception {
            E2EConfig.logStatus();
            Assumptions.assumeTrue(E2EConfig.rocketmq5Available(),
                "skipping: rocketmq5 broker not reachable at " + E2EConfig.ROCKETMQ5_NAMESRV);
            if (useRealLlm()) {
                // Skip before booting the broker (avoids createLiteCapableTopic churn just to skip).
                Assumptions.assumeTrue(E2EConfig.llmAvailable(),
                    "skipping: LLM gateway not reachable or llm.api.key not set");
            }

            String namesrv = E2EConfig.ROCKETMQ5_NAMESRV;
            MeshStoragePlugin storage = new RocketMQ5RemotingStoragePlugin();
            Properties props = new Properties();
            props.setProperty("namesrvAddr", namesrv);
            props.setProperty("eventMesh.server.rocketmq5.namesrvAddr", namesrv);
            storage.init(props);
            // Pre-create parents as 1-queue LITE topics and let routes settle (off the request path —
            // createLiteCapableTopic transiently disrupts this broker's remoting for a short window).
            ((RocketMQ5RemotingStoragePlugin) storage).createLiteCapableTopic(agentParent, 1);
            ((RocketMQ5RemotingStoragePlugin) storage).createLiteCapableTopic(clientParent, 1);
            String ssp = sessionStreamParent();
            if (ssp != null) {
                ((RocketMQ5RemotingStoragePlugin) storage).createLiteCapableTopic(ssp, 1);
            }
            Thread.sleep(3_000L);

            app = new EventMeshApplication(storage, new InMemoryOffsetStore(), 0, 0);
            app.runtime().withStorageConfig(props);
            UniIngressService ingress = app.runtime().ingress();
            SessionRegistry sessionRegistry = new SessionRegistry(new InMemoryMetaStore(), 30_000L);
            AgentRegistrar agentRegistrar = new AgentRegistrar(sessionRegistry,
                (String p) -> ingress.createLiteTopic(p, "init", 1), agentParent, clientParent);
            Matchmaker matchmaker = new Matchmaker(sessionRegistry, BrokerGroupHealth.alwaysHealthy(), 1_800_000L);
            ChannelStrategy strategy = new AgentAnchoredStrategy(clientParent);
            // Mode 2 (pub/sub) is enabled only when a sessionStreamParent is provided.
            SessionRouter sessionRouter = ssp == null
                ? new SessionRouter(ingress, sessionRegistry, strategy, 120_000L)
                : new SessionRouter(ingress, sessionRegistry, strategy, 120_000L, 300_000L, ssp);
            app.withAgentRegistrar(agentRegistrar).withMatchmaker(matchmaker).withSessionRouter(sessionRouter);
            app.start();
            trafficPort = app.trafficPort();

            // Register the agent over the HTTP control plane (subscribe its channel next).
            agentId = "agent-sdk-" + System.nanoTime();
            control = new AgentControlClient("http://localhost:" + trafficPort);
            control.register(agentId, List.of(TEST_MODEL), 100);
            agentClient = CloudEventsClient.builder()
                .runtimeUrl("http://localhost:" + trafficPort).clientId(agentId).build();

            // LLM: real gateway (E2EConfig) for useRealLlm modes; else in-process mock (Ev/ent/Mesh/[DONE]).
            OpenAiLlmClient llm = buildLlm();
            conversations = new ConversationStore(20);
            agent = new StreamingAgent(agentClient, agentParent, agentId, llm, conversations);
            agent.start();
            control.ready(agentId);
            // Heartbeat keeps the agent fresh (30s TTL) so the matchmaker picks it.
            heartbeatThread = Thread.startVirtualThread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(5_000L);
                        control.heartbeat(agentId, agent.activeSessions());
                    } catch (InterruptedException e) {
                        return;
                    } catch (Exception e) {
                        // best-effort
                    }
                }
            });
            Thread.sleep(500L); // let the agent's poll loop start

            // The SDK client UNDER TEST (the caller).
            sdkClient = CloudEventsClient.builder()
                .runtimeUrl("http://localhost:" + trafficPort)
                .clientId("sdk-caller-" + System.nanoTime())
                .pollIntervalMs(100L).build();
            log.info("StreamingSdkE2ETest booted: trafficPort={} agentId={} broker={}",
                trafficPort, agentId, namesrv);
        }

        @AfterEach
        void tearDown() {
            if (heartbeatThread != null) {
                heartbeatThread.interrupt();
            }
            if (control != null && agentId != null) {
                try {
                    control.unregister(agentId);
                } catch (Exception ignore) {
                    // best-effort
                }
            }
            if (agent != null) {
                agent.shutdown();
            }
            if (agentClient != null) {
                agentClient.shutdown();
            }
            if (sdkClient != null) {
                sdkClient.shutdown();
            }
            if (mockLlm != null) {
                mockLlm.stop(0);
            }
            if (app != null) {
                app.shutdown();
            }
        }

        /**
         * Build the LLM client: real gateway ({@link E2EConfig}) when {@link #useRealLlm()}, else an
         * in-process mock OpenAI SSE server emitting Ev/ent/Mesh/[DONE] (or 500 on
         * {@link #FORCE_ERROR_PROMPT}). Extracted so {@code llm} is declared and used adjacently.
         */
        private OpenAiLlmClient buildLlm() throws IOException {
            if (useRealLlm()) {
                log.info("real-LLM mode: base={} model={}", E2EConfig.LLM_BASE_URL, E2EConfig.LLM_MODEL);
                return new OpenAiLlmClient(E2EConfig.LLM_BASE_URL, E2EConfig.LLM_API_KEY, E2EConfig.LLM_MODEL);
            }
            mockLlm = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            mockLlm.createContext("/v1/chat/completions", this::handleLlm);
            mockLlm.start();
            return new OpenAiLlmClient(
                "http://127.0.0.1:" + mockLlm.getAddress().getPort(), "key", "model");
        }

        /**
         * Mock OpenAI streaming endpoint: captures the request body; on {@link #FORCE_ERROR_PROMPT}
         * returns 500 (→ agent error chunk), else emits Ev/ent/Mesh then [DONE].
         */
        private void handleLlm(HttpExchange exchange) throws IOException {
            byte[] reqBody = exchange.getRequestBody().readAllBytes();
            if (reqBody.length > 0) {
                try {
                    llmRequests.add(MAPPER.readTree(reqBody));
                } catch (Exception ignore) {
                    // capture is best-effort
                }
                if (promptsContain(reqBody, FORCE_ERROR_PROMPT)) {
                    byte[] err = "{\"error\":\"simulated 500\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(500, err.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(err);
                    }
                    return;
                }
            }
            String body = String.join("\n",
                "data: {\"choices\":[{\"delta\":{\"content\":\"Ev\"}}]}",
                "",
                "data: {\"choices\":[{\"delta\":{\"content\":\"ent\"}}]}",
                "",
                "data: {\"choices\":[{\"delta\":{\"content\":\"Mesh\"}}]}",
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

        /** True if any message content in the JSON request body equals {@code needle}. */
        private static boolean promptsContain(byte[] reqBody, String needle) {
            try {
                JsonNode root = MAPPER.readTree(reqBody);
                JsonNode messages = root.get("messages");
                if (messages == null) {
                    return false;
                }
                for (JsonNode m : messages) {
                    JsonNode c = m.get("content");
                    if (c != null && needle.equals(c.asText())) {
                        return true;
                    }
                }
            } catch (Exception ignore) {
                // best-effort
            }
            return false;
        }

        static StreamRequest req(String prompt) {
            return StreamRequest.builder().prompt(prompt).build();
        }

        static void addNonEmpty(List<String> out, String chunk) {
            if (chunk != null && !chunk.isEmpty()) {
                out.add(chunk);
            }
        }

        /**
         * Open a session, make one call, drain it via {@code forEach}, close the session, return the
         * non-empty chunk texts in order. The mode-1 single-turn equivalent of the old callOneShot.
         */
        List<String> singleTurn(String prompt) throws Exception {
            StreamingSession session = sdkClient.streaming()
                .openSession(OpenSession.builder().clientId(sdkClient.clientId()).build());
            List<String> texts = new ArrayList<>();
            try {
                try (StreamingResponse r = session.call(req(prompt))) {
                    r.forEach(c -> addNonEmpty(texts, c.getChunk())).get(AWAIT_SEC, TimeUnit.SECONDS);
                }
            } finally {
                session.close();
            }
            return texts;
        }

        /** Poll until at least {@code n} mock-LLM requests captured, or 5s elapsed. */
        boolean awaitLlmRequests(int n) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (llmRequests.size() < n && System.nanoTime() < deadline) {
                Thread.sleep(50L);
            }
            return llmRequests.size() >= n;
        }

        /** Poll until the agent recorded at least {@code minSize} messages for {@code sessionId}. */
        void awaitConversation(String sessionId, int minSize) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                List<Map<String, String>> hist = conversations.get(sessionId);
                if (hist != null && hist.size() >= minSize) {
                    return;
                }
                Thread.sleep(50L);
            }
        }

        /** POST to {@code path} and return the HTTP status (no body). */
        int postStatus(String path) throws IOException {
            HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + trafficPort + path)
                .openConnection();
            conn.setRequestMethod("POST");
            int code = conn.getResponseCode();
            conn.disconnect();
            return code;
        }
    }

    /** Mock-LLM mode shares these deterministic tests (fixed-token Ev/ent/Mesh + captured requests). */
    abstract static class AbstractMockMode extends AbstractMode {

        /** Single-turn {@code forEach}: ordered Ev/ent/Mesh tokens. */
        @Test
        void singleTurnForEachDeliversOrderedTokens() throws Exception {
            assertThat(singleTurn("hello")).containsExactly("Ev", "ent", "Mesh");
        }

        /**
         * Multi-turn: one openSession reused across 2 turns; turn-2's LLM request carries turn-1's
         * prompt (proves both session reuse and agent-side context accumulation through the full stack).
         */
        @Test
        void multiTurnReusesSessionAndCarriesContext() throws Exception {
            StreamingSession session = sdkClient.streaming()
                .openSession(OpenSession.builder().clientId(sdkClient.clientId()).build());
            try {
                assertThat(session.agentId()).isEqualTo(agentId);
                llmRequests.clear();
                // turn 1
                List<String> turn1 = new ArrayList<>();
                try (StreamingResponse r = session.call("my name is Alice")) {
                    r.forEach(c -> addNonEmpty(turn1, c.getChunk())).get(AWAIT_SEC, TimeUnit.SECONDS);
                }
                assertThat(turn1).containsExactly("Ev", "ent", "Mesh");
                awaitConversation(session.sessionId(), 2); // agent recorded turn 1 (user + assistant)
                llmRequests.clear();
                // turn 2 — same sessionId (closing turn 1's response must NOT have closed the session)
                List<String> turn2 = new ArrayList<>();
                try (StreamingResponse r = session.call("what is my name")) {
                    r.forEach(c -> addNonEmpty(turn2, c.getChunk())).get(AWAIT_SEC, TimeUnit.SECONDS);
                }
                assertThat(turn2).containsExactly("Ev", "ent", "Mesh");
                assertThat(awaitLlmRequests(1)).as("turn-2 LLM request captured").isTrue();
                JsonNode messages = llmRequests.get(0).get("messages");
                assertThat(messages).isNotNull();
                boolean carried = false;
                for (JsonNode m : messages) {
                    JsonNode c = m.get("content");
                    if (c != null && "my name is Alice".equals(c.asText())) {
                        carried = true;
                        break;
                    }
                }
                assertThat(carried).as("turn-2 LLM request should carry turn-1 prompt").isTrue();
            } finally {
                session.close(); // POST /session/close/{sessionId}
            }
        }

        /**
         * Session contract (3 guarantees in one boot): per-call model reaches the agent; closing the
         * session → a repeat {@code /session/close} 404s; a terminal error chunk (LLM 500 → agent
         * error) surfaces as a {@link StreamException} via forEach.
         */
        @Test
        void sessionContract() throws Exception {
            // 1) per-call model reaches the agent's LLM request
            llmRequests.clear();
            StreamingSession session = sdkClient.streaming()
                .openSession(OpenSession.builder().clientId(sdkClient.clientId()).build());
            try (StreamingResponse r = session.call(
                    StreamRequest.builder().prompt("hi").model(TEST_MODEL).build())) {
                r.forEach(c -> { }).get(AWAIT_SEC, TimeUnit.SECONDS);
            }
            assertThat(awaitLlmRequests(1)).as("LLM request captured").isTrue();
            assertThat(llmRequests.get(0).get("model").asText()).isEqualTo(TEST_MODEL);

            // 2) closing a session → a repeat /session/close 404s
            session.close();
            assertThat(postStatus("/session/close/" + session.sessionId())).isEqualTo(404);

            // 3) a terminal error chunk (LLM 500 → agent error) surfaces as a StreamException via forEach
            StreamingSession errSession = sdkClient.streaming()
                .openSession(OpenSession.builder().clientId(sdkClient.clientId()).build());
            try (StreamingResponse r = errSession.call(req(FORCE_ERROR_PROMPT))) {
                CompletableFuture<Void> fut = r.forEach(c -> { });
                assertThatThrownBy(() -> fut.get(AWAIT_SEC, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(StreamException.class);
            } finally {
                errSession.close();
            }
        }
    }

    /** Real-LLM mode shares these lenient tests (non-deterministic tokens). */
    abstract static class AbstractRealLlmMode extends AbstractMode {

        @Override
        boolean useRealLlm() {
            return true;
        }

        /** Real-LLM (forEach): non-empty output + terminal done + no error. */
        @Test
        void realLlmStreamDeliversAnswer() throws Exception {
            List<String> texts = new ArrayList<>();
            StreamingSession session = sdkClient.streaming()
                .openSession(OpenSession.builder().clientId(sdkClient.clientId()).build());
            try {
                try (StreamingResponse r = session.call(
                        StreamRequest.builder().prompt("Describe EventMesh in one sentence.").build())) {
                    r.forEach(c -> addNonEmpty(texts, c.getChunk())).get(90, TimeUnit.SECONDS);
                }
            } finally {
                session.close();
            }
            assertThat(texts).isNotEmpty();
            String answer = String.join("", texts);
            assertThat(answer).isNotBlank();
            log.info("real-LLM SDK E2E: forEach {} chunks, answer=\"{}\"", texts.size(), answer);
        }
    }

    /** Mode 1 (agent-anchored): requests multiplexed on {@code clientParent}; agent owns reply lites. */
    @Nested
    class Mode1 extends AbstractMockMode {
    }

    /**
     * Mode 2 (publish/subscribe): a publisher writes chunks onto a per-session lite topic via
     * {@code POST /session/publish/{sessionId}}; a subscriber drains them via {@code GET
     * /session/subscribe/{sessionId}} SSE. No agent, no matchmaking — the sessionId is the routing
     * key. Extends {@link AbstractMode} (mode 2 enabled via {@link #sessionStreamParent()}); the agent
     * boots but is unused on this path.
     */
    @Nested
    class PubSub extends AbstractMode {

        final String pubSubParent = "em5-pubsub-" + System.nanoTime();

        @Override
        String sessionStreamParent() {
            return pubSubParent;
        }

        @Test
        void publishThenSubscribeDeliversOrderedChunks() throws Exception {
            String sessionId = "ps-session-" + System.nanoTime();
            // Subscribe FIRST (the lite is created lazily on the first publish; subscribing before
            // any publish just waits). Drive forEach on a virtual thread, collecting chunks.
            List<String> received = new java.util.concurrent.CopyOnWriteArrayList<>();
            StreamingResponse sub = sdkClient.subscribeSession(sessionId);
            java.util.concurrent.CompletableFuture<Void> drain = sub.forEach(c -> {
                if (c.getChunk() != null && !c.getChunk().isEmpty()) {
                    received.add(c.getChunk());
                }
            });
            // Publish from a second client (same runtime) — the cross-process/cross-time value of MQ.
            CloudEventsClient pubClient = CloudEventsClient.builder()
                .runtimeUrl("http://localhost:" + trafficPort)
                .clientId("ps-pub-" + System.nanoTime()).build();
            try {
                SessionPublisher pub = pubClient.openSessionPublisher(sessionId);
                pub.publish("alpha", false);
                pub.publish("beta", false);
                pub.publish("gamma", false);
                pub.publish("", true); // terminal → forEach completes
            } finally {
                pubClient.shutdown();
            }
            // forEach completes when the terminal chunk arrives.
            drain.get(AWAIT_SEC, TimeUnit.SECONDS);
            sub.close();
            assertThat(received).containsExactly("alpha", "beta", "gamma");
        }
    }

    /** Mode 1 + real LLM gateway. Skipped unless {@code llm.api.key} is set and the gateway is reachable. */
    @Nested
    class Mode1RealLlm extends AbstractRealLlmMode {
    }

    /**
     * Drives the {@link StreamingCallDemo} (eventmesh-examples) {@code main()} end-to-end against the
     * booted runtime + mock agent. It returns normally on success; any failure (timeout, stream error)
     * throws. Extends {@link AbstractMode} (not {@link AbstractMockMode}) to run only this one test.
     */
    @Nested
    class Demo extends AbstractMode {

        @Test
        void demoMainRunsWithoutError() throws Exception {
            // The demo's main() exercises a single-turn forEach call + a multi-turn session.
            // It returns normally on success; any failure (timeout, stream error) throws.
            StreamingCallDemo.main(new String[] {"http://localhost:" + trafficPort, "hello"});
        }
    }
}
