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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A one-command, self-contained runner for {@code StreamingCallDemo} (eventmesh-examples): it boots
 * an in-process {@code EventMeshApplication} + {@code StreamingAgent} (mock LLM, deterministic
 * Ev/ent/Mesh tokens) against a real RocketMQ 5.x broker, then drives the demo's {@code main()}
 * against the booted server on {@code http://localhost:8080}. Lets you SEE the streaming demo run
 * without manually wiring a server.
 *
 * <p>Run (see the {@code runDemo} gradle task):<pre>
 *   gradle :eventmesh-runtime:runDemo
 *   # or point at another broker: gradle :eventmesh-runtime:runDemo -Dit.namesrv5=host:9876
 * </pre>
 *
 * <p>The demo client is unmodified — this only provides the server side it needs.
 */
public class StreamingDemoRunner {

    private static final Logger log = LoggerFactory.getLogger(StreamingDemoRunner.class);

    /** Mock-LLM prompt that makes the server return HTTP 500 (so the demo's error path is visible). */
    private static final String FORCE_ERROR_PROMPT = "FORCE_ERROR";

    public static void main(String[] args) throws Exception {
        String namesrv = System.getProperty("it.namesrv5", E2EConfig.ROCKETMQ5_NAMESRV);
        log.info("=== StreamingDemoRunner: booting server against broker {} ===", namesrv);

        final String agentParent = "demo-runner-agent-" + System.nanoTime();
        final String clientParent = "demo-runner-client-" + System.nanoTime();

        // --- storage + topics ---
        org.apache.eventmesh.api.storage.MeshStoragePlugin storage =
            new org.apache.eventmesh.storage.rocketmq5.storage.RocketMQ5RemotingStoragePlugin();
        Properties props = new Properties();
        props.setProperty("namesrvAddr", namesrv);
        props.setProperty("eventMesh.server.rocketmq5.namesrvAddr", namesrv);
        storage.init(props);
        ((org.apache.eventmesh.storage.rocketmq5.storage.RocketMQ5RemotingStoragePlugin) storage)
            .createLiteCapableTopic(agentParent, 1);
        ((org.apache.eventmesh.storage.rocketmq5.storage.RocketMQ5RemotingStoragePlugin) storage)
            .createLiteCapableTopic(clientParent, 1);
        Thread.sleep(3_000L); // let routes settle (off the request path)

        // --- runtime + session layer (mode 1: streaming call) ---
        org.apache.eventmesh.runtime.boot.EventMeshApplication app =
            new org.apache.eventmesh.runtime.boot.EventMeshApplication(
                storage, new org.apache.eventmesh.runtime.offset.InMemoryOffsetStore(), 8080, 8081);
        app.runtime().withStorageConfig(props);
        org.apache.eventmesh.runtime.ingress.UniIngressService ingress = app.runtime().ingress();
        org.apache.eventmesh.runtime.session.SessionRegistry registry =
            new org.apache.eventmesh.runtime.session.SessionRegistry(
                new org.apache.eventmesh.runtime.cluster.InMemoryMetaStore(), 30_000L);
        org.apache.eventmesh.runtime.session.AgentRegistrar agentRegistrar =
            new org.apache.eventmesh.runtime.session.AgentRegistrar(registry,
                (String p) -> ingress.createLiteTopic(p, "init", 1), agentParent, clientParent);
        org.apache.eventmesh.runtime.session.Matchmaker matchmaker =
            new org.apache.eventmesh.runtime.session.Matchmaker(registry,
                org.apache.eventmesh.runtime.session.BrokerGroupHealth.alwaysHealthy(), 1_800_000L);
        org.apache.eventmesh.runtime.session.ChannelStrategy strategy =
            new org.apache.eventmesh.runtime.session.AgentAnchoredStrategy(clientParent);
        org.apache.eventmesh.runtime.session.SessionRouter router =
            new org.apache.eventmesh.runtime.session.SessionRouter(ingress, registry, strategy, 120_000L);
        app.withAgentRegistrar(agentRegistrar).withMatchmaker(matchmaker).withSessionRouter(router);
        app.start();
        int port = app.trafficPort();
        log.info("runtime booted on http://localhost:{}", port);

        // --- agent + mock LLM ---
        org.apache.eventmesh.client.cloudevents.CloudEventsClient agentClient =
            org.apache.eventmesh.client.cloudevents.CloudEventsClient.builder()
                .runtimeUrl("http://localhost:" + port).clientId("demo-agent").build();
        String agentId = "demo-agent-" + System.nanoTime();
        org.apache.eventmesh.agent.AgentControlClient control =
            new org.apache.eventmesh.agent.AgentControlClient("http://localhost:" + port);
        control.register(agentId, List.of("mini-3b"), 100);
        HttpServer mockLlm = startMockLlm();
        org.apache.eventmesh.agent.llm.OpenAiLlmClient llm =
            new org.apache.eventmesh.agent.llm.OpenAiLlmClient(
                "http://127.0.0.1:" + mockLlm.getAddress().getPort(), "key", "mini-3b");
        org.apache.eventmesh.agent.StreamingAgent agent =
            new org.apache.eventmesh.agent.StreamingAgent(
                agentClient, agentParent, agentId, llm, new org.apache.eventmesh.agent.ConversationStore(20));
        agent.start();
        control.ready(agentId);
        Thread heartbeat = Thread.startVirtualThread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(5_000L);
                    control.heartbeat(agentId, agent.activeSessions());
                } catch (InterruptedException e) {
                    return;
                } catch (Exception ignore) {
                    // best-effort
                }
            }
        });
        Thread.sleep(500L); // let the agent's poll loop start
        log.info("agent ready: agentId={}", agentId);

        // --- drive the (unmodified) demo client ---
        int exit = 0;
        try {
            String[] demoArgs = port == 8080
                ? new String[0] // demo defaults to localhost:8080
                : new String[] {"http://localhost:" + port};
            log.info("=== running StreamingCallDemo.main({}) ===",
                demoArgs.length == 0 ? "(defaults)" : java.util.Arrays.toString(demoArgs));
            org.apache.eventmesh.cloudevents.demo.stream.StreamingCallDemo.main(demoArgs);
            log.info("=== StreamingCallDemo completed OK ===");
        } catch (Exception e) {
            exit = 1;
            log.error("StreamingCallDemo failed", e);
        } finally {
            heartbeat.interrupt();
            try {
                control.unregister(agentId);
            } catch (Exception ignore) {
                // best-effort unregister during shutdown
            }
            agent.shutdown();
            agentClient.shutdown();
            mockLlm.stop(0);
            app.shutdown();
            log.info("server shut down");
            System.exit(exit);
        }
    }

    /** In-process mock OpenAI SSE server: emits Ev/ent/Mesh then [DONE] (or 500 on the error prompt). */
    private static HttpServer startMockLlm() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", (HttpExchange ex) -> {
            byte[] req = ex.getRequestBody().readAllBytes();
            try {
                if (new String(req, StandardCharsets.UTF_8).contains("\"" + FORCE_ERROR_PROMPT + "\"")) {
                    byte[] err = "{\"error\":\"simulated 500\"}".getBytes(StandardCharsets.UTF_8);
                    ex.getResponseHeaders().add("Content-Type", "application/json");
                    ex.sendResponseHeaders(500, err.length);
                    try (OutputStream os = ex.getResponseBody()) {
                        os.write(err);
                    }
                    return;
                }
            } catch (Exception ignore) {
                // best-effort error detection
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
            ex.getResponseHeaders().add("Content-Type", "text/event-stream");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return server;
    }
}
