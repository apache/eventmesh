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

import java.util.Collections;
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
 * <p>Reports health, metrics, status, and connector offsets to the
 * external Admin Server via gRPC BiStream (or, in standalone mode,
 * serves them directly via HTTP endpoints).
 *
 * <p>When {@code adminServerRequired=false}, the client reports are
 * no-ops — the Runtime runs in standalone mode without an external
 * Admin Server.
 */
@Slf4j
public class AdminClient {

    public enum RuntimeState { STARTING, RUNNING, DEGRADED, STOPPING, STOPPED }

    private final String runtimeAddress;
    private final boolean adminServerRequired;
    private final OffsetStore offsetStore;

    // State
    private volatile RuntimeState runtimeState = RuntimeState.STARTING;
    private final Map<String, Object> metrics = new ConcurrentHashMap<>();

    // Schedulers
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> monitorTask;
    private ScheduledFuture<?> offsetSyncTask;

    // Callbacks — set by the caller to customize reporting
    private Supplier<Integer> activeJobCountSupplier = () -> 0;
    private Supplier<List<ConnectorStatus>> connectorStatusSupplier = Collections::emptyList;

    public AdminClient(String runtimeAddress, boolean adminServerRequired,
                       OffsetStore offsetStore) {
        this.runtimeAddress = runtimeAddress;
        this.adminServerRequired = adminServerRequired;
        this.offsetStore = offsetStore;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "admin-client-" + r.hashCode());
            t.setDaemon(true);
            return t;
        });
    }

    public AdminClient(String runtimeAddress) {
        this(runtimeAddress, false, null);
    }

    // ---- lifecycle ----

    public void start() {
        setState(RuntimeState.RUNNING);

        if (!adminServerRequired) {
            log.info("AdminClient started in standalone mode (adminServer.required=false)");
            return;
        }

        // Start periodic tasks
        heartbeatTask = scheduler.scheduleAtFixedRate(
            this::sendHeartbeat, 5, 5, TimeUnit.SECONDS);
        monitorTask = scheduler.scheduleAtFixedRate(
            this::sendMonitorReport, 30, 30, TimeUnit.SECONDS);

        if (offsetStore != null) {
            offsetSyncTask = scheduler.scheduleAtFixedRate(
                this::syncOffsets, 60, 60, TimeUnit.SECONDS);
        }

        log.info("AdminClient started, reporting to Admin Server, state={}", runtimeState);
    }

    public void shutdown() {
        setState(RuntimeState.STOPPING);
        if (heartbeatTask != null) heartbeatTask.cancel(false);
        if (monitorTask != null) monitorTask.cancel(false);
        if (offsetSyncTask != null) offsetSyncTask.cancel(false);
        scheduler.shutdown();
        setState(RuntimeState.STOPPED);
        log.info("AdminClient shut down");
    }

    // ---- state management ----

    public RuntimeState getRuntimeState() { return runtimeState; }

    public void setState(RuntimeState state) {
        this.runtimeState = state;
        log.info("Runtime state changed: {} → {}", runtimeState, state);
    }

    // ---- callbacks ----

    public void setActiveJobCountSupplier(Supplier<Integer> supplier) {
        this.activeJobCountSupplier = supplier;
    }

    public void setConnectorStatusSupplier(Supplier<List<ConnectorStatus>> supplier) {
        this.connectorStatusSupplier = supplier;
    }

    // ---- metrics ----

    public void recordMetric(String key, Object value) {
        metrics.put(key, value);
    }

    public Map<String, Object> getMetrics() {
        return Collections.unmodifiableMap(metrics);
    }

    // ---- periodic tasks (stubs — extend for real gRPC) ----

    private void sendHeartbeat() {
        int activeJobs = activeJobCountSupplier.get();
        log.debug("Heartbeat: address={}, state={}, activeJobs={}",
            runtimeAddress, runtimeState, activeJobs);
        // TODO: gRPC → Admin Server
    }

    private void sendMonitorReport() {
        List<ConnectorStatus> statuses = connectorStatusSupplier.get();
        log.debug("Monitor report: {} connectors, metrics={}",
            statuses.size(), metrics.size());
        // TODO: gRPC → Admin Server
    }

    private void syncOffsets() {
        if (offsetStore != null) {
            offsetStore.flush();
            log.debug("Offset sync flushed");
        }
        // TODO: gRPC → Admin Server
    }

    @Override
    public String toString() {
        return "AdminClient{address=" + runtimeAddress
            + ", state=" + runtimeState
            + ", standAlone=" + !adminServerRequired + '}';
    }
}
