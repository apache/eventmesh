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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.client.cloudevents.CloudEventsClient;
import org.apache.eventmesh.runtime.cluster.ClusterMembership;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.cloudevents.CloudEvent;

/**
 * End-to-end verification of the two cluster features that operators feel most: session load
 * balancing ({@code GET /session/recommend}) and instance restart / crash switching — driven
 * end-to-end through the <b>Java SDK</b> ({@code CloudEventsClient}) over real HTTP against a
 * <b>real Kafka broker</b> (storage) + <b>real Nacos</b> (MetaStore / cluster coordination).
 *
 * <p>Shared infrastructure (instance lifecycle, SDK helpers, cluster-state probes) lives in
 * {@link ClusterE2EHarness}; this class focuses on the balancing / failover scenarios:</p>
 *
 * <ol>
 *   <li><b>recommend returns a live instance</b> — both instances see each other via the Nacos
 *       heartbeat prefix and {@code /session/recommend} yields one of the two advertised URLs.</li>
 *   <li><b>overload avoidance</b> — pushing &gt;5 MB/s of publish inflow into A makes every
 *       {@code /session/recommend} call (even one served BY A) yield B, proving the negative
 *       feedback term of the scoring function end-to-end.</li>
 *   <li><b>graceful restart switching</b> — a subscriber pinned to A survives A's shutdown: the
 *       SDK re-recommends onto B, re-subscribes there, and receives every event (no loss). A then
 *       restarts on the same port/instance-id and rejoins the live set.</li>
 *   <li><b>crash (SIGKILL) switching</b> — instance A runs as a child JVM that is SIGKILLed
 *       (no shutdown hook, no {@code leave()}). Its Nacos registration disappears (ephemeral
 *       naming entry / heartbeat TTL), recommend stops returning it, and the subscriber
 *       re-attaches to B with no message loss.</li>
 * </ol>
 *
 * <p><b>Gated by {@code -Dit.nacos=host:port}</b> (real MetaStore required; the cluster features
 * under test need a shared store) and auto-skips when Kafka is unreachable. Run:</p>
 * <pre>
 *   # local infra: docker run nacos (8848) + kafka (9092)
 *   gradle :eventmesh-runtime:e2eTest5x \
 *     --tests "org.apache.eventmesh.runtime.it.LoadBalancingAndFailoverE2EIntegrationTest" \
 *     -Dit.nacos=127.0.0.1:8848 -Dit.storage=kafka
 * </pre>
 */
@EnabledIfSystemProperty(named = "it.nacos", matches = ".+")
class LoadBalancingAndFailoverE2EIntegrationTest extends ClusterE2EHarness {

    // ------------------------------------------------------------------------------------------------
    // 1. Load balancing: recommend sees the whole live cluster
    // ------------------------------------------------------------------------------------------------

    @Test
    void recommendReturnsLiveInstanceWhenClustered() throws Exception {
        appA = bootInstance(instanceA, PORT_A, ADMIN_A, "A");
        appB = bootInstance(instanceB, PORT_B, ADMIN_B, "B");
        waitForHeartbeats(instanceA, instanceB);

        // Both instances (whichever serves the call) must resolve one of the two live instances.
        String fromA = recommendVia(urlA, "c-lb-1");
        String fromB = recommendVia(urlB, "c-lb-2");
        assertTrue(isLiveUrl(fromA), "recommend via A must return a live instance URL, got: " + fromA);
        assertTrue(isLiveUrl(fromB), "recommend via B must return a live instance URL, got: " + fromB);

        // The membership view really is cluster-wide (both heartbeats in the shared Meta).
        Map<String, String> live = nacosProbe.getWithPrefix(ClusterMembership.INSTANCE_PREFIX);
        assertTrue(live.containsKey(ClusterMembership.INSTANCE_PREFIX + instanceA),
            "instance A must heartbeat into the shared Meta: " + live.keySet());
        assertTrue(live.containsKey(ClusterMembership.INSTANCE_PREFIX + instanceB),
            "instance B must heartbeat into the shared Meta: " + live.keySet());
    }

