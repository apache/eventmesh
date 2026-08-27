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

import org.apache.eventmesh.runtime.cluster.MetaStore;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

/**
 * Production {@link TaskStore} backed by a cluster-shared {@link MetaStore}
 * (issue #5301 Sub-PR C, cluster-shared tier, reintroduces the A2A task model lost in #5274).
 *
 * <p>One record per {@code taskId} at key {@code /em/tasks/<taskId>}. The value is a
 * self-describing envelope:</p>
 * <pre>
 *   "v1|" + base64( taskId|agentId|clientId|status|epoch|createdMs|updatedMs|base64(input)|base64(output) )
 * </pre>
 *
 * <p>Each variable-width field is base64-encoded before joining so opaque caller input (JSON
 * payloads that may contain {@code '|'} or newlines) cannot corrupt the wire format. The
 * {@code taskEpoch} is the monotonic per-task epoch set at creation; status updates use
 * {@link MetaStore#tryAcquire} as a CAS to reject stale writers (issue #5291 idempotency-style
 * — an instance that restarted with a fresh boot epoch cannot accidentally overwrite a still-live
 * task's status from the previous instance).</p>
 *
 * <p>Note on cluster-shared semantics: every Runtime instance sees every task via
 * {@link MetaStore#getWithPrefix}. {@link #listByAgent} is local-filtered (a read fan-out); it
 * does not require a per-agent index because the A2A task volume is small relative to
 * subscriptions. If a follow-up needs an index, it can be added under {@code /em/idx/agent/}.</p>
 */
@Slf4j
public class MetaBackedTaskStore implements TaskStore {

    /** Meta key prefix for A2A tasks. Cluster-shared, namespace-stable. */
    public static final String PREFIX = "/em/tasks/";

    /** Wire-format version. Bump on any breaking change to the inner field set. */
    public static final String WIRE_VERSION = "v1";

    private final MetaStore meta;
    /** Monotonic per-process epoch source — combined with the existing boot epoch of the JVM
     *  to make per-task epochs unique across restarts and instances. */
    private final AtomicLong localEpoch = new AtomicLong();

    public MetaBackedTaskStore(MetaStore meta) {
        this.meta = meta;
    }

    private static String key(String taskId) {
        return PREFIX + taskId;
    }

