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

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.runtime.admin.UniAdminService;
import org.apache.eventmesh.runtime.cluster.ClusterCoordinator;
import org.apache.eventmesh.runtime.cluster.ClusterMembership;
import org.apache.eventmesh.runtime.cluster.ClusterSubscriptionStore;
import org.apache.eventmesh.runtime.cluster.HttpForwarder;
import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;
import org.apache.eventmesh.runtime.cluster.MetaStore;
import org.apache.eventmesh.runtime.http.UniHttpServer;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.push.BufferedEvent;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Two-instance cluster integration test (§13.2): instance A and instance B share one {@link MetaStore}
 * and route events across instances via {@link ClusterCoordinator} + {@link HttpForwarder} over real
 * HTTP. A subscriber on A registers cluster-wide; a publisher on B publishes; B's coordinator sees
 * the subscriber lives on A and forwards via {@code POST /internal/forward}; A delivers locally; the
 * subscriber polls A and receives the event. In-memory storage stub — no broker.
 *
 * <p>Each instance's {@code selfInstanceId} is {@code localhost:<trafficPort>} so {@code addressOf}
 * resolves to a reachable HTTP address. Cluster-wide subscription is registered via
 * {@link ClusterCoordinator#subscribe} (the HTTP {@code /events/subscribe} path is local-only).</p>
 */
class ClusterForwardIntegrationTest {

    private static final String TOPIC = "cross";

    private Instance instA;
    private Instance instB;

    @AfterEach
    void tearDown() {
        if (instA != null) {
            instA.close();
        }
        if (instB != null) {
            instB.close();
        }
    }

    @Test
    void publishOnBdeliversToSubscriberOnA() throws Exception {
        MetaStore meta = new InMemoryMetaStore();
        instA = boot(meta, "A"); // selfInstanceId set to localhost:<portA> inside boot
        instB = boot(meta, "B");

        // Subscriber c1 lives on A. ingress.subscribe now registers cluster-wide automatically
        // (fix: previously the HTTP /events/subscribe path was local-only and a peer's publish
        // never reached this subscriber).
        instA.ingress.subscribe(TOPIC, "c1", org.apache.eventmesh.runtime.subscription.DistributionMode.BROADCAST, null);
        // Heartbeat so the other instance can resolve A's HTTP address.
        instA.membership.heartbeat();
        instB.membership.heartbeat();
        Thread.sleep(200); // let the Meta writes settle

        // Publish on B + pull-and-dispatch: B's coordinator sees c1 is on A → HTTP-forward to A.
        CloudEvent event = CloudEventBuilder.v1()
            .withId("x1").withSource(URI.create("it")).withType("it.event").build();
        instB.ingress.publish(TOPIC, event).get(5, TimeUnit.SECONDS);
        instB.ingress.pullAndDispatch(TOPIC, 100, 0L);

        // A delivered locally (via /internal/forward → ingress.deliverLocal) → c1 polls A.
        List<BufferedEvent> received = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (received.isEmpty() && System.nanoTime() < deadline) {
            received.addAll(instA.ingress.poll("c1", 100, 100L));
        }
        assertEquals(1, received.size(), "event published on B should be forwarded to A and delivered to c1");
        assertEquals("x1", received.get(0).getEvent().getId());
    }

    /** Boot one instance: traffic HTTP server on port 0, cluster wired with selfInstanceId=address. */
    private Instance boot(MetaStore meta, String tag) throws Exception {
        MeshStoragePlugin storage = new InMemoryStorage();
        UniIngressService ingress = new UniIngressService(storage, new InMemoryOffsetStore());
        UniAdminService admin = new UniAdminService(ingress);
        UniHttpServer http = new UniHttpServer(ingress, admin);
        int port = http.start(0);
        String selfId = "localhost:" + port; // addressOf(selfInstanceId) returns this

        ClusterMembership membership = new ClusterMembership(meta, selfId, selfId, 15_000L, System::currentTimeMillis);
        HttpForwarder forwarder = new HttpForwarder(membership);
        ClusterSubscriptionStore subStore = new ClusterSubscriptionStore(meta);
        ClusterCoordinator coordinator = new ClusterCoordinator(selfId, subStore,
            (topic, clientId, event) -> {
                ingress.deliverLocal(topic, clientId, event);
                return true;
            }, forwarder);
        ingress.withCluster(coordinator);
        return new Instance(ingress, http, membership, coordinator, port);
    }

    private static final class Instance {

        final UniIngressService ingress;
        final UniHttpServer http;
        final ClusterMembership membership;
        final ClusterCoordinator coordinator;
        final int port;

        Instance(UniIngressService ingress, UniHttpServer http, ClusterMembership membership,
            ClusterCoordinator coordinator, int port) {
            this.ingress = ingress;
            this.http = http;
            this.membership = membership;
            this.coordinator = coordinator;
            this.port = port;
        }

        void close() {
            try {
                membership.leave();
            } catch (Exception ignored) {
                // best-effort
            }
            http.stop();
        }
    }

    // ---- in-memory storage (shared logical MQ; each instance has its own map but the test only
    // publishes on B and never polls storage on B, so a per-instance map is fine) ----

    static final class InMemoryStorage implements MeshStoragePlugin {

        private final ConcurrentHashMap<String, Queue<CloudEvent>> queues = new ConcurrentHashMap<>();

        @Override
        public void init(java.util.Properties p) {
            // no-op
        }

        @Override
        public void send(String topic, CloudEvent event, SendCallback cb) {
            queues.computeIfAbsent(topic, k -> new ConcurrentLinkedQueue<>()).offer(event);
            SendResult r = new SendResult();
            r.setMessageId(event.getId());
            r.setTopic(topic);
            cb.onSuccess(r);
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
