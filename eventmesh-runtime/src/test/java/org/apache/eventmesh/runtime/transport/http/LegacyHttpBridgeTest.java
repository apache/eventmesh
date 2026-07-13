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

package org.apache.eventmesh.runtime.transport.http;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.runtime.boot.UniRuntime;
import org.apache.eventmesh.runtime.delivery.CloudEventSerializer;
import org.apache.eventmesh.runtime.delivery.HttpCaller;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.subscription.DistributionMode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Legacy EventMesh HTTP clients ({@code EventMeshHttpClient}: webhook-push subscribe + publish)
 * driving the new core through {@link LegacyHttpBridge}. No client-side change.
 */
class LegacyHttpBridgeTest {

    private static final String WEBHOOK_URL = "http://client.example/hook";

    private UniRuntime runtime;
    private CapturingHttpCaller httpCaller;

    @AfterEach
    void tearDown() {
        if (runtime != null) {
            runtime.shutdown();
        }
    }

    @Test
    void legacySubscribeThenPublishDeliversViaWebhook() throws Exception {
        boot();
        CloudEvent event = event("e-1");

        // Stub codec: parses any "body" into the fixed legacy requests the test wants.
        LegacyHttpCodec codec = new LegacyHttpCodec() {
            @Override
            public LegacyPublishRequest parsePublish(byte[] body) {
                return new LegacyPublishRequest("orders", event);
            }

            @Override
            public LegacySubscribeRequest parseSubscribe(byte[] body) {
                return new LegacySubscribeRequest("c1", WEBHOOK_URL, "k", Arrays.asList("orders"),
                    DistributionMode.BROADCAST);
            }
        };
        LegacyHttpBridge bridge = new LegacyHttpBridge(runtime.ingress(), codec, httpCaller,
            event1 -> event1.getId().getBytes(StandardCharsets.UTF_8), "default-secret");

        // 1. legacy subscribe (registers webhook URL as the push channel) BEFORE publish.
        bridge.subscribe(new byte[0]);
        // 2. legacy publish → core persists.
        bridge.publish(new byte[0]).get();

        // 3. background pull-loop dispatches → WebHookChannel POSTs to the client URL.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (httpCaller.posts.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(1, httpCaller.posts.size(), "EventMesh pushed the event to the legacy webhook URL");
        assertEquals(WEBHOOK_URL, httpCaller.posts.get(0).url);
        assertEquals("e-1", new String(httpCaller.posts.get(0).body, StandardCharsets.UTF_8));

        // 4. the webhook returned 2xx → auto-ACK → offset advanced (at-least-once over legacy HTTP).
        assertTrue(runtime.ingress().getOffsetStore().readOffset("orders", "c1", -1) >= 1,
            "offset advanced after webhook delivery accepted");
    }

    @Test
    void legacyUnsubscribeDropsSubscriptions() {
        boot();
        LegacyHttpCodec codec = new LegacyHttpCodec() {
            @Override
            public LegacyPublishRequest parsePublish(byte[] body) {
                return null;
            }

            @Override
            public LegacySubscribeRequest parseSubscribe(byte[] body) {
                return new LegacySubscribeRequest("c1", WEBHOOK_URL, "k", Arrays.asList("orders"),
                    DistributionMode.BROADCAST);
            }
        };
        LegacyHttpBridge bridge = new LegacyHttpBridge(runtime.ingress(), codec, httpCaller,
            event1 -> new byte[0], "default-secret");

        bridge.subscribe(new byte[0]);
        assertEquals(1, runtime.ingress().getSubscriptionManager().activeSubscriptions("orders").size());
        assertEquals(1, bridge.unsubscribe(new byte[0]));
        assertEquals(0, runtime.ingress().getSubscriptionManager().activeSubscriptions("orders").size());
    }

    private void boot() {
        httpCaller = new CapturingHttpCaller(200);
        runtime = new UniRuntime(new InMemoryStorage(), new InMemoryOffsetStore(), 50L, 200L, 100, 500L);
        try {
            runtime.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static CloudEvent event(String id) {
        return CloudEventBuilder.v1().withId(id).withSource(URI.create("svc")).withType("order.created").build();
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
