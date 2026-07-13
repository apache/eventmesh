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
import org.apache.eventmesh.client.cloudevents.CloudEventsClient;
import org.apache.eventmesh.runtime.admin.UniAdminService;
import org.apache.eventmesh.runtime.http.UniHttpServer;
import org.apache.eventmesh.runtime.http.UniWsServer;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end WebSocket push: subscriber connects via {@code CloudEventsClient.subscribeWs} to the
 * netty {@link UniWsServer} (separate port from the HTTP traffic port), a publisher publishes, and
 * the runtime's pull-dispatch → PushService buffer → WS pump pushes the event to the subscriber.
 *
 * <p>In-process (an in-memory storage stub — no real broker needed), so it runs in the normal test
 * suite. Validates the previously-missing wiring: client {@code wsUrl} → {@link UniWsServer} on its
 * own port.</p>
 */
class WebSocketPushIntegrationTest {

    private static final String TOPIC = "ws-it-" + System.nanoTime();

    private UniIngressService ingress;
    private UniHttpServer http;
    private UniWsServer ws;
    private ScheduledExecutorService driver;
    private CloudEventsClient pubClient;
    private CloudEventsClient subClient;

    @BeforeEach
    void boot() throws Exception {
        ingress = new UniIngressService(new InMemoryStorage(), new InMemoryOffsetStore());
        UniAdminService admin = new UniAdminService(ingress);
        http = new UniHttpServer(ingress, admin);
        int httpPort = http.start(0);
        ws = new UniWsServer(ingress);
        int wsPort = ws.start(0);

        // Pull-dispatch driver (UniRuntime's pullLoop would do this against a real broker): poll the
        // in-memory storage + dispatch into the subscriber's PushService buffer, which the WS pump drains.
        driver = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ws-it-driver");
            t.setDaemon(true);
            return t;
        });
        driver.scheduleAtFixedRate(() -> {
            try {
                ingress.pullAndDispatch(TOPIC, 100, 0L);
            } catch (Exception ignored) {
                // best-effort
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        subClient = CloudEventsClient.builder()
            .runtimeUrl("http://localhost:" + httpPort).wsUrl("http://localhost:" + wsPort).clientId("ws-sub").build();
        pubClient = CloudEventsClient.builder()
            .runtimeUrl("http://localhost:" + httpPort).clientId("ws-pub").build();
    }

    @AfterEach
    void tearDown() {
        if (subClient != null) {
            subClient.shutdown();
        }
        if (pubClient != null) {
            pubClient.shutdown();
        }
        if (driver != null) {
            driver.shutdownNow();
        }
        if (ws != null) {
            ws.stop();
        }
        if (http != null) {
            http.stop();
        }
    }

    @Test
    void subscribeWsReceivesPushedEvent() throws Exception {
        List<String> received = new CopyOnWriteArrayList<>();
        subClient.subscribeWs(TOPIC, "BROADCAST", event -> received.add(event.getId()));
        // Let the WS handshake + subscription register server-side.
        Thread.sleep(1_000L);

        CloudEvent event = CloudEventBuilder.v1()
            .withId("ws-1").withSource(java.net.URI.create("it")).withType("it.event")
            .withDataContentType("text/plain").withData("hello-ws".getBytes(StandardCharsets.UTF_8)).build();
        assertTrue(pubClient.publish(TOPIC, event), "publish should be accepted");

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (received.stream().noneMatch("ws-1"::equals) && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertTrue(received.stream().anyMatch("ws-1"::equals),
            "WS subscriber should receive the pushed event (got " + received + ")");
    }

    /** Minimal in-memory MeshStoragePlugin: send queues; poll drains (each event delivered once). */
    static final class InMemoryStorage implements MeshStoragePlugin {

        private final ConcurrentHashMap<String, Queue<CloudEvent>> queues = new ConcurrentHashMap<>();

        @Override
        public void init(java.util.Properties p) { }

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
        public void assignPartitions(String topic, List<Integer> partitions) { }

        @Override
        public void commitOffset(String topic, int partition, long offset) { }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void start() { }

        @Override
        public void shutdown() { }
    }
}
