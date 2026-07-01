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

import java.util.Map;

/**
 * Per-connector configuration model.
 */
public class ConnectorConfig {

    public enum ConnectorType { SOURCE, SINK }

    public enum ThreadPoolMode {
        /** Per-connector dedicated thread pool (production default) */
        DEDICATED,
        /** Shared thread pool across all connectors */
        SHARED
    }

    private String connectorName;
    private ConnectorType type;
    private String pluginClass;
    private Map<String, String> props;
    private ThreadPoolMode poolMode = ThreadPoolMode.DEDICATED;
    private int threadPoolSize = 2;
    private int maxRetry = 3;

    public ConnectorConfig() {}

    // ---- getters ----

    public String getConnectorName() { return connectorName; }
    public ConnectorType getType() { return type; }
    public String getPluginClass() { return pluginClass; }
    public Map<String, String> getProps() { return props; }
    public ThreadPoolMode getPoolMode() { return poolMode; }
    public int getThreadPoolSize() { return threadPoolSize; }
    public int getMaxRetry() { return maxRetry; }

    // ---- setters ----

    public void setConnectorName(String connectorName) { this.connectorName = connectorName; }
    public void setType(ConnectorType type) { this.type = type; }
    public void setPluginClass(String pluginClass) { this.pluginClass = pluginClass; }
    public void setProps(Map<String, String> props) { this.props = props; }
    public void setPoolMode(ThreadPoolMode poolMode) { this.poolMode = poolMode; }
    public void setThreadPoolSize(int threadPoolSize) { this.threadPoolSize = threadPoolSize; }
    public void setMaxRetry(int maxRetry) { this.maxRetry = maxRetry; }

    @Override
    public String toString() {
        return "ConnectorConfig{name=" + connectorName + ", type=" + type
            + ", poolMode=" + poolMode + ", threads=" + threadPoolSize + '}';
    }
}
