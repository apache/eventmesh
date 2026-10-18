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

package org.apache.eventmesh.runtime.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.common.wire.EventMeshFrame;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * Ownership convergence semantics of {@link PartitionOwnership} (§13.2.8④).
 *
 * <p>Regression cover for the boot-order freeze: FencingToken bootEpochs are wall-clock seeded,
 * so a later-booted instance's tokens are permanently higher. When a live peer held a partition,
 * the old token-order rule let the later instance fence the earlier one <em>forever</em> —
 * ownership froze on boot order instead of converging to the deterministic
 * {@link PartitionAssigner} plan (and the earlier instance burned a CAS every cycle re-trying).
 * The rule now is: a LIVE peer keeps the partition until it releases it (releaseStale drops
 * everything that left its own assigner share); only a TTL-evicted owner is taken over.</p>
 */
class PartitionOwnershipConvergenceTest {

    private static final String TOPIC = "orders";
    private static final long TTL_MS = 15_000L;

    /** Storage stub recording the last assignPartitions call per topic. */
    private static final class RecordingStorage implements MeshStoragePlugin {

        private volatile List<Integer> lastAssigned = List.of();

        @Override
        public void init(Properties properties) {
        }

        @Override
        public void start() {
        }

        @Override
        public void shutdown() {
        }

        @Override
        public void send(String topic, EventMeshFrame frame, SendCallback callback) {
            SendResult r = new SendResult();
            r.setMessageId(frame.attributes().get("id"));
            r.setTopic(topic);
            callback.onSuccess(r);
        }

        @Override
        public List<EventMeshFrame> poll(String topic, int partition, long startOffset, int maxEvents, long timeoutMs) {
            return Collections.emptyList();
        }

        @Override
        public void assignPartitions(String topic, List<Integer> partitions) {
            this.lastAssigned = List.copyOf(partitions);
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
        public int partitionCount(String topic) {
            return 4;
        }
    }

    private final InMemoryMetaStore meta = new InMemoryMetaStore();
    private final AtomicLong clock = new AtomicLong(1_000_000L);

    /**
     * Divergent startup views must not freeze ownership: B (booted first) grabs all four
     * partitions while it still sees itself alone; A (booted later, HIGHER tokens) must NOT
     * fence the live B — it waits, B releases what left its share, and both converge to the
     * deterministic plan A=[0,2], B=[1,3].
     */
    @Test
    void convergesToAssignerPlanDespiteBootOrderTokenSkew() {
        RecordingStorage storageA = new RecordingStorage();
        RecordingStorage storageB = new RecordingStorage();
        ClusterMembership memberA = new ClusterMembership(meta, "A", "localhost:1", TTL_MS, clock::get,
            new FencingToken(2_000L, new AtomicLong(0))); // A booted LATER → higher tokens
        final ClusterMembership memberB = new ClusterMembership(meta, "B", "localhost:2", TTL_MS, clock::get,
            new FencingToken(1_000L, new AtomicLong(0)));
        final PartitionOwnership ownA = new PartitionOwnership(memberA, meta, storageA, "A", 5_000L, clock::get,
            new FencingToken(2_000L, new AtomicLong(0)));
        PartitionOwnership ownB = new PartitionOwnership(memberB, meta, storageB, "B", 5_000L, clock::get,
            new FencingToken(1_000L, new AtomicLong(0)));

        // Phase 1: only B is live (A has not heartbeated yet) → B self-allocates all partitions.
        assertTrue(memberB.heartbeat());
        ownB.refreshOnce(Set.of(TOPIC));
        assertEquals(List.of(0, 1, 2, 3), ownB.ownedPartitions(TOPIC), "solo B owns every partition");

        // Phase 2: A joins with a HIGHER token base. Its plan (live=[A,B]) is [0,2], but B
        // holds those with a live heartbeat — A must be fenced, NOT take them over.
        assertTrue(memberA.heartbeat());
        ownA.refreshOnce(Set.of(TOPIC));
        assertEquals(List.of(), ownA.ownedPartitions(TOPIC),
            "a live peer's partitions must not be taken over by token order");
        for (int p : new int[] {0, 2}) {
            assertEquals("B", ownerOf(p), "partition #" + p + " must still name the live peer B");
        }

        // Phase 3: B's next cycle moves its share to the plan ([1,3]) and RELEASES 0,2.
        clock.addAndGet(5_000L);
        memberA.heartbeat();
        memberB.heartbeat();
        ownB.refreshOnce(Set.of(TOPIC));
        assertEquals(List.of(1, 3), ownB.ownedPartitions(TOPIC), "B converges to its plan share");

        // Phase 4: A claims the released partitions → converged deterministic assignment.
        ownA.refreshOnce(Set.of(TOPIC));
        assertEquals(List.of(0, 2), ownA.ownedPartitions(TOPIC), "A converges to its plan share");
        assertEquals(List.of(0, 2), storageA.lastAssigned, "A's storage got its share");
        assertEquals(List.of(1, 3), storageB.lastAssigned, "B's storage got its share");
    }

