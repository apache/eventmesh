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

import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.client.cloudevents.CloudEventsClient;
import org.apache.eventmesh.runtime.boot.EventMeshApplication;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.cloudevents.CloudEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Client-driven integration test over the real runtime + a real broker. Boots the full
 * {@link EventMeshApplication} (traffic HTTP + admin) and drives it through the
 * {@link CloudEventsClient} SDK — the same path production clients take — exercising both
 * publish/subscribe (long-poll) and request-reply.
 *
 * <p><b>Gated by {@code -Dit.storage}</b> (matches {@code rocketmq|kafka}). Run against RocketMQ:</p>
 * <pre>
 *   gradle :eventmesh-runtime:test --tests "org.apache.eventmesh.runtime.it.ClientBrokerIntegrationTest" \
 *     -Dit.storage=rocketmq -Dit.namesrv=host:9876
 * </pre>
 *
 * <p>One consumer (responder) subscribes before any publish, then a 25s settle lets the RocketMQ
 * {@code DefaultLitePullConsumer} rebalance (CONSUME_FROM_LAST_OFFSET otherwise skips a message
 * published before the consumer owns its queues). Then: publish → handler receives (pub/sub);
 * a second client {@code request()}s → responder reads {@code emcorrelationid} and {@code reply()}s
 * → requester receives the reply (request-reply).</p>
 */
@EnabledIfSystemProperty(named = "it.storage", matches = "rocketmq|kafka")
class ClientBrokerIntegrationTest {

    private EventMeshApplication app;
    private int port;
    private MeshStoragePlugin storage;
    private CloudEventsClient responder;
    private CloudEventsClient requester;

    @BeforeEach
    void boot() throws Exception {
        String storageType = System.getProperty("it.storage", "kafka");
        String topic = System.getProperty("it.topic", "em-it-client");
        String namesrv = System.getProperty("it.namesrv", "localhost:9092");

        storage = EventMeshExtensionFactory.getExtension(MeshStoragePlugin.class, storageType);
        if (storage == null) {
            throw new IllegalStateException("no MeshStoragePlugin registered for '" + storageType + "'");
        }
        Properties props = new Properties();
        props.setProperty("namesrvAddr", namesrv);
        props.setProperty("eventMesh.server.kafka.namesrvAddr", namesrv);
        props.setProperty("eventMesh.server.rocketmq.namesrvAddr", namesrv);

        // Initialize storage + create topic BEFORE app.start() (init is idempotent). This avoids
        // the pull loop starting before the topic route is available, and avoids DefaultMQAdminExt hang.
        storage.init(props);
        if (storage instanceof org.apache.eventmesh.storage.rocketmq.storage.RocketMQRemotingStoragePlugin) {
            ((org.apache.eventmesh.storage.rocketmq.storage.RocketMQRemotingStoragePlugin) storage).createTopic(topic, 4);
            for (int w = 0; w < 30; w++) {
                if (((org.apache.eventmesh.storage.rocketmq.storage.RocketMQRemotingStoragePlugin) storage).partitionCount(topic) > 0) break;
                Thread.sleep(1000);
            }
        } else {
            ensureTopic(namesrv, topic, storageType);
        }

        app = new EventMeshApplication(storage, new InMemoryOffsetStore(), 0, 0);
        app.runtime().withStorageConfig(props);
        app.start();
        port = app.trafficPort();
    }

    @AfterEach
    void tearDown() {
        if (responder != null) {
            responder.shutdown();
        }
        if (requester != null) {
            requester.shutdown();
        }
        if (app != null) {
            app.shutdown();
        }
    }

    @Test
    void publishSubscribeAndRequestReply() throws Exception {
        String topic = System.getProperty("it.topic", "em-it-client");
        String url = "http://localhost:" + port;

        responder = CloudEventsClient.builder().runtimeUrl(url).clientId("responder").pollIntervalMs(500L).build();
        requester = CloudEventsClient.builder().runtimeUrl(url).clientId("requester").build();

        List<CloudEvent> received = new CopyOnWriteArrayList<>();
        AtomicReference<CloudEvent> replySent = new AtomicReference<>();

        // Responder: capture every delivered event; when one carries emcorrelationid (a request),
        // reply with a fixed reply event.
        responder.subscribe(topic, "BROADCAST", event -> {
            received.add(event);
            Object corr = event.getExtension("emcorrelationid");
            if (corr != null && !corr.toString().isEmpty()) {
                CloudEvent reply = CloudEventsClient.event("reply-1", "responder", "reply.type",
                    "reply-data".getBytes(StandardCharsets.UTF_8));
                replySent.set(reply);
                responder.reply(corr.toString(), reply);
            }
        });

        // Settle: the RocketMQ consumer lazily subscribes on first poll and needs up to one
        // rebalance cycle (~20s) before it owns queues and can pull.
        Thread.sleep(25_000L);

        // --- publish/subscribe (long-poll) ---
        CloudEvent pub = CloudEventsClient.event("pub-1", "src", "pub.type",
            "hello".getBytes(StandardCharsets.UTF_8));
        assertTrue(responder.publish(topic, pub), "publish should return 202");

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (received.stream().noneMatch(e -> "pub-1".equals(e.getId())) && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }
        assertTrue(received.stream().anyMatch(e -> "pub-1".equals(e.getId())),
            "responder should receive the published event via long-poll");

        // --- request/reply ---
        CloudEvent req = CloudEventsClient.event("req-1", "reqsrc", "req.type",
            "req-data".getBytes(StandardCharsets.UTF_8));
        CloudEvent reply = requester.request(topic, req, 30_000L);
        assertNotNull(reply, "request should be answered before timeout");
        assertEquals("reply-1", reply.getId(), "reply id should match the responder's reply event");
    }

    /**
     * Create {@code topic} on the broker if missing (RocketMQ CODE 17 otherwise). No-op for kafka
     * (auto-creates). Mirrors RealBrokerIntegrationTest's helper.
     */
    private static void ensureTopic(String namesrv, String topic, String storageType) throws Exception {
        if (!"rocketmq".equalsIgnoreCase(storageType)) {
            return;
        }
        org.apache.rocketmq.tools.admin.DefaultMQAdminExt admin =
            new org.apache.rocketmq.tools.admin.DefaultMQAdminExt();
        admin.setNamesrvAddr(namesrv);
        admin.start();
        try {
            org.apache.rocketmq.common.protocol.body.ClusterInfo info = admin.examineBrokerClusterInfo();
            String cluster = info.getClusterAddrTable().keySet().iterator().next();
            admin.createTopic(cluster, topic, 4);
        } catch (org.apache.rocketmq.client.exception.MQClientException e) {
            // already exists — safe to ignore
        } finally {
            admin.shutdown();
        }
    }
}