    // ------------------------------------------------------------------------------------------------
    // 2. Load balancing: overload negative feedback (inflow > 5MB/s → peers win)
    // ------------------------------------------------------------------------------------------------

    @Test
    void overloadedInstanceIsAvoidedByRecommend() throws Exception {
        appA = bootInstance(instanceA, PORT_A, ADMIN_A, "A");
        appB = bootInstance(instanceB, PORT_B, ADMIN_B, "B");
        waitForHeartbeats(instanceA, instanceB);

        // Hammer instance A with publish inflow well above the 5 MB/s overload threshold. The
        // LoadMeter samples every heartbeat tick (5s), so we push ~128 MB to keep several
        // consecutive windows above the threshold even if the push spans 2-3 windows. Events stay
        // at 512 KB: the runtime rejects publish bodies over MAX_MESSAGE_SIZE (1 MB) with 413.
        CloudEventsClient hammer = client(urlA, "hammer");
        byte[] payload = new byte[512 * 1024];
        java.util.Arrays.fill(payload, (byte) 'x');
        long pushedBytes = 0L;
        for (int i = 0; i < 256 && pushedBytes < 128L * 1024 * 1024; i++) {
            CloudEvent e = CloudEventsClient.event("ovl-" + i, "it", "lb.overload", payload);
            if (hammer.publish(topic, e)) {
                pushedBytes += payload.length;
            }
        }
        log("hammered %d MB into A (overload threshold is 5 MB/s)", pushedBytes / (1024 * 1024));
        assertTrue(pushedBytes >= 64L * 1024 * 1024,
            "need enough publish inflow to trip the overload feedback, pushed only " + pushedBytes);

        // While A is hot, EVERY recommend — even one served by A itself — must yield B.
        Set<String> seen = new HashSet<>();
        waitFor("recommend yields the peer while A is overloaded", 40_000L, () -> {
            seen.add(recommendVia(urlA, "c-ovl"));
            return seen.contains(urlB);
        });
        assertTrue(seen.contains(urlB),
            "recommend must avoid the overloaded instance and yield B; saw " + seen);
    }

    // ------------------------------------------------------------------------------------------------
    // 3. Instance restart (graceful): subscriber switches to the peer, then A rejoins
    // ------------------------------------------------------------------------------------------------

    @Test
    void gracefulRestartSwitchesSubscriberToPeerThenRejoins() throws Exception {
        appA = bootInstance(instanceA, PORT_A, ADMIN_A, "A");
        appB = bootInstance(instanceB, PORT_B, ADMIN_B, "B");
        waitForHeartbeats(instanceA, instanceB);

        // Subscriber pinned to A (SDK pins poll to the instanceUrl returned by /events/subscribe).
        CloudEventsClient subOnA = client(urlA, "c-graceful");
        subOnA.subscribe(topic, "BROADCAST", e -> received.add(e.getId()));
        Thread.sleep(6_000L); // pull-loop assign/seek + cluster subscription propagation

        // Publish THROUGH B while the subscriber sits on A (exercises cross-instance delivery).
        publishVia(urlB, "g", 5);
        waitFor("5 events delivered to the A-pinned subscriber", 40_000L,
            () -> distinct(received).size() >= 5);

        // --- graceful restart of A: shutdown hook runs leave() → heartbeat key dropped at once ---
        appA.shutdown();
        appA = null;

        // With A gone, recommend (served by B) must never hand out A's URL again.
        waitFor("recommend stops returning the left instance", 10_000L, () -> {
            String url = recommendVia(urlB, "c-graceful-2");
            return !urlA.equals(url);
        });
        assertNotEquals(urlA, recommendVia(urlB, "c-graceful-2"),
            "a left instance must not be recommended anymore");

        // --- SDK failover: re-recommend onto the survivor and re-subscribe there ---
        String target = recommendVia(urlB, "c-graceful-3");
        assertTrue(isLiveUrl(target), "failover recommend must return a live instance, got: " + target);
        CloudEventsClient subOnB = client(target, "c-graceful-b");
        subOnB.subscribe(topic, "BROADCAST", e -> received.add(e.getId()));
        Thread.sleep(6_000L);

        // No loss across the switch: 5 old + 5 new, duplicates tolerated (at-least-once).
        publishVia(urlB, "h", 5);
        waitFor("5 more events delivered after the switch", 40_000L,
            () -> distinct(received).size() >= 10);
        assertEquals(10, distinct(received).size(),
            "no message loss across a graceful instance restart; got " + received);

        // --- A restarts on the same port with the same instance id → rejoins the live set ---
        appA = bootInstance(instanceA, PORT_A, ADMIN_A, "A");
        waitForHeartbeats(instanceA, instanceB);
        Map<String, String> live = nacosProbe.getWithPrefix(ClusterMembership.INSTANCE_PREFIX);
        assertEquals(2, live.size(), "restarted instance must rejoin the live set: " + live.keySet());

        // The rebalanced cluster still delivers end-to-end (partition ownership re-split).
        publishVia(urlB, "i", 3);
        waitFor("events delivered after the restarted instance rejoined", 40_000L,
            () -> distinct(received).size() >= 13);
        assertEquals(13, distinct(received).size(),
            "no message loss after rejoin; got " + received);
    }

