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
import org.apache.eventmesh.runtime.connector.ConnectorLimitExceededException;
import org.apache.eventmesh.runtime.connector.ConnectorRuntimeService;
import org.apache.eventmesh.runtime.connector.ConnectorStatus;
import org.apache.eventmesh.runtime.connector.JobInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

/**
 * HTTP-based Job Management API.
 *
 * <p>Provides RESTful endpoints for managing Connector jobs:
 * <ul>
 *   <li>POST /admin/jobs — create job</li>
 *   <li>GET /admin/jobs — list jobs</li>
 *   <li>PUT /admin/jobs/{id}/start — start job</li>
 *   <li>PUT /admin/jobs/{id}/stop — stop job</li>
 *   <li>GET /admin/jobs/{id}/status — job status</li>
 *   <li>DELETE /admin/jobs/{id} — delete job</li>
 * </ul>
 */
@Slf4j
public class JobApiController {

    private final ConnectorRuntimeService connectorService;
    private final Map<String, JobInfo> jobs;

    public JobApiController(ConnectorRuntimeService connectorService) {
        this.connectorService = connectorService;
        this.jobs = new ConcurrentHashMap<>();
    }

    // ---- CREATE ----

    public JobInfo createJob(String jobName, ConnectorConfig.ConnectorType type,
                              String connectorClass, Map<String, String> props)
        throws ConnectorLimitExceededException {

        String jobId = UUID.randomUUID().toString().substring(0, 8);
        String connectorName = type.name().toLowerCase() + "-" + jobId;

        ConnectorConfig cfg = new ConnectorConfig();
        cfg.setConnectorName(connectorName);
        cfg.setType(type);
        cfg.setPluginClass(connectorClass);
        cfg.setProps(props);

        connectorService.registerConnector(cfg);

        JobInfo job = new JobInfo();
        job.setJobId(jobId);
        job.setJobName(jobName);
        job.setConnectorType(type);
        job.setConnectorName(connectorName);
        job.setState(JobInfo.JobState.CREATED);

        jobs.put(jobId, job);
        log.info("Created job: {}", job);
        return job;
    }

    // ---- LIST ----

    public List<JobInfo> listJobs() {
        return new ArrayList<>(jobs.values());
    }

    // ---- GET ----

    public JobInfo getJob(String jobId) {
        return jobs.get(jobId);
    }

    // ---- START ----

    public JobInfo startJob(String jobId) throws Exception {
        JobInfo job = requireJob(jobId);
        connectorService.startConnector(job.getConnectorName());
        job.setState(JobInfo.JobState.RUNNING);
        log.info("Started job: {}", jobId);
        return job;
    }

    // ---- STOP ----

    public JobInfo stopJob(String jobId) throws Exception {
        JobInfo job = requireJob(jobId);
        connectorService.stopConnector(job.getConnectorName());
        job.setState(JobInfo.JobState.STOPPED);
        log.info("Stopped job: {}", jobId);
        return job;
    }

    // ---- STATUS ----

    public ConnectorStatus getJobStatus(String jobId) {
        JobInfo job = requireJob(jobId);
        ConnectorStatus status = connectorService.getConnectorStatus(job.getConnectorName());
        if (status != null) {
            job.setState(mapState(status.getState()));
        }
        return status;
    }

    // ---- DELETE ----

    public void deleteJob(String jobId) throws Exception {
        JobInfo job = requireJob(jobId);
        connectorService.unregisterConnector(job.getConnectorName());
        jobs.remove(jobId);
        log.info("Deleted job: {}", jobId);
    }

    // ---- HEALTH ----

    public java.util.Map<String, Object> getHealth() {
        java.util.Map<String, Object> health = new java.util.HashMap<>();
        health.put("status", connectorService.isRunning() ? "UP" : "DOWN");
        health.put("connectorCount", connectorService.getConnectorCount());
        health.put("jobCount", jobs.size());
        return health;
    }

    // ---- helpers ----

    private JobInfo requireJob(String jobId) {
        JobInfo job = jobs.get(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }
        return job;
    }

    private static JobInfo.JobState mapState(ConnectorStatus.State state) {
        switch (state) {
            case RUNNING: return JobInfo.JobState.RUNNING;
            case STOPPED: return JobInfo.JobState.STOPPED;
            case FAILED:  return JobInfo.JobState.FAILED;
            default:      return JobInfo.JobState.CREATED;
        }
    }
}
