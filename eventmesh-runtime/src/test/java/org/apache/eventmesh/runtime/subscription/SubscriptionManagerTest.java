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

package org.apache.eventmesh.runtime.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.common.wire.EventMeshFrame;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

class SubscriptionManagerTest {

    private static final String TOPIC = "orders";

    /**
     * LOAD_BALANCE: one batch of three events, three subscribers — round-robin gives each exactly one.
     */
    @Test
    void loadBalanceRoundRobinsAcrossSubscribers() {
        final FakeStorage storage = new FakeStorage();
        AtomicLong clock = new AtomicLong(0);
        SubscriptionManager manager = new SubscriptionManager(SubscriptionManager.DEFAULT_MAX_IDLE_MS, clock::get);

        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        List<String> c = new ArrayList<>();
        manager.subscribe(TOPIC, "worker-1", DistributionMode.LOAD_BALANCE, null, e -> a.add(e.attributes().get("id")));
        manager.subscribe(TOPIC, "worker-2", DistributionMode.LOAD_BALANCE, null, e -> b.add(e.attributes().get("id")));
        manager.subscribe(TOPIC, "worker-3", DistributionMode.LOAD_BALANCE, null, e -> c.add(e.attributes().get("id")));

        storage.enqueue(List.of(event("order.created", "1"), event("order.created", "2"), event("order.created", "3")));
        int pulled = manager.pollAndDispatch(TOPIC, storage, 100, 0);

        assertEquals(3, pulled);
        assertEquals(1, a.size());
        assertEquals(1, b.size());
        assertEquals(1, c.size());
    }

    /**
     * BROADCAST: every active subscriber receives the same event.
     */
    @Test
    void broadcastDeliversToAll() {
        FakeStorage storage = new FakeStorage();
        AtomicLong clock = new AtomicLong(0);
        SubscriptionManager manager = new SubscriptionManager(SubscriptionManager.DEFAULT_MAX_IDLE_MS, clock::get);

        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        manager.subscribe(TOPIC, "svc-a", DistributionMode.BROADCAST, null, e -> a.add(e.attributes().get("id")));
        manager.subscribe(TOPIC, "svc-b", DistributionMode.BROADCAST, null, e -> b.add(e.attributes().get("id")));

        storage.enqueue(List.of(event("config.change", "42")));
        manager.pollAndDispatch(TOPIC, storage, 100, 0);

        assertEquals(List.of("42"), a);
        assertEquals(List.of("42"), b);
    }

    /**
     * MULTICAST: only subscribers whose filter matches the event receive it.
     */
    @Test
    void multicastFiltersByType() {
        FakeStorage storage = new FakeStorage();
        AtomicLong clock = new AtomicLong(0);
        SubscriptionManager manager = new SubscriptionManager(SubscriptionManager.DEFAULT_MAX_IDLE_MS, clock::get);

        List<String> orders = new ArrayList<>();
        List<String> payments = new ArrayList<>();
        manager.subscribe(TOPIC, "order-svc", DistributionMode.MULTICAST, CloudEventFilter.byType("order.created"),
            e -> orders.add(e.attributes().get("id")));
        manager.subscribe(TOPIC, "pay-svc", DistributionMode.MULTICAST, CloudEventFilter.byType("payment.completed"),
            e -> payments.add(e.attributes().get("id")));

        storage.enqueue(List.of(
            event("order.created", "o-1"),
            event("payment.completed", "p-1"),
            event("inventory.changed", "i-1"))); // matches nobody
        manager.pollAndDispatch(TOPIC, storage, 100, 0);

        assertEquals(List.of("o-1"), orders);
        assertEquals(List.of("p-1"), payments);
    }

    /**
     * A subscription whose heartbeat expired is pruned and receives nothing.
     */
    @Test
    void idleSubscriptionIsPruned() {
        FakeStorage storage = new FakeStorage();
        AtomicLong clock = new AtomicLong(1_000L);
        long maxIdleMs = 10_000L;
        SubscriptionManager manager = new SubscriptionManager(maxIdleMs, clock::get);

        List<String> received = new ArrayList<>();
        final String subId = manager.subscribe(TOPIC, "worker-1", DistributionMode.BROADCAST, null, e -> received.add(e.attributes().get("id")));
        assertTrue(manager.activeSubscriptions(TOPIC).size() >= 1);

        // Advance the clock past the idle window without heartbeating.
        clock.set(1_000L + maxIdleMs + 1);

        storage.enqueue(List.of(event("order.created", "x")));
        manager.pollAndDispatch(TOPIC, storage, 100, 0);

        assertTrue(received.isEmpty(), "pruned subscription should not receive");
        assertFalse(manager.heartbeat(subId), "subscription should have been removed");
        assertEquals(0, manager.activeSubscriptions(TOPIC).size());
    }

