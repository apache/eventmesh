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

import org.apache.eventmesh.api.storage.MeshStoragePlugin;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

/**
 * Multi-instance partition ownership (§13.2.3 / §13.2.8① ②). Periodically:
 * <ol>
 *   <li>renews this instance's heartbeat lease in Meta (via {@link ClusterMembership});</li>
 *   <li>reads the live instance set, runs the deterministic {@link PartitionAssigner}
 *       (partition % n), and records the partitions this instance owns per topic;</li>
 *   <li>pushes the owned-partition set into the storage plugin via {@code assignPartitions} so each
 *       instance only pulls its own partitions (no duplicate consumption).</li>
 * </ol>
 *
 * <p>The pull loop ({@link org.apache.eventmesh.runtime.ingress.UniIngressService#pullAndDispatch})
 * reads {@link #ownedPartitions(String)} to decide which partitions to poll. When ownership is
 * unknown (partitionCount -1, e.g. RocketMQ) it returns {@code null} → poll-all fallback.</p>
 *
 * <p><b>Fencing</b>: ownership is recorded in Meta via atomic CAS ({@link MetaStore#tryAcquire})
 * with a monotonically increasing {@link FencingToken} (§13.2.8④). A stale owner whose token is
 * lower than the current Meta value is fenced on its next refresh and stops polling that partition.
 * Liveness is preserved across membership churn: partitions that leave our assigner share are
 * released (CAS to a {@code ""} tombstone), and partitions held by a TTL-evicted owner are taken
 * over regardless of token order (a partitioned zombie has already lost its own lease gate).
 * Remote offset (§13.2.4, G5) is not yet here — the local offset store remains the source of truth
 * for delivery progress.</p>
 */
@Slf4j
public class PartitionOwnership {

    private final ClusterMembership membership;
    private final MetaStore metaStore;
    private final MeshStoragePlugin storage;
    private final String selfInstanceId;
    private final long intervalMs;
    private final LongSupplier clock;
    /** Per-JVM fencing token (§13.2.8④). next() produces strictly-increasing tokens for CAS. */
    private final FencingToken fencingToken;

    private final ConcurrentHashMap<String, List<Integer>> owned = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FencingToken> myGen = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    /**
     * Lease flag: true while the last heartbeat reached Meta. When false (Meta unreachable),
     * {@link #ownedPartitions} returns empty so the pull-loop polls nothing - preventing a
     * partitioned instance from polling with a stale "self owns all" view (split-brain duplicate).
     */
    private volatile boolean leaseValid = true;
    private ScheduledExecutorService scheduler;
    private Supplier<Set<String>> topicSource;
    /** Tombstone value for released assignment records (§13.2.10; empty string). */
    private static final String RELEASED = "";

    public PartitionOwnership(ClusterMembership membership, MetaStore metaStore, MeshStoragePlugin storage,
        String selfInstanceId, long intervalMs, LongSupplier clock, FencingToken fencingToken) {
        this.membership = membership;
        this.metaStore = metaStore;
        this.storage = storage;
        this.selfInstanceId = selfInstanceId;
        this.intervalMs = intervalMs;
        this.clock = clock;
        this.fencingToken = fencingToken;
    }

