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

package org.apache.eventmesh.runtime.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.common.wire.EventMeshFrame;

import java.net.URI;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

class SecurityFilterTest {

    @Test
    void tokenAuthAllowsValidCredentialRejectsMissing() {
        TokenAuthFilter auth = new TokenAuthFilter(Collections.singleton("good-token"));

        assertTrue(auth.check(event(), ctx("good-token", "tenantA", "orders")).isAllowed());
        FilterVerdict missing = auth.check(event(), ctx(null, "tenantA", "orders"));
        assertFalse(missing.isAllowed());
        assertEquals(401, missing.getRejectStatus());
    }

    @Test
    void aclAllowsGrantedTopicRejectsForbidden() {
        // §13.4.2 rule model: ALLOW tenantA on orders; payments has no rule → default DENY.
        AclFilter acl = new AclFilter(java.util.List.of(
            new AclRule("tenantA", "orders", AclRule.Action.ANY, AclRule.Effect.ALLOW, 10)));

        assertTrue(acl.check(event(), ctx("tok", "tenantA", "orders")).isAllowed());
        FilterVerdict forbidden = acl.check(event(), ctx("tok", "tenantA", "payments"));
        assertFalse(forbidden.isAllowed());
        assertEquals(403, forbidden.getRejectStatus());
    }

    @Test
    void aclDenyRuleBeatsAllowOnSamePriorityAndWildcardMatches() {
        // priority=100 DENY tenantB.* on tenantA.* beats the lower-priority allow; tenantA.* wildcard
        // matches tenantA.orders.
        AclFilter acl = new AclFilter(java.util.List.of(
            new AclRule("tenantB", "tenantA.*", AclRule.Action.ANY, AclRule.Effect.DENY, 100),
            new AclRule("tenantA", "tenantA.*", AclRule.Action.ANY, AclRule.Effect.ALLOW, 50)));
        // tenantA user → allowed by the priority-50 rule.
        assertTrue(acl.check(event(), ctx("tok", "tenantA", "tenantA.orders")).isAllowed());
        // tenantB user → denied by the priority-100 rule (cross-tenant block).
        FilterVerdict cross = acl.check(event(), ctx("tok", "tenantB", "tenantA.orders"));
        assertFalse(cross.isAllowed());
        assertEquals(403, cross.getRejectStatus());
    }

    @Test
    void chainFailsClosedAtFirstDenyingFilter() {
        TokenAuthFilter auth = new TokenAuthFilter(Collections.singleton("good-token"));
        AclFilter acl = new AclFilter(java.util.List.of(
            new AclRule("tenantA", "orders", AclRule.Action.ANY, AclRule.Effect.ALLOW, 10)));
        FilterChain chain = new FilterChain(auth, acl);

        // Bad credential → denied by auth (401), acl never consulted.
        FilterVerdict badCred = chain.check(event(), ctx("wrong", "tenantA", "orders"));
        assertFalse(badCred.isAllowed());
        assertEquals(401, badCred.getRejectStatus());

        // Good credential but forbidden topic → denied by acl (403).
        FilterVerdict badTopic = chain.check(event(), ctx("good-token", "tenantA", "payments"));
        assertFalse(badTopic.isAllowed());
        assertEquals(403, badTopic.getRejectStatus());

        // Both pass → allow.
        assertTrue(chain.check(event(), ctx("good-token", "tenantA", "orders")).isAllowed());
    }

    @Test
    void signatureVerifierAcceptsValidRejectsTampered() {
        SignatureVerifierFilter verifier = new SignatureVerifierFilter("shared-secret");
        CloudEvent event = event();

        String goodSig = verifier.sign(SignatureVerifierFilter.canonical(EventMeshFrame.fromCloudEvent(event)));
        CloudEvent signed = CloudEventBuilder.from(event).withExtension(SignatureVerifierFilter.EXT_SIGNATURE, goodSig).build();
        assertTrue(verifier.check(signed, ctx("tok", "tenantA", "orders")).isAllowed());

        CloudEvent tampered = CloudEventBuilder.from(event)
            .withExtension(SignatureVerifierFilter.EXT_SIGNATURE, goodSig.substring(0, goodSig.length() - 1) + "0").build();
        assertFalse(verifier.check(tampered, ctx("tok", "tenantA", "orders")).isAllowed());

        assertFalse(verifier.check(event(), ctx("tok", "tenantA", "orders")).isAllowed(), "missing signature rejected");
    }

    private static CloudEvent event() {
        return CloudEventBuilder.v1().withId("e-1").withSource(URI.create("svc")).withType("order.created").build();
    }

    private static FilterContext ctx(String credential, String tenant, String topic) {
        return new FilterContext(topic, "client-1", tenant, credential, "127.0.0.1");
    }
}
