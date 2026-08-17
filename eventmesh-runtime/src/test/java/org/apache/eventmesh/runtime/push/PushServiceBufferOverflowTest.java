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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.delivery.AckCallback;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Tests for P1-4 (synchronized offer) and P1-5 (DROP_OLDEST nacks the dropped callback).
 */
class PushServiceBufferOverflowTest {

    private static EventMeshFrame frame(String id) {
        return EventMeshFrame.fromCloudEvent(
            CloudEventBuilder.v1().withId(id).withSource(URI.create("test")).withType("t").build());
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

    @Test
    void dropOldestNacksDroppedCallback() {
        PushService svc = new PushService(2);
        svc.setOverflowPolicy(PushService.OverflowPolicy.DROP_OLDEST);
        svc.register("c1");

        RecordingCallback cb1 = new RecordingCallback();
        RecordingCallback cb2 = new RecordingCallback();
        RecordingCallback cb3 = new RecordingCallback();

        assertTrue(svc.offer("c1", "d1", frame("e1"), cb1));
        assertTrue(svc.offer("c1", "d2", frame("e2"), cb2));
        assertTrue(svc.offer("c1", "d3", frame("e3"), cb3));

        assertEquals(1, cb1.nacks.get(), "dropped oldest callback must be nack'd");
        assertEquals(0, cb2.nacks.get(), "d2 still buffered, not nack'd");
        assertEquals(0, cb3.nacks.get(), "d3 is the new one, not nack'd");
    }

    @Test
    void blockPolicyRejectsAndReturnsFalse() {
        PushService svc = new PushService(1);
        svc.setOverflowPolicy(PushService.OverflowPolicy.BLOCK);
        svc.register("c1");

        RecordingCallback cb1 = new RecordingCallback();
        RecordingCallback cb2 = new RecordingCallback();

        assertTrue(svc.offer("c1", "d1", frame("e1"), cb1));
        assertFalse(svc.offer("c1", "d2", frame("e2"), cb2), "BLOCK policy should reject when full");
        assertEquals(0, cb1.nacks.get());
        assertEquals(0, cb2.nacks.get());
    }

    @Test
    void ackRemovesCallback() {
        PushService svc = new PushService(10);
        svc.register("c1");
        RecordingCallback cb = new RecordingCallback();
        svc.offer("c1", "d1", frame("e1"), cb);

        assertTrue(svc.ack("d1"));
        assertEquals(1, cb.acks.get());
        assertFalse(svc.ack("d1"), "double ack returns false");
    }
}
