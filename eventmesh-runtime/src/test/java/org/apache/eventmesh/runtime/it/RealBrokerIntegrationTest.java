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
import org.apache.eventmesh.runtime.boot.UniRuntime;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.push.BufferedEvent;
import org.apache.eventmesh.runtime.subscription.DistributionMode;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * End-to-end integration test of the uni runtime against a REAL message broker (§18 E2E
 * suite, real-service ).
 *
 * <p><b>Gated by {@code -Dit.storage}</b> (matches {@code rocketmq|kafka}) so it is skipped in the
 * normal suite. To run against a live RocketMQ nameserver:</p>
 * <pre>
 *   gradle :eventmesh-runtime:test \
 *     --tests "org.apache.eventmesh.runtime.it.RealBrokerIntegrationTest" \
 *     -Dit.storage=rocketmq -Dit.namesrv=host:9876
 * </pre>
 * <p>{@code -Dit.topic} (default {@code em-it-orders}) overrides the topic. For RocketMQ the test
 * creates the topic via {@code DefaultMQAdminExt} (brokers may have {@code autoCreateTopicEnable=false}).
 * The storage plugin's {@code DefaultLitePullConsumer} lazily subscribes on first poll and needs up to
 * one rebalance cycle (~20s) before it pulls, so the delivery wait is 40s.</p>
 *
 * <p>The test loads the storage plugin via the EventMesh SPI, points the pull-loop and dispatcher at
 * it, then drives a full publish → subscribe → poll → ack round-trip and asserts the offset
 * advanced — the same contract the unit tests prove in-memory, now over a real MQ.</p>
 */
@EnabledIfSystemProperty(named = "it.storage", matches = "rocketmq|kafka")
class RealBrokerIntegrationTest {

    @Test
    void publishSubscribePollAckOverRealBroker() throws Exception {
        String storageType = System.getProperty("it.storage", "kafka");
        String topic = System.getProperty("it.topic", "em-it-orders");
        String namesrv = System.getProperty("it.namesrv", "localhost:9092");
        String clientId = "it-client-1";

        // 1. Load the storage plugin via SPI (the same path the real runtime uses).
        MeshStoragePlugin storage = EventMeshExtensionFactory.getExtension(MeshStoragePlugin.class, storageType);
        if (storage == null) {
            throw new IllegalStateException("no MeshStoragePlugin registered for '" + storageType
                + "' — check the SPI file META-INF/eventmesh/org.apache.eventmesh.api.storage.MeshStoragePlugin");
        }

        // 2. Configure + boot the runtime. The thin adapter forwards these properties to the
        // underlying Kafka/RocketMQ client; the @Config-injected namesrvAddr comes from the
        // storage module's properties file on the classpath (ConfigService bootstrap).
        Properties props = new Properties();
        props.setProperty("namesrvAddr", namesrv);
        props.setProperty("eventMesh.server.kafka.namesrvAddr", namesrv);
        props.setProperty("eventMesh.server.rocketmq.namesrvAddr", namesrv);

        UniRuntime runtime = new UniRuntime(storage, new InMemoryOffsetStore(), 200L, 500L, 100, 500L);
        // NOTE: in a fully-bootstrapped environment, storage.init() is preceded by
        // ConfigService.getInstance().populateConfigForObject(storage) so the @Config fields
        // (namesrvAddr, etc.) are injected. If your broker address differs from the properties
        // file, set it there before running.
        runtime.withStorageConfig(props);
        try {
            // Ensure the topic exists — production brokers often have autoCreateTopicEnable=false,
            // in which case a send returns CODE 17 "topic not exist, apply first please!".
            ensureTopic(namesrv, topic);
            runtime.start();
            Thread.sleep(2_000L); // let the broker connection settle

            // 3. Subscribe + publish + (background pull-loop dispatches) + poll + ack.
            runtime.ingress().subscribe(topic, clientId, DistributionMode.BROADCAST, null);
            // Let the pull-loop trigger the storage consumer's lazy subscribe + the broker
            // rebalance BEFORE publishing, so the message falls within the consumer's read range
            // (RocketMQ CONSUME_FROM_LAST_OFFSET otherwise skips a message published pre-subscribe).
            Thread.sleep(3_000L);
            CloudEvent event = CloudEventBuilder.v1()
                .withId("it-1").withSource(URI.create("it")).withType("it.event").build();
            runtime.ingress().publish(topic, event).get(10, TimeUnit.SECONDS);

            // RocketMQ DefaultLitePullConsumer lazily subscribes on first storage.poll and needs up to
            // one rebalance cycle (~20s, RebalanceService interval) before it pulls, so allow generous
            // time for the background pull-loop to dispatch the event into the client buffer.
            List<BufferedEvent> received = new java.util.ArrayList<>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
            while (received.isEmpty() && System.nanoTime() < deadline) {
                received.addAll(runtime.ingress().poll(clientId, 100, 500L));
            }
            if (received.isEmpty()) {
                throw new AssertionError("event not delivered within 40s — check broker + pull-loop");
            }
            boolean acked = runtime.ingress().ack(received.get(0).getDeliveryId());
            if (!acked) {
                throw new AssertionError("ack failed for delivery " + received.get(0).getDeliveryId());
            }

            // 4. The offset advanced only on ACK — the core at-least-once contract, now over real MQ.
            long offset = runtime.ingress().getOffsetStore().readOffset(topic, clientId, -1);
            if (offset < 1) {
                throw new AssertionError("offset did not advance after ACK: " + offset);
            }
        } finally {
            runtime.shutdown();
        }
    }

    @SuppressWarnings("unused")
    private static void touchTempDir() throws Exception {
        // Placeholder kept so java.nio.file.Files stays an intentional import in scaffold edits.
        Files.createTempDirectory("em-it-");
    }

    /**
     * Create {@code topic} on the broker if it does not exist (RocketMQ CODE 17 otherwise). Uses
     * {@code DefaultMQAdminExt} from rocketmq-tools (a test-only dep). No-op for non-RocketMQ runs.
     */
    private static void ensureTopic(String namesrv, String topic) throws Exception {
        // Scope the topic to ONE reachable broker master (not cluster-wide) so sends never route to
        // a broker whose data port is unreachable from this machine. See BrokerDiscoverer.
        BrokerDiscoverer.ensureTopicOnReachableBroker(namesrv, topic, 4);
    }
}