    /**
     * A heartbeat refresh keeps an otherwise-idle subscription alive.
     */
    @Test
    void heartbeatKeepsSubscriptionAlive() {
        final FakeStorage storage = new FakeStorage();
        AtomicLong clock = new AtomicLong(1_000L);
        long maxIdleMs = 10_000L;
        SubscriptionManager manager = new SubscriptionManager(maxIdleMs, clock::get);

        List<String> received = new ArrayList<>();
        String subId = manager.subscribe(TOPIC, "worker-1", DistributionMode.BROADCAST, null, e -> received.add(e.attributes().get("id")));

        clock.set(1_000L + 5_000L);
        assertTrue(manager.heartbeat(subId));
        clock.set(1_000L + 5_000L + maxIdleMs - 1); // still within window after heartbeat

        storage.enqueue(List.of(event("order.created", "alive")));
        manager.pollAndDispatch(TOPIC, storage, 100, 0);

        assertEquals(List.of("alive"), received);
    }

    /**
     * unsubscribe(topic, clientId) removes only that client's subscription on the topic; other clients
     * on the same topic remain. This backs the HTTP {@code /events/unsubscribe {clientId, topic}} path.
     */
    @Test
    void unsubscribeByTopicAndClientRemovesOnlyThatClient() {
        final FakeStorage storage = new FakeStorage();
        AtomicLong clock = new AtomicLong(0);
        SubscriptionManager manager = new SubscriptionManager(SubscriptionManager.DEFAULT_MAX_IDLE_MS, clock::get);

        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        manager.subscribe(TOPIC, "svc-a", DistributionMode.BROADCAST, null, e -> a.add(e.attributes().get("id")));
        manager.subscribe(TOPIC, "svc-b", DistributionMode.BROADCAST, null, e -> b.add(e.attributes().get("id")));
        assertEquals(2, manager.activeSubscriptions(TOPIC).size());

        assertTrue(manager.unsubscribe(TOPIC, "svc-a"));
        assertEquals(1, manager.activeSubscriptions(TOPIC).size());

        // svc-a no longer receives; svc-b still does.
        storage.enqueue(List.of(event("order.created", "x")));
        manager.pollAndDispatch(TOPIC, storage, 100, 0);
        assertTrue(a.isEmpty(), "unsubscribed client should not receive");
        assertEquals(List.of("x"), b);

        // Unknown client/topic → false, no change.
        assertFalse(manager.unsubscribe(TOPIC, "nobody"));
        assertFalse(manager.unsubscribe("other-topic", "svc-b"));
    }

    private static CloudEvent event(String type, String id) {
        return CloudEventBuilder.v1()
            .withId(id)
            .withSource(URI.create("test"))
            .withType(type)
            .build();
    }

    /**
     * Minimal in-memory MeshStoragePlugin: returns enqueued batches in FIFO order from poll().
     */
    private static final class FakeStorage implements MeshStoragePlugin {

        private final Queue<List<CloudEvent>> batches = new LinkedList<>();

        void enqueue(List<CloudEvent> events) {
            batches.add(events);
        }

        @Override
        public List<EventMeshFrame> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            if (batches.isEmpty()) {
                return Collections.emptyList();
            }
            List<EventMeshFrame> out = new ArrayList<>();
            for (CloudEvent ce : batches.poll()) {
                out.add(EventMeshFrame.fromCloudEvent(ce));
            }
            return out;
        }

        @Override
        public void init(java.util.Properties properties) {
        }

        @Override
        public void send(String topic, EventMeshFrame frame, SendCallback callback) {
            CloudEvent event = frame.toCloudEvent();
            SendResult r = new SendResult();
            r.setMessageId(event.getId());
            r.setTopic(topic);
            callback.onSuccess(r);
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
