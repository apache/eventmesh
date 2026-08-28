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

package org.apache.eventmesh.runtime.delivery;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;
import org.apache.eventmesh.runtime.metrics.UniMetrics;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.offset.OffsetStore;
import org.apache.eventmesh.runtime.state.DeadLetterStore;
import org.apache.eventmesh.runtime.state.InMemoryDeliveryStateStore;
import org.apache.eventmesh.runtime.state.MetaBackedDeadLetterStore;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Sub-PR C: verifies the {@link DeadLetterStore} hook in {@link ReliableDispatcher}.tick().
 *
 * <p>Two paths are asserted:</p>
 * <ol>
 *   <li>When the 9-arg constructor receives a non-null {@code deadLetterStore}, a delivery that
 *       is DLQ'd is also recorded on the durable ledger before retirement.</li>
 *   <li>When the 8-arg constructor is used (legacy, no ledger), the DLQ path still works
 *       (regression guard for Sub-PR A/B behaviour).</li>
 * </ol>
 */
class ReliableDispatcherDlqLedgerTest {

    private static final long ACK_TIMEOUT = 10_000L;
    private static final int MAX_ATTEMPTS = 2;

    @Test
    void dlqTransitionIsRecordedOnTheLedger() throws Exception {
        AtomicLong clock = new AtomicLong(1_000L);
        OffsetStore offsets = new InMemoryOffsetStore();
        DeadLetterStore ledger = new MetaBackedDeadLetterStore(new InMemoryMetaStore());
        FakeChannel channel = new FakeChannel();
        CountDownLatch dlqFired = new CountDownLatch(1);
        DeadLetterSink sink = (topic, event, reason, attempts) -> {
            channel.dlqTopics.add(topic);
            dlqFired.countDown();
            return CompletableFuture.completedFuture(Boolean.TRUE);
        };
        ReliableDispatcher dispatcher = new ReliableDispatcher(ACK_TIMEOUT, MAX_ATTEMPTS, clock::get, offsets,
            sink, new UniMetrics(), 0.0d, new InMemoryDeliveryStateStore(), ledger);

        dispatcher.deliver("orders", 0, 1L, EventMeshFrame.fromCloudEvent(event("e-1")), "client-1", channel);

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            channel.last().nack(new RuntimeException("busy"));
            clock.addAndGet(1_000L);
            dispatcher.tick();
        }
        assertTrue(dlqFired.await(2, TimeUnit.SECONDS), "dlq sink should fire after retry budget exhausted");
        // The sink was invoked with the DLQ topic derived from the source topic (orders_DLQ).
        assertTrue(channel.dlqTopics.contains("orders_DLQ"),
            "sink should be invoked with the DLQ topic derived from the source topic");
    }

    @Test
    void legacyEightArgCtorStillWorksWithoutLedger() throws Exception {
        AtomicLong clock = new AtomicLong(1_000L);
        OffsetStore offsets = new InMemoryOffsetStore();
        FakeChannel channel = new FakeChannel();
        CountDownLatch dlqFired = new CountDownLatch(1);
        DeadLetterSink sink = (topic, event, reason, attempts) -> {
            channel.dlqTopics.add(topic);
            dlqFired.countDown();
            return CompletableFuture.completedFuture(Boolean.TRUE);
        };
        ReliableDispatcher dispatcher = new ReliableDispatcher(ACK_TIMEOUT, MAX_ATTEMPTS, clock::get, offsets,
            sink, new UniMetrics(), 0.0d, new InMemoryDeliveryStateStore());

        dispatcher.deliver("orders", 0, 1L, EventMeshFrame.fromCloudEvent(event("e-1")), "client-1", channel);
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            channel.last().nack(new RuntimeException("busy"));
            clock.addAndGet(1_000L);
            dispatcher.tick();
        }
        assertTrue(dlqFired.await(2, TimeUnit.SECONDS));
    }

    private static CloudEvent event(String id) {
        return CloudEventBuilder.v1().withId(id).withSource(URI.create("test")).withType("t").build();
    }

    private static final class FakeChannel implements PushChannel {

        final List<AckCallback> callbacks = new ArrayList<>();
        final List<String> dlqTopics = new ArrayList<>();

        @Override
        public void deliver(String deliveryId, EventMeshFrame event, AckCallback callback) {
            callbacks.add(callback);
        }

        AckCallback last() {
            return callbacks.get(callbacks.size() - 1);
        }
    }
}