    /**
     * Crash recovery: once the owner's heartbeat expires (TTL), the partition leaves the live
     * set and the survivor takes it over regardless of token order — the fencing rule must not
     * pin a dead instance's partitions forever.
     */
    @Test
    void ttlEvictedOwnerIsTakenOver() {
        RecordingStorage storageA = new RecordingStorage();
        RecordingStorage storageB = new RecordingStorage();
        ClusterMembership memberA = new ClusterMembership(meta, "A", "localhost:1", TTL_MS, clock::get,
            new FencingToken(1_000L, new AtomicLong(0)));
        final ClusterMembership memberB = new ClusterMembership(meta, "B", "localhost:2", TTL_MS, clock::get,
            new FencingToken(2_000L, new AtomicLong(0))); // B holds with the HIGHER token
        final PartitionOwnership ownA = new PartitionOwnership(memberA, meta, storageA, "A", 5_000L, clock::get,
            new FencingToken(1_000L, new AtomicLong(0)));
        PartitionOwnership ownB = new PartitionOwnership(memberB, meta, storageB, "B", 5_000L, clock::get,
            new FencingToken(2_000L, new AtomicLong(0)));

        // B claims everything while alone, then dies WITHOUT leave() (no heartbeat refresh).
        assertTrue(memberB.heartbeat());
        ownB.refreshOnce(Set.of(TOPIC));
        assertEquals(List.of(0, 1, 2, 3), ownB.ownedPartitions(TOPIC));

        // A is live but fenced from the live B...
        assertTrue(memberA.heartbeat());
        ownA.refreshOnce(Set.of(TOPIC));
        assertEquals(List.of(), ownA.ownedPartitions(TOPIC));

        // ...B's heartbeat expires (TTL) → B is evicted from the live set → A takes over.
        clock.addAndGet(TTL_MS + 1_000L);
        memberA.heartbeat();
        ownA.refreshOnce(Set.of(TOPIC));
        assertEquals(List.of(0, 1, 2, 3), ownA.ownedPartitions(TOPIC),
            "a TTL-evicted owner's partitions must be taken over");
        for (int p = 0; p < 4; p++) {
            assertEquals("A", ownerOf(p), "partition #" + p + " must name the survivor A");
        }
    }

