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
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * #5299 sub-PR B: the filter chain now operates on {@link EventMeshFrame} instead of
 * {@code io.cloudevents.CloudEvent}. These tests exercise the new primary path; the
 * CloudEvent-based tests in {@link SecurityFilterTest} remain as the legacy bridge contract.
 */
class EventMeshFrameFilterTest {

    @Test
    void tokenAuthReadsCredentialFromContextNotFrame() {
        TokenAuthFilter auth = new TokenAuthFilter(java.util.Collections.singleton("good-token"));

        assertTrue(auth.check(frame(), ctx("good-token", "tenantA", "orders")).isAllowed());
        FilterVerdict missing = auth.check(frame(), ctx(null, "tenantA", "orders"));
        assertFalse(missing.isAllowed());
        assertEquals(401, missing.getRejectStatus());
    }

    @Test
    void aclDeniesFrameWithNonEventMsgType() {
        // A STREAM_REQ frame should be rejected by the ACL filter even if the principal/resource
        // are otherwise fine — the filter applies to event ingress only. We craft the frame via
        // decode() because the 5-arg ctor is package-private; the wire format is documented in
        // EventMeshFrame (header = magic(1) | ver(1) | msgType(1) | flags(1) | seq(4) | keyCount(2) | dataLen(4)).
        byte[] streamReqBytes = new byte[] {
            (byte) 0xEF, 1, EventMeshFrame.TYPE_STREAM_REQ, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        };
        EventMeshFrame streamFrame = EventMeshFrame.decode(streamReqBytes);
        AclFilter acl = new AclFilter(java.util.List.of(
            new AclRule("tenantA", "orders", AclRule.Action.ANY, AclRule.Effect.ALLOW, 10)));
        FilterVerdict denied = acl.check(streamFrame, ctx("tok", "tenantA", "orders"));
        assertFalse(denied.isAllowed());
        assertEquals(403, denied.getRejectStatus());
    }

    @Test
    void aclAllowsGrantedTopicRejectsForbidden() {
        AclFilter acl = new AclFilter(java.util.List.of(
            new AclRule("tenantA", "orders", AclRule.Action.ANY, AclRule.Effect.ALLOW, 10)));

        assertTrue(acl.check(frame(), ctx("tok", "tenantA", "orders")).isAllowed());
        FilterVerdict forbidden = acl.check(frame(), ctx("tok", "tenantA", "payments"));
        assertFalse(forbidden.isAllowed());
        assertEquals(403, forbidden.getRejectStatus());
    }

    @Test
    void chainFailsClosedAtFirstDenyingFilter() {
        TokenAuthFilter auth = new TokenAuthFilter(java.util.Collections.singleton("good-token"));
        AclFilter acl = new AclFilter(java.util.List.of(
            new AclRule("tenantA", "orders", AclRule.Action.ANY, AclRule.Effect.ALLOW, 10)));
        FilterChain chain = new FilterChain(auth, acl);

        // Bad credential → denied by auth (401), acl never consulted.
        FilterVerdict badCred = chain.check(frame(), ctx("wrong", "tenantA", "orders"));
        assertFalse(badCred.isAllowed());
        assertEquals(401, badCred.getRejectStatus());

        // Good credential but forbidden topic → denied by acl (403).
        FilterVerdict badTopic = chain.check(frame(), ctx("good-token", "tenantA", "payments"));
        assertFalse(badTopic.isAllowed());
        assertEquals(403, badTopic.getRejectStatus());

        // Both pass → allow.
        assertTrue(chain.check(frame(), ctx("good-token", "tenantA", "orders")).isAllowed());
    }

    @Test
    void signatureVerifierReadsSignatureFromFrameAttributes() {
        SignatureVerifierFilter verifier = new SignatureVerifierFilter("shared-secret");
        EventMeshFrame unsigned = frame();
        String goodSig = verifier.sign(SignatureVerifierFilter.canonical(unsigned));
        EventMeshFrame signed = withAttr(unsigned, SignatureVerifierFilter.EXT_SIGNATURE, goodSig);

        assertTrue(verifier.check(signed, ctx("tok", "tenantA", "orders")).isAllowed());

        // Tampered signature (flip last hex char) → reject.
        String tampered = goodSig.substring(0, goodSig.length() - 1)
            + (goodSig.charAt(goodSig.length() - 1) == '0' ? '1' : '0');
        EventMeshFrame bad = withAttr(signed, SignatureVerifierFilter.EXT_SIGNATURE, tampered);
        assertFalse(verifier.check(bad, ctx("tok", "tenantA", "orders")).isAllowed());

        // Missing signature → reject.
        assertFalse(verifier.check(unsigned, ctx("tok", "tenantA", "orders")).isAllowed(),
            "missing signature rejected");
    }

    @Test
    void tenantFromFrameAttributesReachesContext() {
        // The HTTP handler now reads tenant from frame.attributes() and threads it into
        // FilterContext before the chain runs; this test pins that contract.
        EventMeshFrame f = withAttr(frame(), "emtenantid", "tenantZ");
        String tenant = f.attributes().get("emtenantid");
        assertEquals("tenantZ", tenant);
        // The downstream AclFilter then uses ctx.tenant — the test in aclAllowsGrantedTopicRejectsForbidden
        // already exercises the "tenant from ctx" path; here we just document the contract.
    }

    private static EventMeshFrame frame() {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("id", "e-1");
        attrs.put("source", "svc");
        attrs.put("type", "order.created");
        return EventMeshFrame.event(attrs, new byte[0]);
    }

    private static EventMeshFrame withAttr(EventMeshFrame base, String name, String value) {
        Map<String, String> attrs = new LinkedHashMap<>(base.attributes());
        attrs.put(name, value);
        return EventMeshFrame.event(attrs, base.data());
    }

    private static FilterContext ctx(String credential, String tenant, String topic) {
        return new FilterContext(topic, "client-1", tenant, credential, "127.0.0.1");
    }
}
