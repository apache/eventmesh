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
import org.apache.eventmesh.runtime.admin.UniAdminService;
import org.apache.eventmesh.runtime.cluster.ClusterCoordinator;
import org.apache.eventmesh.runtime.cluster.ClusterMembership;
import org.apache.eventmesh.runtime.cluster.ClusterSubscriptionStore;
import org.apache.eventmesh.runtime.cluster.HttpForwarder;
import org.apache.eventmesh.runtime.cluster.MetaStore;
import org.apache.eventmesh.runtime.cluster.NacosMetaStore;
import org.apache.eventmesh.runtime.http.UniHttpServer;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.push.BufferedEvent;
import org.apache.eventmesh.runtime.subscription.DistributionMode;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Two-instance cluster test against a REAL Nacos Meta (§13.2.6): a subscriber on instance A and a
 * publisher on instance B, where A's storage is empty so delivery can ONLY happen via B forwarding
 * — which requires B to learn of A's subscription through the Meta watch. This is the test that
 * proves the {@link NacosMetaStore} prefix-watch fix (NamingService.subscribe for {@code /em/subs/}),
 * since the InMemoryMetaStore path was already covered by {@code ClusterForwardIntegrationTest}.
 *
 * <p><b>Gated by {@code -Dit.nacos}</b>. No broker needed — each instance has its own in-memory
 * storage (only B's holds the published event).</p>
 */
@EnabledIfSystemProperty(named = "it.nacos", matches = ".+")
class NacosClusterForwardIntegrationTest {

    private static final String TOPIC = "nacos-forward-" + System.nanoTime();

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
    void subscriberOnA_receivesViaForwardFromB_overRealNacos() throws Exception {
        String nacos = System.getProperty("it.nacos");
        // Each instance gets its OWN NacosMetaStore (own naming client + subSnapshot), as in
        // production — sharing one would let A's register populate the shared snapshot and mask the
        // cross-instance watch path under test.
        instA = boot(nacos, "A");
        instB = boot(nacos, "B");

        // Subscriber c1 on A (ingress.subscribe → ClusterCoordinator.subscribe → Nacos /em/subs/).
        instA.ingress.subscribe(TOPIC, "c1", DistributionMode.BROADCAST, null);
        instA.membership.heartbeat();
        instB.membership.heartbeat();

        // Wait for B to discover A's instance (HttpForwarder needs addressOf(A) to forward). The
        // instance heartbeat is a single put; B's naming client sees it via an async push. Nacos
        // push latency varies under load, so allow generous time.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!instB.membership.liveInstances().contains(instA.selfId) && System.nanoTime() < deadline) {
            Thread.sleep(200);
            instA.membership.heartbeat(); // refresh until B sees it (heartbeat lease is short)
        }
        assertTrue(instB.membership.liveInstances().contains(instA.selfId),
            "B should discover A's instance via Nacos NamingService");

        // Wait for B to learn of A's subscription via the Nacos NamingService watch (the fix under
        // test). Until B's subStore sees c1, B's dispatch would find no target and not forward.
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (instB.subStore.targetsFor(TOPIC, null).isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(200);
        }
        assertTrue(!instB.subStore.targetsFor(TOPIC, null).isEmpty(),
            "B should discover A's subscription via the Nacos /em/subs/ watch");

        // Publish on B (lands in B's storage only — A's storage is empty). B's pullAndDispatch pulls
        // it, the coordinator sees c1 lives on A (not self) → HttpForwarder POST /internal/forward
        // → A.deliverLocal → c1's buffer. A's own pullAndDispatch would find nothing (empty storage),
        // so receiving the event PROVES it came via the cross-instance forward.
        CloudEvent event = CloudEventBuilder.v1()
            .withId("nf-1").withSource(URI.create("it")).withType("it.event").build();
        instB.ingress.publish(TOPIC, event).get(5, TimeUnit.SECONDS);
        instB.ingress.pullAndDispatch(TOPIC, 100, 0L);

        List<BufferedEvent> received = new ArrayList<>();
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (received.isEmpty() && System.nanoTime() < deadline) {
            received.addAll(instA.ingress.poll("c1", 100, 100L));
        }
        assertEquals(1, received.size(), "A should receive the event via B's cross-instance forward");
        assertEquals("nf-1", received.get(0).getEvent().getId());
    }

    /** Boot one instance: own NacosMetaStore + own in-memory storage + traffic HTTP (for
     *  /internal/forward) + cluster wired on that MetaStore. */
    private Instance boot(String nacos, String tag) throws Exception {
        MetaStore meta = new NacosMetaStore(nacos);
        MeshStoragePlugin storage = new InMemoryStorage();
        UniIngressService ingress = new UniIngressService(storage, new InMemoryOffsetStore());
        UniAdminService admin = new UniAdminService(ingress);
        UniHttpServer http = new UniHttpServer(ingress, admin);
        int port = http.start(0);
        String selfId = "localhost:" + port; // addressOf(selfInstanceId) must be a reachable host:port

        ClusterMembership membership = new ClusterMembership(meta, selfId, selfId, 15_000L, System::currentTimeMillis);
        HttpForwarder forwarder = new HttpForwarder(membership);
        ClusterSubscriptionStore subStore = new ClusterSubscriptionStore(meta);
        ClusterCoordinator coordinator = new ClusterCoordinator(selfId, subStore,
            (topic, clientId, event) -> {
                ingress.deliverLocal(topic, clientId, event);
                return true;
            }, forwarder);
        ingress.withCluster(coordinator);
        return new Instance(ingress, http, membership, subStore, port, selfId);
    }

    private static final class Instance {

        final UniIngressService ingress;
        final UniHttpServer http;
        final ClusterMembership membership;
        final ClusterSubscriptionStore subStore;
        final int port;
        final String selfId;

        Instance(UniIngressService ingress, UniHttpServer http, ClusterMembership membership,
            ClusterSubscriptionStore subStore, int port, String selfId) {
            this.ingress = ingress;
            this.http = http;
            this.membership = membership;
            this.subStore = subStore;
            this.port = port;
            this.selfId = selfId;
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

    // ---- per-instance in-memory storage (only B's holds the published event) ----

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
