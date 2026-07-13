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

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.runtime.admin.UniAdminServer;
import org.apache.eventmesh.runtime.admin.UniAdminService;
import org.apache.eventmesh.runtime.http.UniHttpServer;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test of the per-topic rate limiter (§6.6 / §13.6.1) over real HTTP. A {@code PUT
 * /admin/ratelimit} installs a token bucket (small burst + near-zero refill); a burst of
 * {@code /events/publish} calls then partially succeeds and partially fails, and
 * {@code GET /admin/metrics} reports the rejected count via {@code rateLimited}.
 *
 * <p>In-memory storage stub — no broker. Rate-limited publishes surface as HTTP 500 (the publish
 * handler's catch-all) carrying a {@code RateLimitedException}; the test asserts the reject count
 * via the metrics endpoint rather than the status code, which is the operationally meaningful signal.</p>
 */
class RateLimitIntegrationTest {

    private static final String TOPIC = "bursty";
    private static final ObjectMapper M = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private UniIngressService ingress;
    private UniHttpServer httpServer;
    private UniAdminServer adminServer;
    private int trafficPort;
    private int adminPort;

    @AfterEach
    void tearDown() {
        if (adminServer != null) {
            adminServer.stop();
        }
        if (httpServer != null) {
            httpServer.stop();
        }
    }

    @Test
    void adminRateLimitThrottlesBurstAndIncrementsMetric() throws Exception {
        boot();
        byte[] event = serialize(CloudEventBuilder());

        // Install a token bucket: capacity 2 (burst of 2), refill 0.1/s (~0 every 100ms).
        int put = HTTP.send(HttpRequest.newBuilder(URI.create(
            "http://localhost:" + adminPort + "/admin/ratelimit"))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(
                "{\"topic\":\"" + TOPIC + "\",\"capacity\":2,\"rate\":0.1}"))
            .build(), HttpResponse.BodyHandlers.ofString()).statusCode();
        assertEquals(200, put, "PUT /admin/ratelimit should succeed");

        // Fire 5 publishes back-to-back. The bucket holds 2 tokens, so ≥3 must be rejected.
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int s = HTTP.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + trafficPort + "/events/publish?topic=" + TOPIC))
                .header("Content-Type", "application/cloudevents+json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(event))
                .build(), HttpResponse.BodyHandlers.ofString()).statusCode();
            statuses.add(s);
        }
        long accepted = statuses.stream().filter(s -> s == 202).count();
        long rejected429 = statuses.stream().filter(s -> s == 429).count();
        assertTrue(accepted <= 2, "at most the burst capacity (2) should be accepted, got " + accepted);
        assertTrue(rejected429 >= 3, "at least 3 of 5 should be rejected with 429, got " + rejected429
            + " (statuses=" + statuses + ")");

        // The metrics endpoint reports the rate-limited count.
        JsonNode metrics = M.readTree(get(adminPort, "/admin/metrics"));
        assertTrue(metrics.get("rateLimited").asLong() >= 3,
            "rateLimited metric should reflect the rejections, got " + metrics.get("rateLimited"));
    }

    private void boot() throws Exception {
        MeshStoragePlugin storage = new InMemoryStorage();
        ingress = new UniIngressService(storage, new InMemoryOffsetStore());
        UniAdminService admin = new UniAdminService(ingress);
        httpServer = new UniHttpServer(ingress, admin);
        trafficPort = httpServer.start(0);
        adminServer = new UniAdminServer(admin);
        adminPort = adminServer.start(0);
    }

    private static String get(int port, String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .GET().build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private static byte[] serialize(CloudEvent event) {
        return EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE).serialize(event);
    }

    private static CloudEvent CloudEventBuilder() {
        return io.cloudevents.core.builder.CloudEventBuilder.v1()
            .withId("e1").withSource(URI.create("it")).withType("it.event").build();
    }

    // ---- in-memory storage ----

    static final class InMemoryStorage implements MeshStoragePlugin {

        private final ConcurrentHashMap<String, Queue<CloudEvent>> queues = new ConcurrentHashMap<>();

        @Override
        public void init(java.util.Properties p) {
            // no-op
        }

        @Override
        public void send(String topic, CloudEvent event, SendCallback cb) {
            queues.computeIfAbsent(topic, k -> new ConcurrentLinkedQueue<>()).offer(event);
            SendResult r = new SendResult();
            r.setMessageId(event.getId());
            r.setTopic(topic);
            cb.onSuccess(r);
        }

        @Override
        public List<CloudEvent> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            return new ArrayList<>();
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
