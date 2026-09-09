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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.runtime.ingress.UniIngressService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Issue #5364 acceptance (plan #5354 Phase 3): the admin plane is token-guarded and
 * fail-closed by default. Missing token -> 401, wrong token -> 401, no token configured
 * at all -> 503 admin_locked (only /admin/health stays open for liveness probes).
 */
class AdminTokenGuardTest {

    private UniAdminServer server;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private int port;

    private void start(UniIngressService ingress, String token) throws Exception {
        UniAdminService svc = new UniAdminService(ingress);
        server = new UniAdminServer(svc);
        if (token != null) {
            server.withAdminToken(token);
        }
        port = server.start(0);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (bearer != null) {
            b.header("Authorization", "Bearer " + bearer);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void tokenConfiguredMatrix() throws Exception {
        start(UniIngressTestSupport.ingress(), "secret-1");
        assertEquals(401, get("/admin/metrics", null).statusCode(),
            "missing token -> 401");
        assertEquals(401, get("/admin/metrics", "wrong").statusCode(),
            "wrong token -> 401");
        assertEquals(200, get("/admin/metrics", "secret-1").statusCode(),
            "correct token -> 200");
        assertEquals(200, get("/admin/health", null).statusCode(),
            "health is exempt (liveness probe)");
        assertEquals(401, get("/metrics", null).statusCode(),
            "prometheus scrape is guarded too");
    }

    @Test
    void failClosedWhenNoTokenConfigured() throws Exception {
        start(UniIngressTestSupport.ingress(), null);
        assertEquals(503, get("/admin/metrics", null).statusCode(),
            "no token configured -> 503 admin_locked (fail-closed)");
        assertEquals(503, get("/admin/subscriptions", "anything").statusCode(),
            "even a presented token cannot unlock the fail-closed plane");
        assertTrue(get("/admin/metrics", null).body().contains("admin_locked"),
            "the 503 body explains the remediation");
        assertEquals(200, get("/admin/health", null).statusCode(),
            "health stays reachable for liveness");
    }

    /** Minimal ingress with a null storage (mirrors PrometheusEndpointTest.NullStorage). */
    static final class UniIngressTestSupport {

        static UniIngressService ingress() {
            return new UniIngressService(new NullStorage(),
                new org.apache.eventmesh.runtime.offset.InMemoryOffsetStore());
        }
    }

    private static final class NullStorage implements org.apache.eventmesh.api.storage.MeshStoragePlugin {

        @Override
        public void init(java.util.Properties props) {
            // no-op
        }

        @Override
        public void send(String topic, org.apache.eventmesh.common.wire.EventMeshFrame frame,
            org.apache.eventmesh.api.SendCallback callback) {
            // no-op
        }

        @Override
        public java.util.List<org.apache.eventmesh.common.wire.EventMeshFrame> poll(
            String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            return java.util.Collections.emptyList();
        }

        @Override
        public void assignPartitions(String topic, java.util.List<Integer> partitions) {
            // no-op
        }

        @Override
        public void commitOffset(String topic, int partition, long offset) {
            // no-op
        }

        @Override
        public int partitionCount(String topic) {
            return 0;
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
