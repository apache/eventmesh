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
 * Global connector runtime configuration.
 */
public class ConnectorRuntimeConfig {

    // Thread pool
    private ConnectorConfig.ThreadPoolMode threadPoolMode =
        ConnectorConfig.ThreadPoolMode.DEDICATED;
    private int dedicatedThreadPoolSize = 2;
    private int sharedThreadPoolSize = 8;

    // Limits
    private int maxConnectors = 16;

    // Admin
    private int healthIntervalSeconds = 5;
    private int monitorReportIntervalSeconds = 30;

    // Connector source
    private String connectorPluginConfigPath = "conf/connectors/";

    public ConnectorRuntimeConfig() {}

    // ---- getters ----

    public ConnectorConfig.ThreadPoolMode getThreadPoolMode() { return threadPoolMode; }
    public int getDedicatedThreadPoolSize() { return dedicatedThreadPoolSize; }
    public int getSharedThreadPoolSize() { return sharedThreadPoolSize; }
    public int getMaxConnectors() { return maxConnectors; }
    public int getHealthIntervalSeconds() { return healthIntervalSeconds; }
    public int getMonitorReportIntervalSeconds() { return monitorReportIntervalSeconds; }
    public String getConnectorPluginConfigPath() { return connectorPluginConfigPath; }

    // ---- setters ----

    public void setThreadPoolMode(ConnectorConfig.ThreadPoolMode threadPoolMode) {
        this.threadPoolMode = threadPoolMode;
    }
    public void setDedicatedThreadPoolSize(int dedicatedThreadPoolSize) {
        this.dedicatedThreadPoolSize = dedicatedThreadPoolSize;
    }
    public void setSharedThreadPoolSize(int sharedThreadPoolSize) {
        this.sharedThreadPoolSize = sharedThreadPoolSize;
    }
    public void setMaxConnectors(int maxConnectors) {
        this.maxConnectors = maxConnectors;
    }
    public void setHealthIntervalSeconds(int healthIntervalSeconds) {
        this.healthIntervalSeconds = healthIntervalSeconds;
    }
    public void setMonitorReportIntervalSeconds(int monitorReportIntervalSeconds) {
        this.monitorReportIntervalSeconds = monitorReportIntervalSeconds;
    }
    public void setConnectorPluginConfigPath(String connectorPluginConfigPath) {
        this.connectorPluginConfigPath = connectorPluginConfigPath;
    }

    @Override
    public String toString() {
        return "ConnectorRuntimeConfig{mode=" + threadPoolMode
            + ", maxConnectors=" + maxConnectors
            + ", dedicatedSize=" + dedicatedThreadPoolSize
            + ", sharedSize=" + sharedThreadPoolSize + '}';
    }
}