    /** A restarted instance rejoins on its next cycle without disturbing a live peer's share. */
    @Test
    void restartedInstanceRejoinsWithoutStealing() {
        RecordingStorage storageA = new RecordingStorage();
        RecordingStorage storageB = new RecordingStorage();
        ClusterMembership memberA = new ClusterMembership(meta, "A", "localhost:1", TTL_MS, clock::get,
            new FencingToken(1_000L, new AtomicLong(0)));
        ClusterMembership memberB = new ClusterMembership(meta, "B", "localhost:2", TTL_MS, clock::get,
            new FencingToken(1_500L, new AtomicLong(0)));

        // Both live from the start → straight to the plan: A=[0,2], B=[1,3].
        final PartitionOwnership ownA = new PartitionOwnership(memberA, meta, storageA, "A", 5_000L, clock::get,
            new FencingToken(1_000L, new AtomicLong(0)));
        final PartitionOwnership ownB = new PartitionOwnership(memberB, meta, storageB, "B", 5_000L, clock::get,
            new FencingToken(1_500L, new AtomicLong(0)));
        assertTrue(memberA.heartbeat());
        assertTrue(memberB.heartbeat());
        ownA.refreshOnce(Set.of(TOPIC));
        ownB.refreshOnce(Set.of(TOPIC));
        assertEquals(List.of(0, 2), ownA.ownedPartitions(TOPIC));
        assertEquals(List.of(1, 3), ownB.ownedPartitions(TOPIC));

        // A "restarts": brand-new ownership state (empty myGen) with a MUCH higher token base.
        final PartitionOwnership ownAReboot = new PartitionOwnership(memberA, meta, storageA, "A", 5_000L, clock::get,
            new FencingToken(9_000L, new AtomicLong(0)));
        clock.addAndGet(5_000L);
        memberA.heartbeat();
        memberB.heartbeat();
        ownAReboot.refreshOnce(Set.of(TOPIC));
        assertEquals(List.of(0, 2), ownAReboot.ownedPartitions(TOPIC),
            "the restarted instance reclaims exactly its plan share");
        assertEquals("B", ownerOf(1), "B keeps its share across A's restart");
        assertEquals("B", ownerOf(3), "B keeps its share across A's restart");
        assertFalse(ownAReboot.ownedPartitions(TOPIC).contains(1)
            || ownAReboot.ownedPartitions(TOPIC).contains(3),
            "the restarted instance must not steal the live peer's partitions");
    }

    private String ownerOf(int partition) {
        String rec = meta.get("/em/assignments/" + TOPIC + "#" + partition);
        if (rec == null || rec.isEmpty()) {
            return null;
        }
        int sep = rec.indexOf('|');
        return sep > 0 ? rec.substring(sep + 1) : null;
    }

    private static String ownerOf(MetaStore store, int partition) {
        String rec = store.get("/em/assignments/" + TOPIC + "#" + partition);
        if (rec == null || rec.isEmpty()) {
            return null;
        }
        int sep = rec.indexOf('|');
        return sep > 0 ? rec.substring(sep + 1) : null;
    }

    /**
     * Nacos-faithful CAS store: the real ConfigService rejects a {@code publishConfigCas} whose new
     * content is EMPTY ({@code ErrCode:-400 "content invalid"}, verified against a live 2.1.1
     * server). {@link InMemoryMetaStore} accepts it, so the release path looked healthy in unit
     * tests while being a silent no-op against real Nacos.
     *
     * <p>Why that matters: the release is what makes membership GROWTH converge. When it fails,
     * the assignment record keeps naming the old owner, the plan's new owner stays fenced by a
     * LIVE peer forever, and the moved partition is polled by NOBODY — its events strand. This
     * store models the real backend so a regression of that shape cannot pass here again.</p>
     */
    private static final class NacosStrictMetaStore extends InMemoryMetaStore {

        @Override
        public boolean tryAcquire(String key, String expectedOldValue, String newValue) {
            if (newValue == null || newValue.isEmpty()) {
                return false; // Nacos: ErrCode:-400, ErrMsg:content invalid
            }
            return super.tryAcquire(key, expectedOldValue, newValue);
        }
    }

