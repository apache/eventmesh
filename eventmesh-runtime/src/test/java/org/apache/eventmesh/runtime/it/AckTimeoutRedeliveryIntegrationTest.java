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

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.client.cloudevents.CloudEventsClient;
import org.apache.eventmesh.runtime.admin.UniAdminService;
import org.apache.eventmesh.runtime.http.UniHttpServer;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * ACK-timeout redelivery failure test against a real broker. A subscriber receives an event but
 * NEVER acks (manual-ack returning false); the {@code ReliableDispatcher}'s ACK timeout must fire
 * and redeliver (at-least-once), and after {@code maxAttempts} the event must reach the DLQ. Uses
 * REAL wall-clock timing (not the controllable clock of {@code DlqIntegrationTest}) + real RocketMQ
 * for the initial poll + the DLQ send.
 *
 * <p><b>Gated by {@code -Dit.storage}</b>. Run:</p>
 * <pre>
 *   gradle :eventmesh-runtime:test --tests "org.apache.eventmesh.runtime.it.AckTimeoutRedeliveryIntegrationTest" \
 *     -Dit.storage=rocketmq -Dit.namesrv=host:9876
 * </pre>
 */
@EnabledIfSystemProperty(named = "it.storage", matches = "rocketmq|kafka")
class AckTimeoutRedeliveryIntegrationTest {

    private static final String TOPIC = "em-it-redeliver-" + System.nanoTime();
    private static final long ACK_TIMEOUT_MS = 3000L;
    private static final int MAX_ATTEMPTS = 3;

    private UniIngressService ingress;
    private UniHttpServer httpServer;
    private ScheduledExecutorService driver;
    private CloudEventsClient subClient;
    private CloudEventsClient pubClient;

    @AfterEach
    void tearDown() throws Exception {
        if (subClient != null) {
            subClient.shutdown();
        }
        if (pubClient != null) {
            pubClient.shutdown();
        }
        if (driver != null) {
            driver.shutdownNow();
        }
        if (httpServer != null) {
            httpServer.stop();
        }
    }

    @Test
    void noAck_triggersRedelivery_thenDlq() throws Exception {
        final String storageType = System.getProperty("it.storage", "rocketmq");
        String namesrv = System.getProperty("it.namesrv", "localhost:9092");

        java.util.Properties props = new java.util.Properties();
        props.setProperty("namesrvAddr", namesrv);
        props.setProperty("eventMesh.server.kafka.namesrvAddr", namesrv);
        props.setProperty("eventMesh.server.rocketmq.namesrvAddr", namesrv);
        ensureTopic(namesrv, TOPIC, storageType);
        ensureTopic(namesrv, TOPIC + "_DLQ", storageType); // DLQ topic must exist (broker autoCreateTopicEnable=false)

        MeshStoragePlugin storage = EventMeshExtensionFactory.getExtension(MeshStoragePlugin.class, storageType);
        storage.init(props); // UniRuntime.start would do this; we boot UniIngressService directly.
        storage.start();
        // Test-friendly ingress: short ACK timeout + low maxAttempts so the test runs in seconds.
        ingress = new UniIngressService(storage, new InMemoryOffsetStore(),
            new org.apache.eventmesh.runtime.subscription.SubscriptionManager(),
            new org.apache.eventmesh.runtime.push.PushService(),
            ACK_TIMEOUT_MS, MAX_ATTEMPTS, System::currentTimeMillis);
        UniAdminService admin = new UniAdminService(ingress);
        httpServer = new UniHttpServer(ingress, admin);
        int port = httpServer.start(0);

        // Drive the pull-loop + dispatcher tick (UniRuntime would do this, but we use a custom
        // ingress for the short timeout).
        driver = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "it-redeliver-driver");
            t.setDaemon(true);
            return t;
        });
        driver.scheduleAtFixedRate(() -> {
            try {
                ingress.pullAndDispatch(TOPIC, 100, 0L);
                ingress.dispatcherTick();
            } catch (Exception ignored) {
                // best-effort
            }
        }, 0, 500, TimeUnit.MILLISECONDS);

        // Subscriber: receive but NEVER ack (subscribeWithAck returns false).
        List<String> receivedIds = new CopyOnWriteArrayList<>();
        subClient = CloudEventsClient.builder()
            .runtimeUrl("http://localhost:" + port).clientId("redeliver-sub")
            .pollIntervalMs(300L).build();
        subClient.subscribeWithAck(TOPIC, "BROADCAST", event -> {
            receivedIds.add(event.getId());
            return false; // never ack -> trigger redelivery
        });

        // Let the consumer lazy-subscribe + rebalance.
        Thread.sleep(25_000L);

        // Publish one event.
        pubClient = CloudEventsClient.builder()
            .runtimeUrl("http://localhost:" + port).clientId("redeliver-pub").build();
        CloudEvent event = CloudEventBuilder.v1()
            .withId("rd-1").withSource(URI.create("it")).withType("it.event")
            .withDataContentType("text/plain").withData("rd".getBytes()).build();
        assertTrue(pubClient.publish(TOPIC, event), "publish should be accepted");

        // Wait for redelivery: the event should be delivered multiple times (at-least-once) as the
        // ACK timeout fires. With maxAttempts=3, expect up to 3 deliveries before DLQ.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (receivedIds.size() < 2 && System.nanoTime() < deadline) {
            Thread.sleep(200);
        }
        assertTrue(receivedIds.size() >= 2,
            "event should be redelivered (at-least-once) after ACK timeout; got " + receivedIds.size()
                + " deliveries: " + receivedIds);

        // After maxAttempts, the event should reach the DLQ (<topic>_DLQ in the storage).
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        List<CloudEvent> dlqed;
        do {
            dlqed = storage.poll(TOPIC + "_DLQ", -1, -1, 100, 0);
            Thread.sleep(500);
        } while ((dlqed == null || dlqed.isEmpty()) && System.nanoTime() < deadline);
        assertTrue(dlqed != null && !dlqed.isEmpty(),
            "event should reach DLQ after maxAttempts=" + MAX_ATTEMPTS);
        assertTrue(dlqed.stream().anyMatch(e -> "rd-1".equals(e.getId())),
            "DLQ should contain the dead-lettered event rd-1");
    }

    private static void ensureTopic(String namesrv, String topic, String storageType) throws Exception {
        // Scope the topic to ONE reachable broker master so sends never route to an unreachable broker.
        BrokerDiscoverer.ensureTopicOnReachableBroker(namesrv, topic, 4);
    }
}

