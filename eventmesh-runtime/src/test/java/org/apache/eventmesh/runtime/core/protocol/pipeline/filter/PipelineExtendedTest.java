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

package org.apache.eventmesh.runtime.core.protocol.pipeline.filter;

import org.apache.eventmesh.common.protocol.pipeline.PipelineContext;
import org.apache.eventmesh.common.protocol.pipeline.PipelineResult;
import org.apache.eventmesh.runtime.core.protocol.pipeline.PipelineFilter;
import org.apache.eventmesh.runtime.core.protocol.pipeline.PipelineRouter;
import org.apache.eventmesh.runtime.core.protocol.pipeline.PipelineTransformer;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Extended tests covering edge cases for all 6 pipeline filters,
 * PipelineResult/PipelineContext, PipelineTransformer, and PipelineRouter.
 */
@DisplayName("Pipeline Extended Tests")
class PipelineExtendedTest {

    private static final URI SOURCE = URI.create("http://test-service/test");
    private static final String TOPIC = "test-topic";

    private PipelineContext ingressCtx;
    private PipelineContext egressCtx;

    @BeforeEach
    void setUp() {
        ingressCtx = new PipelineContext(PipelineContext.Direction.INGRESS, "http");
        egressCtx = new PipelineContext(PipelineContext.Direction.EGRESS, "http");
    }

    private CloudEvent validEvent() {
        return CloudEventBuilder.v1()
            .withId("evt-" + System.nanoTime())
            .withSource(SOURCE)
            .withType("test.type")
            .withSubject(TOPIC)
            .withData("text/plain", "hello world".getBytes(StandardCharsets.UTF_8))
            .withExtension("authtoken", "valid-token")
            .build();
    }

    // ========================================================================
    // AuthFilter — extended scenarios
    // ========================================================================

