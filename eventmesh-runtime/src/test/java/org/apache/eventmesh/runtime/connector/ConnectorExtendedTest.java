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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extended tests for ConnectorRuntimeService, OffsetStore, and model classes.
 */
@DisplayName("Connector Extended Tests")
class ConnectorExtendedTest {

    private ConnectorRuntimeService service;

    @BeforeEach
    void setUp() {
        service = new ConnectorRuntimeService();
        service.start();
    }

    @AfterEach
    void tearDown() {
        if (service.isRunning()) {
            try {
                service.shutdown();
            } catch (Exception ignored) {}
        }
    }

    // ========================================================================
    // ConnectorRuntimeService — SHARED mode
    // ========================================================================

    @Test
    @DisplayName("Service: SHARED mode register and start")
    void service_sharedModeRegister() throws Exception {
        ConnectorRuntimeConfig config = new ConnectorRuntimeConfig();
        config.setThreadPoolMode(ConnectorConfig.ThreadPoolMode.SHARED);
        config.setSharedThreadPoolSize(4);

        ConnectorRuntimeService sharedService = new ConnectorRuntimeService(config);
        sharedService.start();

        ConnectorConfig cfg = new ConnectorConfig();
        cfg.setConnectorName("shared-conn");
        cfg.setType(ConnectorConfig.ConnectorType.SOURCE);
        cfg.setPoolMode(ConnectorConfig.ThreadPoolMode.SHARED);
        cfg.setPluginClass("java.lang.Object");
        sharedService.registerConnector(cfg);

        assertEquals(1, sharedService.getConnectorCount());
        assertNotNull(sharedService.getConnectorStatus("shared-conn"));
        sharedService.shutdown();
    }

    // ========================================================================
    // ConnectorRuntimeService — status listing
    // ========================================================================

    @Test
    @DisplayName("Service: getConnectorStatuses returns all")
    void service_statusesReturnsAll() throws Exception {
        for (int i = 0; i < 3; i++) {
            ConnectorConfig cfg = new ConnectorConfig();
            cfg.setConnectorName("s-" + i);
            cfg.setType(ConnectorConfig.ConnectorType.SOURCE);
            cfg.setPluginClass("java.lang.Object");
            service.registerConnector(cfg);
        }

        List<ConnectorStatus> statuses = service.getConnectorStatuses();
        assertEquals(3, statuses.size());
    }

    @Test
    @DisplayName("Service: getConnectorStatus returns null for unknown")
    void service_statusNullForUnknown() {
        assertNull(service.getConnectorStatus("nonexistent"));
    }

    // ========================================================================
    // ConnectorRuntimeService — lifecycle start/stop
    // ========================================================================

    @Test
    @DisplayName("Service: start connector transitions to RUNNING")
    void service_startSetsRunning() throws Exception {
        ConnectorConfig cfg = new ConnectorConfig();
        cfg.setConnectorName("lifecycle");
        cfg.setType(ConnectorConfig.ConnectorType.SOURCE);
        cfg.setPluginClass("java.lang.Object");
        service.registerConnector(cfg);

        service.startConnector("lifecycle");
        ConnectorStatus status = service.getConnectorStatus("lifecycle");
        assertEquals(ConnectorStatus.State.RUNNING, status.getState());
    }

    @Test
    @DisplayName("Service: stop connector transitions to STOPPED")
    void service_stopSetsStopped() throws Exception {
        ConnectorConfig cfg = new ConnectorConfig();
        cfg.setConnectorName("to-stop");
        cfg.setType(ConnectorConfig.ConnectorType.SINK);
        cfg.setPluginClass("java.lang.Object");
        service.registerConnector(cfg);

        service.stopConnector("to-stop");
        ConnectorStatus status = service.getConnectorStatus("to-stop");
        assertEquals(ConnectorStatus.State.STOPPED, status.getState());
    }

