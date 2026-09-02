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

package org.apache.eventmesh.runtime.connector;

import org.apache.eventmesh.runtime.cluster.MetaStore;
import org.apache.eventmesh.runtime.security.gate.ConnectorAccessDeniedException;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Dynamic connector scheduler (§8 control plane). Stores connector definitions and worker heartbeats
 * in {@link MetaStore}, deterministically assigns each connector to one live worker
 * ({@code workers[floorMod(id.hashCode(), n)]} — mirrors {@link org.apache.eventmesh.runtime.cluster.PartitionAssigner}),
 * and pushes {@code start}/{@code stop} to workers over HTTP.
 *
 * <p>Workers are connector-runtime processes that register + heartbeat via the admin API. They load
 * connector classes from their bundled fat image (startup classpath, {@code Class.forName}) — there
 * is no jar distribution (option ①). Pushes are idempotent: a worker no-ops on {@code /control/start}
 * for a connector already running, so duplicate pushes from multiple runtime instances (no leader
 * election in v1 — the assignment is deterministic, so every instance computes the same owner) are
 * harmless. A connector definition update (same owner, changed config) is pushed as {@code stop} +
 * {@code start}. Gen fencing is deferred to v2 — connectors are at-least-once anyway.</p>
 */
@Slf4j
public class ConnectorScheduler {

    private static final String DEF_PREFIX = "/em/connectors/";
    private static final String WORKER_PREFIX = "/em/connector-workers/";

    private final MetaStore meta;
    private final long ttlMs;
    private final long intervalMs;
    private final LongSupplier clock;
    private final ObjectMapper mapper = new ObjectMapper();

    /** connectorId → last-pushed (ownerWorkerId, defJson). Drives reconcile diffs. */
    private final ConcurrentHashMap<String, Cached> assignmentCache = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** #5304: optional unified security/quota/audit gate; null = allow all. */
    private volatile org.apache.eventmesh.runtime.security.gate.SecurityGate securityGate;

    public ConnectorScheduler withSecurityGate(
            org.apache.eventmesh.runtime.security.gate.SecurityGate gate) {
        this.securityGate = gate;
        return this;
    }

    private ScheduledExecutorService scheduler;

