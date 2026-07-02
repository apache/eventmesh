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
 * Runtime status of a connector instance.
 */
public class ConnectorStatus {

    public enum State { CREATED, RUNNING, STOPPED, FAILED, PAUSED }

    private final String connectorName;
    private final ConnectorConfig.ConnectorType type;
    private State state;
    private long uptimeMs;
    private long messagesProcessed;
    private long errors;
    private String errorMessage;
    private long lastHeartbeat;

    public ConnectorStatus(String connectorName, ConnectorConfig.ConnectorType type) {
        this.connectorName = connectorName;
        this.type = type;
        this.state = State.CREATED;
        this.lastHeartbeat = System.currentTimeMillis();
    }

    // ---- getters ----

    public String getConnectorName() { return connectorName; }
    public ConnectorConfig.ConnectorType getType() { return type; }
    public State getState() { return state; }
    public long getUptimeMs() { return uptimeMs; }
    public long getMessagesProcessed() { return messagesProcessed; }
    public long getErrors() { return errors; }
    public String getErrorMessage() { return errorMessage; }
    public long getLastHeartbeat() { return lastHeartbeat; }

    // ---- setters ----

    public void setState(State state) { this.state = state; }
    public void setUptimeMs(long uptimeMs) { this.uptimeMs = uptimeMs; }
    public void setMessagesProcessed(long n) { this.messagesProcessed = n; }
    public void setErrors(long e) { this.errors = e; }
    public void setErrorMessage(String msg) { this.errorMessage = msg; }
    public void heartbeat() { this.lastHeartbeat = System.currentTimeMillis(); }

    public void incrementMessages() { this.messagesProcessed++; }
    public void incrementErrors() { this.errors++; }

    @Override
    public String toString() {
        return "ConnectorStatus{name=" + connectorName + ", type=" + type
            + ", state=" + state + ", msgs=" + messagesProcessed
            + ", errors=" + errors + '}';
    }
}
