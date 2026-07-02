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

import java.util.List;
import java.util.Map;

/**
 * AdminReporter — pluggable interface for reporting Runtime state to Admin Server.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@code GrpcAdminReporter} — gRPC BiStream to Admin Server</li>
 *   <li>{@code HttpAdminReporter} — HTTP REST to Admin Server</li>
 *   <li>{@code NoopAdminReporter} — standalone mode (no external Admin)</li>
 * </ul>
 */
public interface AdminReporter {

    /**
     * Report a heartbeat with current runtime status.
     * @param address       runtime address (host:port)
     * @param state         current RuntimeState name
     * @param activeJobCount number of active connector jobs
     */
    void reportHeartbeat(String address, String state, int activeJobCount);

    /**
     * Report monitoring metrics and connector statuses.
     * @param address         runtime address
     * @param metrics         current metrics snapshot
     * @param connectorStatus connector health summaries
     */
    void reportMonitor(String address, Map<String, Object> metrics,
                       List<ConnectorStatus> connectorStatus);

    /**
     * Sync connector offsets to Admin Server.
     * @param address runtime address
     * @param offsets offset snapshot (key → position)
     */
    void syncOffsets(String address, Map<String, String> offsets);

    /**
     * Check if the reporter is connected to Admin Server.
     */
    boolean isConnected();

    /**
     * Shutdown the reporter connection.
     */
    void shutdown();
}
