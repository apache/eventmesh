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

import org.apache.eventmesh.client.cloudevents.CloudEventsClient;
import org.apache.eventmesh.runtime.cluster.ClusterMembership;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Cluster e2e suite #2 — the scenarios the first suite ({@link LoadBalancingAndFailoverE2EIntegrationTest})
 * does not cover, all driven through the <b>Java SDK</b> over real HTTP against real Kafka + Nacos:
 *
 * <ul>
 *   <li><b>Balance</b> — partition ownership converges to the deterministic plan across
 *       membership churn (2 instances → 2/2, a third joins → 2/1/1, it leaves → 2/2 again);
 *       {@code LOAD_BALANCE} mode spreads events across subscribers sitting on DIFFERENT instances
 *       (each event exactly once); the same {@code partitionkey} is sticky to one subscriber.</li>
 *   <li><b>Start/stop takeover</b> — events published while an instance is down are
 *       delivered once the survivor takes its partitions over; clients pinned to the stopped
 *       instance re-attach to the survivor (same clientId → the cluster subscription record moves,
 *       no ghost left behind) and keep receiving after the instance rejoins.</li>
 *   <li><b>Abnormal cases</b> — duplicate subscribe does not duplicate delivery; malformed /
 *       oversized / wrong-method requests are rejected without wedging the server; unsubscribe
 *       stops delivery AND clears the cluster-wide subscription; a client that is SIGKILLed
 *       without unsubscribing is evicted from the shared Meta; a broker restart and a MetaStore
 *       restart (docker-gated) pause delivery but lose nothing that was accepted.</li>
 * </ul>
 *
 * <p>Shared infrastructure lives in {@link ClusterE2EHarness}. Run:</p>
 * <pre>
 *   gradle :eventmesh-runtime:e2eTest5x \
 *     --tests "org.apache.eventmesh.runtime.it.ClusterBalanceAndResilienceE2EIntegrationTest" \
 *     -Dit.nacos=127.0.0.1:8848 -Dit.storage=kafka -Dit.kafka.bootstrap=127.0.0.1:9092
 * </pre>
 */
@EnabledIfSystemProperty(named = "it.nacos", matches = ".+")
class ClusterBalanceAndResilienceE2EIntegrationTest extends ClusterE2EHarness {

    // ------------------------------------------------------------------------------------------------
    // 1. Balance: partition ownership converges to the deterministic plan on membership churn
    // ------------------------------------------------------------------------------------------------

    @Test
    void partitionOwnershipConvergesOnMembershipChanges() throws Exception {
        appA = bootInstance(instanceA, PORT_A, ADMIN_A, "A");
        appB = bootInstance(instanceB, PORT_B, ADMIN_B, "B");
        waitForHeartbeats(instanceA, instanceB);

        // A subscriber is required: the ownership refresh only assigns partitions of topics with at
        // least one cluster-wide subscriber (the pull loop's active topic set).
        client(urlA, "c-own").subscribe(topic, "BROADCAST", e -> received.add(e.getId()));
        Thread.sleep(2 * CLUSTER_CYCLE_MS); // cluster sub propagation + first ownership cycles

        // --- phase 1: two live instances → 4 partitions split 2/2 by the sorted-modulo plan ---
        waitForCompleteOwnership(topic, 90_000L);
        assertPlan("two-instance plan must split the 4 partitions 2/2 (sorted modulo)",
            new int[] {0, 2}, new int[] {1, 3});

        // Delivery works on the converged plan.
        publishVia(urlB, "own-1", 5);
        waitFor("5 events delivered on the two-instance plan", 40_000L,
            () -> distinct(received).size() >= 5);

        // --- phase 2: a third instance joins → 4 partitions re-split 2/1/1 ---
        appC = bootInstance(instanceC, PORT_C, ADMIN_C, "C");
        waitForHeartbeats(instanceC);
        waitFor("three-way ownership converges (2/1/1)", 120_000L, () -> {
            Map<String, Integer> counts = ownershipCounts(topic);
            return counts.getOrDefault(instanceA, 0) == 2
                && counts.getOrDefault(instanceB, 0) == 1
                && counts.getOrDefault(instanceC, 0) == 1;
        });
        assertPlan("three-instance plan must be 2/1/1 (sorted modulo)",
            new int[] {0, 3}, new int[] {1}, new int[] {2});

        // No loss across the rebalance: partitions moved between instances resume from the
        // persisted pull offsets (at-least-once — duplicates tolerated, loss is not).
        publishVia(urlB, "own-2", 5);
        waitFor("events delivered while a third instance joined", 40_000L,
            () -> distinct(received).size() >= 10);

        // --- phase 3: the third instance leaves gracefully → back to 2/2 ---
        appC.shutdown();
        appC = null;
        waitFor("two-way ownership restored after C left", 120_000L, () -> {
            Map<String, Integer> counts = ownershipCounts(topic);
            return counts.getOrDefault(instanceA, 0) == 2 && counts.getOrDefault(instanceB, 0) == 2
                && !counts.containsKey(instanceC);
        });
        assertPlan("after C leaves the plan must return to 2/2 (sorted modulo)",
            new int[] {0, 2}, new int[] {1, 3});

        publishVia(urlB, "own-3", 5);
        waitFor("events delivered after the cluster shrank again", 40_000L,
            () -> distinct(received).size() >= 15);
    }

