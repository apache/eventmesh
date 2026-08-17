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

import org.apache.eventmesh.agent.AgentControlClient;
import org.apache.eventmesh.agent.ConversationStore;
import org.apache.eventmesh.agent.StreamingAgent;
import org.apache.eventmesh.agent.llm.OpenAiLlmClient;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.client.cloudevents.CloudEventsClient;
import org.apache.eventmesh.common.stream.StreamChunk;
import org.apache.eventmesh.runtime.boot.EventMeshApplication;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.session.AgentAnchoredStrategy;
import org.apache.eventmesh.runtime.session.AgentRegistrar;
import org.apache.eventmesh.runtime.session.BrokerGroupHealth;
import org.apache.eventmesh.runtime.session.ChannelStrategy;
import org.apache.eventmesh.runtime.session.Matchmaker;
import org.apache.eventmesh.runtime.session.SessionRegistry;
import org.apache.eventmesh.runtime.session.SessionRouter;
import org.apache.eventmesh.storage.rocketmq5.storage.RocketMQ5RemotingStoragePlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * v2 end-to-end streaming session over lite topic. Boots the full {@link EventMeshApplication}
 * (rocketmq5), wires the v2 control plane (SessionRegistry/AgentRegistrar/Matchmaker/SessionRouter) +
 * a channel strategy, registers an in-process {@link StreamingAgent} pointed at an in-process mock
 * OpenAI SSE server (or the real LLM gateway), and drives the round trip - client ->
 * {@code POST /session/open} -> {@code POST /session/stream/{sessionId}} SSE -> agent channel ->
 * agent -> LLM -> reply lite -> SSE - asserting ordered tokens + terminal done + multi-turn history.
 * Hermetic (mock LLM); gated on a real broker.
 *
 * <p>Each variant (mock / real LLM) is a {@link Nested} subclass of {@link AbstractStreamCallMode},
 * which owns the single {@code @BeforeEach boot()} / {@code @AfterEach tearDown()} pair. JUnit creates
 * a fresh instance per test method, so every test boots exactly one EventMeshApplication + agent + LLM
 * and tears them down - no mid-test {@code tearDown()+boot()} re-boot, no shared mutable
 * {@code useRealLlm} fields (the historical source of intra-class flakiness: the re-boot doubled broker
 * {@code createLiteCapableTopic} churn, which transiently disrupts remoting). A subclass picks its LLM
 * source by overriding {@link AbstractStreamCallMode#useRealLlm()}.</p>
 *
 * <p><b>Gated by broker/LLM reachability</b> ({@link E2EConfig}, auto-detected - no {@code -D} needed).
 * Mock-LLM modes run whenever rocketmq5 is reachable; real-LLM modes additionally require the LLM
 * gateway. Run the hermetic (mock) subset:</p>
 * <pre>
 *   gradle :eventmesh-runtime:test --tests "org.apache.eventmesh.runtime.it.LiteStreamCallIntegrationTest"
 * </pre>
 */
class LiteStreamCallIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Shared lifecycle + helpers for one streaming-call mode. Each {@link Nested} subclass selects its
     * mode by overriding {@link #mode2()} / {@link #useRealLlm()} and contributes its own
     * {@code @Test} methods. JUnit creates a fresh instance per test method, so every test boots its
     * own EventMeshApplication + agent + (mock|real) LLM exactly once and tears them down.
     */
    abstract static class AbstractStreamCallMode {

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AbstractStreamCallMode.class);

        final String agentParent = "em5-agent-" + System.nanoTime();
        final String clientParent = "em5-client-" + System.nanoTime();

        EventMeshApplication app;
        int trafficPort;
        StreamingAgent agent;
        CloudEventsClient agentClient;
        AgentControlClient control;
        String agentId;
        HttpServer mockLlm;
        boolean realLlm;
        ConversationStore conversations;
        Thread heartbeatThread;
        /** Each mock-LLM request's parsed body (for multi-turn history assertions). */
        final List<JsonNode> llmRequests = new CopyOnWriteArrayList<>();
        /** Unique per boot - avoids stale Nacos bindings from previous tests. */
        String clientId;

        /** true = real LLM gateway (E2EConfig); false = in-process mock OpenAI SSE server. */
        boolean useRealLlm() {
            return false;
        }

        @BeforeEach
        void boot() throws Exception {
            E2EConfig.logStatus();
            org.junit.jupiter.api.Assumptions.assumeTrue(E2EConfig.rocketmq5Available(),
                "skipping: rocketmq5 broker not reachable at " + E2EConfig.ROCKETMQ5_NAMESRV);
            if (useRealLlm()) {
                // Skip before booting anything: a real-LLM test that can't reach the gateway shouldn't
                // churn the broker (createLiteCapableTopic + app.start) just to be skipped.
                org.junit.jupiter.api.Assumptions.assumeTrue(E2EConfig.llmAvailable(),
                    "skipping: LLM gateway not reachable or llm.api.key not set (set via -Dllm.api.key=... or LLM_API_KEY env)");
            }
            String namesrv = E2EConfig.ROCKETMQ5_NAMESRV;
            clientId = "c-" + System.nanoTime();
            MeshStoragePlugin storage = new RocketMQ5RemotingStoragePlugin();
            Properties props = new Properties();
            props.setProperty("namesrvAddr", namesrv);
            props.setProperty("eventMesh.server.rocketmq5.namesrvAddr", namesrv);
            storage.init(props);
            // Pre-create parents as 1-queue LITE topics and let routes settle.
            ((RocketMQ5RemotingStoragePlugin) storage).createLiteCapableTopic(agentParent, 1);
            ((RocketMQ5RemotingStoragePlugin) storage).createLiteCapableTopic(clientParent, 1);
            Thread.sleep(3_000L);

            app = new EventMeshApplication(storage, new InMemoryOffsetStore(), 0, 0);
            app.runtime().withStorageConfig(props);

            // Wire the v2 control plane (test bypasses main(), so wire components directly).
            org.apache.eventmesh.runtime.ingress.UniIngressService ingress = app.runtime().ingress();
            // MetaStore: Nacos for real-LLM tests (verifies Nacos path); InMemory for mock tests (stable,
            // no cross-test stale-agent issue from Nacos async propagation). MQ is always real rocketmq5.
            org.apache.eventmesh.runtime.cluster.MetaStore metaStore = (useRealLlm() && E2EConfig.nacosAvailable())
                ? new org.apache.eventmesh.runtime.cluster.NacosMetaStore(E2EConfig.NACOS_ADDR)
                : new org.apache.eventmesh.runtime.cluster.InMemoryMetaStore();
            log.info("SessionRegistry backed by: {}", (useRealLlm() && E2EConfig.nacosAvailable())
                ? "NacosMetaStore@" + E2EConfig.NACOS_ADDR : "InMemoryMetaStore");
            SessionRegistry sessionRegistry = new SessionRegistry(metaStore, 30_000L);
            AgentRegistrar agentRegistrar = new AgentRegistrar(sessionRegistry,
                (String p) -> ingress.createLiteTopic(p, "init", 1), agentParent, clientParent);
            Matchmaker matchmaker = new Matchmaker(sessionRegistry, BrokerGroupHealth.alwaysHealthy(), 1_800_000L);
            ChannelStrategy strategy = new AgentAnchoredStrategy(clientParent);
            SessionRouter sessionRouter =
                new SessionRouter(ingress, sessionRegistry, strategy, 120_000L);
            app.withAgentRegistrar(agentRegistrar).withMatchmaker(matchmaker).withSessionRouter(sessionRouter);

            app.start();
            trafficPort = app.trafficPort();

            // LLM: real gateway (E2EConfig) for useRealLlm modes; else in-process mock.
            OpenAiLlmClient llm;
            if (useRealLlm()) {
                realLlm = true;
                llm = new OpenAiLlmClient(E2EConfig.LLM_BASE_URL, E2EConfig.LLM_API_KEY, E2EConfig.LLM_MODEL);
                log.info("real-LLM mode: base={} model={}",
                    E2EConfig.LLM_BASE_URL, E2EConfig.LLM_MODEL);
            } else {
                mockLlm = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                mockLlm.createContext("/v1/chat/completions", this::handleLlm);
                mockLlm.start();
                llm = new OpenAiLlmClient("http://127.0.0.1:" + mockLlm.getAddress().getPort(), "key", "model");
            }

            // Register the agent (HTTP), subscribe its channel, flip READY.
            agentId = "agent-it-" + System.nanoTime();
            control = new AgentControlClient("http://localhost:" + trafficPort);
            AgentControlClient.RegisterResult reg = control.register(agentId, List.of("model"), 100);
            assertThat(reg.parent()).isEqualTo(agentParent);
            agentClient = CloudEventsClient.builder()
                .runtimeUrl("http://localhost:" + trafficPort).clientId(agentId).build();
            conversations = new ConversationStore(20);
            agent = new StreamingAgent(agentClient, agentParent, agentId, llm, conversations);
            agent.start();
            control.ready(agentId);
            // Heartbeat thread: keeps this agent fresh (30s TTL) so the matchmaker doesn't pick stale
            // agents from Nacos left over by previous test runs.
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
            if (mockLlm != null) {
                mockLlm.stop(0);
            }
            if (app != null) {
                app.shutdown();
            }
        }

        /** Mock-LLM assertion: ordered Ev/ent/Mesh tokens + terminal done, no error. */
        void assertOrderedMockTokens() throws Exception {
            String sessionId = openSession();
            List<StreamChunk> chunks = streamSse(sessionId, "hello");

            List<String> texts = chunks.stream().map(StreamChunk::getChunk)
                .filter(c -> !c.isEmpty()).collect(Collectors.toList());
            StreamChunk last = chunks.get(chunks.size() - 1);
            assertThat(last.isDone()).isTrue();
            assertThat(last.getError()).isNull();
            assertThat(texts).containsExactly("Ev", "ent", "Mesh");
        }

        /** Real-LLM assertion: non-deterministic tokens, just require output + done + no error. */
        void assertRealLlmAnswer() throws Exception {
            String sessionId = openSession();
            List<StreamChunk> chunks = streamSse(sessionId, "Describe EventMesh in one sentence");

            StreamChunk last = chunks.get(chunks.size() - 1);
            assertThat(last.isDone()).isTrue();
            assertThat(last.getError()).isNull();
            List<String> texts = chunks.stream().map(StreamChunk::getChunk)
                .filter(c -> !c.isEmpty()).collect(Collectors.toList());
            assertThat(texts).isNotEmpty();
            String answer = String.join("", texts);
            assertThat(answer).isNotBlank();
            log.info("real-LLM E2E: {} chunks, answer=\"{}\"", texts.size(), answer);
        }

        /** POST /session/open {clientId} -> {sessionId}. */
        String openSession() throws Exception {
            HttpURLConnection conn = (HttpURLConnection) new URL(
                "http://localhost:" + trafficPort + "/session/open").openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(("{\"clientId\":\"" + clientId + "\"}").getBytes(StandardCharsets.UTF_8));
            }
            try (java.io.InputStream is = conn.getInputStream()) {
                JsonNode node = MAPPER.readTree(new String(is.readAllBytes(), StandardCharsets.UTF_8));
                String sid = node.get("sessionId").asText();
                assertThat(node.get("agentId").asText()).isEqualTo(agentId);
                return sid;
            }
        }

        /** POST /session/stream/{sessionId} {prompt} -> drain SSE into ordered chunks. */
        List<StreamChunk> streamSse(String sessionId, String prompt) throws Exception {
            HttpURLConnection conn = (HttpURLConnection) new URL(
                "http://localhost:" + trafficPort + "/session/stream/" + sessionId).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setReadTimeout(realLlm ? 120_000 : 60_000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(("{\"prompt\":\"" + prompt + "\"}").getBytes(StandardCharsets.UTF_8));
            }
            List<StreamChunk> out = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data: ")) {
                        continue;
                    }
                    StreamChunk c = MAPPER.readValue(line.substring(6), StreamChunk.class);
                    out.add(c);
                    if (c.isDone()) {
                        break;
                    }
                }
            }
            assertThat(out).isNotEmpty();
            return out;
        }

        /** Mock OpenAI streaming endpoint: emits Ev / ent / Mesh then [DONE]; captures request messages. */
        private void handleLlm(HttpExchange exchange) throws IOException {
            try {
                byte[] reqBody = exchange.getRequestBody().readAllBytes();
                if (reqBody.length > 0) {
                    llmRequests.add(MAPPER.readTree(reqBody));
                }
            } catch (Exception ignore) {
                // capture is best-effort
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
    }

    /** Mode 1 (agent-anchored) + mock LLM: the hermetic default. */
    @Nested
    class Mode1MockTest extends AbstractStreamCallMode {

        @Test
        void sessionStreamDeliversOrderedTokens() throws Exception {
            assertOrderedMockTokens();
        }

        @Test
        void multiTurnHistoryCarriesOver() throws Exception {
            String sessionId = openSession();

            // turn 1
            streamSse(sessionId, "my name is Alice");
            long dl = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (conversations.get(sessionId).size() < 2 && System.nanoTime() < dl) {
                Thread.sleep(50L);
            }
            assertThat(conversations.get(sessionId).size()).as("turn 1 recorded").isGreaterThanOrEqualTo(2);

            // turn 2 (same sessionId -> agent carries turn 1 history into the LLM request)
            llmRequests.clear();
            streamSse(sessionId, "what is my name");

            assertThat(llmRequests).hasSize(1);
            JsonNode turn2Messages = llmRequests.get(0).get("messages");
            assertThat(turn2Messages).isNotNull();
            assertThat(turn2Messages.size()).isGreaterThan(1);
            boolean hasAlice = false;
            for (JsonNode m : turn2Messages) {
                if ("my name is Alice".equals(m.get("content").asText())) {
                    hasAlice = true;
                    break;
                }
            }
            assertThat(hasAlice).as("turn 2 LLM request should carry turn 1 prompt").isTrue();
            assertThat(conversations.get(sessionId).size()).isGreaterThanOrEqualTo(4);
        }
    }

    /** Mode 1 (agent-anchored) + real LLM gateway. Skipped unless the gateway is reachable. */
    @Nested
    class Mode1RealLlmTest extends AbstractStreamCallMode {

        @Override
        boolean useRealLlm() {
            return true;
        }

        @Test
        void realLlmStreamDeliversAnswer() throws Exception {
            assertRealLlmAnswer();
        }
    }
}
