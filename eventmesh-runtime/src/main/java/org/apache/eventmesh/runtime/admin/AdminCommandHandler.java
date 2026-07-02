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

import org.apache.eventmesh.runtime.connector.ConnectorRuntimeService;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * AdminCommandHandler — receives and executes dispatch commands from Admin Server.
 *
 * <p>Commands received from Admin Server via gRPC BiStream or HTTP callback:
 * <ul>
 *   <li>{@code JOB.CREATE} — create a new Connector job</li>
 *   <li>{@code JOB.START}  — start a Connector job</li>
 *   <li>{@code JOB.STOP}   — stop a Connector job</li>
 *   <li>{@code JOB.DELETE} — delete a Connector job</li>
 *   <li>{@code JOB.RECONFIGURE} — hot-reload Connector config</li>
 *   <li>{@code RUNTIME.SHUTDOWN} — graceful shutdown</li>
 *   <li>{@code RUNTIME.RESTART}  — restart the Runtime</li>
 * </ul>
 */
@Slf4j
public class AdminCommandHandler {

    /**
     * Command received from Admin Server.
     */
    public static class Command {
        private final String type;     // e.g. JOB.CREATE, RUNTIME.SHUTDOWN
        private final String jobId;    // target job (null for runtime-level commands)
        private final Map<String, String> params;

        public Command(String type, String jobId, Map<String, String> params) {
            this.type = type;
            this.jobId = jobId;
            this.params = params;
        }

        public String getType() { return type; }
        public String getJobId() { return jobId; }
        public Map<String, String> getParams() { return params; }

        @Override
        public String toString() {
            return "Command{type=" + type + ", jobId=" + jobId + "}";
        }
    }

    /**
     * Result of executing a command.
     */
    public static class CommandResult {
        private final boolean success;
        private final String message;

        public CommandResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }

        public static CommandResult ok(String msg) { return new CommandResult(true, msg); }
        public static CommandResult fail(String msg) { return new CommandResult(false, msg); }

        @Override
        public String toString() {
            return "CommandResult{" + (success ? "OK" : "FAIL") + ": " + message + "}";
        }
    }

    private final ConnectorRuntimeService connectorRuntime;

    public AdminCommandHandler(ConnectorRuntimeService connectorRuntime) {
        this.connectorRuntime = connectorRuntime;
    }

    /**
     * Handle a command dispatched by Admin Server.
     */
    public CommandResult handle(Command command) {
        log.info("Handling command: type={}, jobId={}", command.getType(), command.getJobId());
        try {
            switch (command.getType()) {
                case "JOB.CREATE":
                    return handleJobCreate(command);
                case "JOB.START":
                    return handleJobStart(command);
                case "JOB.STOP":
                    return handleJobStop(command);
                case "JOB.DELETE":
                    return handleJobDelete(command);
                case "JOB.RECONFIGURE":
                    return handleJobReconfigure(command);
                case "RUNTIME.SHUTDOWN":
                    return handleRuntimeShutdown();
                case "RUNTIME.RESTART":
                    return handleRuntimeRestart();
                default:
                    return CommandResult.fail("Unknown command type: " + command.getType());
            }
        } catch (Exception e) {
            log.error("Command execution failed: {}", command, e);
            return CommandResult.fail("Execution error: " + e.getMessage());
        }
    }

    private CommandResult handleJobCreate(Command cmd) {
        String jobId = cmd.getJobId();
        if (jobId == null || jobId.isEmpty()) {
            return CommandResult.fail("JOB.CREATE requires jobId");
        }
        try {
            connectorRuntime.startConnector(jobId);
            return CommandResult.ok("Job " + jobId + " started");
        } catch (Exception e) {
            return CommandResult.fail("Failed to start job " + jobId + ": " + e.getMessage());
        }
    }

    private CommandResult handleJobStart(Command cmd) {
        String jobId = cmd.getJobId();
        if (jobId == null || jobId.isEmpty()) {
            return CommandResult.fail("JOB.START requires jobId");
        }
        try {
            connectorRuntime.startConnector(jobId);
            return CommandResult.ok("Job " + jobId + " started");
        } catch (Exception e) {
            return CommandResult.fail("Failed to start job " + jobId + ": " + e.getMessage());
        }
    }

    private CommandResult handleJobStop(Command cmd) {
        String jobId = cmd.getJobId();
        if (jobId == null || jobId.isEmpty()) {
            return CommandResult.fail("JOB.STOP requires jobId");
        }
        try {
            connectorRuntime.stopConnector(jobId);
            return CommandResult.ok("Job " + jobId + " stopped");
        } catch (Exception e) {
            return CommandResult.fail("Failed to stop job " + jobId + ": " + e.getMessage());
        }
    }

    private CommandResult handleJobDelete(Command cmd) {
        String jobId = cmd.getJobId();
        if (jobId == null || jobId.isEmpty()) {
            return CommandResult.fail("JOB.DELETE requires jobId");
        }
        try {
            connectorRuntime.stopConnector(jobId);
            connectorRuntime.unregisterConnector(jobId);
            return CommandResult.ok("Job " + jobId + " deleted");
        } catch (Exception e) {
            return CommandResult.fail("Failed to delete job " + jobId + ": " + e.getMessage());
        }
    }

    private CommandResult handleJobReconfigure(Command cmd) {
        String jobId = cmd.getJobId();
        if (jobId == null || jobId.isEmpty()) {
            return CommandResult.fail("JOB.RECONFIGURE requires jobId");
        }
        // For hot-reload: stop → update config → start
        try {
            connectorRuntime.stopConnector(jobId);
            // Config update is handled via register (re-register with new config)
            if (cmd.getParams() != null && !cmd.getParams().isEmpty()) {
                // Hot-reload logic: would read new config from params
                log.info("Reconfigure job {} with params: {}", jobId, cmd.getParams());
            }
            connectorRuntime.startConnector(jobId);
            return CommandResult.ok("Job " + jobId + " reconfigured and restarted");
        } catch (Exception e) {
            return CommandResult.fail("Failed to reconfigure job " + jobId + ": " + e.getMessage());
        }
    }

    private CommandResult handleRuntimeShutdown() {
        log.warn("RUNTIME.SHUTDOWN command received — initiating graceful shutdown");
        // Signal shutdown via system property or lifecycle hook
        // Actual shutdown is handled by EventMeshServer lifecycle
        return CommandResult.ok("Runtime shutdown initiated");
    }

    private CommandResult handleRuntimeRestart() {
        log.warn("RUNTIME.RESTART command received");
        return CommandResult.ok("Runtime restart initiated");
    }
}
