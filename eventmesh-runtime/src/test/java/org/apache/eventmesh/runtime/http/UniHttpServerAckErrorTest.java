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

package org.apache.eventmesh.runtime.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.admin.UniAdminService;
import org.apache.eventmesh.runtime.boot.UniRuntime;
import org.apache.eventmesh.runtime.cluster.ClusterMembership;
import org.apache.eventmesh.runtime.cluster.DeliveryTopology;
import org.apache.eventmesh.runtime.cluster.FencingToken;
import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;
import org.apache.eventmesh.runtime.cluster.PartitionOwnership;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;

/**
 * Issue #5432: the HTTP ACK path must always answer with a response.
 *
 * <p>Before, {@code UniHttpServer#ack} called into the reliability layer without catching
 * {@code StaleOwnerException}. When the ownership guard rejected the ACK, the exception escaped
 * the HTTP handler, so the client observed a closed connection without any response — and the
 * retry was answered with "unknown deliveryId" (404), because the ACK callback had already been
 * removed from the push service before the failure. The subscriber offset never advanced.
 *
 * <p>These tests drive the real HTTP server over localhost: publish → subscribe → poll → ack.
 */
class UniHttpServerAckErrorTest {

    private UniRuntime runtime;
    private UniHttpServer server;
    private int port;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (runtime != null) {
            runtime.shutdown();
        }
    }

    private void boot() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        runtime = new UniRuntime(storage, new InMemoryOffsetStore(), 20L, 50L, 100, 50L,
            DeliveryTopology.LOCAL_STICKY_PULL, "self", "self:10105");

        // Cluster mode: install the ownership view so the dispatcher gets an ownership guard.
        // The ownership state machine is deliberately NOT started (LOCAL_STICKY_PULL never
        // starts it), so this instance holds no partition assignment — every ack on a real
        // partition is rejected by the guard, which is exactly the fenced case of #5432.
        InMemoryMetaStore meta = new InMemoryMetaStore();
        FencingToken token = new FencingToken();
        ClusterMembership membership = new ClusterMembership(meta, "self", "self:10105",
            15_000L, System::currentTimeMillis, token);
        PartitionOwnership ownership = new PartitionOwnership(
            membership, meta, storage, "self", 5_000L, System::currentTimeMillis, token);
        runtime.ingress().withPartitionOwnership(ownership);

        runtime.start();
        UniAdminService admin = new UniAdminService(runtime.ingress());
        server = new UniHttpServer(runtime.ingress(), admin);
        port = server.start(0);
    }

    /**
     * The ACK must produce a well-formed HTTP response. The fenced delivery may be reported as
     * rejected, but the connection must not simply drop — the client needs a status code to
     * decide whether to retry.
     */
    @Test
    void ackOfFencedDeliveryReturnsAnHttpResponse() throws Exception {
        boot();

        subscribe("c1", "orders");
        publish("orders", "o-1");

        String deliveryId = pollOne("c1");
        assertTrue(deliveryId != null && !deliveryId.isEmpty(), "the subscriber received a delivery");

        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + "/events/ack")
            .openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = conn.getOutputStream()) {
            out.write(("{\"deliveryId\":\"" + deliveryId + "\"}").getBytes(StandardCharsets.UTF_8));
        }

        // Reading the response code must not blow up with a closed connection.
        int status = conn.getResponseCode();
        assertTrue(status >= 200 && status < 600,
            "the ACK must be answered with an HTTP status, got " + status);
    }

    /**
     * A delivery that was never dispatched must still be answered with a plain 404 — the
     * endpoint's normal "unknown delivery" contract, unchanged by the error handling.
     */
    @Test
    void ackOfUnknownDeliveryReturns404() throws Exception {
        boot();

        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + "/events/ack")
            .openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = conn.getOutputStream()) {
            out.write("{\"deliveryId\":\"d-does-not-exist\"}".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(404, conn.getResponseCode());
    }

    private void subscribe(String clientId, String topic) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + "/events/subscribe")
            .openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = conn.getOutputStream()) {
            out.write(("{\"clientId\":\"" + clientId + "\",\"topic\":\"" + topic + "\",\"mode\":\"BROADCAST\"}")
                .getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(200, conn.getResponseCode());
    }

    private void publish(String topic, String id) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(
            "http://localhost:" + port + "/events/publish?topic=" + topic).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/cloudevents+json");
        try (OutputStream out = conn.getOutputStream()) {
            out.write(("{\"specversion\":\"1.0\",\"id\":\"" + id + "\",\"source\":\"svc\",\"type\":\"t\"}")
                .getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(202, conn.getResponseCode());
    }

    /** Poll the subscriber until a delivery shows up (or give up). */
    private String pollOne(String clientId) throws Exception {
        for (int i = 0; i < 50; i++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(
                "http://localhost:" + port + "/events/poll?clientId=" + clientId + "&max=10&timeoutMs=500")
                .openConnection();
            conn.setRequestMethod("GET");
            String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains("deliveryId")) {
                int start = body.indexOf("\"deliveryId\":\"") + "\"deliveryId\":\"".length();
                int end = body.indexOf('"', start);
                return body.substring(start, end);
            }
            Thread.sleep(100);
        }
        return null;
    }

    /** Minimal in-memory MeshStoragePlugin: publish enqueues, poll drains. */
    private static final class InMemoryStorage implements MeshStoragePlugin {

        private final ConcurrentHashMap<String, Queue<CloudEvent>> queues = new ConcurrentHashMap<>();

        @Override
        public void init(Properties properties) {
        }

        @Override
        public void send(String topic, EventMeshFrame frame, SendCallback callback) {
            CloudEvent event = frame.toCloudEvent();
            queues.computeIfAbsent(topic, k -> new ConcurrentLinkedQueue<>()).offer(event);
            SendResult r = new SendResult();
            r.setMessageId(event.getId());
            r.setTopic(topic);
            callback.onSuccess(r);
        }

        @Override
        public List<EventMeshFrame> poll(String topic, int partition, long startOffset, int maxEvents,
            long timeoutMs) {
            Queue<CloudEvent> q = queues.get(topic);
            if (q == null) {
                return new ArrayList<>();
            }
            List<EventMeshFrame> out = new ArrayList<>();
            CloudEvent e;
            while (out.size() < maxEvents && (e = q.poll()) != null) {
                out.add(EventMeshFrame.fromCloudEvent(e));
            }
            return out;
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
