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

/**
 * Job information model — aligns with Admin Server's JobInfo.
 */
public class JobInfo {

    public enum JobState { CREATED, RUNNING, STOPPED, FAILED }

    private String jobId;
    private String jobName;
    private ConnectorConfig.ConnectorType connectorType;
    private String connectorName;
    private String config;         // JSON config string
    private JobState state;
    private long createTime;
    private long updateTime;
    private String errorMessage;

    public JobInfo() {
        this.createTime = System.currentTimeMillis();
        this.updateTime = this.createTime;
        this.state = JobState.CREATED;
    }

    // ---- getters ----

    public String getJobId() { return jobId; }
    public String getJobName() { return jobName; }
    public ConnectorConfig.ConnectorType getConnectorType() { return connectorType; }
    public String getConnectorName() { return connectorName; }
    public String getConfig() { return config; }
    public JobState getState() { return state; }
    public long getCreateTime() { return createTime; }
    public long getUpdateTime() { return updateTime; }
    public String getErrorMessage() { return errorMessage; }

    // ---- setters ----

    public void setJobId(String jobId) { this.jobId = jobId; }
    public void setJobName(String jobName) { this.jobName = jobName; }
    public void setConnectorType(ConnectorConfig.ConnectorType connectorType) { this.connectorType = connectorType; }
    public void setConnectorName(String connectorName) { this.connectorName = connectorName; }
    public void setConfig(String config) { this.config = config; }
    public void setState(JobState state) {
        this.state = state;
        this.updateTime = System.currentTimeMillis();
    }
    public void setCreateTime(long createTime) { this.createTime = createTime; }
    public void setUpdateTime(long updateTime) { this.updateTime = updateTime; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    @Override
    public String toString() {
        return "JobInfo{id=" + jobId + ", name=" + jobName
            + ", type=" + connectorType + ", state=" + state + '}';
    }
}