    // ------------------------------------------------------------------------------------------------
    // 2. Balance: LOAD_BALANCE mode spreads events over subscribers on different instances
    // ------------------------------------------------------------------------------------------------

    @Test
    void loadBalanceModeSpreadsEventsAcrossSubscribersOnBothInstances() throws Exception {
        appA = bootInstance(instanceA, PORT_A, ADMIN_A, "A");
        appB = bootInstance(instanceB, PORT_B, ADMIN_B, "B");
        waitForHeartbeats(instanceA, instanceB);

        List<String> onA = new CopyOnWriteArrayList<>();
        List<String> onB = new CopyOnWriteArrayList<>();
        client(urlA, "c-lb-sub-a").subscribe(topic, "LOAD_BALANCE", e -> onA.add(e.getId()));
        client(urlB, "c-lb-sub-b").subscribe(topic, "LOAD_BALANCE", e -> onB.add(e.getId()));
        Thread.sleep(6_000L); // ownership assign + cluster subscription propagation

        publishVia(urlA, "lb", 20);
        waitFor("all 20 events delivered across the two subscribers", 60_000L,
            () -> distinct(onA).size() + distinct(onB).size() >= 20);

        Set<String> union = new HashSet<>(onA);
        union.addAll(onB);
        Set<String> both = new HashSet<>(onA);
        both.retainAll(onB);
        int a = distinct(onA).size();
        int b = distinct(onB).size();
        log("LOAD_BALANCE split over 20 events: subscriber-on-A=%d subscriber-on-B=%d", a, b);

        assertEquals(20, union.size(),
            "LOAD_BALANCE must deliver every published event to exactly one subscriber; missing: "
                + missing(union, "lb", 20));
        assertTrue(both.isEmpty(),
            "LOAD_BALANCE must NOT hand the same event to two subscribers; overlap: " + both);
        assertTrue(a >= 6 && b >= 6,
            "the 20 events must spread over BOTH subscribers (a starved subscriber means the "
                + "round-robin is broken); got " + a + " / " + b);
    }

    // ------------------------------------------------------------------------------------------------
    // 3. Balance: the same partitionkey is sticky to one subscriber (stable hash, §13.3.3)
    // ------------------------------------------------------------------------------------------------