    /**
     * Membership growth must re-balance even when the Meta backend refuses empty tombstones:
     * A solo → A=[0,1,2,3]; B joins → A=[0,2], B=[1,3]; C joins → A=[0,3], B=[1], C=[2]. The two
     * releases the third join needs (A drops #2, B drops #3) must succeed through a CAS-able
     * tombstone, otherwise the new instance is fenced by live peers forever.
     */
    @Test
    void membershipGrowthConvergesWhenMetaRejectsEmptyTombstone() {
        final NacosStrictMetaStore strict = new NacosStrictMetaStore();
        final AtomicLong clk = new AtomicLong(2_000_000L);
        final RecordingStorage storageA = new RecordingStorage();
        final RecordingStorage storageB = new RecordingStorage();
        final RecordingStorage storageC = new RecordingStorage();

        final ClusterMembership memberA = new ClusterMembership(strict, "A", "localhost:1", TTL_MS, clk::get,
            new FencingToken(1_000L, new AtomicLong(0)));
        final ClusterMembership memberB = new ClusterMembership(strict, "B", "localhost:2", TTL_MS, clk::get,
            new FencingToken(1_000L, new AtomicLong(0)));
        final ClusterMembership memberC = new ClusterMembership(strict, "C", "localhost:3", TTL_MS, clk::get,
            new FencingToken(1_000L, new AtomicLong(0)));
        final PartitionOwnership ownA = new PartitionOwnership(memberA, strict, storageA, "A", 5_000L, clk::get,
            new FencingToken(1_000L, new AtomicLong(0)));
        final PartitionOwnership ownB = new PartitionOwnership(memberB, strict, storageB, "B", 5_000L, clk::get,
            new FencingToken(1_000L, new AtomicLong(0)));
        final PartitionOwnership ownC = new PartitionOwnership(memberC, strict, storageC, "C", 5_000L, clk::get,
            new FencingToken(1_000L, new AtomicLong(0)));

        // Phase 1: A alone → self-allocates every partition.
        assertTrue(memberA.heartbeat());
        ownA.refreshOnce(Set.of(TOPIC));
        assertEquals(List.of(0, 1, 2, 3), ownA.ownedPartitions(TOPIC), "solo A owns every partition");

        // Phase 2: B joins → A must release 1,3 (through the strict store) and B claims them.
        assertTrue(memberB.heartbeat());
        ownB.refreshOnce(Set.of(TOPIC));
        assertEquals(List.of(), ownB.ownedPartitions(TOPIC), "B is fenced by the live A on its first cycle");
        clk.addAndGet(5_000L);
        memberA.heartbeat();
        ownA.refreshOnce(Set.of(TOPIC));
        assertEquals(List.of(0, 2), ownA.ownedPartitions(TOPIC), "A converges to its two-instance share");
        ownB.refreshOnce(Set.of(TOPIC));
        assertEquals(List.of(1, 3), ownB.ownedPartitions(TOPIC), "B claims the partitions A released");

        // Phase 3: C joins → the plan becomes A=[0,3], B=[1], C=[2]; A must release #2 and B #3.
        assertTrue(memberC.heartbeat());
        ownC.refreshOnce(Set.of(TOPIC));
        assertEquals(List.of(), ownC.ownedPartitions(TOPIC), "C is fenced by the live A on its first cycle");
        clk.addAndGet(5_000L);
        memberA.heartbeat();
        memberB.heartbeat();
        ownA.refreshOnce(Set.of(TOPIC));
        ownB.refreshOnce(Set.of(TOPIC));
        ownC.refreshOnce(Set.of(TOPIC));
        // One more round of cycles so every side observes the other's release.
        clk.addAndGet(5_000L);
        memberA.heartbeat();
        memberB.heartbeat();
        memberC.heartbeat();
        ownA.refreshOnce(Set.of(TOPIC));
        ownB.refreshOnce(Set.of(TOPIC));
        ownC.refreshOnce(Set.of(TOPIC));

        assertEquals(List.of(0, 3), ownA.ownedPartitions(TOPIC), "A converges to 2/1/1 share [0,3]");
        assertEquals(List.of(1), ownB.ownedPartitions(TOPIC), "B converges to 2/1/1 share [1]");
        assertEquals(List.of(2), ownC.ownedPartitions(TOPIC), "C converges to 2/1/1 share [2]");
        assertEquals(List.of(0, 3), storageA.lastAssigned, "A's storage got [0,3]");
        assertEquals(List.of(1), storageB.lastAssigned, "B's storage got [1]");
        assertEquals(List.of(2), storageC.lastAssigned, "C's storage got [2]");
        assertEquals("A", ownerOf(strict, 0), "partition #0 must name A");
        assertEquals("B", ownerOf(strict, 1), "partition #1 must name B");
        assertEquals("C", ownerOf(strict, 2), "partition #2 must name C — the release A had to make");
        assertEquals("A", ownerOf(strict, 3), "partition #3 must name A — the release B had to make");
    }
}
