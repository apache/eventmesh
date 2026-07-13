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
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConnectionPushPumpTest {

    @Test
    void drainsBufferOntoOpenConnectionInOrder() {
        PushService push = new PushService();
        push.register("client-1");
        push.offer("client-1", "d-1", event("e-1"), noopCallback());
        push.offer("client-1", "d-2", event("e-2"), noopCallback());

        FakeConnection conn = new FakeConnection(true);
        ConnectionPushPump pump = new ConnectionPushPump(push, "client-1", conn);

        assertEquals(2, pump.pumpOnce(10));
        assertEquals(List.of("e-1", "e-2"), conn.sentIds);
        assertEquals(List.of("d-1", "d-2"), conn.sentDeliveryIds);
    }

    @Test
    void closedConnectionLeavesEventsBuffered() {
        PushService push = new PushService();
        push.register("client-1");
        push.offer("client-1", "d-1", event("e-1"), noopCallback());

        ConnectionPushPump pump = new ConnectionPushPump(push, "client-1", new FakeConnection(false));
        assertEquals(0, pump.pumpOnce(10), "nothing pushed to a closed connection");
        assertEquals(1, push.pending("client-1"), "event stays buffered for reconnect/redelivery");
    }

    private static AckCallback noopCallback() {
        return new AckCallback() {
            @Override
            public void ack() {
            }

            @Override
            public void nack(Throwable reason) {
            }
        };
    }

    private static CloudEvent event(String id) {
        return CloudEventBuilder.v1().withId(id).withSource(URI.create("test")).withType("t").build();
    }

    private static final class FakeConnection implements Connection {

        final boolean open;
        final List<String> sentIds = new ArrayList<>();
        final List<String> sentDeliveryIds = new ArrayList<>();

        FakeConnection(boolean open) {
            this.open = open;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void send(String deliveryId, CloudEvent event) {
            sentDeliveryIds.add(deliveryId);
            sentIds.add(event.getId());
        }
    }
}
