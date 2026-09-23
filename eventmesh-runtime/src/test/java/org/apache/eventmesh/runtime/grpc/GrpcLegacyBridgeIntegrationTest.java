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

package org.apache.eventmesh.runtime.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.consumer.EventMeshGrpcConsumer;
import org.apache.eventmesh.client.grpc.producer.EventMeshGrpcProducer;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.grpc.common.Response;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.runtime.boot.UniRuntime;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Integration test booting the legacy gRPC bridge against the in-memory storage backend and
 * driving it with the REAL legacy SDK clients (issue #5411 item 7: "the SDK is its own conformance
 * suite") - {@link EventMeshGrpcProducer} publish / batch publish, and the
 * {@link EventMeshGrpcConsumer} stream subscription receiving what was published.
 *
 * <p>Always on (no broker needed): the memory storage plugin boots in-process, exactly like
 * {@code MemoryStorageE2EIntegrationTest}.</p>
 */
class GrpcLegacyBridgeIntegrationTest {

    private UniRuntime runtime;
    private EventMeshGrpcServer grpc;
    private int port;

    @BeforeEach
    void boot() throws Exception {
        runtime = new UniRuntime(new InMemoryStorage(), new InMemoryOffsetStore(), 50L, 200L, 100, 500L);
        runtime.withStorageConfig(new java.util.Properties());
        runtime.start();

        grpc = new EventMeshGrpcServer(runtime.ingress(), 0,
            (url, body, headers) -> 200,
            event -> event.getData() == null ? new byte[0] : event.getData().toBytes());
        port = grpc.start();
    }

    @AfterEach
    void shutdown() {
        if (grpc != null) {
            grpc.stop();
        }
        if (runtime != null) {
            runtime.shutdown();
        }
    }

    private EventMeshGrpcClientConfig config() {
        return EventMeshGrpcClientConfig.builder()
            .serverAddr("127.0.0.1")
            .serverPort(port)
            .producerGroup("TestProducerGroup")
            .consumerGroup("TestConsumerGroup")
            .env("it-env")
            .idc("it-idc")
            .build();
    }

    @Test
    void sdkPublishReceivesSuccessEnvelope() throws Exception {
        try (EventMeshGrpcProducer producer = new EventMeshGrpcProducer(config())) {
            CloudEvent event = CloudEventBuilder.v1()
                .withId("it-1").withSource(URI.create("/it")).withType("it.event")
                .withSubject("grpc-it-topic")
                .withData("ping".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .build();
            Response resp = producer.publish(event);
            assertNotNull(resp);
            assertEquals(StatusCode.SUCCESS.getRetCode(), resp.getRespCode(),
                "publish should answer the legacy SUCCESS envelope, got: " + resp);
        }
    }

    @Test
    void sdkBatchPublishReceivesSuccessEnvelope() throws Exception {
        try (EventMeshGrpcProducer producer = new EventMeshGrpcProducer(config())) {
            CloudEvent e1 = CloudEventBuilder.v1().withId("b-1").withSource(URI.create("/it"))
                .withType("it.event").withSubject("grpc-it-batch")
                .withData("1".getBytes(java.nio.charset.StandardCharsets.UTF_8)).build();
            CloudEvent e2 = CloudEventBuilder.v1().withId("b-2").withSource(URI.create("/it"))
                .withType("it.event").withSubject("grpc-it-batch")
                .withData("2".getBytes(java.nio.charset.StandardCharsets.UTF_8)).build();
            Response resp = producer.publish(List.of(e1, e2));
            assertNotNull(resp);
            assertEquals(StatusCode.SUCCESS.getRetCode(), resp.getRespCode(),
                "batch publish should answer SUCCESS, got: " + resp);
        }
    }

    @Test
    void sdkStreamSubscriptionReceivesPublishedEvent() throws Exception {
        String topic = "grpc-it-stream";
        List<Object> received = new CopyOnWriteArrayList<>();
        CountDownLatch got = new CountDownLatch(1);

        EventMeshGrpcConsumer consumer = new EventMeshGrpcConsumer(config());
        consumer.init();
        consumer.registerListener(new org.apache.eventmesh.client.grpc.consumer.ReceiveMsgHook<Object>() {

            @Override
            public java.util.Optional<Object> handle(Object msg) {
                received.add(msg);
                got.countDown();
                return java.util.Optional.empty();
            }

            @Override
            public org.apache.eventmesh.common.enums.EventMeshProtocolType getProtocolType() {
                return org.apache.eventmesh.common.enums.EventMeshProtocolType.EVENT_MESH_MESSAGE;
            }
        });
        consumer.subscribe(List.of(new SubscriptionItem(topic,
            SubscriptionMode.CLUSTERING, SubscriptionType.ASYNC)));

        try (EventMeshGrpcProducer producer = new EventMeshGrpcProducer(config())) {
            CloudEvent event = CloudEventBuilder.v1()
                .withId("s-1").withSource(URI.create("/it")).withType("it.event")
                .withSubject(topic)
                .withData("stream-payload".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .build();
            Response resp = producer.publish(event);
            assertNotNull(resp);
            assertEquals(StatusCode.SUCCESS.getRetCode(), resp.getRespCode());
        }

        assertTrue(got.await(15, TimeUnit.SECONDS),
            "stream subscriber did not receive the event within 15s");
        assertEquals(1, received.size());
        consumer.close();
    }

    /** Same in-memory storage fixture the other runtime ITs use (queue-per-topic). */
    private static final class InMemoryStorage implements org.apache.eventmesh.api.storage.MeshStoragePlugin {

        private final java.util.concurrent.ConcurrentHashMap<String,
            java.util.Queue<io.cloudevents.CloudEvent>> queues = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public void init(java.util.Properties properties) {
        }

        @Override
        public void send(String topic, org.apache.eventmesh.common.wire.EventMeshFrame frame,
            org.apache.eventmesh.api.SendCallback callback) {
            io.cloudevents.CloudEvent event = frame.toCloudEvent();
            queues.computeIfAbsent(topic, k -> new java.util.concurrent.ConcurrentLinkedQueue<>()).offer(event);
            org.apache.eventmesh.api.SendResult r = new org.apache.eventmesh.api.SendResult();
            r.setMessageId(event.getId());
            r.setTopic(topic);
            callback.onSuccess(r);
        }

        @Override
        public java.util.List<org.apache.eventmesh.common.wire.EventMeshFrame> poll(
            String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            java.util.Queue<io.cloudevents.CloudEvent> q = queues.get(topic);
            if (q == null) {
                return new java.util.ArrayList<>();
            }
            java.util.List<org.apache.eventmesh.common.wire.EventMeshFrame> out = new java.util.ArrayList<>();
            io.cloudevents.CloudEvent e;
            while (out.size() < maxEvents && (e = q.poll()) != null) {
                out.add(org.apache.eventmesh.common.wire.EventMeshFrame.fromCloudEvent(e));
            }
            return out;
        }

        @Override
        public void assignPartitions(String topic, java.util.List<Integer> partitions) {
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
