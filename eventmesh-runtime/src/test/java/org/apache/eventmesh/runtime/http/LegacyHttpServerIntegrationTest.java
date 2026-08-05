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
import org.apache.eventmesh.api.storage.OffsetExtensions;
import org.apache.eventmesh.runtime.admin.UniAdminService;
import org.apache.eventmesh.runtime.boot.UniRuntime;
import org.apache.eventmesh.runtime.delivery.CloudEventSerializer;
import org.apache.eventmesh.runtime.delivery.HttpCaller;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.transport.http.EventMeshMessageHttpCodec;
import org.apache.eventmesh.runtime.transport.http.LegacyHttpBridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Real-HTTP integration: an old {@code EventMeshHttpClient} posts to {@code /eventmesh/publish} and
 * {@code /eventmesh/subscribe} (legacy {@code EventMeshMessage} JSON), and the new runtime serves it
 * via the {@link LegacyHttpBridge} wired into {@link UniHttpServer}. No client-side change.
 */
class LegacyHttpServerIntegrationTest {

    private static final String WEBHOOK_URL = "http://client.example/hook";

    private UniRuntime runtime;
    private UniHttpServer server;
    private int port;
    private CapturingHttpCaller webhook;

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
    void legacyClientPublishAndWebhookPushOverRealHttp() throws Exception {
        boot();

        // 1. legacy subscribe (webhook-push): client registers its URL + topics.
        String subBody = "{\"consumerGroup\":\"c1\",\"url\":\"" + WEBHOOK_URL + "\",\"topics\":[\"orders\"]}";
        assertEquals(200, postStatus("/eventmesh/subscribe", subBody));

        // 2. legacy publish: a plain EventMeshMessage JSON (what the old SDK posts).
        String pubBody = "{\"topic\":\"orders\",\"bizSeqNo\":\"b1\",\"uniqueId\":\"u1\",\"content\":\"hello-legacy\"}";
        assertEquals(200, postStatus("/eventmesh/publish", pubBody));

        // 3. the new core dispatches → WebHookChannel POSTs the message back to the client URL.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (webhook.posts.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(1, webhook.posts.size(), "EventMesh pushed the legacy message to the client webhook URL");
        assertEquals(WEBHOOK_URL, webhook.posts.get(0).url);
        assertEquals("hello-legacy", new String(webhook.posts.get(0).body, StandardCharsets.UTF_8));

        // webhook returned 2xx → auto-ACK → offset advanced
        assertTrue(runtime.ingress().getOffsetStore().readOffset("orders", "c1", 0) >= 1);
    }

    private void boot() throws Exception {
        webhook = new CapturingHttpCaller(200);
        InMemoryStorage storage = new InMemoryStorage();
        runtime = new UniRuntime(storage, new InMemoryOffsetStore(), 50L, 200L, 100, 500L);
        runtime.start();

        // serializer: webhook body = the event's data (the legacy content). Production re-encodes to
        // EventMeshMessage; here the raw content is enough to assert delivery.
        CloudEventSerializer serializer = event -> {
            if (event.getData() == null) {
                return new byte[0];
            }
            return event.getData().toBytes();
        };
        LegacyHttpBridge bridge = new LegacyHttpBridge(runtime.ingress(), new EventMeshMessageHttpCodec(),
            webhook, serializer, "default-secret");

        UniAdminService admin = new UniAdminService(runtime.ingress());
        server = new UniHttpServer(runtime.ingress(), admin).withLegacyEndpoints(bridge);
        port = server.start(0);
    }

    private int postStatus(String path, String jsonBody) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:" + port + path).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        int status = conn.getResponseCode();
        try (InputStream is = status < 400 ? conn.getInputStream() : conn.getErrorStream()) {
            if (is != null) {
                is.readAllBytes();
            }
        }
        return status;
    }

    private static final class CapturingHttpCaller implements HttpCaller {

        final List<Post> posts = new ArrayList<>();
        final int status;

        CapturingHttpCaller(int status) {
            this.status = status;
        }

        @Override
        public int post(String url, byte[] body, Map<String, String> headers) {
            posts.add(new Post(url, body));
            return status;
        }
    }

    private static final class Post {

        final String url;
        final byte[] body;

        Post(String url, byte[] body) {
            this.url = url;
            this.body = body;
        }
    }

    private static final class InMemoryStorage implements MeshStoragePlugin {

        private final ConcurrentHashMap<String, Queue<CloudEvent>> queues = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, AtomicLong> offsetSeq = new ConcurrentHashMap<>();

        @Override
        public void init(Properties properties) {
        }

        @Override
        public void send(String topic, CloudEvent event, SendCallback callback) {
            queues.computeIfAbsent(topic, k -> new ConcurrentLinkedQueue<>()).offer(event);
            SendResult r = new SendResult();
            r.setMessageId(event.getId());
            r.setTopic(topic);
            callback.onSuccess(r);
        }

        @Override
        public List<CloudEvent> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            Queue<CloudEvent> q = queues.get(topic);
            if (q == null) {
                return new ArrayList<>();
            }
            List<CloudEvent> out = new ArrayList<>();
            CloudEvent e;
            while (out.size() < maxEvents && (e = q.poll()) != null) {
                // Write MQ physical offset and partition to CloudEvent extensions for unified offset tracking
                long offset = offsetSeq.computeIfAbsent(topic, k -> new AtomicLong()).incrementAndGet();
                e = CloudEventBuilder.from(e)
                    .withExtension(OffsetExtensions.EM_MQ_OFFSET, offset)
                    .withExtension(OffsetExtensions.EM_MQ_PARTITION, 0)
                    .build();
                out.add(e);
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