    @Test
    void samePartitionKeyAlwaysRoutesToTheSameSubscriber() throws Exception {
        appA = bootInstance(instanceA, PORT_A, ADMIN_A, "A");
        appB = bootInstance(instanceB, PORT_B, ADMIN_B, "B");
        waitForHeartbeats(instanceA, instanceB);

        List<String> onA = new CopyOnWriteArrayList<>();
        List<String> onB = new CopyOnWriteArrayList<>();
        client(urlA, "c-key-sub-a").subscribe(topic, "LOAD_BALANCE", e -> onA.add(e.getId()));
        client(urlB, "c-key-sub-b").subscribe(topic, "LOAD_BALANCE", e -> onB.add(e.getId()));
        Thread.sleep(6_000L);

        CloudEventsClient pub = client(urlA, "pub-key");
        publishWithPartitionKey(pub, "order-42", "k42", 8);
        waitFor("all 8 keyed events delivered", 60_000L,
            () -> distinct(onA).size() + distinct(onB).size() >= 8);
        int firstA = distinct(onA).size();
        int firstB = distinct(onB).size();
        log("partitionkey order-42 -> A=%d B=%d", firstA, firstB);
        assertTrue((firstA == 8 && firstB == 0) || (firstA == 0 && firstB == 8),
            "every event carrying the same partitionkey must land on ONE subscriber (stable hash); got "
                + firstA + " / " + firstB);

        // A different key may hash to the other subscriber — but must still be all-or-nothing.
        publishWithPartitionKey(pub, "order-99", "k99", 6);
        waitFor("all 14 events delivered", 60_000L,
            () -> distinct(onA).size() + distinct(onB).size() >= 14);
        int deltaA = distinct(onA).size() - firstA;
        int deltaB = distinct(onB).size() - firstB;
        log("partitionkey order-99 -> A+%d B+%d", deltaA, deltaB);
        assertTrue((deltaA == 6 && deltaB == 0) || (deltaA == 0 && deltaB == 6),
            "a partitionkey must be sticky too: all 6 events to one subscriber; got +" + deltaA + " / +" + deltaB);
    }

    // ------------------------------------------------------------------------------------------------
    // 4. Takeover: a subscriber of a stopped instance re-attaches to the survivor without loss
    // ------------------------------------------------------------------------------------------------

    @Test
    void subscriberReconnectsToSurvivorAfterInstanceStopWithoutLoss() throws Exception {
        appA = bootInstance(instanceA, PORT_A, ADMIN_A, "A");
        appB = bootInstance(instanceB, PORT_B, ADMIN_B, "B");
        waitForHeartbeats(instanceA, instanceB);

        // Subscriber pinned to A (its polls go to A's URL).
        client(urlA, "c-down").subscribe(topic, "BROADCAST", e -> received.add(e.getId()));
        Thread.sleep(6_000L);

        // Baseline: publish through B while the subscriber sits on A (cross-instance delivery).
        publishVia(urlB, "d1", 5);
        waitFor("5 baseline events delivered to the A-pinned subscriber", 40_000L,
            () -> distinct(received).size() >= 5);

        // --- A stops gracefully (leave() + dropAllClusterSubscriptions on shutdown) ---
        appA.shutdown();
        appA = null;
        waitFor("recommend stops returning the stopped instance", 15_000L, () ->
            !urlA.equals(recommendVia(urlB, "c-down-2")));

        // --- takeover: the same clientId re-attaches to the survivor ---
        String target = recommendVia(urlB, "c-down-3");
        assertTrue(isLiveUrl(target), "failover recommend must return a live instance, got: " + target);
        client(target, "c-down").subscribe(topic, "BROADCAST", e -> received.add(e.getId()));
        // The survivor must own the dead instance's partitions before anything else is published:
        // otherwise those partitions are polled by nobody.
        waitFor("the survivor took over every partition of the stopped instance", 40_000L, () -> {
            Map<Integer, String> owners = partitionOwners(topic);
            return owners.size() == PARTITIONS && owners.values().stream().allMatch(instanceB::equals);
        });
        Thread.sleep(6_000L);

        // Fresh events published now must reach the re-attached subscriber — the switch itself
        // loses nothing. (Events published while the client had no home instance at all are not
        // replayed: at-least-once resumes at re-attach, see the two-subscriber case below.)
        publishVia(urlB, "d2", 5);
        waitFor("the 5 post-takeover events delivered to the re-attached subscriber", 60_000L,
            () -> distinct(received).size() >= 10);
        assertEquals(10, distinct(received).size(),
            "no message loss across an instance stop (5 before + 5 after the re-attach); got " + received);
    }

    // ------------------------------------------------------------------------------------------------
    // 5. Takeover: clients pinned to a stopped instance are taken over by the survivor
    // ------------------------------------------------------------------------------------------------

