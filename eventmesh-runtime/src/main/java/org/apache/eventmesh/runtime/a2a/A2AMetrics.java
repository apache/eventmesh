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

package org.apache.eventmesh.runtime.a2a;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-wide A2A gateway counters (issue #5405). Static mirrors — the gateway may boot
 * in several configurations (main process, standalone) but there is at most one live
 * gateway per JVM, and the admin plane reads these without holding a reference to the
 * gateway instance.
 *
 * <p>Surfaced through {@code GET /admin/metrics} (JSON) and {@code GET /metrics}
 * (Prometheus text exposition) on the admin port.</p>
 */
public final class A2AMetrics {

    private static final Map<String, AtomicLong> COUNTERS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> GAUGES = new ConcurrentHashMap<>();

    private A2AMetrics() {
    }

    /** Counter names, stable wire contract for dashboards. */
    public static final String TASKS_SUBMITTED = "a2a_tasks_submitted";
    public static final String TASKS_COMPLETED = "a2a_tasks_completed";
    public static final String TASKS_FAILED = "a2a_tasks_failed";
    public static final String TASKS_CANCELED = "a2a_tasks_canceled";
    public static final String TASKS_EXPIRED = "a2a_tasks_expired";
    public static final String GATEWAY_REJECTIONS = "a2a_gateway_rejections";

    /** Gauge names. */
    public static final String TASKS_ACTIVE = "a2a_tasks_active";

    public static void inc(String name) {
        COUNTERS.computeIfAbsent(name, k -> new AtomicLong()).incrementAndGet();
    }

    public static void add(String name, long delta) {
        COUNTERS.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(delta);
    }

    public static void setGauge(String name, long value) {
        GAUGES.computeIfAbsent(name, k -> new AtomicLong()).set(value);
    }

    /** Snapshot of all counters (name -> value); used by the admin plane. */
    public static Map<String, Long> counters() {
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        for (String n : new String[] {TASKS_SUBMITTED, TASKS_COMPLETED, TASKS_FAILED,
                                      TASKS_CANCELED, TASKS_EXPIRED, GATEWAY_REJECTIONS}) {
            AtomicLong v = COUNTERS.get(n);
            out.put(n, v == null ? 0L : v.get());
        }
        return out;
    }

    /** Snapshot of all gauges (name -> value). */
    public static Map<String, Long> gauges() {
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        AtomicLong v = GAUGES.get(TASKS_ACTIVE);
        out.put(TASKS_ACTIVE, v == null ? 0L : v.get());
        return out;
    }

    /** Test hook: reset all instruments. */
    static void reset() {
        COUNTERS.clear();
        GAUGES.clear();
    }
}