    /**
     * Begin the heartbeat + assignment refresh loop. {@code topicSource} supplies the active topic
     * set each cycle (typically the subscription manager's live topics).
     */
    public void start(Supplier<Set<String>> topicSource) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        this.topicSource = topicSource;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "em-partition-ownership");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::refresh, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void refresh() {
        Set<String> topics = topicSource == null ? Collections.emptySet() : topicSource.get();
        refreshOnce(topics);
    }

    /**
     * One ownership cycle, split out of {@link #refresh()} for deterministic tests: the scheduler
     * drives {@code refresh()} (which pulls the topic set from {@code topicSource}); tests call
     * this directly with an explicit topic set. Package-private.
     */
    void refreshOnce(Set<String> topics) {
        try {
            // §13.2.8② lease = heartbeat. If Meta is unreachable the heartbeat returns false: lose
            // the lease and skip this cycle. ownedPartitions() then returns empty (poll nothing),
            // so a partitioned instance stops polling instead of degrading to "self owns all" and
            // duplicating the quorum's consumption (split-brain).
            if (!membership.heartbeat()) {
                leaseValid = false;
                return;
            }
            leaseValid = true;
            if (topics == null || topics.isEmpty()) {
                return;
            }
            List<String> live = membership.liveInstances();
            if (live.isEmpty()) {
                return;
            }
            for (String topic : topics) {
                int count = safePartitionCount(topic);
                if (count <= 0) {
                    owned.remove(topic); // unknown → poll-all fallback
                    continue;
                }
                Map<Integer, String> assignment = PartitionAssigner.assign(count, live);
                List<Integer> mine = PartitionAssigner.ownedBy(assignment, selfInstanceId);
                // §13.2.8④ fencing: keep only partitions whose Meta assignment still says we're
                // the owner (or that we newly acquire via CAS). A stale owner whose lease expired
                // sees a newer token in Meta and backs off (atomic CAS fencing — see class javadoc).
                // Release partitions that left our assigner share (membership churn) so the new
                // rightful owner is not fenced by our — possibly higher — token forever.
                releaseStale(topic, mine);
                List<Integer> fenced = new java.util.ArrayList<>(mine.size());
                for (int p : mine) {
                    if (acquireOrFence(topic, p, live)) {
                        fenced.add(p);
                    }
                }
                owned.put(topic, fenced);
                try {
                    storage.assignPartitions(topic, fenced);
                } catch (Exception e) {
                    log.warn("assignPartitions failed for {}: {}", topic, e.toString());
                }
            }
        } catch (Exception e) {
            log.warn("partition ownership refresh failed: {}", e.toString());
        }
    }

    /**
     * Acquire (or confirm) ownership of one partition via the Meta assignment table, using an
     * atomic CAS ({@link MetaStore#tryAcquire}) with a monotonically increasing {@link FencingToken}
     * (§13.2.8④). Returns false when another instance holds a fencing token that is ≥ ours — we've
     * been fenced and must stop polling this partition.
     *
     * <p>Meta record: {@code /em/assignments/<topic#partition> = "<token>|<ownerInstanceId>"}.
     *
     * <p>The CAS replaces the old read-then-write race: two instances that both read {@code null}
     * would both {@code put} and the last writer would silently win. With {@code tryAcquire},
     * exactly one CAS succeeds and the loser re-reads on the next refresh cycle.</p>
     */
    private boolean acquireOrFence(String topic, int partition, List<String> live) {
        String key = "/em/assignments/" + topic + "#" + partition;
        String pkey = topic + "#" + partition;

        String currentRec = null;
        FencingToken currentToken = null;
        String currentOwner = null;
        try {
            currentRec = metaStore.get(key);
            if (currentRec != null) {
                int sep = currentRec.indexOf('|');
                if (sep > 0) {
                    currentToken = FencingToken.parse(currentRec.substring(0, sep));
                    currentOwner = currentRec.substring(sep + 1);
                }
            }
        } catch (Exception e) {
            log.debug("assignment read failed for {}: {}", key, e.toString());
        }

        FencingToken myToken = fencingToken.next();

        // Case 1: unclaimed (absent, or the "" tombstone left by releaseStale) — CAS → our token
        if (currentOwner == null) {
            boolean ok = metaStore.tryAcquire(key, currentRec, myToken + "|" + selfInstanceId);
            if (ok) {
                myGen.put(pkey, myToken);
                return true;
            }
            // Lost the race — another instance claimed it. Re-read next cycle.
            return false;
        }

        // Case 2: still ours — sync local token with Meta
        if (currentOwner.equals(selfInstanceId)) {
            if (currentToken != null) {
                myGen.put(pkey, currentToken);
            }
            return true;
        }

        // Case 3: another instance holds it — take over when their lease is gone (TTL eviction)
        // or our token is strictly higher; otherwise we're fenced and stop polling.
        boolean ownerEvicted = !live.contains(currentOwner);
        FencingToken mine = myGen.get(pkey);
        if (mine == null) {
            mine = myToken;
        }
        if (ownerEvicted || currentToken == null || mine.compareTo(currentToken) > 0) {
            // Fence them: CAS currentValue → our value. An evicted owner that is still polling has
            // already failed its own heartbeat gate (leaseValid=false → polls nothing), so forcing
            // the takeover is safe; a live higher-token owner keeps the partition.
            boolean ok = metaStore.tryAcquire(key, currentRec, myToken + "|" + selfInstanceId);
            if (ok) {
                myGen.put(pkey, myToken);
                return true;
            }
            // CAS failed — someone else changed it; re-read next cycle
            return false;
        }
        // Their token is ≥ ours — we're fenced
        log.info("fenced: partition {}#{} held by {} (token {} >= our {})",
            topic, partition, currentOwner, currentToken, mine);
        myGen.remove(pkey);
        return false;
    }

    /**
     * Release Meta assignment records for partitions of {@code topic} that are no longer in
     * {@code mine} — the deterministic assigner moved them to a peer after membership churn. The
     * record is CAS'd to the released tombstone {@code ""} (only if it still names us, so a
     * concurrent takeover is never clobbered), letting the new rightful owner claim it on its next
     * cycle instead of being fenced by our — possibly higher — token forever.
     */
    private void releaseStale(String topic, List<Integer> mine) {
        String prefix = topic + "#";
        for (String pkey : myGen.keySet()) {
            if (!pkey.startsWith(prefix)) {
                continue;
            }
            int p;
            try {
                p = Integer.parseInt(pkey.substring(prefix.length()));
            } catch (NumberFormatException e) {
                continue;
            }
            if (mine.contains(p)) {
                continue;
            }
            String key = "/em/assignments/" + pkey;
            String rec = null;
            try {
                rec = metaStore.get(key);
            } catch (Exception e) {
                log.debug("assignment read failed for {}: {}", key, e.toString());
            }
            if (selfInstanceId.equals(ownerOf(rec))) {
                try {
                    metaStore.tryAcquire(key, rec, RELEASED);
                } catch (Exception e) {
                    log.debug("assignment release failed for {}: {}", key, e.toString());
                }
            }
            myGen.remove(pkey);
        }
    }

    private static String ownerOf(String record) {
        if (record == null || record.isEmpty()) {
            return null;
        }
        int sep = record.indexOf('|');
        return sep > 0 ? record.substring(sep + 1) : null;
    }

    /**
     * @return partitions this instance owns for {@code topic}; {@code null} = unknown (poll all),
     *         empty list = owns none this cycle (also returned when the lease is invalid - Meta
     *         unreachable - so the pull-loop polls nothing and avoids split-brain duplicates).
     */
    public List<Integer> ownedPartitions(String topic) {
        if (!leaseValid) {
            return java.util.Collections.emptyList();
        }
        return owned.get(topic);
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private int safePartitionCount(String topic) {
        try {
            return storage.partitionCount(topic);
        } catch (Exception e) {
            return -1;
        }
    }
}
