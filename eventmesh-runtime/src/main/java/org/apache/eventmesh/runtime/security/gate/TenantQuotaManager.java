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

import org.apache.eventmesh.runtime.security.gate.QuotaManager.Resource;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory per-tenant quota tracker (issue #5304 default implementation). Limits are uniform
 * across tenants and set once at construction; per-tenant overrides are a Meta-backed follow-up
 * (the constructor signature intentionally leaves room).
 *
 * <p>THROUGHPUT uses a fixed window ({@code windowMs}); counters reset lazily on the next
 * acquire after the window rolls. CONNECTIONS / SUBSCRIPTIONS / BACKLOG are gauge-style —
 * acquire/release must be paired or the usage leaks.</p>
 *
 * <p>Thread-safe; hot path is a single {@link AtomicLong#incrementAndGet} + compare.</p>
 */
public final class TenantQuotaManager implements QuotaManager {

    private final long maxConnections;
    private final long maxSubscriptions;
    private final long maxThroughputPerWindow;
    private final long maxBacklog;
    private final long windowMs;

    private final ConcurrentHashMap<String, Usage> usage = new ConcurrentHashMap<>();

    private static final class Usage {

        final AtomicLong connections = new AtomicLong();
        final AtomicLong subscriptions = new AtomicLong();
        final AtomicLong backlog = new AtomicLong();
        volatile long windowStart = System.currentTimeMillis();
        final AtomicLong throughput = new AtomicLong();

        void rollWindow(long windowMs) {
            long now = System.currentTimeMillis();
            if (now - windowStart >= windowMs) {
                throughput.set(0);
                windowStart = now;
            }
        }
    }

    public TenantQuotaManager(long maxConnections, long maxSubscriptions,
                              long maxThroughputPerWindow, long maxBacklog, long windowMs) {
        this.maxConnections = maxConnections;
        this.maxSubscriptions = maxSubscriptions;
        this.maxThroughputPerWindow = maxThroughputPerWindow;
        this.maxBacklog = maxBacklog;
        this.windowMs = windowMs;
    }

    @Override
    public boolean tryAcquire(String quotaKey, Resource resource, long units) {
        Objects.requireNonNull(quotaKey, "quotaKey");
        Usage u = usage.computeIfAbsent(quotaKey, k -> new Usage());
        switch (resource) {
            case CONNECTIONS:
                return u.connections.incrementAndGet() <= maxConnections
                    || rollback(u.connections, units);
            case SUBSCRIPTIONS:
                return u.subscriptions.incrementAndGet() <= maxSubscriptions
                    || rollback(u.subscriptions, units);
            case BACKLOG:
                long backlog = u.backlog.addAndGet(units);
                if (backlog > maxBacklog) {
                    u.backlog.addAndGet(-units);
                    return false;
                }
                return true;
            case THROUGHPUT:
            default:
                synchronized (u) {
                    u.rollWindow(windowMs);
                }
                return u.throughput.addAndGet(units) <= maxThroughputPerWindow
                    || rollback(u.throughput, units);
        }
    }

    private static boolean rollback(AtomicLong counter, long units) {
        counter.addAndGet(-units);
        return false;
    }

    @Override
    public void release(String quotaKey, Resource resource, long units) {
        Usage u = usage.get(quotaKey);
        if (u == null) {
            return;
        }
        switch (resource) {
            case CONNECTIONS:
                u.connections.addAndGet(-units);
                break;
            case SUBSCRIPTIONS:
                u.subscriptions.addAndGet(-units);
                break;
            case BACKLOG:
                u.backlog.addAndGet(-units);
                break;
            case THROUGHPUT:
            default:
                // window-based; self-expiring, no explicit release
                break;
        }
    }

    /** Current usage snapshot (metrics / tests). */
    public long currentUsage(String quotaKey, Resource resource) {
        Usage u = usage.get(quotaKey);
        if (u == null) {
            return 0;
        }
        switch (resource) {
            case CONNECTIONS:
                return u.connections.get();
            case SUBSCRIPTIONS:
                return u.subscriptions.get();
            case BACKLOG:
                return u.backlog.get();
            case THROUGHPUT:
            default:
                return u.throughput.get();
        }
    }

    /** Drop all accounting for a key (tenant removed). */
    public void remove(String quotaKey) {
        usage.remove(quotaKey);
    }

    /** Visible for tests: expose the underlying map size. */
    int trackedKeys() {
        return usage.size();
    }

    /** Visible for tests. */
    Map<String, Usage> usageMap() {
        return usage;
    }
}
