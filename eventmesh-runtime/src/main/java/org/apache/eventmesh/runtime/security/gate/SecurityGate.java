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

import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.runtime.security.FilterChain;
import org.apache.eventmesh.runtime.security.FilterVerdict;

import java.util.Objects;

/**
 * Unified security / quota / audit entrypoint (issue #5304). Every ingress path — HTTP publish,
 * subscribe, ACK, Connector scheduler, A2A gateway — funnels through
 * {@link #check(RequestContext, EventMeshFrame)} so policy is enforced in one place instead of
 * being re-implemented per protocol.
 *
 * <p>Ordering: <b>auth/ACL first, then quota</b>. An authenticated-but-over-quota request
 * consumes no quota slot and returns {@code 429} semantics via {@link GateDecision}; an
 * unauthenticated request never reaches the quota counter. Audit is emitted for every
 * decision (allowed, denied, quota-exceeded) — acceptance criterion "audit events are emitted
 * for authorized operations".</p>
 *
 * <p>The gate reuses the existing {@link FilterChain} (TokenAuthFilter, AclFilter, ...) for
 * authentication and authorization — it does not duplicate policy. What it adds is the uniform
 * {@link RequestContext} plumbing, quota enforcement and audit emission.</p>
 */
public final class SecurityGate {

    private final FilterChain filterChain;
    private final QuotaManager quotaManager;
    private final AuditSink auditSink;

    /** Quota resource charged per operation type. */
    private static QuotaManager.Resource resourceFor(RequestContext.Operation op) {
        switch (op) {
            case PUBLISH:
                return QuotaManager.Resource.THROUGHPUT;
            case SUBSCRIBE:
                return QuotaManager.Resource.SUBSCRIPTIONS;
            case ACK:
                return QuotaManager.Resource.BACKLOG;
            default:
                // CONNECTOR / A2A / ADMIN: charged as throughput units of their own work.
                return QuotaManager.Resource.THROUGHPUT;
        }
    }

    public SecurityGate(FilterChain filterChain, QuotaManager quotaManager, AuditSink auditSink) {
        this.filterChain = Objects.requireNonNull(filterChain, "filterChain");
        this.quotaManager = quotaManager == null ? QuotaManager.unlimited() : quotaManager;
        this.auditSink = auditSink == null ? AuditSink.disabled() : auditSink;
    }

    /**
     * Check a request and charge quota for it. Never throws; the caller maps
     * {@link GateDecision#isAllowed()} to a protocol-specific rejection (HTTP status, TCP error
     * frame, Netty response).
     *
     * @param context identity + intent of the caller
     * @param frame   the payload frame when one exists (publish path); may be {@code null} for
     *                operations without a frame (agent register, task submit payloads are not
     *                frames) — filters that need a frame treat null as a stub.
     * @return the decision; callers SHOULD pass the release handle to {@link #release} when the
     *         accounted unit ends (connection closed, subscription removed, backlog drained)
     */
    public GateDecision check(RequestContext context, EventMeshFrame frame) {
        Objects.requireNonNull(context, "context");

        // 1) Authentication + authorization via the existing filter chain.
        EventMeshFrame effectiveFrame = frame != null ? frame
            : EventMeshFrame.event(java.util.Collections.emptyMap(), new byte[0]);
        FilterVerdict verdict = filterChain.check(effectiveFrame, context.toFilterContext());
        if (!verdict.isAllowed()) {
            audit(context, AuditSink.Outcome.DENIED, verdict.getReason());
            return GateDecision.denied(verdict.getRejectStatus(), verdict.getReason());
        }

        // 2) Quota. Charge one unit of the resource this operation consumes.
        QuotaManager.Resource resource = resourceFor(context.getOperation());
        if (!quotaManager.tryAcquire(context.getQuotaKey(), resource, 1)) {
            audit(context, AuditSink.Outcome.QUOTA_EXCEEDED, resource.name());
            return GateDecision.quotaExceeded(resource);
        }

        // 3) Authorized + admitted.
        audit(context, AuditSink.Outcome.ALLOWED, null);
        return GateDecision.allowed();
    }

    /** Release a previously-charged unit (connection closed, subscription removed, backlog drained). */
    public void release(RequestContext context, long units) {
        quotaManager.release(context.getQuotaKey(), resourceFor(context.getOperation()), units);
    }

    private void audit(RequestContext context, AuditSink.Outcome outcome, String detail) {
        try {
            auditSink.emit(context, outcome, detail);
        } catch (RuntimeException ignored) {
            // audit must never fail the request
        }
    }
}
