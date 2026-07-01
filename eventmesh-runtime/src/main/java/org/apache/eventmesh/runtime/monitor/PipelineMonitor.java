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

package org.apache.eventmesh.runtime.monitor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

/**
 * PipelineMonitor — collects pipeline processing metrics.
 *
 * <p>Metrics:
 * <ul>
 *   <li>{@code pipeline.ingress.total.count} — total ingress events</li>
 *   <li>{@code pipeline.ingress.filtered.count} — filtered/dropped events</li>
 *   <li>{@code pipeline.ingress.error.count} — pipeline errors</li>
 *   <li>{@code pipeline.ingress.latency.avg_ms} — average processing latency</li>
 *   <li>{@code pipeline.egress.total.count} — total egress events</li>
 *   <li>{@code pipeline.egress.filtered.count} — filtered egress events</li>
 *   <li>{@code pipeline.egress.latency.avg_ms} — egress latency</li>
 * </ul>
 */
@Slf4j
public class PipelineMonitor {

    // Ingress counters
    private final AtomicLong ingressTotal = new AtomicLong(0);
    private final AtomicLong ingressFiltered = new AtomicLong(0);
    private final AtomicLong ingressError = new AtomicLong(0);
    private final AtomicLong ingressLatencySumMs = new AtomicLong(0);
    private final AtomicLong ingressLatencyCount = new AtomicLong(0);

    // Egress counters
    private final AtomicLong egressTotal = new AtomicLong(0);
    private final AtomicLong egressFiltered = new AtomicLong(0);
    private final AtomicLong egressLatencySumMs = new AtomicLong(0);
    private final AtomicLong egressLatencyCount = new AtomicLong(0);

    // Per-filter stats
    private final Map<String, AtomicLong> filterHits = new ConcurrentHashMap<>();

    // ---- record methods ----

    /** Record an ingress event that was accepted. */
    public void recordIngress(long latencyMs) {
        ingressTotal.incrementAndGet();
        if (latencyMs >= 0) {
            ingressLatencySumMs.addAndGet(latencyMs);
            ingressLatencyCount.incrementAndGet();
        }
    }

    /** Record an ingress event that was filtered. */
    public void recordIngressFiltered() {
        ingressFiltered.incrementAndGet();
    }

    /** Record an ingress pipeline error. */
    public void recordIngressError() {
        ingressError.incrementAndGet();
    }

    /** Record an egress event. */
    public void recordEgress(long latencyMs) {
        egressTotal.incrementAndGet();
        if (latencyMs >= 0) {
            egressLatencySumMs.addAndGet(latencyMs);
            egressLatencyCount.incrementAndGet();
        }
    }

    /** Record an egress event that was filtered. */
    public void recordEgressFiltered() {
        egressFiltered.incrementAndGet();
    }

    /** Record a filter hit (for per-filter statistics). */
    public void recordFilterHit(String filterName) {
        filterHits.computeIfAbsent(filterName, k -> new AtomicLong(0)).incrementAndGet();
    }

    // ---- metrics snapshot ----

    /** Get a snapshot of all metrics for reporting. */
    public Map<String, Object> getMetrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pipeline.ingress.total.count", ingressTotal.get());
        m.put("pipeline.ingress.filtered.count", ingressFiltered.get());
        m.put("pipeline.ingress.error.count", ingressError.get());
        m.put("pipeline.ingress.latency.avg_ms", avg(ingressLatencySumMs, ingressLatencyCount));
        m.put("pipeline.egress.total.count", egressTotal.get());
        m.put("pipeline.egress.filtered.count", egressFiltered.get());
        m.put("pipeline.egress.latency.avg_ms", avg(egressLatencySumMs, egressLatencyCount));

        // Per-filter stats
        for (Map.Entry<String, AtomicLong> e : filterHits.entrySet()) {
            m.put("pipeline.filter." + e.getKey() + ".hits", e.getValue().get());
        }
        return Collections.unmodifiableMap(m);
    }

    /** Reset all counters. */
    public void reset() {
        ingressTotal.set(0);
        ingressFiltered.set(0);
        ingressError.set(0);
        ingressLatencySumMs.set(0);
        ingressLatencyCount.set(0);
        egressTotal.set(0);
        egressFiltered.set(0);
        egressLatencySumMs.set(0);
        egressLatencyCount.set(0);
        filterHits.clear();
    }

    private static double avg(AtomicLong sum, AtomicLong count) {
        long c = count.get();
        return c > 0 ? (double) sum.get() / c : 0.0;
    }
}
