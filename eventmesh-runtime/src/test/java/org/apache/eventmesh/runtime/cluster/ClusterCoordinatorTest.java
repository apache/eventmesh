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

package org.apache.eventmesh.runtime.cluster;

import org.apache.eventmesh.runtime.subscription.DistributionMode;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterCoordinatorTest {

    @Test
    void subscribeOnADispatchOnBDeliversViaForward() {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        Map<String, List<CloudEvent>> onA = new HashMap<>();
        Map<String, List<CloudEvent>> onB = new HashMap<>();

        Forwarder forwarder = (targetInstance, clientId, topic, event) -> {
            if ("A".equals(targetInstance)) {
                return record(onA, clientId, event);
            }
            if ("B".equals(targetInstance)) {
                return record(onB, clientId, event);
            }
            return false;
        };

        ClusterCoordinator a = new ClusterCoordinator("A", new ClusterSubscriptionStore(meta),
            (topic, clientId, event) -> record(onA, clientId, event), forwarder);
        ClusterCoordinator b = new ClusterCoordinator("B", new ClusterSubscriptionStore(meta),
            (topic, clientId, event) -> record(onB, clientId, event), forwarder);

        a.subscribe("orders", "c1", DistributionMode.BROADCAST, "");

        // B owns the partition, pulls the event and dispatches — c1 lives on A.
        int delivered = b.dispatch("orders", event("o-1"));
        assertEquals(1, delivered);
        assertEquals(List.of("o-1"), ids(onA.get("c1")));
        assertTrue(onB.isEmpty(), "nothing delivered locally on the dispatching instance");
    }

    @Test
    void localSubscriberDeliveredWithoutForward() {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        Map<String, List<CloudEvent>> onA = new HashMap<>();
        Forwarder forwarder = (inst, cid, topic, e) -> {
            throw new AssertionError("local delivery must not forward");
        };
        ClusterCoordinator a = new ClusterCoordinator("A", new ClusterSubscriptionStore(meta),
            (topic, clientId, event) -> record(onA, clientId, event), forwarder);

        a.subscribe("orders", "c1", DistributionMode.BROADCAST, "");
        assertEquals(1, a.dispatch("orders", event("o-1")));
        assertEquals(List.of("o-1"), ids(onA.get("c1")));
    }

    @Test
    void loadBalancePicksOneAcrossInstances() {
        InMemoryMetaStore meta = new InMemoryMetaStore();
        Map<String, List<CloudEvent>> onA = new HashMap<>();
        Map<String, List<CloudEvent>> onB = new HashMap<>();
        Forwarder forwarder = (targetInstance, clientId, topic, event) -> "A".equals(targetInstance)
            ? record(onA, clientId, event) : record(onB, clientId, event);
        ClusterCoordinator a = new ClusterCoordinator("A", new ClusterSubscriptionStore(meta),
            (topic, cid, e) -> record(onA, cid, e), forwarder);
        ClusterCoordinator b = new ClusterCoordinator("B", new ClusterSubscriptionStore(meta),
            (topic, cid, e) -> record(onB, cid, e), forwarder);

        // Two workers on different instances, LOAD_BALANCE on the same topic.
        a.subscribe("orders", "w-1", DistributionMode.LOAD_BALANCE, "");
        b.subscribe("orders", "w-2", DistributionMode.LOAD_BALANCE, "");

        int total = 0;
        for (int i = 0; i < 4; i++) {
            total += a.dispatch("orders", event("o-" + i));
        }
        // Each dispatch goes to exactly one worker (round-robin across the 2).
        assertEquals(4, total);
        int w1 = onA.getOrDefault("w-1", new ArrayList<>()).size();
        int w2 = onB.getOrDefault("w-2", new ArrayList<>()).size();
        assertEquals(4, w1 + w2, "no fan-out under LOAD_BALANCE");
    }

    private static boolean record(Map<String, List<CloudEvent>> sink, String clientId, CloudEvent event) {
        sink.computeIfAbsent(clientId, k -> new ArrayList<>()).add(event);
        return true;
    }

    private static List<String> ids(List<CloudEvent> events) {
        List<String> out = new ArrayList<>();
        if (events == null) {
            return out;
        }
        for (CloudEvent e : events) {
            out.add(e.getId());
        }
        return out;
    }

    private static CloudEvent event(String id) {
        return CloudEventBuilder.v1().withId(id).withSource(URI.create("test")).withType("t").build();
    }
}
