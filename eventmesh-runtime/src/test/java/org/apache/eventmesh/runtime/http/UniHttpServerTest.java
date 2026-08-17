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
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
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
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Drives the uni runtime over real localhost HTTP — publish → subscribe → poll → ack — with
 * no external broker (uses an in-memory MeshStoragePlugin). This is the end-to-end vertical the
 * unit tests prove piecewise, now exercised through the actual {@link UniHttpServer}.
 */
class UniHttpServerTest {

    private final ObjectMapper mapper = new ObjectMapper();
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

    @Test
    void publishSubscribePollAckOverHttp() throws Exception {
        boot();

        // subscribe
        ObjectNode subReq = mapper.createObjectNode();
        subReq.put("clientId", "c1");
        subReq.put("topic", "orders");
        subReq.put("mode", "BROADCAST");
        JsonNode subResp = post("/events/subscribe", mapper.writeValueAsBytes(subReq));
        assertTrue(subResp.has("subscriptionId"), "subscribe returns a subscriptionId");

        // publish a structured CloudEvent
        CloudEvent event = CloudEventBuilder.v1()
            .withId("o-1").withSource(URI.create("svc")).withType("order.created").build();
        byte[] eventJson = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE).serialize(event);
        JsonNode pubResp = post("/events/publish?topic=orders", eventJson);
        assertEquals("accepted", pubResp.get("status").asText());

        // poll (the background pull-loop dispatched the event into the client buffer)
        JsonNode polled = readJson(get("/events/poll?clientId=c1&max=10&timeoutMs=2000"));
        assertEquals(1, polled.size(), "one event delivered");
        String deliveryId = polled.get(0).get("deliveryId").asText();
        assertEquals("o-1", polled.get(0).get("event").get("id").asText());

        // ack → offset advances
        ObjectNode ackReq = mapper.createObjectNode();
        ackReq.put("deliveryId", deliveryId);
        assertEquals("acked", post("/events/ack", mapper.writeValueAsBytes(ackReq)).get("status").asText());

        // admin endpoints live on the separate UniAdminServer (not on this traffic server);
        // they're covered by UniAdminServerTest.
    }

    private void boot() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        runtime = new UniRuntime(storage, new InMemoryOffsetStore(), 50L, 200L, 100, 500L);
        runtime.start();
        UniAdminService admin = new UniAdminService(runtime.ingress());
        server = new UniHttpServer(runtime.ingress(), admin);
        port = server.start(0);
    }

    // ---- tiny HttpURLConnection HTTP client ----

    private JsonNode post(String path, byte[] body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + path).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }
        return readJson(conn);
    }

    private byte[] get(String path) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + path).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        try (InputStream is = conn.getInputStream()) {
            return is.readAllBytes();
        }
    }

    private JsonNode readJson(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        try (InputStream is = code < 400 ? conn.getInputStream() : conn.getErrorStream()) {
            return readJson(is.readAllBytes());
        }
    }

    private JsonNode readJson(byte[] bytes) throws IOException {
        return mapper.readTree(bytes);
    }

    /** Minimal in-memory MeshStoragePlugin shared with the other integration scaffolds. */
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
        public List<EventMeshFrame> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
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
