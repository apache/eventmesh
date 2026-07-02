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
 * ConnectorMonitor — collects per-connector throughput and error metrics.
 *
 * <p>Metrics per connector:
 * <ul>
 *   <li>{@code connector.{name}.source.tps} — source records per second</li>
 *   <li>{@code connector.{name}.source.lag} — source consumption lag</li>
 *   <li>{@code connector.{name}.sink.tps} — sink records per second</li>
 *   <li>{@code connector.{name}.error.count} — total error count</li>
 *   <li>{@code connector.{name}.source.total} — total source records</li>
 *   <li>{@code connector.{name}.sink.total} — total sink records</li>
 * </ul>
 */
@Slf4j
public class ConnectorMonitor {

    private final Map<String, ConnectorStats> stats = new ConcurrentHashMap<>();

    // ---- record methods ----

    /** Record source connector polled N records. */
    public void recordSourceRecords(String connectorName, int count) {
        getOrCreate(connectorName).sourceTotal.addAndGet(count);
        getOrCreate(connectorName).sourceBatch.tick(count);
    }

    /** Record sink connector written N records. */
    public void recordSinkRecords(String connectorName, int count) {
        getOrCreate(connectorName).sinkTotal.addAndGet(count);
        getOrCreate(connectorName).sinkBatch.tick(count);
    }

    /** Record a connector error. */
    public void recordError(String connectorName) {
        getOrCreate(connectorName).errorCount.incrementAndGet();
    }

    /** Record source lag (records behind). */
    public void recordLag(String connectorName, long lag) {
        getOrCreate(connectorName).sourceLag.set(lag);
    }

    /** Record TPS for a connector. */
    public void recordSourceTps(String connectorName, double tps) {
        getOrCreate(connectorName).sourceTps = tps;
    }

    public void recordSinkTps(String connectorName, double tps) {
        getOrCreate(connectorName).sinkTps = tps;
    }

    // ---- metrics snapshot ----

    /** Get a snapshot of all connector metrics. */
    public Map<String, Object> getMetrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, ConnectorStats> entry : stats.entrySet()) {
            String prefix = "connector." + entry.getKey() + ".";
            ConnectorStats s = entry.getValue();
            m.put(prefix + "source.tps", s.sourceTps);
            m.put(prefix + "source.lag", s.sourceLag.get());
            m.put(prefix + "source.total", s.sourceTotal.get());
            m.put(prefix + "sink.tps", s.sinkTps);
            m.put(prefix + "sink.total", s.sinkTotal.get());
            m.put(prefix + "error.count", s.errorCount.get());
        }
        return Collections.unmodifiableMap(m);
    }

    /** Reset all metrics. */
    public void reset() {
        stats.clear();
    }

    /** Remove stats for a connector. */
    public void remove(String connectorName) {
        stats.remove(connectorName);
    }

    // -- internal --

    private ConnectorStats getOrCreate(String name) {
        return stats.computeIfAbsent(name, k -> new ConnectorStats());
    }

    /** Per-connector statistics holder. */
    private static class ConnectorStats {
        final AtomicLong sourceTotal = new AtomicLong(0);
        final AtomicLong sinkTotal = new AtomicLong(0);
        final AtomicLong errorCount = new AtomicLong(0);
        final AtomicLong sourceLag = new AtomicLong(0);
        final TpsTracker sourceBatch = new TpsTracker();
        final TpsTracker sinkBatch = new TpsTracker();
        volatile double sourceTps;
        volatile double sinkTps;
    }

    /** Simple TPS tracker — counts records in the current second. */
    private static class TpsTracker {
        volatile long lastTickMs;
        long count;

        synchronized void tick(int records) {
            long now = System.currentTimeMillis();
            long elapsed = now - lastTickMs;
            if (elapsed >= 1000) {
                lastTickMs = now;
                count = records;
            } else {
                count += records;
            }
        }
    }
}