    public ConnectorScheduler(MetaStore meta, long ttlMs, long intervalMs, LongSupplier clock) {
        this.meta = meta;
        this.ttlMs = ttlMs;
        this.intervalMs = intervalMs;
        this.clock = clock;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "em-connector-scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(safe(this::reconcile), 0, intervalMs, TimeUnit.MILLISECONDS);
        log.info("connector scheduler started (tick={}ms, workerTtl={}ms)", intervalMs, ttlMs);
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    // ---- connector CRUD ----

    public void createConnector(ConnectorDef def) {
        // #5304: connector CRUD goes through the unified gate when installed.
        org.apache.eventmesh.runtime.security.gate.SecurityGate gate = securityGate;
        if (gate != null) {
            org.apache.eventmesh.runtime.security.gate.RequestContext rc
                = org.apache.eventmesh.runtime.security.gate.RequestContext.builder(
                    org.apache.eventmesh.runtime.security.gate.RequestContext.Operation.CONNECTOR)
                .topic(def.getTopic())
                .clientId(def.getClientId())
                .source("connector")
                .build();
            org.apache.eventmesh.runtime.security.gate.GateDecision decision = gate.check(rc, null);
            if (!decision.isAllowed()) {
                throw new ConnectorAccessDeniedException(decision.getReason());
            }
        }
        meta.put(DEF_PREFIX + def.getId(), writeJson(def));
        reconcile();
    }

    public boolean deleteConnector(String id) {
        boolean existed = meta.delete(DEF_PREFIX + id);
        reconcile();
        return existed;
    }

    public List<ConnectorDef> listConnectors() {
        List<ConnectorDef> out = new ArrayList<>();
        for (String json : meta.getWithPrefix(DEF_PREFIX).values()) {
            try {
                out.add(mapper.readValue(json, ConnectorDef.class));
            } catch (Exception e) {
                log.warn("malformed connector def: {}", e.toString());
            }
        }
        return out;
    }

    public ConnectorDef getConnector(String id) {
        String json = meta.get(DEF_PREFIX + id);
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, ConnectorDef.class);
        } catch (Exception e) {
            log.warn("malformed connector def {}: {}", id, e.toString());
            return null;
        }
    }

    /** connectorId → owner workerId (last reconciled), for admin/status. */
    public Map<String, String> assignments() {
        Map<String, String> out = new LinkedHashMap<>();
        assignmentCache.forEach((id, c) -> out.put(id, c.owner));
        return out;
    }

    // ---- worker registry ----

    public void registerWorker(String workerId, String address) {
        meta.put(WORKER_PREFIX + workerId, clock.getAsLong() + "|" + address);
        reconcile();
    }

    public void heartbeat(String workerId, String address) {
        meta.put(WORKER_PREFIX + workerId, clock.getAsLong() + "|" + address);
    }

    public void leaveWorker(String workerId) {
        meta.delete(WORKER_PREFIX + workerId);
        reconcile();
    }

    public List<Worker> liveWorkers() {
        long now = clock.getAsLong();
        List<Worker> out = new ArrayList<>();
        for (Map.Entry<String, String> e : meta.getWithPrefix(WORKER_PREFIX).entrySet()) {
            try {
                String val = e.getValue();
                int sep = val.indexOf('|');
                long ts = Long.parseLong(sep > 0 ? val.substring(0, sep) : val);
                if (now - ts <= ttlMs) {
                    String id = e.getKey().substring(WORKER_PREFIX.length());
                    String addr = sep > 0 ? val.substring(sep + 1) : null;
                    out.add(new Worker(id, addr));
                }
            } catch (NumberFormatException ignored) {
                // malformed heartbeat — ignore
            }
        }
        Collections.sort(out);
        return out;
    }

    // ---- reconcile ----

    private synchronized void reconcile() {
        Map<String, String> defJsons = meta.getWithPrefix(DEF_PREFIX);
        List<Worker> workers = liveWorkers();
        Map<String, String> addrById = new HashMap<>();
        for (Worker w : workers) {
            if (w.address != null) {
                addrById.put(w.id, w.address);
            }
        }

        // 1. Current defs: push start/stop diffs.
        for (Map.Entry<String, String> e : defJsons.entrySet()) {
            String id = e.getKey().substring(DEF_PREFIX.length());
            String defJson = e.getValue();
            Worker owner = workers.isEmpty() ? null
                : workers.get(Math.floorMod(id.hashCode(), workers.size()));
            String newOwnerId = owner == null ? null : owner.id;
            Cached cached = assignmentCache.get(id);
            String oldOwnerId = cached == null ? null : cached.owner;
            boolean defChanged = cached == null || !defJson.equals(cached.defJson);

            if (oldOwnerId != null && !oldOwnerId.equals(newOwnerId)) {
                // Old owner still alive → tell it to stop; if dead (no address), its connectors died with it.
                String addr = addrById.get(oldOwnerId);
                if (addr != null) {
                    pushStop(addr, id);
                }
            }
            if (owner != null && (!owner.id.equals(oldOwnerId) || defChanged)) {
                pushStart(owner.address, defJson);
            }
            assignmentCache.put(id, new Cached(newOwnerId, defJson));
        }

        // 2. Defs removed from Meta: stop on their last owner (if still alive).
        for (String id : new ArrayList<>(assignmentCache.keySet())) {
            if (!defJsons.containsKey(DEF_PREFIX + id)) {
                Cached cached = assignmentCache.remove(id);
                if (cached != null && cached.owner != null) {
                    String addr = addrById.get(cached.owner);
                    if (addr != null) {
                        pushStop(addr, id);
                    }
                }
            }
        }
    }

    private void pushStart(String workerAddress, String defJson) {
        if (workerAddress == null) {
            return;
        }
        if (postJson("http://" + workerAddress + "/control/start", defJson)) {
            log.info("pushed start to {}", workerAddress);
        }
    }

    private void pushStop(String workerAddress, String id) {
        if (workerAddress == null) {
            return;
        }
        try {
            String body = mapper.writeValueAsString(Collections.singletonMap("id", id));
            if (postJson("http://" + workerAddress + "/control/stop", body)) {
                log.info("pushed stop {} to {}", id, workerAddress);
            }
        } catch (Exception e) {
            log.warn("stop body serialize failed for {}: {}", id, e.toString());
        }
    }

    private boolean postJson(String urlStr, String json) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            log.debug("POST {} failed: {}", urlStr, e.toString());
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String writeJson(ConnectorDef def) {
        try {
            return mapper.writeValueAsString(def);
        } catch (Exception e) {
            throw new RuntimeException("connector def serialize failed", e);
        }
    }

    private Runnable safe(Runnable r) {
        return () -> {
            try {
                r.run();
            } catch (Exception e) {
                log.warn("scheduler tick failed: {}", e.toString());
            }
        };
    }

    /** A live worker registration: id + HTTP address (host:port of its admin server). */
    public static class Worker implements Comparable<Worker> {

        public final String id;
        public final String address;

        public Worker(String id, String address) {
            this.id = id;
            this.address = address;
        }

        @Override
        public int compareTo(Worker o) {
            return id.compareTo(o.id);
        }
    }

    private static class Cached {

        final String owner;
        final String defJson;

        Cached(String owner, String defJson) {
            this.owner = owner;
            this.defJson = defJson;
        }
    }
}
