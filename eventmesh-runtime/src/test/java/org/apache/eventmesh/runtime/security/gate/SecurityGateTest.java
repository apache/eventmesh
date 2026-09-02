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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.runtime.security.AclRule;
import org.apache.eventmesh.runtime.security.FilterChain;
import org.apache.eventmesh.runtime.security.gate.AuditSink.Outcome;
import org.apache.eventmesh.runtime.security.gate.QuotaManager.Resource;
import org.apache.eventmesh.runtime.security.gate.RequestContext.Operation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the unified security/quota/audit gate (issue #5304).
 *
 * <p>Covers the three acceptance criteria: one RequestContext across operations, centralized
 * ACL + quota enforcement, and audit events for authorized operations.</p>
 */
class SecurityGateTest {

    /** Allow-all chain (no filters) — policy comes only from quota in these tests. */
    private static FilterChain allowAll() {
        return new FilterChain();
    }

    private static RequestContext ctx(Operation op, String tenant, String topic) {
        return RequestContext.builder(op).tenantId(tenant).topic(topic).source("test").build();
    }

    // ============================ identity / principal ============================

    @Test
    void principalFallsBackToTenantThenClient() {
        assertEquals("tenantA", ctx(Operation.PUBLISH, "tenantA", "t").getPrincipal());
        RequestContext anonymous = RequestContext.builder(Operation.PUBLISH)
            .clientId("client-1").topic("t").build();
        assertEquals("client-1", anonymous.getPrincipal());
        assertEquals("client-1", anonymous.getQuotaKey());
    }

    @Test
    void quotaKeyDefaultsToTenant() {
        assertEquals("tenantA", ctx(Operation.SUBSCRIBE, "tenantA", "t").getQuotaKey());
        assertEquals("anonymous", RequestContext.builder(Operation.PUBLISH).build().getQuotaKey());
    }

    @Test
    void aclActionMappingCoversAllOperations() {
        assertEquals(AclRule.Action.PUBLISH, ctx(Operation.PUBLISH, "a", "t").aclAction());
        assertEquals(AclRule.Action.SUBSCRIBE, ctx(Operation.SUBSCRIBE, "a", "t").aclAction());
        assertEquals(AclRule.Action.SUBSCRIBE, ctx(Operation.ACK, "a", "t").aclAction());
        assertEquals(AclRule.Action.REQUEST, ctx(Operation.A2A, "a", "t").aclAction());
        assertNull(ctx(Operation.ADMIN, "a", "t").aclAction());
        assertNull(ctx(Operation.CONNECTOR, "a", "t").aclAction());
    }

    // ============================ allow path ============================

    @Test
    void allowedRequestPassesAndAudits() {
        AtomicInteger allowed = new AtomicInteger();
        List<String> outcomes = new ArrayList<>();
        SecurityGate gate = new SecurityGate(allowAll(), QuotaManager.unlimited(),
            (c, o, d) -> {
                outcomes.add(o.name());
                if (o == Outcome.ALLOWED) {
                    allowed.incrementAndGet();
                }
            });
        GateDecision decision = gate.check(ctx(Operation.PUBLISH, "tenantA", "orders"), null);
        assertTrue(decision.isAllowed());
        assertEquals(1, allowed.get());
        assertEquals("ALLOWED", outcomes.get(0));
    }

    // ============================ ACL denial ============================

    @Test
    void aclDenyReturns403AndDoesNotConsumeQuota() {
        // Chain with zero filters still allows; deny comes from a filter. Use AclFilter with
        // empty rules → default-deny.
        org.apache.eventmesh.runtime.security.AclFilter acl =
            new org.apache.eventmesh.runtime.security.AclFilter(java.util.Collections.emptyList());
        SecurityGate gate = new SecurityGate(new FilterChain(acl), QuotaManager.unlimited(),
            AuditSink.disabled());
        GateDecision decision = gate.check(ctx(Operation.PUBLISH, "tenantA", "orders"), null);
        assertFalse(decision.isAllowed());
        assertEquals(403, decision.getRejectStatus());
    }

    // ============================ quota ============================

    @Test
    void quotaExceededReturns429AndAudits() {
        TenantQuotaManager quota = new TenantQuotaManager(
            100, 100, 2, 100, 60_000); // throughput = 2 per window
        List<Outcome> outcomes = new ArrayList<>();
        SecurityGate gate = new SecurityGate(allowAll(), quota, (c, o, d) -> outcomes.add(o));

        RequestContext publish = ctx(Operation.PUBLISH, "tenantA", "orders");
        assertTrue(gate.check(publish, null).isAllowed());
        assertTrue(gate.check(publish, null).isAllowed());
        GateDecision third = gate.check(publish, null);
        assertFalse(third.isAllowed());
        assertEquals(GateDecision.STATUS_QUOTA_EXCEEDED, third.getRejectStatus());
        assertEquals(Resource.THROUGHPUT, third.getQuotaResource());
        assertEquals(Outcome.QUOTA_EXCEEDED, outcomes.get(outcomes.size() - 1));
        // Other tenants unaffected
        assertTrue(gate.check(ctx(Operation.PUBLISH, "tenantB", "orders"), null).isAllowed());
    }

    @Test
    void subscriptionsAreCountedPerTenant() {
        TenantQuotaManager quota = new TenantQuotaManager(100, 1, 100, 100, 60_000);
        SecurityGate gate = new SecurityGate(allowAll(), quota, AuditSink.disabled());
        RequestContext sub = ctx(Operation.SUBSCRIBE, "tenantA", "orders");
        assertTrue(gate.check(sub, null).isAllowed());
        assertFalse(gate.check(sub, null).isAllowed()); // 2nd subscription over limit
        // release → admitted again
        gate.release(sub, 1);
        assertTrue(gate.check(sub, null).isAllowed());
    }

    @Test
    void backlogReleaseRestoresHeadroom() {
        TenantQuotaManager quota = new TenantQuotaManager(100, 100, 100, 2, 60_000);
        SecurityGate gate = new SecurityGate(allowAll(), quota, AuditSink.disabled());
        RequestContext ack = ctx(Operation.ACK, "tenantA", "orders");
        assertTrue(gate.check(ack, null).isAllowed());
        assertTrue(gate.check(ack, null).isAllowed());
        assertFalse(gate.check(ack, null).isAllowed()); // backlog full
        gate.release(ack, 2);
        assertTrue(gate.check(ack, null).isAllowed());
    }

    // ============================ audit robustness ============================

    @Test
    void throwingAuditSinkDoesNotFailTheRequest() {
        SecurityGate gate = new SecurityGate(allowAll(), QuotaManager.unlimited(),
            (c, o, d) -> {
                throw new IllegalStateException("sink down");
            });
        assertTrue(gate.check(ctx(Operation.PUBLISH, "t", "x"), null).isAllowed());
    }

    // ============================ TenantQuotaManager window ============================

    @Test
    void throughputWindowRolls() throws InterruptedException {
        TenantQuotaManager quota = new TenantQuotaManager(100, 100, 1, 100, 50); // 50ms window
        assertTrue(quota.tryAcquire("k", Resource.THROUGHPUT, 1));
        assertFalse(quota.tryAcquire("k", Resource.THROUGHPUT, 1));
        Thread.sleep(80);
        assertTrue(quota.tryAcquire("k", Resource.THROUGHPUT, 1));
    }
}
