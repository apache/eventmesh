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

import org.apache.eventmesh.runtime.state.TaskStore;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;

/**
 * Sub-PR D1: smoke-test the A2A Gateway HTTP server boot path. Boots the Netty
 * server on a fixed port, hits the /a2a/health endpoint over loopback,
 * asserts 200 OK, then shuts the server down.
 */
class A2AGatewaySmokeTest {

    private A2AGatewayServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        // Use a high port to avoid collisions with system services.
        port = 18080;
        server = new A2AGatewayServer(port, new NoopTransport(), new StubTaskStore(),
            new InMemoryAgentCardRegistry());
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    void healthEndpointRespondsOk() throws Exception {
        URL url = URI.create("http://127.0.0.1:" + port + "/a2a/health").toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        int code = conn.getResponseCode();
        assertEquals(200, code, "health endpoint must return 200");
        byte[] body = conn.getInputStream().readAllBytes();
        String s = new String(body, StandardCharsets.UTF_8);
        assertNotNull(s);
        assertEquals(true, s.contains("\"status\":\"ok\""));
    }

    /**
     * Stub {@link TaskStore} returning null / empty for every call. Used only to satisfy the
     * gateway's constructor; the smoke test never submits a task, so the stub's behavior is
     * never exercised.
     */
    static final class StubTaskStore implements TaskStore {

        @Override
        public TaskRecord createTask(String taskId, String agentId, String clientId, String input) {
            return null;
        }

        @Override
        public TaskRecord getTask(String taskId) {
            return null;
        }

        @Override
        public boolean updateStatus(String taskId, long expectedTaskEpoch,
                                    TaskStore.Status newStatus, String output) {
            return false;
        }

        @Override
        public java.util.List<TaskRecord> listByAgent(String agentId, TaskStore.Status statusFilter) {
            return Collections.emptyList();
        }

        @Override
        public java.util.List<String> expireStale(long olderThanMs) {
            return Collections.emptyList();
        }

        @Override
        public void flush() {
            // no buffered writes
        }

        @Override
        public void close() {
            // nothing to release
        }
    }

    /**
     * No-op A2AMessageTransport: every publish is dropped, every subscribe returns a constant id.
     */
    static final class NoopTransport implements org.apache.eventmesh.protocol.a2a.A2AMessageTransport {

        @Override
        public void publish(String topic, CloudEvent event) {
            // drop
        }

        @Override
        public String subscribe(String topicPattern,
                                org.apache.eventmesh.protocol.a2a.A2AMessageTransport.MessageCallback callback) {
            return "noop";
        }

        @Override
        public void unsubscribe(String subscriptionId) {
            // no-op
        }
    }
}