    private static String b64(String s) {
        if (s == null) {
            return "";
        }
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64Decode(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }

    private static String encode(TaskRecord r) {
        // The inner field set is joined by '|' and base64'd as a single blob; the outer envelope
        // is "v1|<base64>" so the version byte and the payload can be sliced unambiguously.
        StringBuilder inner = new StringBuilder(256);
        inner.append(b64(r.taskId)).append('|')
             .append(b64(r.agentId)).append('|')
             .append(b64(r.clientId)).append('|')
             .append(r.status.name()).append('|')
             .append(r.taskEpoch).append('|')
             .append(r.createdAtMs).append('|')
             .append(r.updatedAtMs).append('|')
             .append(b64(r.input)).append('|')
             .append(b64(r.output));
        String payload = Base64.getEncoder().encodeToString(
            inner.toString().getBytes(StandardCharsets.UTF_8));
        return WIRE_VERSION + "|" + payload;
    }

    private static TaskRecord decode(String value) {
        if (value == null) {
            return null;
        }
        int sep = value.indexOf('|');
        if (sep <= 0) {
            throw new IllegalStateException("malformed task wire value (no version): " + value);
        }
        String version = value.substring(0, sep);
        if (!WIRE_VERSION.equals(version)) {
            throw new IllegalStateException("unsupported task wire version: " + version);
        }
        String payload = new String(Base64.getDecoder().decode(value.substring(sep + 1)),
            StandardCharsets.UTF_8);
        // The inner payload contains 9 base64'd fields joined by '|' (output may be empty = null).
        // Split on '|' with a fixed cap of 8 separators so the trailing field can contain '|'
        // (it is base64, so it cannot, but defensive splitting is cheap).
        List<String> parts = splitFixed(payload, 9);
        String taskId  = b64Decode(parts.get(0));
        String agentId = b64Decode(parts.get(1));
        String clientId = b64Decode(parts.get(2));
        Status status  = Status.valueOf(parts.get(3));
        long epoch     = Long.parseLong(parts.get(4));
        long created   = Long.parseLong(parts.get(5));
        long updated   = Long.parseLong(parts.get(6));
        String input   = b64Decode(parts.get(7));
        String output  = b64Decode(parts.get(8));
        return new TaskRecord(taskId, agentId, clientId, status, created, updated, input, output, epoch);
    }

    private static List<String> splitFixed(String s, int expectedFields) {
        // Walk the string collecting at most expectedFields-1 split positions; the remainder
        // is the last field. base64 output never contains '|' so this is unambiguous.
        List<String> out = new ArrayList<>(expectedFields);
        int start = 0;
        for (int i = 0; i < expectedFields - 1; i++) {
            int sep = s.indexOf('|', start);
            if (sep < 0) {
                throw new IllegalStateException("malformed task wire payload: " + s);
            }
            out.add(s.substring(start, sep));
            start = sep + 1;
        }
        out.add(s.substring(start));
        return out;
    }

    @Override
    public TaskRecord createTask(String taskId, String agentId, String clientId, String input) {
        if (taskId == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        // Combine JVM boot epoch (per-process, never decreases) with a local counter so the
        // resulting taskEpoch is unique across restarts of the same JVM and across instances.
        long epoch = (System.currentTimeMillis() << 20) | (localEpoch.incrementAndGet() & 0xFFFFF);
        TaskRecord rec = new TaskRecord(taskId, agentId, clientId, Status.PENDING,
            now, now, input, null, epoch);
        if (!meta.putIfAbsent(key(taskId), encode(rec))) {
            return null;
        }
        return rec;
    }

    @Override
    public TaskRecord getTask(String taskId) {
        if (taskId == null) {
            return null;
        }
        return decode(meta.get(key(taskId)));
    }

    @Override
    public boolean updateStatus(String taskId, long expectedTaskEpoch, Status newStatus, String output) {
        if (taskId == null || newStatus == null) {
            return false;
        }
        TaskRecord cur = getTask(taskId);
        if (cur == null || cur.taskEpoch != expectedTaskEpoch) {
            return false;
        }
        // Build the candidate new value with bumped updatedAtMs
        TaskRecord next = new TaskRecord(cur.taskId, cur.agentId, cur.clientId, newStatus,
            cur.createdAtMs, System.currentTimeMillis(), cur.input, output, cur.taskEpoch);
        // CAS the encoded value. expectedOldValue must match the current Meta value verbatim,
        // so re-read just before the CAS to minimise the lost-update window.
        String currentEncoded = meta.get(key(taskId));
        if (currentEncoded == null) {
            return false;
        }
        return meta.tryAcquire(key(taskId), currentEncoded, encode(next));
    }

    @Override
    public List<TaskRecord> listByAgent(String agentId, Status statusFilter) {
        if (agentId == null) {
            return List.of();
        }
        Map<String, String> all = meta.getWithPrefix(PREFIX);
        List<TaskRecord> out = new ArrayList<>();
        for (String value : all.values()) {
            TaskRecord r = decode(value);
            if (r != null && agentId.equals(r.agentId)
                && (statusFilter == null || r.status == statusFilter)) {
                out.add(r);
            }
        }
        return out;
    }

    @Override
    public List<String> expireStale(long olderThanMs) {
        long deadline = System.currentTimeMillis() - olderThanMs;
        List<String> expired = new ArrayList<>();
        Map<String, String> all = meta.getWithPrefix(PREFIX);
        for (Map.Entry<String, String> e : all.entrySet()) {
            TaskRecord r = decode(e.getValue());
            if (r != null && r.updatedAtMs < deadline) {
                if (meta.delete(e.getKey())) {
                    expired.add(r.taskId);
                }
            }
        }
        return expired;
    }

    @Override
    public void flush() {
        // Meta is the source of truth; no buffered writes here.
    }

    @Override
    public void close() {
        // Do NOT close the underlying MetaStore.
    }
}
