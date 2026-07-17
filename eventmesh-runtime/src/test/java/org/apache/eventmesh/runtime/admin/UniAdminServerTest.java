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

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.subscription.DistributionMode;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Drives the independent {@link UniAdminServer} over localhost HTTP — admin endpoints live on their
 * own port, separate from the traffic {@code UniHttpServer}.
 */
class UniAdminServerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private UniIngressService ingress;
    private UniAdminServer server;
    private int port;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void adminEndpointsReflectRuntimeState() throws Exception {
        boot();
        ingress.subscribe("orders", "c1", DistributionMode.BROADCAST, null);

        // subscriptions endpoint sees the live subscription
        JsonNode subs = readJson(get("/admin/subscriptions?topic=orders"));
        assertEquals(1, subs.size());
        assertEquals("c1", subs.get(0).get("clientId").asText());

        // clients endpoint
        JsonNode clients = readJson(get("/admin/clients?topic=orders"));
        assertEquals("c1", clients.get(0).get("clientId").asText());

        // health
        JsonNode health = readJson(get("/admin/health"));
        assertEquals("UP", health.get("status").asText());

        // reject the client
        JsonNode rejected = readJson(post("/admin/client/reject?clientId=c1", ""));
        assertEquals(1, rejected.get("removed").asInt());
        assertEquals(0, ingress.getSubscriptionManager().activeSubscriptions("orders").size());
    }

    private void boot() throws Exception {
        ingress = new UniIngressService(new InMemStorage(), new InMemoryOffsetStore());
        server = new UniAdminServer(new UniAdminService(ingress));
        port = server.start(0);
    }

    private byte[] get(String path) throws Exception {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) URI.create("http://localhost:" + port + path).toURL().openConnection();
        conn.setReadTimeout(5000);
        try (java.io.InputStream is = conn.getInputStream()) {
            return is.readAllBytes();
        }
    }

    private byte[] post(String path, String body) throws Exception {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) URI.create("http://localhost:" + port + path).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.getOutputStream().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try (java.io.InputStream is = conn.getInputStream()) {
            return is.readAllBytes();
        }
    }

    private JsonNode readJson(byte[] bytes) throws Exception {
        return mapper.readTree(bytes);
    }

    @SuppressWarnings("unused")
    private static CloudEvent event(String id) {
        return CloudEventBuilder.v1().withId(id).withSource(URI.create("t")).withType("t").build();
    }

    private static final class InMemStorage implements MeshStoragePlugin {

        private final ConcurrentHashMap<String, Queue<CloudEvent>> queue = new ConcurrentHashMap<>();

        @Override
        public void init(Properties properties) {
        }

        @Override
        public void send(String topic, CloudEvent event, SendCallback callback) {
            queue.computeIfAbsent(topic, k -> new ConcurrentLinkedQueue<>()).offer(event);
            SendResult r = new SendResult();
            r.setMessageId(event.getId());
            callback.onSuccess(r);
        }

        @Override
        public List<CloudEvent> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            return new ArrayList<>();
        }

        @Override
        public void assignPartitions(String topic, List<Integer> partitions) {
        }

        @Override
        public void commitOffset(String topic, int partition, long offset) {
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
}

