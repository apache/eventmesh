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

package org.apache.eventmesh.runtime.ingress;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;

import lombok.extern.slf4j.Slf4j;

/**
 * Self-collected load metrics for this runtime instance, used by the session-distribution load
 * balancer (see {@code docs/eventmesh-architecture-refinement.md} §3). The client reports nothing;
 * EventMesh samples its own traffic at the ingress/egress points and surfaces a snapshot that the
 * heartbeat carries to the cluster so {@code /session/recommend} can score instances globally.
 *
 * <p>Byte rates are sliding-window-free: raw byte counters are sampled periodically and divided by
 * the elapsed seconds since the last sample, yielding an EWMA-like bytes/sec. Per-{@code clientId}
 * buckets feed the "large client spread" recommendation (avoid stacking one client's sessions on a
 * single instance). CPU load comes from {@link OperatingSystemMXBean}.</p>
 */
@Slf4j
public class LoadMeter {

    private final AtomicLong inflowBytes = new AtomicLong();
    private final AtomicLong outflowBytes = new AtomicLong();
    /** Per-clientId inflow bytes (best-effort; used to detect large clients for spread). */
    private final ConcurrentHashMap<String, AtomicLong> clientInflow = new ConcurrentHashMap<>();
    private final IntSupplier activeSessionsSupplier;
    private final OperatingSystemMXBean os;

    // sliding snapshot state
    private volatile long lastSampleTimeMs = System.currentTimeMillis();
    private volatile long lastInflow = 0L;
    private volatile long lastOutflow = 0L;
    private volatile double inflowBytesPerSec = 0.0;
    private volatile double outflowBytesPerSec = 0.0;

    public LoadMeter(IntSupplier activeSessionsSupplier) {
        this(activeSessionsSupplier, ManagementFactory.getOperatingSystemMXBean());
    }

    LoadMeter(IntSupplier activeSessionsSupplier, OperatingSystemMXBean os) {
        this.activeSessionsSupplier = activeSessionsSupplier;
        this.os = os;
    }

    /** Account inbound bytes (a published event's body entered the runtime). */
    public void recordInflow(int bytes) {
        if (bytes <= 0) {
            return;
        }
        inflowBytes.addAndGet(bytes);
    }

    /** Account inbound bytes attributed to a specific client (for large-client spread scoring). */
    public void recordInflow(String clientId, int bytes) {
        recordInflow(bytes);
        if (clientId != null && bytes > 0) {
            clientInflow.computeIfAbsent(clientId, k -> new AtomicLong()).addAndGet(bytes);
        }
    }

    /** Account outbound bytes (an event/SSE frame left the runtime toward a client). */
    public void recordOutflow(int bytes) {
        if (bytes > 0) {
            outflowBytes.addAndGet(bytes);
        }
    }

    /**
     * Refresh the bytes/sec rates from the raw counters. Called periodically by the heartbeat loop
     * (PartitionOwnership, ~5s) — cheap enough to run every tick.
     */
    public void sample() {
        long now = System.currentTimeMillis();
        long curIn = inflowBytes.get();
        long curOut = outflowBytes.get();
        long elapsedMs = now - lastSampleTimeMs;
        if (elapsedMs > 0) {
            inflowBytesPerSec = (curIn - lastInflow) * 1000.0 / elapsedMs;
            outflowBytesPerSec = (curOut - lastOutflow) * 1000.0 / elapsedMs;
        }
        lastSampleTimeMs = now;
        lastInflow = curIn;
        lastOutflow = curOut;
    }

    /**
     * @return an immutable snapshot of this instance's current load (rates are from the last
     *         {@link #sample()}; call sample() on the heartbeat tick before reading).
     */
    public Snapshot snapshot() {
        double cpuLoad;
        try {
            // com.sun management bean — available on the JVMs EventMesh targets (OpenJDK 17/21).
            cpuLoad = ((com.sun.management.OperatingSystemMXBean) os).getProcessCpuLoad();
        } catch (Throwable t) {
            cpuLoad = -1;
        }
        return new Snapshot(
            activeSessionsSupplier.getAsInt(),
            (long) inflowBytesPerSec,
            (long) outflowBytesPerSec,
            cpuLoad < 0 ? 0.0 : cpuLoad);
    }

    /** Approximate per-client inflow bytes since process start (for spread scoring). */
    public Map<String, Long> clientInflowSnapshot() {
        Map<String, Long> out = new HashMap<>();
        for (Map.Entry<String, AtomicLong> e : clientInflow.entrySet()) {
            out.put(e.getKey(), e.getValue().get());
        }
        return Collections.unmodifiableMap(out);
    }

    /** Immutable load snapshot, serialized into the heartbeat value by the cluster layer. */
    public static final class Snapshot {
        public final int activeSessions;
        public final long inflowBytesPerSec;
        public final long outflowBytesPerSec;
        public final double cpuLoad;

        public Snapshot(int activeSessions, long inflowBytesPerSec, long outflowBytesPerSec, double cpuLoad) {
            this.activeSessions = activeSessions;
            this.inflowBytesPerSec = inflowBytesPerSec;
            this.outflowBytesPerSec = outflowBytesPerSec;
            this.cpuLoad = cpuLoad;
        }

        /** Parse a heartbeat value's trailing load fields: {@code <active>|<inflow>|<outflow>|<cpu>}. */
        public static Snapshot parseLoad(String[] tail) {
            // tail = the parts after ts|addr; may be empty (old peer) or 4 fields.
            if (tail == null || tail.length < 4) {
                return null; // peer didn't report load
            }
            try {
                return new Snapshot(
                    Integer.parseInt(tail[0].trim()),
                    Long.parseLong(tail[1].trim()),
                    Long.parseLong(tail[2].trim()),
                    Double.parseDouble(tail[3].trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        @Override
        public String toString() {
            return activeSessions + "|" + inflowBytesPerSec + "|" + outflowBytesPerSec + "|" + cpuLoad;
        }
    }
}