    @Test
    void clientsPinnedToStoppedInstanceAreTakenOverWithoutLoss() throws Exception {
        appA = bootInstance(instanceA, PORT_A, ADMIN_A, "A");
        appB = bootInstance(instanceB, PORT_B, ADMIN_B, "B");
        waitForHeartbeats(instanceA, instanceB);

        List<String> pinA = new CopyOnWriteArrayList<>();
        List<String> pinB = new CopyOnWriteArrayList<>();
        client(urlA, "c-pin-A").subscribe(topic, "BROADCAST", e -> pinA.add(e.getId()));
        client(urlB, "c-pin-B").subscribe(topic, "BROADCAST", e -> pinB.add(e.getId()));
        Thread.sleep(6_000L);

        // Both subscribers (on different instances) receive everything — the cluster view routes
        // each pulled event to its subscriber's home instance.
        publishVia(urlB, "p1", 5);
        waitFor("both pinned subscribers received the baseline", 40_000L,
            () -> distinct(pinA).size() >= 5 && distinct(pinB).size() >= 5);
        assertTrue(clusterSubscription(topic, "c-pin-A") != null,
            "the cluster subscription of c-pin-A must be registered in the shared Meta");

        // --- A stops; its client loses its home instance ---
        appA.shutdown();
        appA = null;
        waitFor("recommend stops returning the stopped instance", 15_000L, () ->
            !urlA.equals(recommendVia(urlB, "c-pin-2")));

        // Takeover must complete before the outage window is exercised: the survivor has to own
        // EVERY partition the stopped instance held, otherwise those partitions are polled by
        // nobody and the events below would simply sit in Kafka.
        waitFor("the survivor took over every partition of the stopped instance", 40_000L, () -> {
            Map<Integer, String> owners = partitionOwners(topic);
            return owners.size() == PARTITIONS && owners.values().stream().allMatch(instanceB::equals);
        });

        // Published while c-pin-A has NO home instance: its cluster subscription was dropped by
        // the stopped instance (that is the point of the shutdown cleanup), so it is not a
        // subscriber at this moment. c-pin-B never moved — the cluster must still hand it every
        // one of these, which is the actual "no loss across an instance stop" guarantee. For
        // c-pin-A delivery resumes where it re-attaches (at-least-once), it is not a replay of
        // history for a client that was not subscribed.
        publishVia(urlB, "p2", 5);

        // --- takeover: same clientId re-subscribes on the survivor ---
        String target = recommendVia(urlB, "c-pin-3");
        assertTrue(isLiveUrl(target), "failover recommend must return a live instance, got: " + target);
        client(target, "c-pin-A").subscribe(topic, "BROADCAST", e -> pinA.add(e.getId()));
        Thread.sleep(6_000L);

        // The subscription record moved to the survivor — no ghost entry pointing at the dead A.
        String sub = clusterSubscription(topic, "c-pin-A");
        assertTrue(sub != null && sub.contains(instanceB),
            "c-pin-A's cluster subscription must now live on the survivor; got: " + sub);

        // c-pin-B was attached the whole time: it must see all 10 (5 before + 5 during the
        // outage), which is what "an instance stop loses no event" means for a live subscriber.
        long deadline = System.currentTimeMillis() + 60_000L;
        while (System.currentTimeMillis() < deadline && distinct(pinB).size() < 10) {
            Thread.sleep(500L);
        }
        assertEquals(10, distinct(pinB).size(),
            "the surviving subscriber lost events published while A was down; got pinB=" + pinB
                + " pinA=" + pinA);

        // c-pin-A keeps everything already delivered before the stop (nothing is retracted) …
        assertTrue(distinct(pinA).size() >= 5,
            "c-pin-A lost events that were delivered before the stop; got " + pinA);
        // … and may additionally see the outage-window events replayed when the survivor took
        // over the dead instance's partitions, but it can never exceed what was published.
        assertTrue(distinct(pinA).size() <= 10,
            "c-pin-A received events that were never published; got " + pinA);

        // --- A rejoins on the same port/instance-id: the rebalanced cluster keeps delivering ---
        appA = bootInstance(instanceA, PORT_A, ADMIN_A, "A");
        waitForHeartbeats(instanceA, instanceB);
        int pinAHighWater = distinct(pinA).size();
        publishVia(urlB, "p3", 3);
        // Both subscribers are attached now, so all 3 new events must reach both of them however
        // the rebalanced plan splits the partitions between A and B.
        waitFor("both subscribers received the events published after the rejoin", 40_000L,
            () -> distinct(pinA).size() >= pinAHighWater + 3 && distinct(pinB).size() >= 13);
        assertEquals(pinAHighWater + 3, distinct(pinA).size(),
            "no loss after the rejoin (c-pin-A); got " + pinA);
        assertEquals(13, distinct(pinB).size(), "no loss after the rejoin (c-pin-B); got " + pinB);
    }

