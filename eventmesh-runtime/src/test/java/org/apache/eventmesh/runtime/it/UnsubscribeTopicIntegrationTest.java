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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.client.cloudevents.CloudEventsClient;
import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.admin.UniAdminService;
import org.apache.eventmesh.runtime.http.UniHttpServer;
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

/**
 * In-process unsubscribe-topic E2E: subscribe to A + B, unsubscribe(A), publish to both,
 * verify only B delivers. Uses in-memory storage (no broker).
 */
class UnsubscribeTopicIntegrationTest {

    private static final String TOPIC_A = "unsub-a-" + System.nanoTime();
    private static final String TOPIC_B = "unsub-b-" + System.nanoTime();

    private UniIngressService ingress;
    private UniHttpServer http;
    private ScheduledExecutorService driver;
    private CloudEventsClient client;
    private InMemoryStorage storage;

    @BeforeEach
    void boot() throws Exception {
        storage = new InMemoryStorage();
        ingress = new UniIngressService(storage, new InMemoryOffsetStore());
        UniAdminService admin = new UniAdminService(ingress);
        http = new UniHttpServer(ingress, admin);
        int port = http.start(0);
        driver = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "unsub-it-driver");
            t.setDaemon(true);
            return t;
        });
        driver.scheduleAtFixedRate(() -> {
            try {
                ingress.pullAndDispatch(TOPIC_A, 100, 0L);
                ingress.pullAndDispatch(TOPIC_B, 100, 0L);
            } catch (Exception expected) {
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        client = CloudEventsClient.builder()
            .runtimeUrl("http://localhost:" + port).clientId("unsub-it").pollIntervalMs(200L).build();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.shutdown();
        }
        if (driver != null) {
            driver.shutdownNow();
        }
        if (http != null) {
            http.stop();
        }
    }

    @Test
    void unsubscribeOneTopicKeepsOtherRunning() throws Exception {
        List<String> receivedA = new CopyOnWriteArrayList<>();
        List<String> receivedB = new CopyOnWriteArrayList<>();
        client.subscribe(TOPIC_A, "BROADCAST", e -> receivedA.add(e.getId()));
        client.subscribe(TOPIC_B, "BROADCAST", e -> receivedB.add(e.getId()));
        Thread.sleep(500L);

        // Unsubscribe topic A only.
        client.unsubscribe(TOPIC_A);
        Thread.sleep(300L);

        // Publish to both.
        client.publish(TOPIC_A, CloudEventsClient.event("a-1", "src", "type", "a".getBytes(StandardCharsets.UTF_8)));
        client.publish(TOPIC_B, CloudEventsClient.event("b-1", "src", "type", "b".getBytes(StandardCharsets.UTF_8)));

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (receivedB.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }

        assertTrue(receivedB.stream().anyMatch("b-1"::equals), "topic B should still deliver after unsubscribing A");
        assertFalse(receivedA.stream().anyMatch("a-1"::equals), "topic A should NOT deliver after unsubscribe");
    }

    static final class InMemoryStorage implements MeshStoragePlugin {

        final ConcurrentHashMap<String, Queue<CloudEvent>> queues = new ConcurrentHashMap<>();

        @Override
        public void init(java.util.Properties p) {
        }

        @Override
        public void send(String topic, EventMeshFrame frame, SendCallback cb) {
            CloudEvent event = frame.toCloudEvent();
            queues.computeIfAbsent(topic, k -> new ConcurrentLinkedQueue<>()).offer(event);
            SendResult r = new SendResult();
            r.setMessageId(event.getId());
            r.setTopic(topic);
            cb.onSuccess(r);
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
