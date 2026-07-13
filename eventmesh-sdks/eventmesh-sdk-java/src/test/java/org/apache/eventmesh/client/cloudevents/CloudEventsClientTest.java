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

package org.apache.eventmesh.client.cloudevents;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.runtime.boot.UniRuntime;
import org.apache.eventmesh.runtime.http.UniHttpServer;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end: {@link CloudEventsClient} drives the real {@link UniRuntime} + {@link UniHttpServer}
 * over localhost HTTP — publish → subscribe (long-poll) → handler receives → auto-ACK.
 */
class CloudEventsClientTest {

    private UniRuntime runtime;
    private UniHttpServer server;
    private CloudEventsClient client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.shutdown();
        }
        if (server != null) {
            server.stop();
        }
        if (runtime != null) {
            runtime.shutdown();
        }
    }

    @Test
    void publishSubscribePollAckOverHttp() throws Exception {
        boot();
        List<CloudEvent> received = new ArrayList<>();

        client.subscribe("orders", "BROADCAST", received::add);
        assertTrue(client.publish("orders", CloudEventsClient.event("o-1", "svc", "order.created",
            "hello".getBytes())));

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (received.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(1, received.size());
        assertEquals("o-1", received.get(0).getId());
    }

    private void boot() throws Exception {
        runtime = new UniRuntime(new InMemStorage(), new InMemoryOffsetStore(), 50L, 200L, 100, 500L);
        runtime.start();
        server = new UniHttpServer(runtime.ingress(), new org.apache.eventmesh.runtime.admin.UniAdminService(runtime.ingress()));
        int port = server.start(0);
        client = CloudEventsClient.builder()
            .runtimeUrl("http://127.0.0.1:" + port).clientId("c1").pollIntervalMs(100L).build();
    }

    private static final class InMemStorage implements MeshStoragePlugin {

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