    // ------------------------------------------------------------------------------------------------
    // 6. Abnormal: duplicate subscribe (same clientId) must not duplicate or lose events
    // ------------------------------------------------------------------------------------------------

    @Test
    void duplicateSubscribeDoesNotDuplicateDelivery() throws Exception {
        appA = bootInstance(instanceA, PORT_A, ADMIN_A, "A");
        appB = bootInstance(instanceB, PORT_B, ADMIN_B, "B");
        waitForHeartbeats(instanceA, instanceB);

        // Two connections of the SAME logical client (a reconnect that forgot to unsubscribe):
        // the cluster view is keyed by clientId, so both registrations collapse into one target.
        List<String> first = new CopyOnWriteArrayList<>();
        List<String> second = new CopyOnWriteArrayList<>();
        client(urlA, "c-dup").subscribe(topic, "BROADCAST", e -> first.add(e.getId()));
        client(urlA, "c-dup").subscribe(topic, "BROADCAST", e -> second.add(e.getId()));
        Thread.sleep(6_000L);

        publishVia(urlB, "dup", 5);
        waitFor("all 5 events delivered to the duplicated client", 40_000L,
            () -> distinct(first).size() + distinct(second).size() >= 5);

        List<String> all = new ArrayList<>(first);
        all.addAll(second);
        assertEquals(5, distinct(all).size(),
            "a duplicate subscribe must not lose events; got " + all);
        assertEquals(5, all.size(),
            "a duplicate subscribe must not duplicate events (cluster view dedups by clientId); got " + all);
    }

    // ------------------------------------------------------------------------------------------------
    // 7. Abnormal: malformed / oversized / wrong-method requests are rejected, server stays healthy
    // ------------------------------------------------------------------------------------------------

    @Test
    void malformedRequestsAreRejectedAndServerStaysHealthy() throws Exception {
        appA = bootInstance(instanceA, PORT_A, ADMIN_A, "A");
        appB = bootInstance(instanceB, PORT_B, ADMIN_B, "B");
        waitForHeartbeats(instanceA, instanceB);

        String topicParam = "/events/publish?topic=" + java.net.URLEncoder.encode(topic, StandardCharsets.UTF_8);

        // Oversized body → 413. The runtime rejects on the Content-Length header and closes the
        // connection without draining the 2 MB body, so the client may see the status line (413)
        // or a reset before it finishes writing (status 0). Both mean "rejected unread".
        int oversized = postRaw(urlA, topicParam, new byte[2 * 1024 * 1024], "application/cloudevents+json");
        assertTrue(oversized == 413 || oversized == 0,
            "an oversized publish body must be rejected (413, or a dropped connection), got " + oversized);

        // Malformed CloudEvent → 400.
        int malformed = postRaw(urlA, topicParam, "this-is-not-a-cloudevent".getBytes(StandardCharsets.UTF_8),
            "application/cloudevents+json");
        assertEquals(400, malformed, "a malformed publish body must be rejected with 400");

        // Missing topic query param → 400.
        int noTopic = postRaw(urlA, "/events/publish", ceJson("bad-1"), "application/cloudevents+json");
        assertEquals(400, noTopic, "a publish without the topic param must be rejected with 400");

        // Wrong method → 405.
        int wrongMethod = requestRaw(urlA, topicParam, "GET");
        assertEquals(405, wrongMethod, "GET on the publish endpoint must be rejected with 405");

        // Subscribe without mode → 400.
        int noMode = postRaw(urlB, "/events/subscribe",
            ("{\"clientId\":\"c-bad-sub\",\"topic\":\"" + topic + "\"}").getBytes(StandardCharsets.UTF_8),
            "application/json");
        assertEquals(400, noMode, "a subscribe without mode must be rejected with 400");

        // --- the servers that just absorbed the abuse must be fully functional ---
        List<String> got = new CopyOnWriteArrayList<>();
        client(urlB, "c-healthy").subscribe(topic, "BROADCAST", e -> got.add(e.getId()));
        Thread.sleep(6_000L);
        publishVia(urlA, "healthy", 5);
        waitFor("events delivered after the malformed-request abuse", 40_000L,
            () -> distinct(got).size() >= 5);
        assertEquals(5, distinct(got).size(), "no event lost after the abuse; got " + got);

        // Control plane still answers on the instance that took the worst of it.
        assertTrue(isLiveUrl(recommendVia(urlA, "c-healthy-2")),
            "recommend must keep working on the abused instance");
    }

