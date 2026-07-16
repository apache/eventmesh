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

package org.apache.eventmesh.runtime.ingress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.offset.OffsetStore;
import org.apache.eventmesh.runtime.push.BufferedEvent;
import org.apache.eventmesh.runtime.push.PushService;
import org.apache.eventmesh.runtime.subscription.DistributionMode;
import org.apache.eventmesh.runtime.subscription.SubscriptionManager;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

class UniIngressServiceTest {

    @Test
    void publishPullPollAckEndToEnd() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        OffsetStore offsets = new InMemoryOffsetStore();
        UniIngressService svc = new UniIngressService(storage, offsets);

        svc.subscribe("orders", "client-1", DistributionMode.BROADCAST, null);

        svc.publish("orders", event("o-1")).get(); // persisted to MQ
        assertEquals(1, svc.pullAndDispatch("orders", 100, 0), "one event pulled & dispatched");

        List<BufferedEvent> delivered = svc.poll("client-1", 100, 0);
        assertEquals(1, delivered.size());
        assertEquals("o-1", delivered.get(0).getEvent().getId());

        assertTrue(svc.ack(delivered.get(0).getDeliveryId()));
        assertTrue(offsets.readOffset("orders", "client-1", -1) >= 1, "offset advances only on ACK");
    }

    @Test
    void loadBalanceSpreadsAcrossSubscribers() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        OffsetStore offsets = new InMemoryOffsetStore();
        UniIngressService svc = new UniIngressService(storage, offsets);

        svc.subscribe("orders", "w-1", DistributionMode.LOAD_BALANCE, null);
        svc.subscribe("orders", "w-2", DistributionMode.LOAD_BALANCE, null);

        svc.publish("orders", event("o-1")).get();
        svc.publish("orders", event("o-2")).get();
        svc.pullAndDispatch("orders", 100, 0);

        int total = svc.poll("w-1", 100, 0).size() + svc.poll("w-2", 100, 0).size();
        assertEquals(2, total, "round-robin gave each event to one worker");
    }

    @Test
    void ackTimeoutRedelivers() throws Exception {
        AtomicLong clock = new AtomicLong(0L);
        InMemoryStorage storage = new InMemoryStorage();
        OffsetStore offsets = new InMemoryOffsetStore();
        UniIngressService svc = new UniIngressService(storage, offsets, new SubscriptionManager(),
            new PushService(), 10_000L, 3, clock::get);

        svc.subscribe("orders", "client-1", DistributionMode.BROADCAST, null);
        svc.publish("orders", event("o-1")).get();
        svc.pullAndDispatch("orders", 100, 0);
        assertEquals(1, svc.poll("client-1", 100, 0).size());

        // Subscriber never ACKs — advance the clock past the ACK window and run the retry sweep.
        clock.addAndGet(10_000L);
        svc.dispatcherTick();

        List<BufferedEvent> redelivered = svc.poll("client-1", 100, 0);
        assertEquals(1, redelivered.size(), "unacked event should be redelivered");
    }

    @Test
    void publishRespectsTopicRateLimit() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        UniIngressService svc = new UniIngressService(storage, new InMemoryOffsetStore());
        // Capacity 1, near-zero refill: the second immediate publish is rejected.
        svc.setTopicRateLimit("orders", 1, 0.0001);

        svc.publish("orders", event("o-1")).get(); // consumes the single burst token
        CompletableFuture<Void> second = svc.publish("orders", event("o-2"));

        assertTrue(second.isCompletedExceptionally(), "second publish over the limit should be rejected");
        org.apache.eventmesh.runtime.ratelimit.RateLimitedException ex =
            (org.apache.eventmesh.runtime.ratelimit.RateLimitedException) second.handle((v, t) -> t).get();
        assertEquals("orders", ex.getTopic());
    }

    @Test
    void requestReplyReturnsTheReply() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        UniIngressService svc = new UniIngressService(storage, new InMemoryOffsetStore());

        CloudEvent reply = event("reply-1");
        // The request blocks; a responder thread supplies the reply.
        CompletableFuture<Void> responder = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            svc.reply("corr-1", reply);
        });

        CloudEvent request = CloudEventBuilder.from(event("req-1"))
            .withExtension(UniIngressService.EXT_CORRELATION_ID, "corr-1").build();
        CloudEvent received = svc.request("rpc", request, 2_000L);

        assertSame(reply, received);
        responder.get();
    }

    @Test
    void requestTimesOutWhenNoReply() {
        InMemoryStorage storage = new InMemoryStorage();
        UniIngressService svc = new UniIngressService(storage, new InMemoryOffsetStore());

        CloudEvent request = CloudEventBuilder.from(event("req-1"))
            .withExtension(UniIngressService.EXT_CORRELATION_ID, "corr-2").build();

        assertThrows(TimeoutException.class, () -> svc.request("rpc", request, 100L));
    }

    @Test
    void lateReplyIsDiscarded() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        UniIngressService svc = new UniIngressService(storage, new InMemoryOffsetStore());

        CloudEvent request = CloudEventBuilder.from(event("req-1"))
            .withExtension(UniIngressService.EXT_CORRELATION_ID, "corr-3").build();
        assertThrows(TimeoutException.class, () -> svc.request("rpc", request, 100L));

        // Reply arrives after the request already timed out — must be discarded, not resurrect the future.
        assertFalse(svc.reply("corr-3", event("late")));
    }

    @Test
    void metricsTrackPublishDispatchAck() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        UniIngressService svc = new UniIngressService(storage, new InMemoryOffsetStore());
        final org.apache.eventmesh.runtime.metrics.UniMetrics metrics = svc.getMetrics();

        svc.subscribe("orders", "client-1", DistributionMode.BROADCAST, null);
        svc.publish("orders", event("o-1")).get();
        svc.pullAndDispatch("orders", 100, 0);
        java.util.List<BufferedEvent> delivered = svc.poll("client-1", 100, 0);
        svc.ack(delivered.get(0).getDeliveryId());

        assertEquals(1, metrics.getPublishCount());
        assertEquals(1, metrics.getEventsDispatched());
        assertEquals(1, metrics.getAckCount());
        assertEquals(0, metrics.getDlqCount());
        assertEquals(0, metrics.getRedeliveries());
    }

    private static CloudEvent event(String id) {
        return CloudEventBuilder.v1().withId(id).withSource(URI.create("test")).withType("order.created").build();
    }

    /**
     * Minimal in-memory MeshStoragePlugin: send() enqueues, poll() drains.
     */
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

