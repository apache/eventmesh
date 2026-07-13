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
 * <p><b>Not yet here</b>: generation fencing (§13.2.8④, G3) and remote offset (§13.2.4, G5). Today
 * this is the "self-allocated" plan — every instance computes the same deterministic map, so no
 * instance overlaps, but a network-partitionned stale owner isn't fenced until its lease expires.
 * Nacos's lack of prefix-scan can also make {@code liveInstances} incomplete — etcd backend (G6)
 * fixes that.</p>
 */
@Slf4j
public class PartitionOwnership {

    private final ClusterMembership membership;
    private final MetaStore metaStore;
    private final MeshStoragePlugin storage;
    private final String selfInstanceId;
    private final long intervalMs;
    private final LongSupplier clock;

    private final ConcurrentHashMap<String, List<Integer>> owned = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> myGen = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    /**
     * Lease flag: true while the last heartbeat reached Meta. When false (Meta unreachable),
     * {@link #ownedPartitions} returns empty so the pull-loop polls nothing - preventing a
     * partitioned instance from polling with a stale "self owns all" view (split-brain duplicate).
     */
    private volatile boolean leaseValid = true;
    private ScheduledExecutorService scheduler;
    private Supplier<Set<String>> topicSource;

    public PartitionOwnership(ClusterMembership membership, MetaStore metaStore, MeshStoragePlugin storage,
        String selfInstanceId, long intervalMs, LongSupplier clock) {
        this.membership = membership;
        this.metaStore = metaStore;
        this.storage = storage;
        this.selfInstanceId = selfInstanceId;
        this.intervalMs = intervalMs;
        this.clock = clock;
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
            Set<String> topics = topicSource == null ? Collections.emptySet() : topicSource.get();
            if (topics.isEmpty()) {
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
                // §13.2.8④ gen fencing: keep only partitions whose Meta assignment still says we're
                // the owner (or that we newly acquire). A stale owner whose lease expired sees a
                // newer gen in Meta and backs off (soft fencing — see class javadoc).
                List<Integer> fenced = new java.util.ArrayList<>(mine.size());
                for (int p : mine) {
                    if (acquireOrFence(topic, p)) {
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
     * Acquire (or confirm) ownership of one partition via the Meta assignment table, with a
     * monotonically increasing generation (§13.2.8④). Returns false when another instance has a
     * newer generation in Meta — we've been fenced and must stop polling this partition.
     *
     * <p>Meta record: {@code /em/assignments/<topic#partition> = "<gen>|<ownerInstanceId>"}.</p>
     */
    private boolean acquireOrFence(String topic, int partition) {
        String key = "/em/assignments/" + topic + "#" + partition;
        String pkey = topic + "#" + partition;
        long metaGen = -1L;
        String metaOwner = null;
        try {
            String rec = metaStore.get(key);
            if (rec != null) {
                int sep = rec.indexOf('|');
                if (sep > 0) {
                    metaGen = Long.parseLong(rec.substring(0, sep));
                    metaOwner = rec.substring(sep + 1);
                }
            }
        } catch (Exception e) {
            log.debug("assignment read failed for {}: {}", key, e.toString());
        }

        if (metaOwner == null) {
            // First claim.
            metaStore.put(key, "0|" + selfInstanceId);
            myGen.put(pkey, 0L);
            return true;
        }
        if (metaOwner.equals(selfInstanceId)) {
            // Still ours — keep our gen in sync with Meta (may have been refreshed by a restart).
            myGen.put(pkey, metaGen);
            return true;
        }
        // Another instance holds the Meta assignment.
        long mine = myGen.getOrDefault(pkey, -1L);
        if (metaGen > mine) {
            // They have a newer generation — we're fenced. Drop ownership.
            log.info("fenced: partition {}#{} taken over by {} (gen {} > our {})", topic, partition, metaOwner, metaGen, mine);
            myGen.remove(pkey);
            return false;
        }
        // We're (re)claiming it — bump the generation so any stale owner fences on its next refresh.
        long newGen = metaGen + 1;
        metaStore.put(key, newGen + "|" + selfInstanceId);
        myGen.put(pkey, newGen);
        return true;
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
