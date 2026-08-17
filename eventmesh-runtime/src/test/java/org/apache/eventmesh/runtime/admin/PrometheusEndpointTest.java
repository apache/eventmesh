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

package org.apache.eventmesh.runtime.admin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the Prometheus scrape endpoint {@code GET /metrics} on the admin port: text exposition
 * format with counter/gauge types matching the alert rules in production-readiness §9.3.
 */
class PrometheusEndpointTest {

    private UniAdminServer adminServer;
    private int port;

    /** Minimal in-memory storage for the admin-only test. */
    private static final class NullStorage implements MeshStoragePlugin {

        @Override
        public void init(Properties p) {
        }

        @Override
        public void send(String t, EventMeshFrame f, SendCallback cb) {
            cb.onSuccess(null);
        }

        @Override
        public List<EventMeshFrame> poll(String t, int p, long o, int m, long to) {
            return List.of();
        }

        @Override
        public void assignPartitions(String t, List<Integer> p) {
        }

        @Override
        public void commitOffset(String t, int p, long o) {
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
        }

        @Override
        public void shutdown() {
        }
    }

    @BeforeEach
    void boot() throws Exception {
        UniIngressService ingress = new UniIngressService(new NullStorage(), new InMemoryOffsetStore());
        adminServer = new UniAdminServer(new UniAdminService(ingress));
        port = adminServer.start(0);
    }

    @AfterEach
    void tearDown() {
        if (adminServer != null) {
            adminServer.stop();
        }
    }

    @Test
    void prometheusScrapeContainsCounters() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + "/metrics").openConnection();
        try {
            int status = conn.getResponseCode();
            assertTrue(status == 200, "GET /metrics should return 200, got " + status);
            String contentType = conn.getContentType();
            assertTrue(contentType != null && contentType.contains("text/plain"),
                "Content-Type should be text/plain, got " + contentType);

            String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("# TYPE eventmesh_publish_count counter"),
                "should contain publish counter TYPE declaration:\n" + body);
            assertTrue(body.contains("eventmesh_publish_count "),
                "should contain publish counter value");
            assertTrue(body.contains("# TYPE eventmesh_pending_deliveries gauge"),
                "should contain pending gauge TYPE declaration:\n" + body);
            assertTrue(body.contains("eventmesh_dlq_count "),
                "should contain dlq counter");
        } finally {
            conn.disconnect();
        }
    }
}
