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

package org.apache.eventmesh.runtime.state;

import org.apache.eventmesh.runtime.state.DeliveryStateStore.Record;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.rocksdb.FlushOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import lombok.extern.slf4j.Slf4j;

/**
 * Durable {@link DeliveryStateStore} backed by a local RocksDB instance (issue #5301 Sub-PR B).
 *
 * <p>Key = {@code deliveryId} (UTF-8). Value = a small ASCII record encoding the dispatch
 * metadata needed to replay an ACK on restart:</p>
 *
 * <pre>
 *   "{topic}|{partition}|{offset}|{clientId}|{attempt}|{nextAttemptAtMs}|{base64-event-bytes}"
 * </pre>
 *
 * <p>The event is base64-wrapped so the line stays ASCII-only \u2014 RocksDB key/value are
 * arbitrary bytes but the ASCII restriction simplifies grep-debug and avoids
 * {@code StandardCharsets} ambiguity in a small value space. The encoding is intentionally
 * compact (the alternative \u2014 JSON \u2014 costs an extra dependency for the same field
 * count).</p>
 *
 * <p>Throughput target: 10K+ puts/s. The hot path is a single {@code db.put} per delivery;
 * {@code recover()} is a full scan but happens only on JVM start, not per-ACK.</p>
 */
@Slf4j
public class RocksDBDeliveryStateStore implements DeliveryStateStore {

    static {
        RocksDB.loadLibrary();
    }

    private final RocksDB db;
    private volatile boolean closed = false;

    public RocksDBDeliveryStateStore(String path) {
        Options options = new Options().setCreateIfMissing(true);
        try {
            this.db = RocksDB.open(options, path);
        } catch (RocksDBException e) {
            throw new IllegalStateException("failed to open RocksDB delivery state store at " + path, e);
        }
        options.close();
    }

    @Override
    public void put(Record record) {
        if (closed) {
            throw new IllegalStateException("store is closed");
        }
        byte[] key = record.deliveryId.getBytes(StandardCharsets.UTF_8);
        byte[] value = encode(record);
        try {
            db.put(key, value);
        } catch (RocksDBException e) {
            throw new IllegalStateException("RocksDB put failed for " + record.deliveryId, e);
        }
    }

    @Override
    public boolean remove(String deliveryId) {
        if (closed) {
            throw new IllegalStateException("store is closed");
        }
        try {
            db.delete(deliveryId.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (RocksDBException e) {
            throw new IllegalStateException("RocksDB delete failed for " + deliveryId, e);
        }
    }

    @Override
    public Record get(String deliveryId) {
        try {
            byte[] value = db.get(deliveryId.getBytes(StandardCharsets.UTF_8));
            return value == null ? null : decode(value);
        } catch (RocksDBException e) {
            log.warn("RocksDB get failed for {}", deliveryId, e);
            return null;
        }
    }

    @Override
    public void iterate(Consumer<Record> visitor) {
        try (RocksIterator it = db.newIterator()) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                visitor.accept(decode(it.value()));
            }
        }
    }

    @Override
    public int count() {
        int[] n = {0};
        try (RocksIterator it = db.newIterator()) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                n[0]++;
            }
        }
        return n[0];
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
        if (closed) {
            return;
        }
        closed = true;
        db.close();
    }

    private static byte[] encode(Record r) {
        String b64 = java.util.Base64.getEncoder().encodeToString(r.encodedEvent);
        return (r.topic + "|" + r.partition + "|" + r.offset + "|" + r.clientId
            + "|" + r.attempt + "|" + r.nextAttemptAtMs + "|" + b64)
            .getBytes(StandardCharsets.US_ASCII);
    }

    private static Record decode(byte[] value) {
        String s = new String(value, StandardCharsets.US_ASCII);
        // topic|partition|offset|clientId|attempt|nextAttemptAtMs|b64
        // The base64 payload never contains '|', so the last '|' is the b64 boundary.
        int p1 = s.indexOf('|');
        int p2 = s.indexOf('|', p1 + 1);
        int p3 = s.indexOf('|', p2 + 1);
        int p4 = s.indexOf('|', p3 + 1);
        int p5 = s.indexOf('|', p4 + 1);
        int p6 = s.indexOf('|', p5 + 1);
        String topic = s.substring(0, p1);
        int partition = Integer.parseInt(s.substring(p1 + 1, p2));
        long offset = Long.parseLong(s.substring(p2 + 1, p3));
        String clientId = s.substring(p3 + 1, p4);
        int attempt = Integer.parseInt(s.substring(p4 + 1, p5));
        long nextAttemptAtMs = Long.parseLong(s.substring(p5 + 1, p6));
        byte[] encodedEvent = java.util.Base64.getDecoder().decode(s.substring(p6 + 1));
        return new Record(null, topic, partition, offset, clientId, attempt, nextAttemptAtMs, encodedEvent);
    }
}
