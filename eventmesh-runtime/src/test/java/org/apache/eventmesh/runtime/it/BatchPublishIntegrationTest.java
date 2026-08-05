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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.api.storage.OffsetExtensions;
import org.apache.eventmesh.client.cloudevents.CloudEventsClient;
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
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * In-process batch publish E2E: publish 10 events via {@code publishBatch}, verify all received via
 * long-poll subscribe. Uses an in-memory storage stub (no broker).
 */
class BatchPublishIntegrationTest {

    private static final String TOPIC = "batch-it-" + System.nanoTime();

    private UniIngressService ingress;
    private UniHttpServer http;
    private ScheduledExecutorService driver;
    private CloudEventsClient client;

    @BeforeEach
    void boot() throws Exception {
        ingress = new UniIngressService(new InMemoryStorage(), new InMemoryOffsetStore());
        UniAdminService admin = new UniAdminService(ingress);
        http = new UniHttpServer(ingress, admin);
        int port = http.start(0);
        driver = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "batch-it-driver");
            t.setDaemon(true);
            return t;
        });
        driver.scheduleAtFixedRate(() -> {
            try {
                ingress.pullAndDispatch(TOPIC, 100, 0L);
            } catch (Exception expected) {
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        client = CloudEventsClient.builder()
            .runtimeUrl("http://localhost:" + port).clientId("batch-it").pollIntervalMs(200L).build();
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
    void batchPublishAllDelivered() throws Exception {
        List<String> received = new CopyOnWriteArrayList<>();
        client.subscribe(TOPIC, "BROADCAST", e -> received.add(e.getId()));
        Thread.sleep(500L);

        List<CloudEvent> batch = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            batch.add(CloudEventsClient.event("b" + i, "src", "batch.type",
                ("m" + i).getBytes(StandardCharsets.UTF_8)));
        }
        assertTrue(client.publish(TOPIC, batch), "batch publish should return 202");

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (received.size() < 10 && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertEquals(10, received.size(), "all 10 batch events should be delivered");
    }

    static final class InMemoryStorage implements MeshStoragePlugin {

        final ConcurrentHashMap<String, Queue<CloudEvent>> queues = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, AtomicLong> offsetSeq = new ConcurrentHashMap<>();

        @Override
        public void init(java.util.Properties p) {
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