    // ------------------------------------------------------------------------------------------------
    // 8. Abnormal: unsubscribe stops delivery AND clears the cluster-wide subscription
    // ------------------------------------------------------------------------------------------------

    @Test
    void unsubscribeStopsDeliveryAndClearsTheClusterSubscription() throws Exception {
        appA = bootInstance(instanceA, PORT_A, ADMIN_A, "A");
        appB = bootInstance(instanceB, PORT_B, ADMIN_B, "B");
        waitForHeartbeats(instanceA, instanceB);

        List<String> keep = new CopyOnWriteArrayList<>();
        List<String> gone = new CopyOnWriteArrayList<>();
        CloudEventsClient subKeep = client(urlA, "c-keep");
        CloudEventsClient subGone = client(urlB, "c-gone");
        subKeep.subscribe(topic, "BROADCAST", e -> keep.add(e.getId()));
        subGone.subscribe(topic, "BROADCAST", e -> gone.add(e.getId()));
        Thread.sleep(6_000L);

        publishVia(urlB, "u1", 3);
        waitFor("both subscribers received the baseline", 40_000L,
            () -> distinct(keep).size() >= 3 && distinct(gone).size() >= 3);
        assertTrue(clusterSubscription(topic, "c-gone") != null,
            "c-gone's subscription must be visible in the shared Meta before the unsubscribe");

        // --- unsubscribe c-gone (server-side, cluster-wide) ---
        subGone.unsubscribe(topic);
        waitFor("c-gone's cluster subscription removed from the shared Meta", 20_000L,
            () -> clusterSubscription(topic, "c-gone") == null);

        publishVia(urlB, "u2", 3);
        waitFor("the remaining subscriber keeps receiving", 40_000L,
            () -> distinct(keep).size() >= 6);
        Thread.sleep(3_000L); // give a wrongly-routed event time to (not) arrive
        assertEquals(6, distinct(keep).size(), "the remaining subscriber must receive everything; got " + keep);
        assertEquals(3, distinct(gone).size(),
            "the unsubscribed client must receive nothing after /events/unsubscribe; got " + gone);
    }

    // ------------------------------------------------------------------------------------------------
    // 9. Abnormal: a client that is SIGKILLed without unsubscribing is evicted from the shared Meta
    // ------------------------------------------------------------------------------------------------

    @Test
    void crashedClientSubscriptionIsEvictedFromClusterMeta() throws Exception {
        appA = bootInstance(instanceA, PORT_A, ADMIN_A, "A");

        // A client in its OWN JVM — SIGKILLing it sends no /events/unsubscribe (no shutdown hook).
        String deadClient = "c-crash-" + runId;
        spawnJavaChild("stale-client.log", java.util.List.of(), StaleClientChild.class.getName(),
            java.util.List.of(urlA, topic, deadClient));
        waitFor("the doomed client's subscription registered in the shared Meta", 40_000L,
            () -> clusterSubscription(topic, deadClient) != null);

        crashChild.destroyForcibly();
        assertTrue(crashChild.waitFor(15, TimeUnit.SECONDS), "SIGKILLed client child must exit");
        Thread.sleep(1_000L); // let its last poll age out

        // UniRuntime's scheduler runs exactly this reaper every 60s with a 60s threshold
        // (UniRuntime: "cleanupStaleClients(60_000)"). Drive it directly to keep the test fast —
        // what is under test is that eviction PROPAGATES: the local subscription AND the
        // cluster-wide /em/subs entry must both go, otherwise every peer keeps a ghost
        // subscription for a client that no longer exists.
        int evicted = appA.runtime().ingress().cleanupStaleClients(1L);
        log("stale-client reaper evicted %d subscription(s)", evicted);
        assertTrue(evicted >= 1, "the crashed client's subscription must be evicted locally");

        waitFor("the crashed client's subscription removed from the shared Meta", 20_000L,
            () -> clusterSubscription(topic, deadClient) == null);
        assertNull(clusterSubscription(topic, deadClient),
            "a crashed client must not leave a ghost subscription in the shared Meta");
    }

