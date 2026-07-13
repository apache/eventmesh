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
import org.apache.eventmesh.runtime.cluster.NacosMetaStore;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Multi-instance no-duplicate verification against a real broker + real Nacos. Two
 * {@link EventMeshApplication} instances share one RocketMQ + one Nacos; a subscriber on instance A
 * and a publisher (to instance B). Each published event must be delivered to the subscriber
 * <b>exactly once</b> - proving the broker-rebalance (subscribe-mode, shared consumer group) +
 * ClusterCoordinator routing + lease-gate together prevent duplicate consumption across instances.
 *
 * <p><b>Gated by {@code -Dit.nacos} + {@code -Dit.storage=rocketmq}</b>. Run:</p>
 * <pre>
 *   gradle :eventmesh-runtime:test --tests "org.apache.eventmesh.runtime.it.MultiInstanceRocketMqIntegrationTest" \
 *     -Dit.nacos=host:5529 -Dit.namesrv=host:9876 -Dit.storage=rocketmq
 * </pre>
 */
@EnabledIfSystemProperty(named = "it.nacos", matches = ".+")
class MultiInstanceRocketMqIntegrationTest {

    private static final String TOPIC = "em-it-multi-" + System.nanoTime();
    private static final int MSG_COUNT = 5;

    private EventMeshApplication appA;
    private EventMeshApplication appB;
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
        if (appB != null) {
            appB.shutdown();
        }
        if (appA != null) {
            appA.shutdown();
        }
    }

    @Test
    void eachMessageDeliveredExactlyOnceAcrossTwoInstances() throws Exception {
        String storageType = System.getProperty("it.storage", "rocketmq");
        String namesrv = System.getProperty("it.namesrv", "localhost:9092");
        String nacos = System.getProperty("it.nacos");

        Properties props = new Properties();
        props.setProperty("namesrvAddr", namesrv);
        props.setProperty("eventMesh.server.kafka.namesrvAddr", namesrv);
        props.setProperty("eventMesh.server.rocketmq.namesrvAddr", namesrv);
        ensureTopic(namesrv, TOPIC, storageType);

        // Two instances, shared RocketMQ + shared Nacos, each clustered.
        appA = bootApp(storageType, props, nacos, "A");
        appB = bootApp(storageType, props, nacos, "B");

        // Subscriber c1 on A (BROADCAST). Collects delivered event ids.
        subClient = CloudEventsClient.builder()
            .runtimeUrl("http://localhost:" + appA.trafficPort()).clientId("c1").pollIntervalMs(500L).build();
        List<String> received = new CopyOnWriteArrayList<>();
        subClient.subscribe(TOPIC, "BROADCAST", event -> received.add(event.getId()));

        // Let both consumers' lazy subscribe + broker rebalance settle before publishing.
        Thread.sleep(25_000L);

        // Publish MSG_COUNT events to instance B.
        pubClient = CloudEventsClient.builder()
            .runtimeUrl("http://localhost:" + appB.trafficPort()).clientId("pub").build();
        for (int i = 1; i <= MSG_COUNT; i++) {
            CloudEvent event = CloudEventBuilder.v1()
                .withId("m" + i).withSource(URI.create("it")).withType("it.event")
                .withDataContentType("text/plain").withData(("p" + i).getBytes()).build();
            assertTrue(pubClient.publish(TOPIC, event), "publish m" + i + " should be accepted");
        }

        // Wait until all MSG_COUNT distinct ids are received.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (distinctIds(received).size() < MSG_COUNT && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }

        // Exactly-once: every id present, no duplicates.
        assertEquals(MSG_COUNT, distinctIds(received).size(),
            "all " + MSG_COUNT + " events should be delivered");
        assertEquals(received.size(), distinctIds(received).size(),
            "no duplicate deliveries (got " + received.size() + " deliveries for " + distinctIds(received).size() + " distinct ids): " + received);
    }

    private static Set<String> distinctIds(List<String> ids) {
        return new HashSet<>(ids);
    }

    /** Boot one EventMeshApplication with its own RocketMQ storage (SPI) + own NacosMetaStore. */
    private EventMeshApplication bootApp(String storageType, Properties props, String nacos, String tag) throws Exception {
        MeshStoragePlugin storage = EventMeshExtensionFactory.getExtension(MeshStoragePlugin.class, storageType);
        if (storage == null) {
            throw new IllegalStateException("no MeshStoragePlugin for '" + storageType + "'");
        }
        EventMeshApplication app = new EventMeshApplication(storage, new InMemoryOffsetStore(), 0, 0);
        app.runtime().withStorageConfig(props);
        app.enableCluster(new NacosMetaStore(nacos), "it-multi-" + tag + "-" + System.nanoTime());
        app.start();
        return app;
    }

    private static void ensureTopic(String namesrv, String topic, String storageType) throws Exception {
        // Scope the topic to ONE reachable broker master so sends never route to an unreachable broker.
        BrokerDiscoverer.ensureTopicOnReachableBroker(namesrv, topic, 4);
    }
}