    @Test
    @DisplayName("Service: stop unknown connector throws")
    void service_stopUnknownThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> service.stopConnector("ghost"));
    }

    @Test
    @DisplayName("Service: unregister unknown throws")
    void service_unregisterUnknownThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> service.unregisterConnector("ghost"));
    }

    @Test
    @DisplayName("Service: isRunning tracks state")
    void service_isRunning() {
        assertTrue(service.isRunning());
        service.shutdown();
        assertFalse(service.isRunning());
    }

    @Test
    @DisplayName("Service: double start is idempotent")
    void service_doubleStartIdempotent() {
        service.start(); // already started in setUp — should be no-op
        assertTrue(service.isRunning());
    }

    @Test
    @DisplayName("Service: double shutdown is idempotent")
    void service_doubleShutdownIdempotent() {
        service.shutdown();
        service.shutdown(); // should be no-op
        assertFalse(service.isRunning());
    }

    // ========================================================================
    // ConnectorRuntimeService — SINK type
    // ========================================================================

    @Test
    @DisplayName("Service: SINK connector registers correctly")
    void service_sinkConnectorRegisters() throws Exception {
        ConnectorConfig cfg = new ConnectorConfig();
        cfg.setConnectorName("my-sink");
        cfg.setType(ConnectorConfig.ConnectorType.SINK);
        cfg.setPluginClass("java.lang.Object");
        service.registerConnector(cfg);

        ConnectorStatus status = service.getConnectorStatus("my-sink");
        assertEquals(ConnectorConfig.ConnectorType.SINK, status.getType());
    }

    // ========================================================================
    // OffsetStore
    // ========================================================================

    @Test
    @DisplayName("OffsetStore: load returns null for unset key")
    void offset_loadNullForUnset() {
        InMemoryOffsetStore store = new InMemoryOffsetStore();
        assertNull(store.load("nonexistent", "topic", 0));
    }

    @Test
    @DisplayName("OffsetStore: save overwrites existing")
    void offset_saveOverwrites() {
        InMemoryOffsetStore store = new InMemoryOffsetStore();
        store.save("conn", "topic", 0, "100");
        store.save("conn", "topic", 0, "200");
        assertEquals("200", store.load("conn", "topic", 0));
    }

    @Test
    @DisplayName("OffsetStore: loadAll returns empty for unknown")
    void offset_loadAllEmptyForUnknown() {
        InMemoryOffsetStore store = new InMemoryOffsetStore();
        assertTrue(store.loadAll("ghost").isEmpty());
    }

    @Test
    @DisplayName("OffsetStore: flush is no-op")
    void offset_flushNoOp() {
        InMemoryOffsetStore store = new InMemoryOffsetStore();
        store.save("c", "t", 0, "1");
        store.flush(); // should not throw or lose data
        assertEquals("1", store.load("c", "t", 0));
    }

    @Test
    @DisplayName("OffsetStore: multiple connectors isolated")
    void offset_connectorIsolation() {
        InMemoryOffsetStore store = new InMemoryOffsetStore();
        store.save("conn-a", "orders", 0, "42");
        store.save("conn-b", "orders", 0, "99");

        Map<String, String> aOffsets = store.loadAll("conn-a");
        Map<String, String> bOffsets = store.loadAll("conn-b");

        assertEquals(1, aOffsets.size());
        assertEquals(1, bOffsets.size());
        assertNotEquals(
            aOffsets.values().iterator().next(),
            bOffsets.values().iterator().next());
    }

    // ========================================================================
    // ConnectorConfig — model defaults
    // ========================================================================

    @Test
    @DisplayName("Config: default pool mode is DEDICATED")
    void config_defaultPoolModeDedicated() {
        assertEquals(ConnectorConfig.ThreadPoolMode.DEDICATED,
            new ConnectorConfig().getPoolMode());
    }

    @Test
    @DisplayName("Config: default thread pool size is 2")
    void config_defaultThreadsIs2() {
        assertEquals(2, new ConnectorConfig().getThreadPoolSize());
    }

    @Test
    @DisplayName("Config: default max retry is 3")
    void config_defaultMaxRetryIs3() {
        assertEquals(3, new ConnectorConfig().getMaxRetry());
    }

    @Test
    @DisplayName("Config: full setter/getter chain")
    void config_fullSetters() {
        ConnectorConfig cfg = new ConnectorConfig();
        cfg.setConnectorName("test");
        cfg.setType(ConnectorConfig.ConnectorType.SINK);
        cfg.setPluginClass("com.example.MyPlugin");
        cfg.setPoolMode(ConnectorConfig.ThreadPoolMode.SHARED);
        cfg.setThreadPoolSize(4);
        cfg.setMaxRetry(5);
        Map<String, String> props = new HashMap<>();
        props.put("k", "v");
        cfg.setProps(props);

        assertEquals("test", cfg.getConnectorName());
        assertEquals(ConnectorConfig.ConnectorType.SINK, cfg.getType());
        assertEquals("com.example.MyPlugin", cfg.getPluginClass());
        assertEquals(ConnectorConfig.ThreadPoolMode.SHARED, cfg.getPoolMode());
        assertEquals(4, cfg.getThreadPoolSize());
        assertEquals(5, cfg.getMaxRetry());
        assertEquals("v", cfg.getProps().get("k"));
    }

    // ========================================================================
    // ConnectorRuntimeConfig — model defaults
    // ========================================================================

    @Test
    @DisplayName("RuntimeConfig: default max connectors is 16")
    void runtimeConfig_defaultMaxConnectors() {
        assertEquals(16, new ConnectorRuntimeConfig().getMaxConnectors());
    }

    @Test
    @DisplayName("RuntimeConfig: default pool mode is DEDICATED")
    void runtimeConfig_defaultPoolMode() {
        assertEquals(ConnectorConfig.ThreadPoolMode.DEDICATED,
            new ConnectorRuntimeConfig().getThreadPoolMode());
    }

    @Test
    @DisplayName("RuntimeConfig: default health interval is 5s")
    void runtimeConfig_defaultHealthInterval() {
        assertEquals(5, new ConnectorRuntimeConfig().getHealthIntervalSeconds());
    }

    @Test
    @DisplayName("RuntimeConfig: default monitor interval is 30s")
    void runtimeConfig_defaultMonitorInterval() {
        assertEquals(30, new ConnectorRuntimeConfig().getMonitorReportIntervalSeconds());
    }

    @Test
    @DisplayName("RuntimeConfig: default shared pool size is 8")
    void runtimeConfig_defaultSharedPoolSize() {
        assertEquals(8, new ConnectorRuntimeConfig().getSharedThreadPoolSize());
    }

    @Test
    @DisplayName("RuntimeConfig: default dedicated pool size is 2")
    void runtimeConfig_defaultDedicatedPoolSize() {
        assertEquals(2, new ConnectorRuntimeConfig().getDedicatedThreadPoolSize());
    }

    @Test
    @DisplayName("RuntimeConfig: full setter/getter chain")
    void runtimeConfig_fullSetters() {
        ConnectorRuntimeConfig cfg = new ConnectorRuntimeConfig();
        cfg.setMaxConnectors(32);
        cfg.setThreadPoolMode(ConnectorConfig.ThreadPoolMode.SHARED);
        cfg.setDedicatedThreadPoolSize(4);
        cfg.setSharedThreadPoolSize(16);
        cfg.setHealthIntervalSeconds(10);
        cfg.setMonitorReportIntervalSeconds(60);
        cfg.setConnectorPluginConfigPath("plugins/");

        assertEquals(32, cfg.getMaxConnectors());
        assertEquals(ConnectorConfig.ThreadPoolMode.SHARED, cfg.getThreadPoolMode());
        assertEquals(4, cfg.getDedicatedThreadPoolSize());
        assertEquals(16, cfg.getSharedThreadPoolSize());
        assertEquals(10, cfg.getHealthIntervalSeconds());
        assertEquals(60, cfg.getMonitorReportIntervalSeconds());
        assertEquals("plugins/", cfg.getConnectorPluginConfigPath());
    }

    @Test
    @DisplayName("RuntimeConfig: toString format")
    void runtimeConfig_toString() {
        ConnectorRuntimeConfig cfg = new ConnectorRuntimeConfig();
        String s = cfg.toString();
        assertTrue(s.contains("DEDICATED"));
        assertTrue(s.contains("16"));
    }

    // ========================================================================
    // ConnectorStatus — model
    // ========================================================================

    @Test
    @DisplayName("Status: initial state is CREATED")
    void status_initialStateCreated() {
        ConnectorStatus s = new ConnectorStatus("test", ConnectorConfig.ConnectorType.SOURCE);
        assertEquals(ConnectorStatus.State.CREATED, s.getState());
    }

    @Test
    @DisplayName("Status: increment messages and errors")
    void status_incrementCounters() {
        ConnectorStatus s = new ConnectorStatus("test", ConnectorConfig.ConnectorType.SOURCE);
        assertEquals(0, s.getMessagesProcessed());
        s.incrementMessages();
        s.incrementMessages();
        assertEquals(2, s.getMessagesProcessed());

        assertEquals(0, s.getErrors());
        s.incrementErrors();
        assertEquals(1, s.getErrors());
    }

    @Test
    @DisplayName("Status: heartbeat updates timestamp")
    void status_heartbeat() throws InterruptedException {
        ConnectorStatus s = new ConnectorStatus("test", ConnectorConfig.ConnectorType.SOURCE);
        long before = s.getLastHeartbeat();
        Thread.sleep(5);
        s.heartbeat();
        assertTrue(s.getLastHeartbeat() > before);
    }

    @Test
    @DisplayName("Status: error message set/get")
    void status_errorMessage() {
        ConnectorStatus s = new ConnectorStatus("test", ConnectorConfig.ConnectorType.SOURCE);
        assertNull(s.getErrorMessage());
        s.setErrorMessage("Connection timeout");
        assertEquals("Connection timeout", s.getErrorMessage());
    }

    @Test
    @DisplayName("Status: uptime tracking")
    void status_uptimeTracking() {
        ConnectorStatus s = new ConnectorStatus("test", ConnectorConfig.ConnectorType.SOURCE);
        assertEquals(0, s.getUptimeMs());
        s.setUptimeMs(5000);
        assertEquals(5000, s.getUptimeMs());
    }

    @Test
    @DisplayName("Status: all states transition")
    void status_allStates() {
        ConnectorStatus s = new ConnectorStatus("s", ConnectorConfig.ConnectorType.SOURCE);
        assertEquals(ConnectorStatus.State.CREATED, s.getState());
        s.setState(ConnectorStatus.State.RUNNING);
        assertEquals(ConnectorStatus.State.RUNNING, s.getState());
        s.setState(ConnectorStatus.State.PAUSED);
        assertEquals(ConnectorStatus.State.PAUSED, s.getState());
        s.setState(ConnectorStatus.State.FAILED);
        assertEquals(ConnectorStatus.State.FAILED, s.getState());
        s.setState(ConnectorStatus.State.STOPPED);
        assertEquals(ConnectorStatus.State.STOPPED, s.getState());
    }

    @Test
    @DisplayName("Status: toString format")
    void status_toString() {
        ConnectorStatus s = new ConnectorStatus("my-conn", ConnectorConfig.ConnectorType.SINK);
        s.incrementMessages();
        String str = s.toString();
        assertTrue(str.contains("my-conn"));
        assertTrue(str.contains("SINK"));
    }

    // ========================================================================
    // JobInfo — model
    // ========================================================================

    @Test
    @DisplayName("JobInfo: default state is CREATED")
    void jobInfo_initialStateCreated() {
        JobInfo job = new JobInfo();
        assertEquals(JobInfo.JobState.CREATED, job.getState());
        assertTrue(job.getCreateTime() > 0);
        assertEquals(job.getCreateTime(), job.getUpdateTime());
    }

    @Test
    @DisplayName("JobInfo: full setter/getter chain")
    void jobInfo_fullSetters() {
        JobInfo job = new JobInfo();
        job.setJobId("j-001");
        job.setJobName("my-job");
        job.setConnectorType(ConnectorConfig.ConnectorType.SOURCE);
        job.setConnectorName("source-abc");
        job.setConfig("{\"pollInterval\":1000}");
        job.setState(JobInfo.JobState.RUNNING);
        job.setErrorMessage("connection refused");

        assertEquals("j-001", job.getJobId());
        assertEquals("my-job", job.getJobName());
        assertEquals(ConnectorConfig.ConnectorType.SOURCE, job.getConnectorType());
        assertEquals("source-abc", job.getConnectorName());
        assertEquals("{\"pollInterval\":1000}", job.getConfig());
        assertEquals(JobInfo.JobState.RUNNING, job.getState());
        assertEquals("connection refused", job.getErrorMessage());
        assertTrue(job.getUpdateTime() >= job.getCreateTime()); // state change updated
    }

    @Test
    @DisplayName("JobInfo: all job states")
    void jobInfo_allStates() {
        JobInfo job = new JobInfo();
        assertEquals(JobInfo.JobState.CREATED, job.getState());
        job.setState(JobInfo.JobState.RUNNING);
        assertEquals(JobInfo.JobState.RUNNING, job.getState());
        job.setState(JobInfo.JobState.STOPPED);
        assertEquals(JobInfo.JobState.STOPPED, job.getState());
        job.setState(JobInfo.JobState.FAILED);
        assertEquals(JobInfo.JobState.FAILED, job.getState());
    }

    @Test
    @DisplayName("JobInfo: toString format")
    void jobInfo_toString() {
        JobInfo job = new JobInfo();
        job.setJobId("j1");
        job.setJobName("test-job");
        job.setConnectorType(ConnectorConfig.ConnectorType.SINK);
        String s = job.toString();
        assertTrue(s.contains("j1"));
        assertTrue(s.contains("test-job"));
    }

    // ========================================================================
    // ConnectorLimitExceededException
    // ========================================================================

    @Test
    @DisplayName("Limit exception: stores current and max counts")
    void limitException_storesCounts() {
        ConnectorLimitExceededException e =
            new ConnectorLimitExceededException(16, 16);
        assertEquals(16, e.getCurrentCount());
        assertEquals(16, e.getMaxCount());
        assertTrue(e.getMessage().contains("16"));
    }

    // ========================================================================
    // ConnectorConfig.ConnectorType
    // ========================================================================

    @Test
    @DisplayName("Enum: ConnectorType values")
    void enum_connectorTypeValues() {
        assertEquals(ConnectorConfig.ConnectorType.SOURCE,
            ConnectorConfig.ConnectorType.valueOf("SOURCE"));
        assertEquals(ConnectorConfig.ConnectorType.SINK,
            ConnectorConfig.ConnectorType.valueOf("SINK"));
    }

    @Test
    @DisplayName("Enum: ThreadPoolMode values")
    void enum_threadPoolModeValues() {
        assertEquals(ConnectorConfig.ThreadPoolMode.DEDICATED,
            ConnectorConfig.ThreadPoolMode.valueOf("DEDICATED"));
        assertEquals(ConnectorConfig.ThreadPoolMode.SHARED,
            ConnectorConfig.ThreadPoolMode.valueOf("SHARED"));
    }
}