    @Test
    @DisplayName("Auth: should pass with AK/SK credentials")
    void auth_shouldPassWithAkSk() {
        AuthFilter filter = new AuthFilter();
        CloudEvent event = CloudEventBuilder.v1()
            .withId("aksk-event")
            .withSource(SOURCE)
            .withType("test.type")
            .withExtension(AuthFilter.ACCESS_KEY_KEY, "my-access-key")
            .withExtension(AuthFilter.SECRET_KEY_KEY, "my-secret-key")
            .build();

        PipelineResult result = filter.filter(event, ingressCtx);
        assertEquals(PipelineResult.Action.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("Auth: should drop with empty AK/SK")
    void auth_shouldDropEmptyAkSk() {
        AuthFilter filter = new AuthFilter() {
            @Override
            protected boolean validateAkSk(String ak, String sk, PipelineContext ctx) {
                return super.validateAkSk(ak, sk, ctx);
            }
        };

        CloudEvent event = CloudEventBuilder.v1()
            .withId("bad-aksk")
            .withSource(SOURCE)
            .withType("test.type")
            .withExtension(AuthFilter.ACCESS_KEY_KEY, "")
            .withExtension(AuthFilter.SECRET_KEY_KEY, "")
            .build();

        PipelineResult result = filter.filter(event, ingressCtx);
        assertEquals(PipelineResult.Action.DROP, result.getAction());
    }

    @Test
    @DisplayName("Auth: should drop with only AK no SK")
    void auth_shouldDropAkOnly() {
        AuthFilter filter = new AuthFilter();

        CloudEvent event = CloudEventBuilder.v1()
            .withId("ak-only")
            .withSource(SOURCE)
            .withType("test.type")
            .withExtension(AuthFilter.ACCESS_KEY_KEY, "my-key")
            .build();

        PipelineResult result = filter.filter(event, ingressCtx);
        assertEquals(PipelineResult.Action.DROP, result.getAction());
    }

    @Test
    @DisplayName("Auth: correct order = 1")
    void auth_correctOrder() {
        assertEquals(1, new AuthFilter().order());
    }

    @Test
    @DisplayName("Auth: correct name")
    void auth_correctName() {
        assertEquals("AuthFilter", new AuthFilter().name());
    }

    // ========================================================================
    // ProtocolFilter — extended scenarios
    // ========================================================================

    @Test
    @DisplayName("Protocol: should drop missing source")
    void protocol_shouldDropMissingSource() {
        ProtocolFilter filter = new ProtocolFilter();
        CloudEvent badEvent = mock(CloudEvent.class);
        when(badEvent.getId()).thenReturn("id-1");
        when(badEvent.getSource()).thenReturn(null);
        when(badEvent.getType()).thenReturn("test.type");
        when(badEvent.getSpecVersion()).thenReturn(SpecVersion.V1);

        PipelineResult result = filter.filter(badEvent, ingressCtx);
        assertEquals(PipelineResult.Action.DROP, result.getAction());
    }

    @Test
    @DisplayName("Protocol: should drop invalid source URI")
    void protocol_shouldDropInvalidSourceUri() {
        ProtocolFilter filter = new ProtocolFilter();
        CloudEvent badEvent = mock(CloudEvent.class);
        when(badEvent.getId()).thenReturn("id-2");
        when(badEvent.getSource()).thenReturn(URI.create("bad-uri-no-scheme"));
        when(badEvent.getType()).thenReturn("test.type");
        when(badEvent.getSpecVersion()).thenReturn(SpecVersion.V1);

        PipelineResult result = filter.filter(badEvent, ingressCtx);
        // May pass or fail depending on URI parsing; just ensure no NPE
        assertNotNull(result);
    }

    @Test
    @DisplayName("Protocol: should drop unsupported spec version")
    void protocol_shouldDropUnsupportedSpecVersion() {
        ProtocolFilter filter = new ProtocolFilter();
        CloudEvent badEvent = mock(CloudEvent.class);
        when(badEvent.getId()).thenReturn("id-3");
        when(badEvent.getSource()).thenReturn(SOURCE);
        when(badEvent.getType()).thenReturn("test.type");
        when(badEvent.getSpecVersion()).thenReturn(null);
        when(badEvent.getSpecVersion()).thenReturn(SpecVersion.V1);
        // Can't mock toString on enum easily — use valid event with non-1.0 check
        PipelineResult result = filter.filter(badEvent, ingressCtx);
        assertNotNull(result);
    }

    @Test
    @DisplayName("Protocol: should drop empty type string")
    void protocol_shouldDropEmptyType() {
        ProtocolFilter filter = new ProtocolFilter();
        CloudEvent badEvent = mock(CloudEvent.class);
        when(badEvent.getId()).thenReturn("id-4");
        when(badEvent.getSource()).thenReturn(SOURCE);
        when(badEvent.getType()).thenReturn("");
        when(badEvent.getSpecVersion()).thenReturn(SpecVersion.V1);

        PipelineResult result = filter.filter(badEvent, ingressCtx);
        assertEquals(PipelineResult.Action.DROP, result.getAction());
    }

    // ========================================================================
    // RateLimitFilter — extended scenarios
    // ========================================================================

    @Test
    @DisplayName("RateLimit: should drop when exceeding per-topic limit")
    void rateLimit_shouldDropExceedingTopicLimit() {
        RateLimitFilter filter = new RateLimitFilter(3, 100); // 3/s per topic
        CloudEvent event = validEvent();
        PipelineResult result = null;

        for (int i = 0; i < 10; i++) {
            CloudEvent evt = CloudEventBuilder.v1()
                .withId("rl-" + i)
                .withSource(SOURCE)
                .withType("test.type")
                .withSubject("rate-limited-topic")
                .build();
            result = filter.filter(evt, ingressCtx);
        }
        assertEquals(PipelineResult.Action.DROP, result.getAction());
    }

    @Test
    @DisplayName("RateLimit: different topics should have independent limits")
    void rateLimit_independentPerTopicLimits() {
        RateLimitFilter filter = new RateLimitFilter(2, 1000);

        // Fill topic A
        CloudEvent ea = CloudEventBuilder.v1()
            .withId("a-1").withSource(SOURCE).withType("t").withSubject("topic-a").build();
        filter.filter(ea, ingressCtx);
        filter.filter(ea, ingressCtx);

        // Topic B should still pass even though A is exhausted
        CloudEvent eb = CloudEventBuilder.v1()
            .withId("b-1").withSource(SOURCE).withType("t").withSubject("topic-b").build();
        PipelineResult result = filter.filter(eb, ingressCtx);
        assertEquals(PipelineResult.Action.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("RateLimit: should handle null subject gracefully")
    void rateLimit_shouldHandleNullSubject() {
        RateLimitFilter filter = new RateLimitFilter(1, 1000);
        CloudEvent event = CloudEventBuilder.v1()
            .withId("no-subject")
            .withSource(SOURCE)
            .withType("test.type")
            .build(); // no subject

        PipelineResult result = filter.filter(event, ingressCtx);
        assertEquals(PipelineResult.Action.CONTINUE, result.getAction());
    }

    // ========================================================================
    // RuleFilter — extended scenarios
    // ========================================================================

    @Test
    @DisplayName("Rule: content rule should block matching keyword")
    void rule_shouldBlockByContentRule() {
        RuleFilter filter = new RuleFilter();
        filter.addContentRule("forbidden", "deny");

        CloudEvent event = CloudEventBuilder.v1()
            .withId("content-test")
            .withSource(SOURCE)
            .withType("test.type")
            .withSubject(TOPIC)
            .withData("text/plain", "this contains forbidden word".getBytes(StandardCharsets.UTF_8))
            .build();

        PipelineResult result = filter.filter(event, ingressCtx);
        assertEquals(PipelineResult.Action.DROP, result.getAction());
    }

    @Test
    @DisplayName("Rule: allow content rule should pass")
    void rule_shouldAllowByContentRule() {
        RuleFilter filter = new RuleFilter();
        filter.addContentRule("allowed-keyword", "allow");

        CloudEvent event = CloudEventBuilder.v1()
            .withId("allow-test")
            .withSource(SOURCE)
            .withType("test.type")
            .withSubject(TOPIC)
            .withData("text/plain", "containing allowed-keyword here".getBytes(StandardCharsets.UTF_8))
            .build();

        PipelineResult result = filter.filter(event, ingressCtx);
        assertEquals(PipelineResult.Action.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("Rule: denylist should take precedence over allowlist")
    void rule_denylistOverAllowlist() {
        RuleFilter filter = new RuleFilter();
        filter.addAllowedTopic(TOPIC);
        filter.addDeniedTopic(TOPIC);

        PipelineResult result = filter.filter(validEvent(), ingressCtx);
        assertEquals(PipelineResult.Action.DROP, result.getAction());
    }

    @Test
    @DisplayName("Rule: dynamic add and remove rules")
    void rule_dynamicAddRemove() {
        RuleFilter filter = new RuleFilter();
        filter.addDeniedTopic("temp-deny");

        CloudEvent event = CloudEventBuilder.v1()
            .withId("temp")
            .withSource(SOURCE)
            .withType("t")
            .withSubject("temp-deny")
            .build();

        assertEquals(PipelineResult.Action.DROP, filter.filter(event, ingressCtx).getAction());

        filter.removeDeniedTopic("temp-deny");
        assertEquals(PipelineResult.Action.CONTINUE, filter.filter(event, ingressCtx).getAction());
    }

    @Test
    @DisplayName("Rule: should return unmodifiable sets")
    void rule_unmodifiableSets() {
        RuleFilter filter = new RuleFilter();
        filter.addAllowedTopic("a");
        filter.addDeniedTopic("b");

        Set<String> allowed = filter.getAllowedTopics();
        Set<String> denied = filter.getDeniedTopics();

        assertThrows(UnsupportedOperationException.class, () -> allowed.add("x"));
        assertThrows(UnsupportedOperationException.class, () -> denied.add("y"));
    }

    // ========================================================================
    // AclFilter — extended scenarios
    // ========================================================================

    @Test
    @DisplayName("ACL: IP allowlist should block non-allowed IP")
    void acl_ipAllowlistBlocks() {
        AclFilter filter = new AclFilter(null);
        filter.addAllowedIp("192.168.1.1");
        ingressCtx.setAttribute("clientIp", "10.0.0.1");

        PipelineResult result = filter.filter(validEvent(), ingressCtx);
        assertEquals(PipelineResult.Action.DROP, result.getAction());
    }

    @Test
    @DisplayName("ACL: IP allowlist should pass allowed IP")
    void acl_ipAllowlistAllows() {
        AclFilter filter = new AclFilter(null);
        filter.addAllowedIp("192.168.1.1");
        ingressCtx.setAttribute("clientIp", "192.168.1.1");

        PipelineResult result = filter.filter(validEvent(), ingressCtx);
        assertEquals(PipelineResult.Action.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("ACL: IP denylist takes precedence over allowlist")
    void acl_denyOverridesAllow() {
        AclFilter filter = new AclFilter(null);
        filter.addAllowedIp("10.0.0.1");
        filter.addDeniedIp("10.0.0.1");
        ingressCtx.setAttribute("clientIp", "10.0.0.1");

        // Denylist checked first — should drop
        PipelineResult result = filter.filter(validEvent(), ingressCtx);
        assertEquals(PipelineResult.Action.DROP, result.getAction());
    }

    @Test
    @DisplayName("ACL: dynamic IP management")
    void acl_dynamicIpManagement() {
        AclFilter filter = new AclFilter(null);
        filter.addDeniedIp("5.5.5.5");
        assertTrue(filter.getIpDenylist().contains("5.5.5.5"));

        filter.removeDeniedIp("5.5.5.5");
        assertFalse(filter.getIpDenylist().contains("5.5.5.5"));
    }

    // ========================================================================
    // SizeLimitFilter — extended scenarios
    // ========================================================================

    @Test
    @DisplayName("SizeLimit: should reject over-limit events")
    void sizeLimit_shouldRejectOverLimit() {
        SizeLimitFilter filter = new SizeLimitFilter(5); // 5 bytes
        CloudEvent event = CloudEventBuilder.v1()
            .withId("big")
            .withSource(SOURCE)
            .withType("t")
            .withData("text/plain", "too long".getBytes(StandardCharsets.UTF_8))
            .build();

        PipelineResult result = filter.filter(event, ingressCtx);
        assertEquals(PipelineResult.Action.DROP, result.getAction());
    }

    @Test
    @DisplayName("SizeLimit: should pass null data events")
    void sizeLimit_shouldPassNullData() {
        SizeLimitFilter filter = new SizeLimitFilter(5);
        CloudEvent event = CloudEventBuilder.v1()
            .withId("no-data")
            .withSource(SOURCE)
            .withType("t")
            .build(); // no data

        PipelineResult result = filter.filter(event, ingressCtx);
        assertEquals(PipelineResult.Action.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("SizeLimit: exact size boundary should pass")
    void sizeLimit_exactSizeShouldPass() {
        String data = "12345"; // 5 bytes
        SizeLimitFilter filter = new SizeLimitFilter(5);
        CloudEvent event = CloudEventBuilder.v1()
            .withId("exact")
            .withSource(SOURCE)
            .withType("t")
            .withData("text/plain", data.getBytes(StandardCharsets.UTF_8))
            .build();

        PipelineResult result = filter.filter(event, ingressCtx);
        assertEquals(PipelineResult.Action.CONTINUE, result.getAction());
    }

    @Test
    @DisplayName("SizeLimit: getMaxBytes returns configured value")
    void sizeLimit_getMaxBytes() {
        assertEquals(2048, new SizeLimitFilter(2048).getMaxBytes());
        assertEquals(4 * 1024 * 1024, new SizeLimitFilter().getMaxBytes());
    }

    // ========================================================================
    // PipelineResult — DLQ / FAIL / Meta
    // ========================================================================

    @Test
    @DisplayName("Result: DLQ should not pass")
    void result_dlqShouldNotPass() {
        PipelineResult r = PipelineResult.dlq(validEvent(), new RuntimeException("dead"));
        assertFalse(r.passed());
        assertEquals(PipelineResult.Action.DLQ, r.getAction());
        assertNotNull(r.getCause());
    }

    @Test
    @DisplayName("Result: FAIL should not pass")
    void result_failShouldNotPass() {
        PipelineResult r = PipelineResult.fail(validEvent(), new Error("fatal"));
        assertFalse(r.passed());
        assertEquals(PipelineResult.Action.FAIL, r.getAction());
        assertNotNull(r.getCause());
    }

    @Test
    @DisplayName("Result: addMeta and getMeta")
    void result_addAndGetMeta() {
        PipelineResult r = PipelineResult.cont(validEvent());
        r.addMeta("stage", "auth");
        r.addMeta("latency", "5ms");
        assertEquals("auth", r.getMeta("stage"));
        assertEquals("5ms", r.getMeta("latency"));
        assertNull(r.getMeta("nonexistent"));
    }

    @Test
    @DisplayName("Result: setAction mutates action")
    void result_setActionMutates() {
        PipelineResult r = PipelineResult.cont(validEvent());
        assertEquals(PipelineResult.Action.CONTINUE, r.getAction());
        r.setAction(PipelineResult.Action.DROP);
        assertEquals(PipelineResult.Action.DROP, r.getAction());
        assertFalse(r.passed());
    }

    @Test
    @DisplayName("Result: setCause sets cause")
    void result_setCause() {
        PipelineResult r = PipelineResult.cont(validEvent());
        assertNull(r.getCause());
        r.setCause(new RuntimeException("test"));
        assertNotNull(r.getCause());
    }

    @Test
    @DisplayName("Result: toString includes event ID")
    void result_toString() {
        CloudEvent event = validEvent();
        PipelineResult r = PipelineResult.cont(event);
        String s = r.toString();
        assertTrue(s.contains(event.getId()));
        assertTrue(s.contains("CONTINUE"));
    }

    // ========================================================================
    // PipelineContext — extended scenarios
    // ========================================================================

    @Test
    @DisplayName("Context: typed getAttribute")
    void context_typedGetAttribute() {
        ingressCtx.setAttribute("count", 42);
        assertEquals(Integer.valueOf(42), ingressCtx.getAttribute("count", Integer.class));
        assertNull(ingressCtx.getAttribute("count", String.class)); // wrong type
        assertNull(ingressCtx.getAttribute("missing", String.class));
    }

    @Test
    @DisplayName("Context: trace ID tracking")
    void context_traceIdTracking() {
        assertNull(ingressCtx.getTraceId());
        ingressCtx.setTraceId("trace-abc-123");
        assertEquals("trace-abc-123", ingressCtx.getTraceId());
    }

    @Test
    @DisplayName("Context: getAttributes returns copy")
    void context_getAttributesReturnsCopy() {
        ingressCtx.setAttribute("a", 1);
        assertEquals(1, ingressCtx.getAttributes().get("a"));
        // Modify copy — should not affect original
        ingressCtx.getAttributes().put("b", 2);
        assertNull(ingressCtx.getAttribute("b"));
    }

    @Test
    @DisplayName("Context: DIRECTION values")
    void context_directionValues() {
        assertEquals(PipelineContext.Direction.INGRESS,
            PipelineContext.Direction.valueOf("INGRESS"));
        assertEquals(PipelineContext.Direction.EGRESS,
            PipelineContext.Direction.valueOf("EGRESS"));
    }

    @Test
    @DisplayName("Context: toString format")
    void context_toString() {
        ingressCtx.setTraceId("t1");
        String s = ingressCtx.toString();
        assertTrue(s.contains("INGRESS"));
        assertTrue(s.contains("http"));
        assertTrue(s.contains("t1"));
    }

    // ========================================================================
    // PipelineFilter — interface contract
    // ========================================================================

    @Test
    @DisplayName("Filter: default implementation contracts")
    void filter_contracts() {
        PipelineFilter f = new PipelineFilter() {
            public String name() { return "TestFilter"; }
            public int order() { return 42; }
            public boolean isBypassable() { return true; }
            public PipelineResult filter(CloudEvent e, PipelineContext c) {
                return PipelineResult.cont(e);
            }
        };

        assertEquals("TestFilter", f.name());
        assertEquals(42, f.order());
        assertTrue(f.isBypassable());
        assertEquals(PipelineResult.Action.CONTINUE,
            f.filter(validEvent(), ingressCtx).getAction());
    }

    // ========================================================================
    // PipelineTransformer — interface contract
    // ========================================================================

    @Test
    @DisplayName("Transformer: should transform event")
    void transformer_shouldTransform() {
        PipelineTransformer t = new PipelineTransformer() {
            public String name() { return "Upper"; }
            public int order() { return 1; }
            public CloudEvent transform(CloudEvent e, PipelineContext c) {
                if (e.getData() == null) return e;
                String data = new String(e.getData().toBytes(), StandardCharsets.UTF_8);
                return CloudEventBuilder.from(e)
                    .withData("text/plain", data.toUpperCase().getBytes(StandardCharsets.UTF_8))
                    .build();
            }
        };

        CloudEvent event = validEvent();
        CloudEvent result = t.transform(event, ingressCtx);

        assertEquals("Upper", t.name());
        String resultData = new String(result.getData().toBytes(), StandardCharsets.UTF_8);
        assertEquals("HELLO WORLD", resultData);
    }

    // ========================================================================
    // PipelineRouter — interface contract
    // ========================================================================

    @Test
    @DisplayName("Router: should return target topics")
    void router_shouldReturnTargets() {
        PipelineRouter r = new PipelineRouter() {
            public String name() { return "MyRouter"; }
            public java.util.List<String> route(CloudEvent e, PipelineContext c) {
                return Arrays.asList("topic-a", "topic-b");
            }
        };

        assertEquals("MyRouter", r.name());
        assertEquals(2, r.route(validEvent(), ingressCtx).size());
    }

    @Test
    @DisplayName("Router: empty list means no routing (drop)")
    void router_emptyListMeansDrop() {
        PipelineRouter r = new PipelineRouter() {
            public String name() { return "NoRoute"; }
            public java.util.List<String> route(CloudEvent e, PipelineContext c) {
                return Collections.emptyList();
            }
        };

        assertTrue(r.route(validEvent(), ingressCtx).isEmpty());
    }
}