    // ------------------------------------------------------------------------------------------------
    // 4. Instance crash (SIGKILL, no leave()): TTL/expiry-based switching
    // ------------------------------------------------------------------------------------------------

    @Test
    void crashKillSwitchesAfterHeartbeatExpiry() throws Exception {
        appB = bootInstance(instanceB, PORT_B, ADMIN_B, "B");

        // Instance A runs as a CHILD JVM (its own process) so it can be SIGKILLed for real.
        spawnChildInstance(instanceCrash, PORT_CRASH, ADMIN_CRASH, "crash");

        // Subscriber pinned to the child A; publish through the child A as well.
        CloudEventsClient subOnCrash = client(urlCrash, "c-crash");
        subOnCrash.subscribe(topic, "BROADCAST", e -> received.add(e.getId()));
        Thread.sleep(6_000L);
        publishVia(urlCrash, "c", 5);
        waitFor("5 events delivered to the child-A subscriber", 40_000L,
            () -> distinct(received).size() >= 5);

        // --- SIGKILL: no shutdown hook, no leave(); the heartbeat entry must expire on its own ---
        crashChild.destroyForcibly();
        assertTrue(crashChild.waitFor(15, TimeUnit.SECONDS), "SIGKILLed child must exit");
        log("child A SIGKILLed (exit=%s); waiting for its heartbeat to expire", crashChild.exitValue());

        // Nacos naming entries are ephemeral (dropped with the dead connection) and/or pruned by
        // the 15s heartbeat TTL — either way the dead instance must leave the live set.
        waitFor("crashed instance leaves the live set", HEARTBEAT_TTL_MS + 25_000L, () ->
            !nacosProbe.getWithPrefix(ClusterMembership.INSTANCE_PREFIX)
                .containsKey(ClusterMembership.INSTANCE_PREFIX + instanceCrash));
        assertNotEquals(urlCrash, recommendVia(urlB, "c-crash-2"),
            "a crashed (expired) instance must not be recommended anymore");

        // --- SDK failover onto the survivor B ---
        CloudEventsClient subOnB = client(urlB, "c-crash-b");
        subOnB.subscribe(topic, "BROADCAST", e -> received.add(e.getId()));
        Thread.sleep(6_000L);
        publishVia(urlB, "d", 5);
        waitFor("5 more events delivered after the crash switch", 40_000L,
            () -> distinct(received).size() >= 10);
        assertEquals(10, distinct(received).size(),
            "no message loss across a crash switch; got " + received);
    }
}
