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
 * Paired release handle for a quota acquisition (issue #5358 / plan #5354 Phase 0).
 *
 * <p>Gauge-style {@link QuotaManager.Resource} types (CONNECTIONS, SUBSCRIPTIONS,
 * BACKLOG) must be released when the unit of work ends — connection closed,
 * subscription removed, backlog drained. Calling {@code tryAcquire} without a
 * guaranteed {@code release} leaks the slot. This handle makes the pairing
 * structural: acquire returns it, {@link #close()} releases it exactly once,
 * and try-with-resources keeps the release on the exception path.</p>
 *
 * <p>Window-style resources (THROUGHPUT counters self-expiring per window) may
 * ignore the handle — closing is a no-op when {@code releasable} is false.</p>
 */
public final class QuotaHandle implements AutoCloseable {

    private final QuotaManager manager;
    private final String quotaKey;
    private final QuotaManager.Resource resource;
    private final long units;
    private final boolean releasable;
    private boolean closed;

    QuotaHandle(QuotaManager manager, String quotaKey, QuotaManager.Resource resource,
                long units, boolean releasable) {
        this.manager = manager;
        this.quotaKey = quotaKey;
        this.resource = resource;
        this.units = units;
        this.releasable = releasable;
    }

    /** The resource this handle was acquired against. */
    public QuotaManager.Resource resource() {
        return resource;
    }

    /**
     * Release the acquired units. Idempotent: a second {@code close()} is a no-op.
     * No-op for non-releasable (window-style) acquisitions.
     */
    @Override
    public void close() {
        if (closed || !releasable) {
            closed = true;
            return;
        }
        closed = true;
        manager.release(quotaKey, resource, units);
    }
}
