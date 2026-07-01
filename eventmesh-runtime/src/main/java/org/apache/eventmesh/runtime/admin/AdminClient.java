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

package org.apache.eventmesh.runtime.admin;

import org.apache.eventmesh.runtime.connector.ConnectorStatus;
import org.apache.eventmesh.runtime.connector.OffsetStore;
import org.apache.eventmesh.runtime.monitor.PipelineMonitor;
import org.apache.eventmesh.runtime.monitor.ConnectorMonitor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

/**
 * AdminClient — in-process management plane for the EventMesh Runtime.
 *
 * <p>Delegates actual communication to a pluggable {@link AdminReporter}.
 * Built-in reporters: Noop (standalone), Grpc, Http.
 *
 * <p>When {@code adminServerRequired=false}, a NoopAdminReporter is used
 * and the Runtime runs in standalone mode without an external Admin Server.
 */
@Slf4j
public class AdminClient {

    public enum RuntimeState { STARTING, RUNNING, DEGRADED, STOPPING, STOPPED }

    private final String runtimeAddress;
    private final boolean adminServerRequired;
    private final OffsetStore offsetStore;
    private final PipelineMonitor pipelineMonitor;
    private final ConnectorMonitor connectorMonitor;
    private final AdminReporter reporter;

    // State
    private volatile RuntimeState runtimeState = RuntimeState.STARTING;

    // Schedulers
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> monitorTask;
    private ScheduledFuture<?> offsetSyncTask;

    // Callbacks — set by the caller to customize reporting
    private Supplier<Integer> activeJobCountSupplier = () -> 0;
    private Supplier<List<ConnectorStatus>> connectorStatusSupplier = Collections::emptyList;

    public AdminClient(String runtimeAddress, boolean adminServerRequired,
                       OffsetStore offsetStore, AdminReporter reporter,
                       PipelineMonitor pipelineMonitor, ConnectorMonitor connectorMonitor) {
        this.runtimeAddress = runtimeAddress;
        this.adminServerRequired = adminServerRequired;
        this.offsetStore = offsetStore;
        this.reporter = (reporter != null) ? reporter : new NoopAdminReporter();
        this.pipelineMonitor = (pipelineMonitor != null) ? pipelineMonitor : new PipelineMonitor();
        this.connectorMonitor = (connectorMonitor != null) ? connectorMonitor : new ConnectorMonitor();
        this.scheduler = Executors.newScheduledThreadPool(3, r -> {
            Thread t = new Thread(r, "admin-client");
            t.setDaemon(true);
            return t;
        });
    }

    /** Standalone constructor (NoopReporter) */
    public AdminClient(String runtimeAddress) {
        this(runtimeAddress, false, null, null, null, null);
    }

    /** Standalone with offset store */
    public AdminClient(String runtimeAddress, boolean adminServerRequired,
                       OffsetStore offsetStore) {
        this(runtimeAddress, adminServerRequired, offsetStore, null, null, null);
    }

    // ---- lifecycle ----

    public void start() {
        setState(RuntimeState.RUNNING);

        heartbeatTask = scheduler.scheduleAtFixedRate(
            this::sendHeartbeat, 5, 5, TimeUnit.SECONDS);
        monitorTask = scheduler.scheduleAtFixedRate(
            this::sendMonitorReport, 30, 30, TimeUnit.SECONDS);

        if (offsetStore != null) {
            offsetSyncTask = scheduler.scheduleAtFixedRate(
                this::syncOffsets, 60, 60, TimeUnit.SECONDS);
        }

        if (!adminServerRequired || !reporter.isConnected()) {
            log.info("AdminClient started in standalone mode (state={})", runtimeState);
        } else {
            log.info("AdminClient started, reporting to Admin Server, state={}", runtimeState);
        }
    }

    public void shutdown() {
        setState(RuntimeState.STOPPING);
        if (heartbeatTask != null) heartbeatTask.cancel(false);
        if (monitorTask != null) monitorTask.cancel(false);
        if (offsetSyncTask != null) offsetSyncTask.cancel(false);
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        reporter.shutdown();
        setState(RuntimeState.STOPPED);
        log.info("AdminClient shut down");
    }

    // ---- state management ----

    public RuntimeState getRuntimeState() { return runtimeState; }

    public void setState(RuntimeState state) {
        RuntimeState old = this.runtimeState;
        this.runtimeState = state;
        log.info("Runtime state: {} → {}", old, state);
    }

    // ---- callbacks ----

    public void setActiveJobCountSupplier(Supplier<Integer> supplier) {
        this.activeJobCountSupplier = supplier;
    }

    public void setConnectorStatusSupplier(Supplier<List<ConnectorStatus>> supplier) {
        this.connectorStatusSupplier = supplier;
    }

    // ---- monitors ----

    public PipelineMonitor getPipelineMonitor() { return pipelineMonitor; }
    public ConnectorMonitor getConnectorMonitor() { return connectorMonitor; }

    // ---- periodic tasks ----

    private void sendHeartbeat() {
        try {
            int activeJobs = activeJobCountSupplier.get();
            reporter.reportHeartbeat(runtimeAddress, runtimeState.name(), activeJobs);
            if (log.isDebugEnabled()) {
                log.debug("Heartbeat: addr={}, state={}, jobs={}",
                    runtimeAddress, runtimeState, activeJobs);
            }
        } catch (Exception e) {
            log.warn("Heartbeat failed", e);
        }
    }

    private void sendMonitorReport() {
        try {
            List<ConnectorStatus> statuses = connectorStatusSupplier.get();
            Map<String, Object> metrics = collectMetrics();
            reporter.reportMonitor(runtimeAddress, metrics, statuses);
            if (log.isDebugEnabled()) {
                log.debug("Monitor report: {} connectors, {} metrics",
                    statuses.size(), metrics.size());
            }
        } catch (Exception e) {
            log.warn("Monitor report failed", e);
        }
    }

    private void syncOffsets() {
        try {
            if (offsetStore != null) {
                offsetStore.flush();
                // Build offsets snapshot for remote sync
                Map<String, String> snapshot = new LinkedHashMap<>();
                reporter.syncOffsets(runtimeAddress, snapshot);
            }
        } catch (Exception e) {
            log.warn("Offset sync failed", e);
        }
    }

    /** Collect all metrics from monitors into a single unmodifiable map. */
    public Map<String, Object> collectMetrics() {
        Map<String, Object> all = new LinkedHashMap<>();
        all.putAll(pipelineMonitor.getMetrics());
        all.putAll(connectorMonitor.getMetrics());
        all.put("runtime.state", runtimeState.name());
        all.put("runtime.address", runtimeAddress);
        return Collections.unmodifiableMap(all);
    }

    // ---- toString ----
    @Override
    public String toString() {
        return "AdminClient{address=" + runtimeAddress
            + ", state=" + runtimeState
            + ", standalone=" + !adminServerRequired + '}';
    }

    // -- visibility for tests --

    AdminReporter getReporter() { return reporter; }

    // ---- inner: NoopAdminReporter ----

    static class NoopAdminReporter implements AdminReporter {
        @Override public void reportHeartbeat(String addr, String state, int jobs) {}
        @Override public void reportMonitor(String addr, Map<String, Object> metrics, List<ConnectorStatus> statuses) {}
        @Override public void syncOffsets(String addr, Map<String, String> offsets) {}
        @Override public boolean isConnected() { return false; }
        @Override public void shutdown() {}
    }
}
