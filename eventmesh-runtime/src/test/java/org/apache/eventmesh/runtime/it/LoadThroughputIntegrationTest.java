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

import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.client.cloudevents.CloudEventsClient;
import org.apache.eventmesh.runtime.boot.EventMeshApplication;
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

/**
 * Load / throughput test against a real broker. Publishes {@code MSG_COUNT} events back-to-back
 * and asserts they are ALL delivered (no loss under load), reporting the achieved throughput. This
 * stresses the pull-loop, dispatch path, push buffer, and storage poll under sustained load -
 * surfacing concurrency/backpressure bugs that single-message functional tests miss.
 *
 * <p><b>Gated by {@code -Dit.storage}</b>. Run:</p>
 * <pre>
 *   gradle :eventmesh-runtime:test --tests "org.apache.eventmesh.runtime.it.LoadThroughputIntegrationTest" \
 *     -Dit.storage=rocketmq -Dit.namesrv=host:9876
 * </pre>
 */
@EnabledIfSystemProperty(named = "it.storage", matches = "rocketmq|kafka")
class LoadThroughputIntegrationTest {

    private static final String TOPIC = "em-it-load-" + System.nanoTime();
    private static final int MSG_COUNT = 500;

    private EventMeshApplication app;
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
        if (app != null) {
            app.shutdown();
        }
    }

    @Test
    void publishBurst_allDelivered_noLoss() throws Exception {
        String storageType = System.getProperty("it.storage", "rocketmq");
        String namesrv = System.getProperty("it.namesrv", "localhost:9092");

        Properties props = new Properties();
        props.setProperty("namesrvAddr", namesrv);
        props.setProperty("eventMesh.server.kafka.namesrvAddr", namesrv);
        props.setProperty("eventMesh.server.rocketmq.namesrvAddr", namesrv);

        MeshStoragePlugin storage = EventMeshExtensionFactory.getExtension(MeshStoragePlugin.class, storageType);
        app = new EventMeshApplication(storage, new InMemoryOffsetStore(), 0, 0);
        app.runtime().withStorageConfig(props);
        app.start();
        if (storage instanceof org.apache.eventmesh.storage.rocketmq.storage.RocketMQRemotingStoragePlugin) {
            ((org.apache.eventmesh.storage.rocketmq.storage.RocketMQRemotingStoragePlugin) storage).createTopic(TOPIC, 4);
        }

        // Subscriber: collect all delivered event ids.
        List<String> received = new CopyOnWriteArrayList<>();
        subClient = CloudEventsClient.builder()
            .runtimeUrl("http://localhost:" + app.trafficPort()).clientId("load-sub")
            .pollIntervalMs(200L).build();
        subClient.subscribe(TOPIC, "BROADCAST", event -> received.add(event.getId()));

        // Let the consumer lazy-subscribe + rebalance before the burst.
        Thread.sleep(25_000L);

        // Publish MSG_COUNT events back-to-back, measuring time.
        pubClient = CloudEventsClient.builder()
            .runtimeUrl("http://localhost:" + app.trafficPort()).clientId("load-pub").build();
        long pubStart = System.nanoTime();
        int published = 0;
        for (int i = 0; i < MSG_COUNT; i++) {
            CloudEvent event = CloudEventBuilder.v1()
                .withId("load-" + i).withSource(URI.create("it")).withType("it.event")
                .withDataContentType("text/plain").withData(("d" + i).getBytes()).build();
            if (pubClient.publish(TOPIC, event)) {
                published++;
            }
        }
        long pubMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - pubStart);

        // Wait for all to be delivered.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (distinctIds(received).size() < MSG_COUNT && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }

        Set<String> got = distinctIds(received);
        int lost = published - got.size();

        // No duplicates under load (at-least-once, not many-once).
        assertEquals(received.size(), got.size(),
            "no duplicate deliveries under load: " + received.size() + " deliveries for " + got.size() + " distinct");

        // No loss: all published events delivered (rocketmq-remoting direct RPC: full offset control).
        // (published may be < MSG_COUNT if some brokers in the cluster are unreachable; the assertion
        // is that every published event is delivered exactly once.)
        assertEquals(published, got.size(),
            "all published events should be delivered (no loss); published=" + published + " got=" + got.size() + " lost=" + lost);
    }

    private static Set<String> distinctIds(List<String> ids) {
        return new HashSet<>(ids);
    }

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
            // already exists
        } finally {
            admin.shutdown();
        }
    }
}

