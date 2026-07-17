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

package org.apache.eventmesh.runtime.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.push.PushService;
import org.apache.eventmesh.runtime.subscription.DistributionMode;
import org.apache.eventmesh.runtime.subscription.SubscriptionManager;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

class UniAdminServiceTest {

    @Test
    void adminReflectsRuntimeState() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        UniIngressService svc = new UniIngressService(storage, new InMemoryOffsetStore());
        final UniAdminService admin = new UniAdminService(svc);

        svc.subscribe("orders", "client-1", DistributionMode.BROADCAST, null);
        svc.publish("orders", event("o-1")).get();
        svc.pullAndDispatch("orders", 100, 0);

        assertEquals(1, admin.pendingDeliveries(), "one in-flight delivery");
        assertEquals(1, admin.clientPending("client-1"), "one buffered for the client");
        assertEquals(1, admin.subscriptions("orders").size());

        List<org.apache.eventmesh.runtime.push.BufferedEvent> polled = svc.poll("client-1", 100, 0);
        svc.ack(polled.get(0).getDeliveryId());

        assertEquals(0, admin.pendingDeliveries());
        assertTrue(admin.offsets("orders").containsKey("client-1#-1"), "offset recorded under clientId#partition");
    }

    @Test
    void rejectClientEvictsSubscriptionsAndBuffer() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        UniIngressService svc = new UniIngressService(storage, new InMemoryOffsetStore());
        final UniAdminService admin = new UniAdminService(svc);

        svc.subscribe("orders", "client-1", DistributionMode.BROADCAST, null);
        svc.publish("orders", event("o-1")).get();
        svc.pullAndDispatch("orders", 100, 0);

        assertEquals(1, admin.rejectClient("client-1"));
        assertEquals(0, admin.subscriptions("orders").size());
        assertEquals(0, admin.clientPending("client-1"));
    }

    @Test
    void dlqReplayRepublishsToOriginalTopic() throws Exception {
        AtomicLong clock = new AtomicLong(0L);
        InMemoryStorage storage = new InMemoryStorage();
        UniIngressService svc = new UniIngressService(storage, new InMemoryOffsetStore(),
            new SubscriptionManager(), new PushService(), 1_000L, 1, clock::get);
        final UniAdminService admin = new UniAdminService(svc);

        svc.subscribe("orders", "client-1", DistributionMode.BROADCAST, null);
        svc.publish("orders", event("doomed")).get();
        svc.pullAndDispatch("orders", 100, 0);

        // Never ACK; advance past the ACK window. maxAttempts=1 → straight to DLQ.
        clock.addAndGet(1_000L);
        svc.dispatcherTick();
        assertEquals(1, svc.getMetrics().getDlqCount());

        // NOTE: the DLQ'd event is still sitting in the client's push buffer (the dispatcher does
        // not purge an already-buffered copy on DLQ — at-least-once lets the idempotent client
        // handle it). Drain the stale copy before replay so the assertion is unambiguous.
        svc.poll("client-1", 100, 0);

        // Replay drains orders_DLQ and re-publishes to orders.
        assertEquals(1, admin.dlqReplay("orders", 100));

        // The replayed event is now back on the original topic and re-dispatches to the client.
        svc.pullAndDispatch("orders", 100, 0);
        assertEquals(1, svc.poll("client-1", 100, 0).size());
    }

    private static CloudEvent event(String id) {
        return CloudEventBuilder.v1().withId(id).withSource(URI.create("test")).withType("t").build();
    }

    /** Minimal in-memory MeshStoragePlugin. */
    private static final class InMemoryStorage implements MeshStoragePlugin {

        private final ConcurrentHashMap<String, Queue<CloudEvent>> queues = new ConcurrentHashMap<>();

        @Override
        public void init(Properties properties) {
        }

        @Override
        public void send(String topic, CloudEvent event, SendCallback callback) {
            queues.computeIfAbsent(topic, k -> new ConcurrentLinkedQueue<>()).offer(event);
            SendResult r = new SendResult();
            r.setMessageId(event.getId());
            r.setTopic(topic);
            callback.onSuccess(r);
        }

        @Override
        public List<CloudEvent> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            Queue<CloudEvent> q = queues.get(topic);
            if (q == null) {
                return new ArrayList<>();
            }
            List<CloudEvent> out = new ArrayList<>();
            CloudEvent e;
            while (out.size() < maxEvents && (e = q.poll()) != null) {
                out.add(e);
            }
            return out;
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
