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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.rocksdb.FlushOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import lombok.extern.slf4j.Slf4j;

/**
 * Local durable {@link TaskStore} backed by a RocksDB instance on disk (issue #5405).
 *
 * <p>The default A2A task store for single-instance / docker deployments: no Meta
 * (Nacos) dependency, state survives restarts under the same data-dir layout as the
 * offset and delivery-state stores ({@code <data>/a2a-tasks}). Clustered deployments
 * keep {@link MetaBackedTaskStore} for cross-instance visibility.</p>
 *
 * <p><b>Wire format:</b> identical envelope to {@code MetaBackedTaskStore} —
 * {@code "v1|" + base64(inner)} where {@code inner} is the pipe-joined base64 field
 * list — so a task written by one backend is readable by the other (migration path
 * between local and Meta modes stays lossless up to schema version).</p>
 *
 * <p><b>CAS semantics:</b> {@link #updateStatus} is guarded by the per-record
 * {@code taskEpoch} (same contract as the Meta backend): a stale writer whose epoch
 * does not match the stored record is rejected. The local RocksDB write is single
 * process, so the CAS only guards logical staleness, not concurrency.</p>
 */
@Slf4j
public class RocksDBTaskStore implements TaskStore {

    static {
        RocksDB.loadLibrary();
    }

    /** Wire-format version marker, shared with {@code MetaBackedTaskStore}. */
    public static final String WIRE_VERSION = "v1";

    private final RocksDB db;
    private final AtomicLong localEpoch = new AtomicLong();

    public RocksDBTaskStore(String path) {
        Options options = new Options().setCreateIfMissing(true);
        try {
            db = RocksDB.open(options, path);
            log.info("RocksDBTaskStore opened at {}", path);
        } catch (RocksDBException e) {
            throw new IllegalStateException("failed to open RocksDB task store at " + path, e);
        }
    }

    private static byte[] key(String taskId) {
        return taskId.getBytes(StandardCharsets.UTF_8);
    }

    // ---- encode / decode: identical to MetaBackedTaskStore ----

    static String encode(TaskRecord r) {
        StringBuilder inner = new StringBuilder(256);
        inner.append(b64(r.taskId)).append('|')
             .append(b64(r.agentId)).append('|')
             .append(b64(r.clientId)).append('|')
             .append(r.status.name()).append('|')
             .append(r.taskEpoch).append('|')
             .append(r.createdAtMs).append('|')
             .append(r.updatedAtMs).append('|')
             .append(b64(r.input)).append('|')
             .append(b64(r.output))
             .append('|').append(b64(r.contextId));
        String payload = Base64.getEncoder().encodeToString(
            inner.toString().getBytes(StandardCharsets.UTF_8));
        return WIRE_VERSION + "|" + payload;
    }

    static TaskRecord decode(String value) {
        if (value == null) {
            return null;
        }
        int sep = value.indexOf('|');
        if (sep <= 0) {
            throw new IllegalStateException("malformed task wire value (no version)");
        }
        String version = value.substring(0, sep);
        if (!WIRE_VERSION.equals(version)) {
            throw new IllegalStateException("unsupported task wire version: " + version);
        }
        String payload = new String(Base64.getDecoder().decode(value.substring(sep + 1)),
            StandardCharsets.UTF_8);
        List<String> parts = splitFixed(payload, 10);
        return new TaskRecord(
            b64Decode(parts.get(0)), b64Decode(parts.get(1)), b64Decode(parts.get(2)),
            Status.valueOf(parts.get(3)),
            Long.parseLong(parts.get(5)), Long.parseLong(parts.get(6)),
            b64Decode(parts.get(7)), b64Decode(parts.get(8)),
            Long.parseLong(parts.get(4)), b64Decode(parts.get(9)));
    }

