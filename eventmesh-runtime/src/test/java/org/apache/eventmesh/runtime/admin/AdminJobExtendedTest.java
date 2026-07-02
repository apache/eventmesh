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

import org.apache.eventmesh.runtime.connector.ConnectorConfig;
import org.apache.eventmesh.runtime.connector.ConnectorRuntimeService;
import org.apache.eventmesh.runtime.connector.ConnectorStatus;
import org.apache.eventmesh.runtime.connector.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.connector.JobInfo;
import org.apache.eventmesh.runtime.monitor.PipelineMonitor;
import org.apache.eventmesh.runtime.monitor.ConnectorMonitor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extended tests for AdminClient and JobApiController.
 */
@DisplayName("Admin & Job API Extended Tests")
class AdminJobExtendedTest {

    private ConnectorRuntimeService connectorService;
    private JobApiController jobApi;

    @BeforeEach
    void setUp() {
        connectorService = new ConnectorRuntimeService();
        connectorService.start();
        jobApi = new JobApiController(connectorService);
    }

    @AfterEach
    void tearDown() {
        if (connectorService.isRunning()) {
            connectorService.shutdown();
        }
    }

    // ========================================================================
    // JobApiController — full lifecycle
    // ========================================================================

    @Test
    @DisplayName("JobAPI: create → start → stop → delete lifecycle")
    void jobApi_fullLifecycle() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put("interval", "1000");

        // Create
        JobInfo job = jobApi.createJob("lifecycle-job",
            ConnectorConfig.ConnectorType.SOURCE,
            "java.lang.Object", props);
        assertNotNull(job.getJobId());
        assertEquals(JobInfo.JobState.CREATED, job.getState());
        assertEquals("lifecycle-job", job.getJobName());

        // Start
        JobInfo started = jobApi.startJob(job.getJobId());
        assertEquals(JobInfo.JobState.RUNNING, started.getState());

        // Stop
        JobInfo stopped = jobApi.stopJob(job.getJobId());
        assertEquals(JobInfo.JobState.STOPPED, stopped.getState());

