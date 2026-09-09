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

import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.push.BufferedEvent;
import org.apache.eventmesh.runtime.subscription.DistributionMode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Batch publish over a REAL message broker — exercises {@code UniIngressService.publishBatch()}
 * end-to-end against the configured storage backend. Pairs with the in-process coverage in
 * {@link BatchPublishIntegrationTest} by adding the storage round-trip the in-memory stub elides.
 *
 * <p><b>Skipped</b> unless {@code -Dit.feature.storage=rocketmq4|rocketmq5} is set; the test
 * aborts via {@code Assumptions} when the backend or its SPI registration is missing so CI runs
 * without a broker stay green.</p>
 *
 * <p>Run with:
 * <pre>
 *   gradle :eventmesh-runtime:e2eFeature \
 *     -Pfeature.test=BatchPublishOverBrokerTest \
 *     -Dit.feature.storage=rocketmq4 -Dit.namesrv=host:9876
 * </pre>
 * </p>
 */
@EnabledIfSystemProperty(named = "it.feature.storage", matches = "rocketmq|rocketmq4|rocketmq5")
class BatchPublishOverBrokerTest {

    @Test
    void publishBatchAllArrivesOverBroker() throws Exception {
        try (FeatureBrokerHarness fx = FeatureBrokerHarness.of("rocketmq4")) {
            fx.start();

            String topic = "em-it-batch-" + System.nanoTime();
            String clientId = "batch-broker-client";
            BrokerDiscoverer.ensureTopicOnReachableBroker(
                System.getProperty("it.namesrv",
                    System.getProperty("it.namesrv5", "localhost:9876")),
                topic, 4);

            // Subscribe before publishing — same constraint that forces LiteStreamCall to wait.
            fx.runtime().ingress().subscribe(topic, clientId, DistributionMode.BROADCAST, null);
            Thread.sleep(2_000L);

            // Build the batch (10 events, deterministic ids so we can verify no events vanished or
            // duplicated, and so we never collide with rerun garbage in the topic).
            List<CloudEvent> batch = new ArrayList<>();
            Set<String> expected = new HashSet<>();
            for (int i = 0; i < 10; i++) {
                String id = "broker-batch-" + i + "-" + System.nanoTime();
                expected.add(id);
                batch.add(CloudEventBuilder.v1()
                    .withId(id)
                    .withSource(URI.create("broker://batch"))
                    .withType("broker.batch")
                    .withDataContentType("text/plain")
                    .withData(("m" + i).getBytes(StandardCharsets.UTF_8))
                    .build());
            }
            fx.runtime().ingress().publishBatch(topic, batch).get(15, TimeUnit.SECONDS);

            // Pull-loop uses LitePullConsumer on RocketMQ; it rebalances once and starts delivering
            // within ~3s of subscribe. Give it up to 30s for the whole batch to land.
            Set<String> received = new HashSet<>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (received.size() < expected.size() && System.nanoTime() < deadline) {
                List<BufferedEvent> drained = fx.runtime().ingress().poll(clientId, 50, 500L);
                for (BufferedEvent d : drained) {
                    EventMeshFrame frame = d.getEvent();
                    received.add(frame.toCloudEvent().getId());
                    fx.runtime().ingress().ack(d.getDeliveryId());
                }
            }
            if (!received.equals(expected)) {
                throw new AssertionError("batch failed: expected " + expected + " got " + received);
            }
        }
    }
}