    private static String b64(String s) {
        return s == null ? "" : Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64Decode(String s) {
        return (s == null || s.isEmpty()) ? null
            : new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }

    private static List<String> splitFixed(String s, int expectedFields) {
        List<String> out = new ArrayList<>(expectedFields);
        int start = 0;
        for (int i = 0; i < expectedFields - 1; i++) {
            int sep = s.indexOf('|', start);
            if (sep < 0) {
                throw new IllegalStateException("malformed task wire payload");
            }
            out.add(s.substring(start, sep));
            start = sep + 1;
        }
        out.add(s.substring(start));
        return out;
    }

    @Override
    public TaskRecord createTask(String taskId, String agentId, String clientId, String input) {
        return createTask(taskId, agentId, clientId, input, null);
    }

    @Override
    public TaskRecord createTask(String taskId, String agentId, String clientId, String input,
                                 String contextId) {
        if (taskId == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        long epoch = (System.currentTimeMillis() << 20) | (localEpoch.incrementAndGet() & 0xFFFFF);
        TaskRecord rec = new TaskRecord(taskId, agentId, clientId, Status.PENDING,
            now, now, input, null, epoch, contextId);
        try {
            byte[] k = key(taskId);
            if (db.get(k) != null) {
                return null; // duplicate taskId, same contract as the Meta backend
            }
            db.put(k, encode(rec).getBytes(StandardCharsets.UTF_8));
        } catch (RocksDBException e) {
            throw new IllegalStateException("RocksDB createTask failed: " + e.getMessage(), e);
        }
        return rec;
    }

    @Override
    public TaskRecord getTask(String taskId) {
        if (taskId == null) {
            return null;
        }
        try {
            byte[] val = db.get(key(taskId));
            return val == null ? null : decode(new String(val, StandardCharsets.UTF_8));
        } catch (RocksDBException e) {
            throw new IllegalStateException("RocksDB getTask failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean updateStatus(String taskId, long expectedTaskEpoch, Status newStatus, String output) {
        if (taskId == null || newStatus == null) {
            return false;
        }
        try {
            byte[] k = key(taskId);
            byte[] curBytes = db.get(k);
            if (curBytes == null) {
                return false;
            }
            TaskRecord cur = decode(new String(curBytes, StandardCharsets.UTF_8));
            if (cur.taskEpoch != expectedTaskEpoch) {
                return false;
            }
            TaskRecord next = new TaskRecord(cur.taskId, cur.agentId, cur.clientId, newStatus,
                cur.createdAtMs, System.currentTimeMillis(), cur.input, output, cur.taskEpoch, cur.contextId);
            db.put(k, encode(next).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (RocksDBException e) {
            log.warn("RocksDB updateStatus failed for {}: {}", taskId, e.getMessage());
            return false;
        }
    }

    @Override
    public List<TaskRecord> listByAgent(String agentId, Status statusFilter) {
        List<TaskRecord> out = new ArrayList<>();
        if (agentId == null) {
            return out;
        }
        try (RocksIterator it = db.newIterator()) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                TaskRecord r = decode(new String(it.value(), StandardCharsets.UTF_8));
                if (r != null && agentId.equals(r.agentId)
                        && (statusFilter == null || r.status == statusFilter)) {
                    out.add(r);
                }
            }
        }
        return out;
    }

    @Override
    public List<String> expireStale(long olderThanMs) {
        long deadline = System.currentTimeMillis() - olderThanMs;
        List<String> expired = new ArrayList<>();
        List<byte[]> toDelete = new ArrayList<>();
        try (RocksIterator it = db.newIterator()) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                TaskRecord r = decode(new String(it.value(), StandardCharsets.UTF_8));
                if (r != null && r.updatedAtMs < deadline) {
                    expired.add(r.taskId);
                    toDelete.add(key(r.taskId));
                }
            }
        }
        try {
            for (byte[] k : toDelete) {
                db.delete(k);
            }
        } catch (RocksDBException e) {
            log.warn("RocksDB expireStale delete failed: {}", e.getMessage());
        }
        return expired;
    }

    @Override
    public void flush() {
        try (FlushOptions fo = new FlushOptions().setWaitForFlush(true)) {
            db.flush(fo);
        } catch (RocksDBException e) {
            log.warn("RocksDB flush failed: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        db.close();
    }
}
