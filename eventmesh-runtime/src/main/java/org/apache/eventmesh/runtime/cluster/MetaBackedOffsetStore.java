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

import org.apache.eventmesh.runtime.offset.OffsetStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * Two-tier offset store (§13.2.4): a local {@link OffsetStore} (RocksDB) as the fast write path +
 * crash-recovery source, plus an asynchronous remote copy in {@link MetaStore} so a clientId that
 * migrates to another instance resumes from the cluster-wide progress (zero replay).
 *
 * <p>Write path: {@code local.writeOffset} immediately (sub-ms), then mark the key dirty; a
 * background flusher batch-writes dirty keys to Meta every {@code flushIntervalMs} (write
 * off-loading so Meta isn't hammered per-ACK). Read path on takeover: {@code max(local, remote)} —
 * Meta is the cluster-wide truth, local may be ahead if a flush is in flight.</p>
 *
 * <p>When Meta is unavailable the flusher logs and keeps the dirty set; local RocksDB still serves
 * reads/writes, so the runtime degrades to local-only without losing progress (§13.2.9).</p>
 */
@Slf4j
public class MetaBackedOffsetStore implements OffsetStore {

    private static final String META_PREFIX = "/em/offsets/";

    private final OffsetStore local;
    private final MetaStore meta;
    private final long flushIntervalMs;
    private final ScheduledExecutorService flusher;
    private final ConcurrentHashMap<String, Long> dirty = new ConcurrentHashMap<>();

    public MetaBackedOffsetStore(OffsetStore local, MetaStore meta, long flushIntervalMs) {
        this.local = local;
        this.meta = meta;
        this.flushIntervalMs = flushIntervalMs;
        this.flusher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "em-offset-meta-flush");
            t.setDaemon(true);
            return t;
        });
        this.flusher.scheduleWithFixedDelay(this::flushDirty, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public long readOffset(String topic, String clientId, int partition) {
        long localOff = local.readOffset(topic, clientId, partition);
        long remoteOff = readRemote(topic, clientId, partition);
        return Math.max(localOff, remoteOff);
    }

    @Override
    public void writeOffset(String topic, String clientId, int partition, long offset) {
        local.writeOffset(topic, clientId, partition, offset);
        dirty.put(metaKey(topic, clientId, partition), offset);
    }

    @Override
    public Map<String, Long> readAllOffsets(String topic) {
        // Admin view: local is sufficient (it mirrors every write); remote is only ahead for keys
        // this instance never owned, which aren't this instance's business to report.
        return local.readAllOffsets(topic);
    }

    @Override
    public java.util.Set<String> readAllTopics() {
        // Delegate to local — it mirrors every write and is the crash-recovery source.
        return local.readAllTopics();
    }

    @Override
    public void flush() {
        local.flush();
        flushDirty();
    }

    @Override
    public void close() {
        flusher.shutdownNow();
        try {
            flush();
        } catch (Exception e) {
            log.warn("final offset flush to meta failed: {}", e.toString());
        }
        local.close();
    }

    /** Push the dirty offset set to Meta; keys that fail stay dirty for the next cycle. */
    private void flushDirty() {
        if (dirty.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Long> e : dirty.entrySet()) {
            try {
                meta.put(e.getKey(), Long.toString(e.getValue()));
                dirty.remove(e.getKey());
            } catch (Exception ex) {
                // keep dirty — retry next cycle. Meta being down doesn't block local writes.
                log.debug("offset meta flush retry for {}: {}", e.getKey(), ex.toString());
            }
        }
    }

    private long readRemote(String topic, String clientId, int partition) {
        try {
            String v = meta.get(metaKey(topic, clientId, partition));
            return v == null ? -1L : Long.parseLong(v);
        } catch (Exception e) {
            log.debug("offset meta read failed for {}#{}#{}: {}", topic, clientId, partition, e.toString());
            return -1L;
        }
    }

    private static String metaKey(String topic, String clientId, int partition) {
        return META_PREFIX + OffsetStore.buildKey(topic, clientId, partition);
    }
}