    // ------------------------------------------------------------------------------------------------
    // 10. Abnormal: broker restart — delivery pauses, nothing accepted is lost (docker-gated)
    // ------------------------------------------------------------------------------------------------

    @Test
    void brokerOutageLosesNothingThatWasAccepted() throws Exception {
        Assumptions.assumeTrue(dockerCanRestart(kafkaContainer()),
            "docker container '" + kafkaContainer() + "' not available — skipping the broker-outage test");
        appA = bootInstance(instanceA, PORT_A, ADMIN_A, "A");
        appB = bootInstance(instanceB, PORT_B, ADMIN_B, "B");
        waitForHeartbeats(instanceA, instanceB);

        client(urlA, "c-kafka").subscribe(topic, "BROADCAST", e -> received.add(e.getId()));
        Thread.sleep(6_000L);
        publishVia(urlB, "k1", 5);
        waitFor("5 baseline events delivered", 40_000L, () -> distinct(received).size() >= 5);

        // The control plane (membership / recommend) does not depend on the broker.
        assertTrue(isLiveUrl(recommendVia(urlB, "c-kafka-2")),
            "recommend must keep answering while the broker is down");

        log("restarting the Kafka container ({})...", kafkaContainer());
        dockerRestart(kafkaContainer());
        log("Kafka is back; waiting for the runtime to reconnect");

        // The producer reconnects on its own; poll until the runtime accepts publishes again.
        CloudEventsClient probe = client(urlB, "pub-k2-probe");
        waitFor("publish accepted again after the broker restart", 240_000L, () ->
            probe.publish(topic, CloudEventsClient.event("k2-probe", "it", "lb.e2e",
                "probe".getBytes(StandardCharsets.UTF_8))));

        publishVia(urlB, "k2", 5);
        waitFor("all events delivered after the broker restart", 90_000L,
            () -> distinct(received).size() >= 11);
        assertEquals(11, distinct(received).size(),
            "a broker restart must not lose accepted events (5 + 1 probe + 5); got " + received);
    }

    // ------------------------------------------------------------------------------------------------
    // 11. Abnormal: MetaStore restart — polling pauses (lease gate), delivery resumes, no loss
    // ------------------------------------------------------------------------------------------------

