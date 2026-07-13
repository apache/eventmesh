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

package org.apache.eventmesh.runtime.push;

import org.apache.eventmesh.runtime.delivery.AckCallback;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushServiceTest {

    @Test
    void offerThenPollDeliversBufferedEvents() {
        PushService push = new PushService();
        push.register("client-1");

        RecordingCallback cb = new RecordingCallback();
        assertTrue(push.offer("client-1", "d-1", event("e-1"), cb));
        assertTrue(push.offer("client-1", "d-2", event("e-2"), cb));

        java.util.List<BufferedEvent> polled = push.poll("client-1", 10, 0);
        assertEquals(2, polled.size());
        assertEquals("d-1", polled.get(0).getDeliveryId());
        assertEquals("d-2", polled.get(1).getDeliveryId());
    }

    @Test
    void ackResolvesCallback() {
        PushService push = new PushService();
        push.register("client-1");

        RecordingCallback cb = new RecordingCallback();
        push.offer("client-1", "d-1", event("e-1"), cb);

        assertTrue(push.ack("d-1"));
        assertEquals(1, cb.acks.get());
        assertFalse(push.ack("d-1"), "second ack is a no-op");
    }

    @Test
    void bufferFullAppliesBackpressure() {
        PushService push = new PushService(2);
        push.register("client-1");
        RecordingCallback cb = new RecordingCallback();

        assertTrue(push.offer("client-1", "d-1", event("e-1"), cb));
        assertTrue(push.offer("client-1", "d-2", event("e-2"), cb));
        assertFalse(push.offer("client-1", "d-3", event("e-3"), cb), "third offer over capacity");
        assertEquals(2, push.pending("client-1"));
    }

    @Test
    void longPollingChannelRejectsOnFullBuffer() {
        PushService push = new PushService(1);
        push.register("client-1");
        LongPollingChannel channel = new LongPollingChannel(push, "client-1");

        RecordingCallback cb1 = new RecordingCallback();
        RecordingCallback cb2 = new RecordingCallback();
        channel.deliver("d-1", event("e-1"), cb1);
        channel.deliver("d-2", event("e-2"), cb2); // over capacity

        assertEquals(0, cb1.nacks.get(), "first delivery buffered, not rejected");
        assertEquals(1, cb2.nacks.get(), "overflow delivery nacked");
        assertEquals(1, push.pending("client-1"));
    }

    private static CloudEvent event(String id) {
        return CloudEventBuilder.v1().withId(id).withSource(URI.create("test")).withType("t").build();
    }

    private static final class RecordingCallback implements AckCallback {

        final AtomicInteger acks = new AtomicInteger();
        final AtomicInteger nacks = new AtomicInteger();

        @Override
        public void ack() {
            acks.incrementAndGet();
        }

        @Override
        public void nack(Throwable reason) {
            nacks.incrementAndGet();
        }
    }
}
