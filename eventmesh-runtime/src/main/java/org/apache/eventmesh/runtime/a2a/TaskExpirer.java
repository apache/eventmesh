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

import org.apache.eventmesh.runtime.state.TaskStore;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

/**
 * Reaper that periodically evicts expired A2A tasks from a {@link TaskStore}.
 *
 * <p>Issue #5302 Sub-PR D2: a task that is abandoned by the gateway (the agent
 * is gone, the client disconnected, the A2A call timed out without an explicit
 * cancel) lingers in the {@link TaskStore} until something deletes it. The
 * reaper calls {@link TaskStore#expireStale(long)} on a fixed cadence and
 * returns the expired taskIds so the caller can release any associated
 * resources (in-memory caches, pending futures, SSE subscribers).</p>
 *
 * <p>The reaper is opt-in: it starts when {@link #start()} is called and stops
 * when {@link #shutdown()} is called. The cadence and TTL are configurable; the
 * default is to scan every 60s and expire tasks idle for more than 24h.</p>
 *
 * <p>The reaper is intentionally <b>not</b> wired into the gateway's start()
 * path by default. The A2A gateway already has a per-task timeout
 * ({@code A2AGatewayService.taskTimeoutMs}, default 2 minutes) that auto-fails
 * tasks that miss a response. The reaper is for the OTHER stale case: a task
 * that has a terminal status (COMPLETED / FAILED / CANCELED) but no caller
 * ever asked to retire it. The reaper is opt-in so existing deployments that
 * do not want a background scanner are not affected.</p>
 */
@Slf4j
public final class TaskExpirer {

    /** Default idle TTL: 24 hours. */
    public static final long DEFAULT_IDLE_TTL_MS = 24L * 60L * 60L * 1000L;

    /** Default scan interval: 60 seconds. */
    public static final long DEFAULT_SCAN_INTERVAL_MS = 60L * 1000L;

    private final TaskStore taskStore;
    private final long idleTtlMs;
    private final long scanIntervalMs;
    private final ExpiredTaskListener listener;

    private ScheduledExecutorService scheduler;
    private final AtomicLong totalExpired = new AtomicLong();

    /**
     * Listener notified after every scan with the list of evicted taskIds.
     * The default no-op listener is used when {@code listener} is null.
     */
    @FunctionalInterface
    public interface ExpiredTaskListener {
        void onExpired(List<String> taskIds);
    }

    /**
     * Create a reaper with default TTL and scan interval, no listener.
     */
    public TaskExpirer(TaskStore taskStore) {
        this(taskStore, DEFAULT_IDLE_TTL_MS, DEFAULT_SCAN_INTERVAL_MS, null);
    }

    /**
     * Create a reaper with custom TTL, scan interval, and an optional listener.
     *
     * @param taskStore       the store to scan (must be non-null)
     * @param idleTtlMs       tasks idle for longer than this are evicted (must be > 0)
     * @param scanIntervalMs  how often the reaper scans (must be > 0)
     * @param listener        notified after every scan with the evicted taskIds; may be null
     */
    public TaskExpirer(TaskStore taskStore, long idleTtlMs, long scanIntervalMs,
                       ExpiredTaskListener listener) {
        this.taskStore = Objects.requireNonNull(taskStore, "taskStore");
        if (idleTtlMs <= 0) {
            throw new IllegalArgumentException("idleTtlMs must be > 0, got " + idleTtlMs);
        }
        if (scanIntervalMs <= 0) {
            throw new IllegalArgumentException("scanIntervalMs must be > 0, got " + scanIntervalMs);
        }
        this.idleTtlMs = idleTtlMs;
        this.scanIntervalMs = scanIntervalMs;
        this.listener = listener == null ? ids -> { } : listener;
    }

    /**
     * Start the reaper. Idempotent: a second call while the reaper is running is a
     * no-op. The reaper is initially scheduled after one scan interval, not
     * immediately, so a fresh gateway start has time to warm up.
     */
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "a2a-task-expirer");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::scan, scanIntervalMs, scanIntervalMs, TimeUnit.MILLISECONDS);
        log.info("TaskExpirer started: idleTtlMs={}, scanIntervalMs={}", idleTtlMs, scanIntervalMs);
    }

    /**
     * Stop the reaper. After shutdown, the reaper can be re-started by calling
     * {@link #start()} again.
     */
    public synchronized void shutdown() {
        if (scheduler == null) {
            return;
        }
        scheduler.shutdownNow();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("TaskExpirer scheduler did not terminate within 5s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        scheduler = null;
        log.info("TaskExpirer stopped; totalExpired={}", totalExpired.get());
    }

    /**
     * Run a single scan. Visible for testing: a test can drive the reaper without
     * waiting for the scheduler to fire.
     *
     * @return the list of evicted taskIds (empty if nothing was expired)
     */
    public List<String> scan() {
        try {
            List<String> expired = taskStore.expireStale(idleTtlMs);
            if (!expired.isEmpty()) {
                totalExpired.addAndGet(expired.size());
                log.info("TaskExpirer evicted {} stale task(s); total={}", expired.size(), totalExpired.get());
            }
            try {
                listener.onExpired(expired);
            } catch (RuntimeException e) {
                log.warn("TaskExpirer listener threw: {}", e.getMessage());
            }
            return expired;
        } catch (RuntimeException e) {
            log.warn("TaskExpirer scan failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * @return cumulative count of expired tasks since this reaper was created.
     */
    public long getTotalExpired() {
        return totalExpired.get();
    }

    public long getIdleTtlMs() {
        return idleTtlMs;
    }

    public long getScanIntervalMs() {
        return scanIntervalMs;
    }

    public boolean isRunning() {
        return scheduler != null;
    }
}
