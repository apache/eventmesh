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

package org.apache.eventmesh.runtime.security.gate;

/**
 * Quota accounting SPI (issue #5304): subscription, connection, throughput and backlog limits
 * per {@code quotaKey} (tenant by default). Implementations track per-key usage and decide
 * whether a new unit of work is admitted.
 *
 * <p>Deliberately synchronous and cheap — the gate calls it on every request, so
 * implementations must not do remote lookups (they may be fed by a Meta watcher instead,
 * mirroring how {@code AclFilter} hot-swaps its rule list).</p>
 *
 * <p>The default {@link #unlimited()} admits everything, preserving behavior for deployments
 * that have not configured quotas.</p>
 */
public interface QuotaManager {

    /** What is being accounted. */
    enum Resource {
        /** Concurrent connections / bound clients. */
        CONNECTIONS,
        /** Active subscriptions. */
        SUBSCRIPTIONS,
        /** Publish throughput (events per window). */
        THROUGHPUT,
        /** Undelivered backlog events. */
        BACKLOG
    }

    /**
     * Try to acquire {@code units} of {@code resource} under {@code quotaKey}.
     *
     * @return {@code true} if admitted (usage increased); {@code false} if the quota is
     *         exhausted — the caller MUST reject the request with 429 semantics.
     */
    boolean tryAcquire(String quotaKey, Resource resource, long units);

    /**
     * Release previously-acquired units (connection closed, subscription removed, backlog
     * drained). Optional for THROUGHPUT (window-based counters self-expire); required for
     * CONNECTIONS / SUBSCRIPTIONS / BACKLOG or they leak.
     */
    void release(String quotaKey, Resource resource, long units);

    /** A no-op quota manager that admits everything. */
    static QuotaManager unlimited() {
        return UnlimitedQuotaManager.INSTANCE;
    }
}
