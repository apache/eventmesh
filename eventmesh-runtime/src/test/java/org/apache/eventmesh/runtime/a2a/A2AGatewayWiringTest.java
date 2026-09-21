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

package org.apache.eventmesh.runtime.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.runtime.boot.EventMeshApplication;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.state.RocksDBTaskStore;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Issue #5405 acceptance: the A2A gateway booted WITH the main process —
 * <ul>
 *   <li>{@code withA2aGateway} wires the REAL transport ({@code EventMeshA2ATransport}
 *       bridged onto the runtime ingress) and a durable {@link RocksDBTaskStore};</li>
 *   <li>the REST plane answers health, honors the bearer token, and persists the
 *       {@code contextId} through a submit round-trip.</li>
 * </ul>
 */
class A2AGatewayWiringTest {

    private EventMeshApplication app;
    private Path taskDir;
    private int a2aPort;

    @AfterEach
    void tearDown() {
        if (app != null) {
            try {
                app.shutdown();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }

    private EventMeshApplication boot(boolean withToken) throws Exception {
        taskDir = Files.createTempDirectory("a2a-wiring-tasks");
        // port 0 = auto-select: three test methods boot/teardown sequentially, a fixed port
        // would race the previous teardown's socket release (no SO_REUSEADDR on Windows).
        EventMeshApplication application = new EventMeshApplication(
            new NoopStorage(), new InMemoryOffsetStore(), 0, 0);
        application.withA2aGateway(0,
            new RocksDBTaskStore(taskDir.toString()), withToken ? "secret-1" : null);
        application.start();
        a2aPort = application.a2aGatewayPort();
        app = application;
        return application;
    }

    @Test
    void gatewayBootsWithMainProcessAndAnswersHealth() throws Exception {
        boot(false);
        HttpURLConnection conn = (HttpURLConnection) new URL(
            "http://127.0.0.1:" + a2aPort + "/a2a/health").openConnection();
        assertEquals(200, conn.getResponseCode());
    }

    @Test
    void tokenRejectsMissingAndWrongBearer() throws Exception {
        boot(true);
        // "Connection: close" per request: the Netty pipeline in this test has no keep-alive
        // handler, so a reused connection can surface as EOF on the follow-up request.
        assertStatusWithRetry(401, "/a2a/health", null);
        assertStatusWithRetry(401, "/a2a/tasks/none", "Bearer wrong");
        assertStatusWithRetry(200, "/a2a/health", "Bearer secret-1");
    }

    private void assertStatusWithRetry(int expected, String path, String auth) throws Exception {
        IOException last = null;
        for (int i = 0; i < 3; i++) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(
                    "http://127.0.0.1:" + a2aPort + path).openConnection();
                conn.setRequestProperty("Connection", "close");
                if (auth != null) {
                    conn.setRequestProperty("Authorization", auth);
                }
                assertEquals(expected, conn.getResponseCode());
                // 4xx/5xx make getInputStream() throw; the status assert above already passed.
                try {
                    conn.getInputStream().close();
                } catch (IOException ok) {
                    // expected for non-2xx statuses
                }
                return;
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    @Test
    void submitPersistsContextIdInTaskStore() throws Exception {
        boot(false);
        // no agents registered => submit is rejected with a failed future surfaced as 400; the
        // task is never created. Register an agent card first via the registry.
        A2AGatewayService svc = app.a2aGatewayService();
        svc.getAgentCardRegistry().registerCard(
            org.apache.eventmesh.protocol.a2a.AgentIdentity.builder()
                .orgId("default").unitId("default").agentId("agent-wiring").build(),
            buildCard("agent-wiring"));

        String body = "{\"targetAgent\":\"agent-wiring\",\"message\":\"hi\","
            + "\"contextId\":\"conv-100\",\"sync\":false}";
        HttpURLConnection conn = (HttpURLConnection) new URL(
            "http://127.0.0.1:" + a2aPort + "/a2a/tasks").openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(202, conn.getResponseCode());
        byte[] resp = conn.getInputStream().readAllBytes();
        JsonNode node = new ObjectMapper().readTree(resp);
        String taskId = node.get("taskId").asText();

        // the durable store carries the contextId
        assertNotNull(svc.getTaskStore().getTask(taskId));
        assertEquals("conv-100", svc.getTaskStore().getTask(taskId).contextId);

        // and the snapshot echoes it
        assertEquals("conv-100", svc.getTaskStatus(taskId).getRecord().contextId);
    }

    private static org.apache.eventmesh.protocol.a2a.model.AgentCard buildCard(String name) {
        return org.apache.eventmesh.protocol.a2a.model.AgentCard.builder()
            .name(name)
            .description("wiring test agent")
            .version("1.0.0")
            .supportedInterfaces(List.of(org.apache.eventmesh.protocol.a2a.model.AgentInterface
                .builder().url("http://127.0.0.1:0/a2a")
                .protocolBinding("JSONRPC").protocolVersion("0.3").build()))
            .capabilities(org.apache.eventmesh.protocol.a2a.model.AgentCapabilities.builder()
                .streaming(false).pushNotifications(false).build())
            .skills(Collections.emptyList())
            .defaultInputModes(List.of("text/plain"))
            .defaultOutputModes(List.of("text/plain"))
            .build();
    }

    /** Minimal no-op storage so the runtime boots without a broker. */
    static final class NoopStorage implements MeshStoragePlugin {

        @Override
        public void init(Properties props) {
            // no-op
        }

        @Override
        public void send(String topic, org.apache.eventmesh.common.wire.EventMeshFrame frame,
                org.apache.eventmesh.api.SendCallback callback) {
            // no-op
        }

        @Override
        public List<org.apache.eventmesh.common.wire.EventMeshFrame> poll(String topic, int partition,
                long startOffset, int maxEvents, long timeoutMs) {
            return Collections.emptyList();
        }

        @Override
        public void assignPartitions(String topic, List<Integer> partitions) {
            // no-op
        }

        @Override
        public void commitOffset(String topic, int partition, long offset) {
            // no-op
        }

        @Override
        public int partitionCount(String topic) {
            return 1;
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void start() {
            // no-op
        }

        @Override
        public void shutdown() {
            // no-op
        }
    }
}
