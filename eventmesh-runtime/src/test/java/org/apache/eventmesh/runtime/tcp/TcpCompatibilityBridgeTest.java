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

package org.apache.eventmesh.runtime.tcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.delivery.AckCallback;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.subscription.DistributionMode;
import org.apache.eventmesh.runtime.tcp.TcpAckRegistry;
import org.apache.eventmesh.runtime.tcp.TcpIngressBridge;
import org.apache.eventmesh.runtime.tcp.internal.TcpFrameCodec;
import org.apache.eventmesh.runtime.tcp.internal.TcpPushChannel;
import org.apache.eventmesh.runtime.tcp.internal.TcpRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

class TcpCompatibilityBridgeTest {

    @Test
    void egressDeliversFrameThenClientAckResolvesCallback() {
        TcpAckRegistry registry = new TcpAckRegistry();
        List<byte[]> written = new ArrayList<>();
        TcpPushChannel channel = new TcpPushChannel(
            new StubCodec(), frame -> {
                written.add(frame);
            }, registry);

        RecordingCallback cb = new RecordingCallback();
        channel.deliver("d-1", EventMeshFrame.fromCloudEvent(event("e-1")), cb);

        assertEquals(1, written.size(), "push frame written to the socket");
        assertEquals(1, registry.pending(), "ACK callback parked until client ACKs");

        assertTrue(registry.onClientAck("d-1"), "client ACK resolves the delivery");
        assertEquals(1, cb.acks.get());
        assertEquals(0, registry.pending());
    }

    @Test
    void ingressRoutesPublishSubscribeAckToCore() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        UniIngressService ingress = new UniIngressService(storage, new InMemoryOffsetStore());
        TcpAckRegistry registry = new TcpAckRegistry();
        TcpIngressBridge bridge = new TcpIngressBridge(ingress, registry, (clientId, frame) -> {
            String s = new String(frame, StandardCharsets.UTF_8);
            if (s.startsWith("PUB:")) {
                String topic = s.substring("PUB:".length(), s.indexOf('|'));
                return TcpRequest.publish(topic,
                    org.apache.eventmesh.common.wire.EventMeshFrame.fromCloudEvent(event(s.substring(s.indexOf('|') + 1))));
            }
            if (s.startsWith("SUB:")) {
                String[] parts = s.substring("SUB:".length()).split("/");
                return TcpRequest.subscribe(parts[0], parts[1], DistributionMode.BROADCAST);
            }
            if (s.startsWith("ACK:")) {
                return TcpRequest.ack(s.substring("ACK:".length()));
            }
            return null;
        });

        // PUBLISH frame → core persists the event.
        bridge.onClientFrame("c1", "PUB:orders|e-1".getBytes(StandardCharsets.UTF_8)).get();
        assertTrue(storage.queueOf("orders").stream().anyMatch(e -> "e-1".equals(e.getId())));

        // SUBSCRIBE frame → core registers the subscription.
        bridge.onClientFrame("c1", "SUB:orders/c1".getBytes(StandardCharsets.UTF_8)).get();
        assertEquals(1, ingress.getSubscriptionManager().activeSubscriptions("orders").size());

        // ACK frame → resolves an egress delivery previously parked in the registry.
        RecordingCallback parked = new RecordingCallback();
        registry.register("d-9", parked);
        bridge.onClientFrame("c1", "ACK:d-9".getBytes(StandardCharsets.UTF_8)).get();
        assertEquals(1, parked.acks.get());
    }

    private static CloudEvent event(String id) {
        return CloudEventBuilder.v1().withId(id).withSource(URI.create("svc")).withType("t").build();
    }

    /** Deterministic codec: push frame = "PUSH:&lt;deliveryId&gt;:&lt;eventId&gt;"; ACK frame = "ACK:&lt;deliveryId&gt;". */
    private static final class StubCodec implements TcpFrameCodec {

        @Override
        public byte[] encodePush(String deliveryId, EventMeshFrame frame) {
            return ("PUSH:" + deliveryId + ":" + frame.attributes().get("id")).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String extractDeliveryIdFromAck(byte[] ackFrame) {
            String s = new String(ackFrame, StandardCharsets.UTF_8);
            return s.startsWith("ACK:") ? s.substring("ACK:".length()) : null;
        }
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

    private static final class InMemoryStorage implements MeshStoragePlugin {

        private final ConcurrentHashMap<String, Queue<CloudEvent>> queues = new ConcurrentHashMap<>();

        Queue<CloudEvent> queueOf(String topic) {
            return queues.getOrDefault(topic, new java.util.LinkedList<>());
        }

        @Override
        public void init(Properties properties) {
        }

        @Override
        public void send(String topic, EventMeshFrame frame, SendCallback callback) {
            CloudEvent event = frame.toCloudEvent();
            queues.computeIfAbsent(topic, k -> new ConcurrentLinkedQueue<>()).offer(event);
            SendResult r = new SendResult();
            r.setMessageId(event.getId());
            r.setTopic(topic);
            callback.onSuccess(r);
        }

        @Override
        public List<EventMeshFrame> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            return new ArrayList<>();
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
