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
import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.admin.UniAdminServer;
import org.apache.eventmesh.runtime.admin.UniAdminService;
import org.apache.eventmesh.runtime.http.UniHttpServer;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.push.BufferedEvent;
import org.apache.eventmesh.runtime.subscription.DistributionMode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
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
 * Integration test of the dead-letter + replay loop (§13.3.6 / §13.5.4) over real HTTP. A published
 * event is delivered, explicitly nacked past {@code maxAttempts}, routed to {@code <topic>_DLQ},
 * then replayed via {@code POST /admin/dlq/replay} back onto the original topic. Uses an in-memory
 * storage stub (no broker) and a controllable clock so the retry state machine advances without
 * wall-clock sleeps.
 */
class DlqIntegrationTest {

    private static final String TOPIC = "orders";
    private static final String DLQ_TOPIC = TOPIC + "_DLQ";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private InMemoryStorage storage;
    private UniIngressService ingress;
    private UniHttpServer httpServer;
    private UniAdminServer adminServer;
    private int adminPort;
    private final AtomicLong clock = new AtomicLong(1_000_000L);

    @AfterEach
    void tearDown() {
        if (adminServer != null) {
            adminServer.stop();
        }
        if (httpServer != null) {
            httpServer.stop();
        }
        // ReliableDispatcher has no resources of its own (UniRuntime drives tick()); nothing to close.
    }

    @Test
    void nackExhaustsRetriesIntoDlqThenAdminReplay() throws Exception {
        boot(2); // 2 attempts → DLQ on the 2nd expiry

        // 1. subscribe + publish + dispatch (deliver to the client buffer).
        ingress.subscribe(TOPIC, "c1", DistributionMode.BROADCAST, null);
        CloudEvent event = CloudEventBuilder.v1()
            .withId("e1").withSource(URI.create("it")).withType("it.event").build();
        ingress.publish(TOPIC, event).get(5, TimeUnit.SECONDS);
        int dispatched = ingress.pullAndDispatch(TOPIC, 100, 0L);
        assertEquals(1, dispatched, "pullAndDispatch should pull & dispatch the event");

        // 2. the client polls the delivered event and gets its deliveryId.
        List<BufferedEvent> polled = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (polled.isEmpty() && System.nanoTime() < deadline) {
            polled.addAll(ingress.poll("c1", 100, 100L));
        }
        assertEquals(1, polled.size(), "event should be delivered to the client buffer");

        // 3. Leave the delivery UNacked. Each tick() that finds nextAttemptAtMs elapsed either
        // redelivers (bump attempt, reschedule at now+ackTimeout) or, once attempt >= maxAttempts,
        // routes the event to the DLQ sink (<topic>_DLQ in storage). Advance the clock past the
        // ACK window between ticks so the delivery expires each cycle.
        clock.addAndGet(2_000L); // past ackTimeoutMs (1s)
        // Tick until the DLQ metric increments (non-destructive — don't poll storage here, that
        // drains the DLQ queue before the assertion can read it).
        long dlqDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (ingress.getMetrics().getDlqCount() < 1 && System.nanoTime() < dlqDeadline) {
            ingress.getDispatcher().tick();
            clock.addAndGet(2_000L); // advance past the next ACK window
            Thread.sleep(10);
        }
        List<CloudEvent> dlqed = storage.poll(DLQ_TOPIC, -1, -1, 100, 0).stream()
            .map(EventMeshFrame::toCloudEvent).collect(java.util.stream.Collectors.toList());
        assertEquals(1, dlqed.size(), "event should land in <topic>_DLQ after maxAttempts");
        assertEquals("e1", dlqed.get(0).getId());
        assertTrue(ingress.getMetrics().getDlqCount() >= 1, "dlqCount metric should increment");

        // 4. admin replay: POST /admin/dlq/replay re-publishes DLQ events to the original topic.
        int status = HTTP.send(HttpRequest.newBuilder(
            URI.create("http://localhost:" + adminPort + "/admin/dlq/replay?topic=" + TOPIC + "&max=10"))
            .POST(HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString()).statusCode();
        assertEquals(200, status);

        // 5. the replayed event is back on the original topic; dispatch + poll it again.
        ingress.pullAndDispatch(TOPIC, 100, 0L);
        List<BufferedEvent> replayed = new ArrayList<>();
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (replayed.isEmpty() && System.nanoTime() < deadline) {
            replayed.addAll(ingress.poll("c1", 100, 100L));
        }
        assertEquals(1, replayed.size(), "replayed event should be redelivered to the client");
        assertEquals("e1", replayed.get(0).getEvent().attributes().get("id"));
    }

    private void boot(int maxAttempts) throws Exception {
        storage = new InMemoryStorage();
        // Test-friendly ingress: inject the clock so tick() advances without wall-clock waits.
        ingress = new UniIngressService(storage, new InMemoryOffsetStore(),
            new org.apache.eventmesh.runtime.subscription.SubscriptionManager(),
            new org.apache.eventmesh.runtime.push.PushService(),
            1_000L, maxAttempts, clock::get);
        UniAdminService admin = new UniAdminService(ingress);
        httpServer = new UniHttpServer(ingress, admin);
        httpServer.start(0);
        adminServer = new UniAdminServer(admin);
        adminPort = adminServer.start(0);
    }

    // ---- in-memory storage (records DLQ writes + serves replay poll) ----

    static final class InMemoryStorage implements MeshStoragePlugin {

        private final ConcurrentHashMap<String, Queue<CloudEvent>> queues = new ConcurrentHashMap<>();

        @Override
        public void init(Properties p) {
            // no-op
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
