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

package org.apache.eventmesh.runtime.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.client.tcp.EventMeshTCPClient;
import org.apache.eventmesh.client.tcp.EventMeshTCPClientFactory;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.UserAgent;
import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.transport.tcp.MeshEventToPackageBody;
import org.apache.eventmesh.runtime.transport.tcp.MeshMessagePackageRouter;
import org.apache.eventmesh.runtime.transport.tcp.TcpAckRegistry;
import org.apache.eventmesh.runtime.transport.tcp.UniTcpServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;

/**
 * Legacy TCP SDK compatibility integration test (§15.6 / v1.10 decision): the real old
 * {@code EventMeshMessage} TCP SDK (the unchanged client jars) connects to the new
 * {@link UniTcpServer} over a real socket, subscribes, publishes, and receives the pushed event —
 * proving the "old TCP clients zero-change" claim end-to-end. In-memory storage stub — no broker.
 *
 * <p>Exercise path: SDK {@code init()} (HELLO + HEARTBEAT) → {@code subscribe} + {@code listen} →
 * a second SDK client {@code publish(EventMeshMessage)} → the runtime dispatches → the subscriber's
 * {@code ReceiveMsgHook} fires with the message. This covers the protocol-management commands
 * (HELLO/HEARTBEAT/LISTEN/SUBSCRIBE) and the message path (publish + ASYNC_MESSAGE_TO_CLIENT push)
 * the {@link UniTcpServer.FrameHandler} now handles directly.</p>
 */
class LegacyTcpClientIntegrationTest {

    private static final String TOPIC = "legacy-tcp-it";

    private UniTcpServer server;
    private int port;
    private EventMeshTCPClient<EventMeshMessage> subClient;
    private EventMeshTCPClient<EventMeshMessage> pubClient;
    private UniIngressService ingress;
    private java.util.concurrent.ScheduledExecutorService driver;
    private InMemoryStorage storage;

    @AfterEach
    void tearDown() throws Exception {
        if (subClient != null) {
            subClient.close();
        }
        if (pubClient != null) {
            pubClient.close();
        }
        if (driver != null) {
            driver.shutdownNow();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void oldSdkPublishesAndSubscriberReceives() throws Exception {
        boot();
        // Give the in-memory storage / dispatch loop a moment to be ready.
        Thread.sleep(200);

        // Subscriber: real old SDK client.
        subClient = EventMeshTCPClientFactory.createEventMeshTCPClient(
            EventMeshTCPClientConfig.builder()
                .host("127.0.0.1").port(port)
                .userAgent(UserAgent.builder().group("sub-1").host("127.0.0.1").port(0)
                    .username("u").password("p").build())
                .build(),
            EventMeshMessage.class);
        subClient.init();
        List<EventMeshMessage> received = new ArrayList<>();
        subClient.registerSubBusiHandler(msg -> {
            received.add(msg);
            return java.util.Optional.empty();
        });
        subClient.subscribe(TOPIC, SubscriptionMode.BROADCASTING, SubscriptionType.ASYNC);
        subClient.listen();

        // Publisher: real old SDK client.
        pubClient = EventMeshTCPClientFactory.createEventMeshTCPClient(
            EventMeshTCPClientConfig.builder()
                .host("127.0.0.1").port(port)
                .userAgent(UserAgent.builder().group("pub-1").host("127.0.0.1").port(0)
                    .username("u").password("p").build())
                .build(),
            EventMeshMessage.class);
        pubClient.init();

        EventMeshMessage msg = new EventMeshMessage();
        msg.setTopic(TOPIC);
        msg.setBody("hello-legacy-tcp");
        pubClient.publish(msg, 10_000L);

        // The runtime's pull-loop (200ms) pulls + dispatches → ASYNC_MESSAGE_TO_CLIENT push → SDK
        // ReceiveMsgHook fires. Allow generous time for the SDK's netty read + callback.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (received.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(1, received.size(), "legacy TCP subscriber should receive the published message");
        assertEquals(TOPIC, received.get(0).getTopic());
        assertTrue(received.get(0).getBody().contains("hello-legacy-tcp"),
            "body should survive the CloudEvent round-trip");
    }

    private void boot() throws Exception {
        storage = new InMemoryStorage();
        ingress = new UniIngressService(storage, new InMemoryOffsetStore());
        // UniIngressService has no scheduler of its own (UniRuntime drives it); run a pull/dispatch
        // loop here so published events get pulled from storage and dispatched to subscribers.
        driver = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "it-tcp-driver");
            t.setDaemon(true);
            return t;
        });
        driver.scheduleAtFixedRate(() -> {
            try {
                ingress.pullAndDispatch(TOPIC, 100, 0L);
                ingress.dispatcherTick();
            } catch (Exception e) {
                // best-effort
            }
        }, 0, 100, java.util.concurrent.TimeUnit.MILLISECONDS);

        server = new UniTcpServer(ingress, new TcpAckRegistry(), new MeshMessagePackageRouter(),
            new MeshEventToPackageBody());
        port = server.start(0);
    }

    // ---- in-memory storage ----

    static final class InMemoryStorage implements MeshStoragePlugin {

        private final ConcurrentHashMap<String, Queue<CloudEvent>> queues = new ConcurrentHashMap<>();

        @Override
        public void init(java.util.Properties p) {
            // no-op
        }

        @Override
        public void send(String topic, EventMeshFrame frame, SendCallback cb) {
            CloudEvent event = frame.toCloudEvent();
            queues.computeIfAbsent(topic, k -> new ConcurrentLinkedQueue<>()).offer(event);
            SendResult r = new SendResult();
            r.setMessageId(event.getId());
            r.setTopic(topic);
            cb.onSuccess(r);
        }

        @Override
        public List<EventMeshFrame> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            Queue<CloudEvent> q = queues.get(topic);
            if (q == null) {
                return new ArrayList<>();
            }
            List<EventMeshFrame> out = new ArrayList<>();
            CloudEvent e;
            while (out.size() < maxEvents && (e = q.poll()) != null) {
                out.add(EventMeshFrame.fromCloudEvent(e));
            }
            return out;
        }

        @Override
        public void assignPartitions(String topic, List<Integer> partitions) {
            // no-op
        }

        @Override
        public void commitOffset(String topic, int partition, long offset) {
            // no-op
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
            // no-op
        }

        @Override
        public void shutdown() {
            // no-op
        }
    }
}
