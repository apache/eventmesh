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

package org.apache.eventmesh.runtime.offset;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.rocksdb.FlushOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import lombok.extern.slf4j.Slf4j;

/**
 * Durable {@link OffsetStore} backed by a local RocksDB instance (§12.6.3).
 *
 * <p>Key = {@code topic#clientId#partition} (UTF-8), value = the offset as an ASCII decimal string.
 * A crash leaves the last flushed offset on disk, so a restarted subscriber resumes from the
 * persisted position with zero replay (assuming the SubscriptionManager ACKs before advancing).
 * Multi-instance remote copy in Meta (Phase 2.5) is layered on top of, not instead of, this store.</p>
 */
@Slf4j
public class RocksDBOffsetStore implements OffsetStore {

    static {
        RocksDB.loadLibrary();
    }

    private final RocksDB db;

    /** Serializes read-compare-put so concurrent ACKs cannot regress the stored offset (#5289). */
    private final Object writeLock = new Object();

    /**
     * Open (or create) a RocksDB offset store at {@code path}.
     *
     * @param path filesystem directory for the RocksDB instance
     */
    public RocksDBOffsetStore(String path) {
        Options options = new Options().setCreateIfMissing(true);
        try {
            this.db = RocksDB.open(options, path);
        } catch (RocksDBException e) {
            throw new IllegalStateException("failed to open RocksDB offset store at " + path, e);
        }
        // Options must be retained for the DB's lifetime; it is closed implicitly when db closes.
        options.close();
    }

    @Override
    public long readOffset(String topic, String clientId, int partition) {
        try {
            byte[] value = db.get(key(topic, clientId, partition));
            return value == null ? -1L : Long.parseLong(new String(value, StandardCharsets.US_ASCII));
        } catch (RocksDBException e) {
            log.warn("RocksDB get failed for {}/{}/{}", topic, clientId, partition, e);
            return -1L;
        }
    }

    private volatile long offsetWriteFailures = 0;

    /**
     * Atomically monotonic offset write (issue #5289): read-compare-put under {@link #writeLock} so
     * a concurrent ACK carrying a lower offset can never overwrite a higher stored value. Returns
     * {@code false} on a RocksDB failure so the caller keeps the delivery in flight (issue #5290)
     * instead of silently losing the offset advance.
     */
    @Override
    public boolean writeOffset(String topic, String clientId, int partition, long offset) {
        byte[] keyBytes = key(topic, clientId, partition);
        synchronized (writeLock) {
            try {
                byte[] existing = db.get(keyBytes);
                long current = existing != null
                    ? Long.parseLong(new String(existing, StandardCharsets.US_ASCII))
                    : -1L;
                if (offset <= current) {
                    return true;
                }
                db.put(keyBytes, Long.toString(offset).getBytes(StandardCharsets.US_ASCII));
                return true;
            } catch (RocksDBException e) {
                offsetWriteFailures++;
                log.warn("RocksDB put failed for {}/{}/{} offset={} (total failures={})",
                    topic, clientId, partition, offset, offsetWriteFailures, e);
                return false;
            }
        }
    }

    /**
     * @return cumulative count of RocksDB write failures (for metrics/health checks).
     */
    public long getOffsetWriteFailures() {
        return offsetWriteFailures;
    }

    @Override
    public Map<String, Long> readAllOffsets(String topic) {
        byte[] prefix = (topic + "#").getBytes(StandardCharsets.UTF_8);
        Map<String, Long> result = new HashMap<>();
        try (RocksIterator it = db.newIterator()) {
            for (it.seek(prefix); it.isValid(); it.next()) {
                String key = new String(it.key(), StandardCharsets.UTF_8);
                if (!key.startsWith(topic + "#")) {
                    break;
                }
                long offset = Long.parseLong(new String(it.value(), StandardCharsets.US_ASCII));
                result.put(key.substring(topic.length() + 1), offset);
            }
        }
        return result;
    }

    /**
     * Scan all keys in RocksDB and extract the unique topic prefixes (before the first {@code #}).
     * Used by the restart recovery path to discover which topics have persisted ACK offsets.
     */
    @Override
    public java.util.Set<String> readAllTopics() {
        java.util.Set<String> topics = new java.util.HashSet<>();
        try (RocksIterator it = db.newIterator()) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                String key = new String(it.key(), StandardCharsets.UTF_8);
                int sep = key.indexOf('#');
                if (sep > 0) {
                    topics.add(key.substring(0, sep));
                }
            }
        }
        return topics;
    }

    @Override
    public void flush() {
        try (FlushOptions flushOptions = new FlushOptions().setWaitForFlush(true)) {
            db.flush(flushOptions);
        } catch (RocksDBException e) {
            log.warn("RocksDB flush failed", e);
        }
    }

    @Override
    public void close() {
        db.close();
    }

    private static byte[] key(String topic, String clientId, int partition) {
        return OffsetStore.buildKey(topic, clientId, partition).getBytes(StandardCharsets.UTF_8);
    }
}