    @Test
    void metaStoreOutagePausesPollingThenRecoversWithoutLoss() throws Exception {
        Assumptions.assumeTrue(dockerCanRestart(nacosContainer()),
            "docker container '" + nacosContainer() + "' not available — skipping the MetaStore-outage test");
        appA = bootInstance(instanceA, PORT_A, ADMIN_A, "A");
        appB = bootInstance(instanceB, PORT_B, ADMIN_B, "B");
        waitForHeartbeats(instanceA, instanceB);

        client(urlA, "c-meta").subscribe(topic, "BROADCAST", e -> received.add(e.getId()));
        Thread.sleep(6_000L);
        publishVia(urlB, "n1", 5);
        waitFor("5 baseline events delivered", 40_000L, () -> distinct(received).size() >= 5);

        log("restarting the Nacos container ({})...", nacosContainer());
        dockerRestart(nacosContainer());

        // Publishing does NOT touch the MetaStore: these 5 are accepted and land in Kafka while
        // the instances' heartbeats fail (PartitionOwnership's lease gate stops polling rather
        // than degrading to a stale "self owns all" view — split-brain prevention).
        publishVia(urlB, "n2", 5);
        log("published 5 events during the Meta outage; they must be delivered after recovery");

        // Recovery: both instances heartbeat into the shared Meta again (fresh timestamps).
        waitFor("both instances heartbeat again after the Meta restart", 180_000L, () -> {
            try {
                Map<String, String> live = nacosProbe.getWithPrefix(ClusterMembership.INSTANCE_PREFIX);
                long now = System.currentTimeMillis();
                for (String id : new String[] {instanceA, instanceB}) {
                    String v = live.get(ClusterMembership.INSTANCE_PREFIX + id);
                    if (v == null) {
                        return false;
                    }
                    int sep = v.indexOf('|');
                    if (sep < 0) {
                        return false;
                    }
                    if (now - Long.parseLong(v.substring(0, sep)) > 20_000L) {
                        return false;
                    }
                }
                return true;
            } catch (RuntimeException e) {
                return false; // Meta still unreachable
            }
        });

        // Polling resumed: the events accepted during the outage are delivered late, not lost.
        waitFor("all 10 events delivered after the Meta restart", 120_000L,
            () -> distinct(received).size() >= 10);
        assertEquals(10, distinct(received).size(),
            "a MetaStore restart must not lose accepted events (5 + 5 during the outage); got " + received);

        // The cluster still serves new sessions afterwards.
        assertTrue(isLiveUrl(recommendVia(urlB, "c-meta-2")),
            "recommend must work again after the Meta restart");
    }

    // ------------------------------------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------------------------------------

    /** A minimal valid CloudEvents-JSON body (for raw-HTTP negative tests). */
    private static byte[] ceJson(String id) {
        return ("{\"specversion\":\"1.0\",\"id\":\"" + id + "\",\"source\":\"/it\",\"type\":\"lb.e2e\","
            + "\"datacontenttype\":\"application/octet-stream\",\"data\":\"eA==\"}")
            .getBytes(StandardCharsets.UTF_8);
    }

    /** Publish {@code count} events that all carry the same {@code partitionkey} extension. */
    private void publishWithPartitionKey(CloudEventsClient pub, String partitionKey, String idPrefix, int count) {
        for (int i = 1; i <= count; i++) {
            CloudEvent e = CloudEventBuilder.v1()
                .withId(idPrefix + "-" + i)
                .withSource(URI.create("it"))
                .withType("lb.e2e")
                .withExtension("partitionkey", partitionKey)
                .withDataContentType("application/octet-stream")
                .withData(("payload-" + idPrefix + "-" + i).getBytes(StandardCharsets.UTF_8))
                .build();
            assertTrue(pub.publish(topic, e), "publish " + idPrefix + "-" + i + " should be accepted (202)");
        }
    }

    /** Which of the published ids ({@code prefix-1} .. {@code prefix-count}) never arrived. */
    private static Set<String> missing(Set<String> arrived, String prefix, int count) {
        Set<String> out = new HashSet<>();
        for (int i = 1; i <= count; i++) {
            String id = prefix + "-" + i;
            if (!arrived.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /**
     * Assert the ownership map equals the expected deterministic plan: {@code perInstance[i]} lists
     * the partitions instance {@code i} (in constructor order A, B, C) must own.
     */
    private void assertPlan(String message, int[]... perInstance) {
        Map<Integer, String> owners = partitionOwners(topic);
        assertEquals(PARTITIONS, owners.size(),
            message + "; every partition must have exactly one owner, got " + owners);
        String[] ids = {instanceA, instanceB, instanceC};
        for (int i = 0; i < perInstance.length; i++) {
            for (int p : perInstance[i]) {
                assertEquals(ids[i], owners.get(p),
                    message + "; partition " + p + " owner mismatch, full plan: " + owners);
            }
        }
        // And nothing unexpected owns anything.
        for (Map.Entry<Integer, String> e : owners.entrySet()) {
            boolean expected = false;
            for (int[] parts : perInstance) {
                for (int p : parts) {
                    expected |= p == e.getKey();
                }
            }
            assertTrue(expected, message + "; unexpected owner for partition " + e.getKey() + ": " + owners);
        }
    }
}
