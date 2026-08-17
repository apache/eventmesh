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

package org.apache.eventmesh.runtime.subscription;

import java.util.Objects;
import java.util.function.Consumer;


/**
 * A single client subscription registered with {@link SubscriptionManager}.
 *
 * <p>The {@code handler} is the delivery target — in the full architecture this wraps a
 * {@code TransportChannel} (WebSocket / SSE / Long-Polling, §7.2); for Phase 2 it is a plain
 * callback so the dispatch logic can be built and unit-tested before the push transport lands.</p>
 */
public final class Subscription {

    private final String subscriptionId;
    private final String clientId;
    private final String topic;
    private final DistributionMode mode;
    private final CloudEventFilter filter;
    private final Consumer<org.apache.eventmesh.common.wire.EventMeshFrame> handler;

    // Heartbeat bookkeeping — a subscription whose heartbeat expires is pruned from dispatch.
    private volatile long lastHeartbeatMs;

    public Subscription(String subscriptionId, String clientId, String topic, DistributionMode mode,
        CloudEventFilter filter, Consumer<org.apache.eventmesh.common.wire.EventMeshFrame> handler, long nowMs) {
        this.subscriptionId = Objects.requireNonNull(subscriptionId, "subscriptionId");
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.topic = Objects.requireNonNull(topic, "topic");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.filter = filter == null ? CloudEventFilter.ACCEPT_ALL : filter;
        this.handler = Objects.requireNonNull(handler, "handler");
        this.lastHeartbeatMs = nowMs;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getTopic() {
        return topic;
    }

    public DistributionMode getMode() {
        return mode;
    }

    public CloudEventFilter getFilter() {
        return filter;
    }

    public Consumer<org.apache.eventmesh.common.wire.EventMeshFrame> getHandler() {
        return handler;
    }

    public long getLastHeartbeatMs() {
        return lastHeartbeatMs;
    }

    void refreshHeartbeat(long nowMs) {
        this.lastHeartbeatMs = nowMs;
    }

    /**
     * Whether this subscription's heartbeat has gone quiet longer than {@code maxIdleMs}.
     */
    boolean isExpired(long nowMs, long maxIdleMs) {
        return nowMs - lastHeartbeatMs > maxIdleMs;
    }
}