        // Delete
        jobApi.deleteJob(job.getJobId());
        assertNull(jobApi.getJob(job.getJobId()));
    }

    @Test
    @DisplayName("JobAPI: SINK type connector lifecycle")
    void jobApi_sinkLifecycle() throws Exception {
        JobInfo job = jobApi.createJob("sink-job",
            ConnectorConfig.ConnectorType.SINK,
            "java.lang.Object", new HashMap<>());

        assertEquals(ConnectorConfig.ConnectorType.SINK, job.getConnectorType());

        jobApi.startJob(job.getJobId());
        assertEquals(JobInfo.JobState.RUNNING,
            jobApi.getJob(job.getJobId()).getState());
    }

    @Test
    @DisplayName("JobAPI: get job status from connector")
    void jobApi_getJobStatus() throws Exception {
        JobInfo job = jobApi.createJob("status-job",
            ConnectorConfig.ConnectorType.SOURCE,
            "java.lang.Object", new HashMap<>());

        ConnectorStatus status = jobApi.getJobStatus(job.getJobId());
        assertNotNull(status);
        assertEquals(ConnectorStatus.State.CREATED, status.getState());
    }

    @Test
    @DisplayName("JobAPI: get unknown job returns null")
    void jobApi_getUnknownJob() {
        assertNull(jobApi.getJob("ghost-id"));
    }

    @Test
    @DisplayName("JobAPI: start unknown job throws")
    void jobApi_startUnknownJob() {
        assertThrows(IllegalArgumentException.class,
            () -> jobApi.startJob("ghost-id"));
    }

    @Test
    @DisplayName("JobAPI: stop unknown job throws")
    void jobApi_stopUnknownJob() {
        assertThrows(IllegalArgumentException.class,
            () -> jobApi.stopJob("ghost-id"));
    }

    @Test
    @DisplayName("JobAPI: delete unknown job throws")
    void jobApi_deleteUnknownJob() {
        assertThrows(IllegalArgumentException.class,
            () -> jobApi.deleteJob("ghost-id"));
    }

    @Test
    @DisplayName("JobAPI: status for unknown job throws")
    void jobApi_statusUnknownJob() {
        assertThrows(IllegalArgumentException.class,
            () -> jobApi.getJobStatus("ghost-id"));
    }

    @Test
    @DisplayName("JobAPI: list multiple jobs")
    void jobApi_listMultipleJobs() throws Exception {
        jobApi.createJob("a", ConnectorConfig.ConnectorType.SOURCE,
            "java.lang.Object", new HashMap<>());
        jobApi.createJob("b", ConnectorConfig.ConnectorType.SINK,
            "java.lang.Object", new HashMap<>());
        jobApi.createJob("c", ConnectorConfig.ConnectorType.SOURCE,
            "java.lang.Object", new HashMap<>());

        List<JobInfo> jobs = jobApi.listJobs();
        assertEquals(3, jobs.size());
    }

    @Test
    @DisplayName("JobAPI: health check when running")
    void jobApi_healthCheck() {
        Map<String, Object> health = jobApi.getHealth();
        assertEquals("UP", health.get("status"));
        assertEquals(0, health.get("connectorCount"));
        assertEquals(0, health.get("jobCount"));
    }

    @Test
    @DisplayName("JobAPI: health check with jobs")
    void jobApi_healthCheckWithJobs() throws Exception {
        jobApi.createJob("h-job", ConnectorConfig.ConnectorType.SOURCE,
            "java.lang.Object", new HashMap<>());

        Map<String, Object> health = jobApi.getHealth();
        assertEquals("UP", health.get("status"));
        assertEquals(1, health.get("connectorCount"));
        assertEquals(1, health.get("jobCount"));
    }

    @Test
    @DisplayName("JobAPI: health check DOWN when service stopped")
    void jobApi_healthCheckDown() {
        connectorService.shutdown();
        Map<String, Object> health = jobApi.getHealth();
        assertEquals("DOWN", health.get("status"));
    }

    // ========================================================================
    // AdminClient — full coverage
    // ========================================================================

    @Test
    @DisplayName("Admin: starts in RUNNING state")
    void admin_startsInRunning() {
        AdminClient client = new AdminClient("localhost:50051");
        client.start();
        assertEquals(AdminClient.RuntimeState.RUNNING, client.getRuntimeState());
        client.shutdown();
    }

    @Test
    @DisplayName("Admin: shutdown transitions STOPPING → STOPPED")
    void admin_shutdownTransitions() {
        AdminClient client = new AdminClient("localhost:50051");
        client.start();
        client.shutdown();
        assertEquals(AdminClient.RuntimeState.STOPPED, client.getRuntimeState());
    }

    @Test
    @DisplayName("Admin: with adminServerRequired=true starts schedulers")
    void admin_adminServerRequired() {
        InMemoryOffsetStore store = new InMemoryOffsetStore();
        AdminClient client = new AdminClient("localhost:50051", true, store);
        client.start();
        assertEquals(AdminClient.RuntimeState.RUNNING, client.getRuntimeState());
        client.shutdown();
    }

    @Test
    @DisplayName("Admin: adminServerRequired=false is standalone")
    void admin_standaloneMode() {
        AdminClient client = new AdminClient("localhost:50051", false, null);
        client.start();
        assertEquals(AdminClient.RuntimeState.RUNNING, client.getRuntimeState());
        client.shutdown();
    }

    @Test
    @DisplayName("Admin: record and retrieve metrics via monitors")
    void admin_metrics() {
        PipelineMonitor pipelineMonitor = new PipelineMonitor();
        ConnectorMonitor connectorMonitor = new ConnectorMonitor();
        AdminClient client = new AdminClient("localhost:50051", false, null,
            null, pipelineMonitor, connectorMonitor);

        pipelineMonitor.recordIngress(5L);
        pipelineMonitor.recordIngressFiltered();
        connectorMonitor.recordSourceRecords("test-c", 10);

        Map<String, Object> metrics = client.collectMetrics();
        assertEquals(1L, metrics.get("pipeline.ingress.total.count"));
        assertEquals(1L, metrics.get("pipeline.ingress.filtered.count"));
        assertEquals(10L, metrics.get("connector.test-c.source.total"));
    }

    @Test
    @DisplayName("Admin: metrics are unmodifiable")
    void admin_metricsUnmodifiable() {
        PipelineMonitor pipelineMonitor = new PipelineMonitor();
        ConnectorMonitor connectorMonitor = new ConnectorMonitor();
        AdminClient client = new AdminClient("localhost:50051", false, null,
            null, pipelineMonitor, connectorMonitor);

        Map<String, Object> metrics = client.collectMetrics();
        assertThrows(UnsupportedOperationException.class,
            () -> metrics.put("new", "bad"));
    }

    @Test
    @DisplayName("Admin: callback suppliers")
    void admin_callbackSuppliers() {
        AdminClient client = new AdminClient("localhost:50051");
        client.setActiveJobCountSupplier(() -> 5);
        client.setConnectorStatusSupplier(Collections::emptyList);

        // Suppliers set without exception — validated
        client.start();
        client.shutdown();
    }

    @Test
    @DisplayName("Admin: explicit state transitions")
    void admin_explicitStateTransitions() {
        AdminClient client = new AdminClient("localhost:50051");
        assertEquals(AdminClient.RuntimeState.STARTING, client.getRuntimeState());

        client.setState(AdminClient.RuntimeState.RUNNING);
        assertEquals(AdminClient.RuntimeState.RUNNING, client.getRuntimeState());

        client.setState(AdminClient.RuntimeState.DEGRADED);
        assertEquals(AdminClient.RuntimeState.DEGRADED, client.getRuntimeState());

        client.setState(AdminClient.RuntimeState.STOPPING);
        assertEquals(AdminClient.RuntimeState.STOPPING, client.getRuntimeState());

        client.setState(AdminClient.RuntimeState.STOPPED);
        assertEquals(AdminClient.RuntimeState.STOPPED, client.getRuntimeState());
    }

    @Test
    @DisplayName("Admin: toString format")
    void admin_toString() {
        AdminClient client = new AdminClient("10.0.0.1:50051");
        String s = client.toString();
        assertTrue(s.contains("10.0.0.1:50051"));
        assertTrue(s.contains("STARTING"));
        assertTrue(s.contains("standalone=true"));
    }

    @Test
    @DisplayName("Admin: toString with admin server enabled")
    void admin_toStringWithAdminServer() {
        AdminClient client = new AdminClient("host:50051", true, null);
        String s = client.toString();
        assertTrue(s.contains("standalone=false"));
    }

    @Test
    @DisplayName("Admin: all RuntimeState enum values")
    void admin_allRuntimeStates() {
        assertEquals(AdminClient.RuntimeState.STARTING,
            AdminClient.RuntimeState.valueOf("STARTING"));
        assertEquals(AdminClient.RuntimeState.RUNNING,
            AdminClient.RuntimeState.valueOf("RUNNING"));
        assertEquals(AdminClient.RuntimeState.DEGRADED,
            AdminClient.RuntimeState.valueOf("DEGRADED"));
        assertEquals(AdminClient.RuntimeState.STOPPING,
            AdminClient.RuntimeState.valueOf("STOPPING"));
        assertEquals(AdminClient.RuntimeState.STOPPED,
            AdminClient.RuntimeState.valueOf("STOPPED"));
    }

    // ========================================================================
    // JobApiController — job status mapping edge cases
    // ========================================================================

    @Test
    @DisplayName("JobAPI: status mapping for PAUSED → CREATED fallback")
    void jobApi_statusMappingPausedToCreated() throws Exception {
        JobInfo job = jobApi.createJob("paused-job",
            ConnectorConfig.ConnectorType.SOURCE,
            "java.lang.Object", new HashMap<>());

        // Set connector status to PAUSED manually via service
        ConnectorStatus connStatus = connectorService.getConnectorStatus(job.getConnectorName());
        connStatus.setState(ConnectorStatus.State.PAUSED);

        // getJobStatus should map PAUSED → default(CREATED)
        ConnectorStatus status = jobApi.getJobStatus(job.getJobId());
        assertNotNull(status);
        assertEquals(ConnectorStatus.State.PAUSED, status.getState());
        // JobInfo.state is updated in the controller (maps PAUSED → CREATED by default case)
        assertEquals(JobInfo.JobState.CREATED, jobApi.getJob(job.getJobId()).getState());
    }

    @Test
    @DisplayName("JobAPI: second create should differ in jobId")
    void jobApi_uniqueJobIds() throws Exception {
        JobInfo j1 = jobApi.createJob("dup-name",
            ConnectorConfig.ConnectorType.SOURCE,
            "java.lang.Object", new HashMap<>());
        JobInfo j2 = jobApi.createJob("dup-name",
            ConnectorConfig.ConnectorType.SOURCE,
            "java.lang.Object", new HashMap<>());

        assertNotEquals(j1.getJobId(), j2.getJobId());
        assertEquals(2, jobApi.listJobs().size());
    }
}
