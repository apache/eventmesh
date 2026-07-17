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

package org.apache.eventmesh.client.cloudevents;

/** Builder for {@link CloudEventsClient}. */
public class CloudEventsClientBuilder {

    private String runtimeUrl = "http://localhost:8080";
    private String clientId = "client-1";
    private long pollIntervalMs = 1000L;
    /** WebSocket push server base URL (e.g. {@code http://localhost:8082}) — the runtime's WS
     * transport runs on a separate port from the HTTP traffic port. If null, subscribeWs falls back
     * to deriving from {@code runtimeUrl} (only works if the runtime serves WS on the traffic port,
     * which it normally does not — so set this explicitly for WS). */
    private String wsUrl;

    public CloudEventsClientBuilder runtimeUrl(String runtimeUrl) {
        this.runtimeUrl = runtimeUrl;
        return this;
    }

    public CloudEventsClientBuilder clientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public CloudEventsClientBuilder pollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
        return this;
    }

    /** Set the WebSocket push server base URL (the runtime's WS port, separate from the traffic port). */
    public CloudEventsClientBuilder wsUrl(String wsUrl) {
        this.wsUrl = wsUrl;
        return this;
    }

    public CloudEventsClient build() {
        return new CloudEventsClient(runtimeUrl, clientId, pollIntervalMs, wsUrl);
    }
}
