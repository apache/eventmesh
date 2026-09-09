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
 * Real-broker version of {@link UnsubscribeTopicIntegrationTest}: subscribe to two topics via real
 * MQ, unsubscribe from one, publish to both, and confirm the surviving subscription still receives
 * while the dropped one does not. The in-process test covers the same dispatch logic but mocks the
 * storage, so it cannot catch failures where the dispatcher confuses topics at the broker boundary.
 *
 * <p>Run: {@code -Dit.feature.storage=rocketmq4 -Dit.namesrv=host:9876}. Skipped otherwise.</p>
 */
@EnabledIfSystemProperty(named = "it.feature.storage", matches = "rocketmq|rocketmq4|rocketmq5")
class UnsubscribeOverBrokerTest {

    @Test
    void unsubscribeOneTopicKeepsOther() throws Exception {
        try (FeatureBrokerHarness fx = FeatureBrokerHarness.of("rocketmq4")) {
            fx.start();

            String topicA = "em-it-unsub-a-" + System.nanoTime();
            String topicB = "em-it-unsub-b-" + System.nanoTime();
            String clientId = "unsub-broker-client";
            String namesrv = System.getProperty("it.namesrv",
                System.getProperty("it.namesrv5", "localhost:9876"));
            BrokerDiscoverer.ensureTopicOnReachableBroker(namesrv, topicA, 4);
            BrokerDiscoverer.ensureTopicOnReachableBroker(namesrv, topicB, 4);

            // 1. Subscribe to both topics.
            String subA = fx.runtime().ingress().subscribe(topicA, clientId, DistributionMode.BROADCAST, null);
            String subB = fx.runtime().ingress().subscribe(topicB, clientId, DistributionMode.BROADCAST, null);
            // Re-balance window before producing — broker learns about consumer first.
            Thread.sleep(3_000L);

            // 2. Unsubscribe one of them (the contract under test).
            boolean removed = fx.runtime().ingress().unsubscribe(subA);
            if (!removed) {
                throw new AssertionError("unsubscribe(topicA) returned false — sub not tracked");
            }

            // 3. Publish one event to each topic; tag by id prefix so we can route the received events
            //    back to their originating topic on the single polling client.
            long token = System.nanoTime();
            String aId = "ua-" + token;
            String bId = "ub-" + token;
            fx.runtime().ingress().publish(topicA, CloudEventBuilder.v1()
                .withId(aId).withSource(URI.create("broker://ua"))
                .withType("ua").withDataContentType("text/plain")
                .withData("ua-payload".getBytes(StandardCharsets.UTF_8)).build()).get(10, TimeUnit.SECONDS);
            fx.runtime().ingress().publish(topicB, CloudEventBuilder.v1()
                .withId(bId).withSource(URI.create("broker://ub"))
                .withType("ub").withDataContentType("text/plain")
                .withData("ub-payload".getBytes(StandardCharsets.UTF_8)).build()).get(10, TimeUnit.SECONDS);

            // 4. Drain the buffer; we expect ONLY the B-side event to arrive.
            Set<String> receivedIds = new HashSet<>();
            List<String> receivedTypes = new ArrayList<>();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
            while (receivedIds.size() < 1 && System.nanoTime() < deadline) {
                List<BufferedEvent> drained = fx.runtime().ingress().poll(clientId, 50, 500L);
                for (BufferedEvent d : drained) {
                    EventMeshFrame frame = d.getEvent();
                    String id = frame.toCloudEvent().getId();
                    receivedIds.add(id);
                    receivedTypes.add(frame.toCloudEvent().getType());
                    fx.runtime().ingress().ack(d.getDeliveryId());
                }
            }

            if (!receivedIds.contains(bId)) {
                throw new AssertionError("topic B event was not delivered: received=" + receivedIds);
            }
            if (receivedIds.contains(aId)) {
                throw new AssertionError("topic A event leaked after unsubscribe: received=" + receivedIds);
            }
            if (receivedTypes.contains("ua")) {
                throw new AssertionError("topic A type leaked after unsubscribe: receivedTypes=" + receivedTypes);
            }
        }
    }
}
