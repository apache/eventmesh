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

package org.apache.eventmesh.runtime.core.protocol;

import org.apache.eventmesh.common.protocol.pipeline.PipelineContext;
import org.apache.eventmesh.common.protocol.pipeline.PipelineResult;
import org.apache.eventmesh.runtime.boot.FilterEngine;
import org.apache.eventmesh.runtime.boot.RouterEngine;
import org.apache.eventmesh.runtime.boot.TransformerEngine;
import org.apache.eventmesh.runtime.core.protocol.pipeline.PipelineFilter;
import org.apache.eventmesh.runtime.core.protocol.pipeline.filter.AuthFilter;
import org.apache.eventmesh.runtime.core.protocol.pipeline.filter.ProtocolFilter;
import org.apache.eventmesh.runtime.core.protocol.pipeline.filter.SizeLimitFilter;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for IngressProcessor and EgressProcessor — the unified pipeline
 * entry and exit points.
 */
@DisplayName("Ingress & Egress Processor Tests")
class IngressEgressProcessorTest {

    private static final URI SOURCE = URI.create("http://test-service/test");
    private static final String TOPIC = "test-topic";
    private static final String PIPELINE_KEY = "group1:topic1";

    private FilterEngine filterEngine;
    private TransformerEngine transformerEngine;
    private RouterEngine routerEngine;

    @BeforeEach
    void setUp() {
        filterEngine = mock(FilterEngine.class);
        transformerEngine = mock(TransformerEngine.class);
        routerEngine = mock(RouterEngine.class);
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
    // IngressProcessor — constructor variants
    // ========================================================================

    @Test
    @DisplayName("Ingress: should construct with no pipeline filters")
    void ingress_shouldConstructWithoutPipelineFilters() {
        IngressProcessor processor = new IngressProcessor(filterEngine, transformerEngine, routerEngine);
        assertNotNull(processor);
    }

    @Test
    @DisplayName("Ingress: should construct with pipeline filters")
    void ingress_shouldConstructWithPipelineFilters() {
        List<PipelineFilter> filters = Arrays.asList(new AuthFilter(), new ProtocolFilter());
        IngressProcessor processor = new IngressProcessor(filters, filterEngine, transformerEngine, routerEngine);
        assertNotNull(processor);
    }

    // ========================================================================
    // IngressProcessor — all filters pass
    // ========================================================================

    @Test
    @DisplayName("Ingress: should pass event through all pipeline stages")
    void ingress_shouldPassThroughAllStages() {
        List<PipelineFilter> filters = Collections.singletonList(new AuthFilter());
        IngressProcessor processor = new IngressProcessor(filters, filterEngine, transformerEngine, routerEngine);

        CloudEvent event = validEvent();
        PipelineContext ctx = new PipelineContext(PipelineContext.Direction.INGRESS, "http");

        CloudEvent result = processor.process(event, PIPELINE_KEY, ctx);
        assertNotNull(result);
        assertEquals(event.getId(), result.getId());
    }

    @Test
    @DisplayName("Ingress: should pass with empty filter chain")
    void ingress_shouldPassWithEmptyFilterChain() {
        IngressProcessor processor = new IngressProcessor(
            Collections.emptyList(), filterEngine, transformerEngine, routerEngine);

        CloudEvent event = validEvent();
        PipelineContext ctx = new PipelineContext(PipelineContext.Direction.INGRESS, "grpc");

        CloudEvent result = processor.process(event, PIPELINE_KEY, ctx);
        assertNotNull(result);
    }

    // ========================================================================
    // IngressProcessor — filter drops event
    // ========================================================================

    @Test
    @DisplayName("Ingress: should return null when filter drops")
    void ingress_shouldReturnNullWhenFilterDrops() {
        PipelineFilter dropFilter = new PipelineFilter() {
            public String name() { return "DropAll"; }
            public int order() { return 0; }
            public boolean isBypassable() { return true; }
            public PipelineResult filter(CloudEvent e, PipelineContext c) {
                return PipelineResult.drop(e);
            }
        };

        IngressProcessor processor = new IngressProcessor(
            Collections.singletonList(dropFilter), filterEngine, transformerEngine, routerEngine);

        CloudEvent event = validEvent();
        PipelineContext ctx = new PipelineContext(PipelineContext.Direction.INGRESS, "tcp");

        CloudEvent result = processor.process(event, PIPELINE_KEY, ctx);
        assertNull(result);
    }

    // ========================================================================
    // IngressProcessor — DLQ handling
    // ========================================================================

    @Test
    @DisplayName("Ingress: should return null but log DLQ when filter returns DLQ")
    void ingress_shouldHandleDLQ() {
        PipelineFilter dlqFilter = new PipelineFilter() {
            public String name() { return "DlqFilter"; }
            public int order() { return 0; }
            public boolean isBypassable() { return true; }
            public PipelineResult filter(CloudEvent e, PipelineContext c) {
                return PipelineResult.dlq(e, new RuntimeException("test-dlq"));
            }
        };

        IngressProcessor processor = new IngressProcessor(
            Collections.singletonList(dlqFilter), filterEngine, transformerEngine, routerEngine);

        CloudEvent event = validEvent();
        PipelineContext ctx = new PipelineContext(PipelineContext.Direction.INGRESS, "http");

        CloudEvent result = processor.process(event, PIPELINE_KEY, ctx);
        assertNull(result);
    }

    // ========================================================================
    // IngressProcessor — filter skip via context
    // ========================================================================

    @Test
    @DisplayName("Ingress: should skip filter when disabled in context")
    void ingress_shouldSkipFilterWhenDisabled() {
        PipelineFilter dropFilter = new PipelineFilter() {
            public String name() { return "SkipMe"; }
            public int order() { return 0; }
            public boolean isBypassable() { return true; }
            public PipelineResult filter(CloudEvent e, PipelineContext c) {
                return PipelineResult.drop(e); // would drop if not skipped
            }
        };

        IngressProcessor processor = new IngressProcessor(
            Collections.singletonList(dropFilter), filterEngine, transformerEngine, routerEngine);

        CloudEvent event = validEvent();
        PipelineContext ctx = new PipelineContext(PipelineContext.Direction.INGRESS, "http");
        ctx.setAttribute("pipeline.disabled.SkipMe", true);

        // Should pass because filter is skipped
        CloudEvent result = processor.process(event, PIPELINE_KEY, ctx);
        assertNotNull(result);
    }

    // ========================================================================
    // IngressProcessor — backward-compatible overload (no ctx)
    // ========================================================================

    @Test
    @DisplayName("Ingress: backward-compatible overload should work")
    void ingress_backwardCompatibleOverload() {
        IngressProcessor processor = new IngressProcessor(filterEngine, transformerEngine, routerEngine);

        CloudEvent event = validEvent();
        CloudEvent result = processor.process(event, PIPELINE_KEY);
        assertNotNull(result);
    }

    @Test
    @DisplayName("Ingress: backward-compatible overload should pass with filters")
    void ingress_backwardCompatibleWithFilters() {
        List<PipelineFilter> filters = Collections.singletonList(new AuthFilter());
        IngressProcessor processor = new IngressProcessor(filters, filterEngine, transformerEngine, routerEngine);

        CloudEvent event = validEvent();
        CloudEvent result = processor.process(event, PIPELINE_KEY);
        assertNotNull(result);
    }

    // ========================================================================
    // IngressProcessor — size limit filter blocks oversized
    // ========================================================================

    @Test
    @DisplayName("Ingress: should reject oversized events")
    void ingress_shouldRejectOversizedEvents() {
        SizeLimitFilter sizeFilter = new SizeLimitFilter(10); // 10 bytes max
        List<PipelineFilter> filters = Collections.singletonList(sizeFilter);
        IngressProcessor processor = new IngressProcessor(filters, filterEngine, transformerEngine, routerEngine);

        CloudEvent event = CloudEventBuilder.v1()
            .withId("big-event")
            .withSource(SOURCE)
            .withType("test.type")
            .withSubject(TOPIC)
            .withData("text/plain", "this is larger than 10 bytes".getBytes(StandardCharsets.UTF_8))
            .build();

        PipelineContext ctx = new PipelineContext(PipelineContext.Direction.INGRESS, "http");
        CloudEvent result = processor.process(event, PIPELINE_KEY, ctx);
        assertNull(result);
    }

    // ========================================================================
    // EgressProcessor — basic
    // ========================================================================

    @Test
    @DisplayName("Egress: should construct and pass event")
    void egress_shouldConstructAndPass() {
        EgressProcessor processor = new EgressProcessor(filterEngine, transformerEngine);
        assertNotNull(processor);

        CloudEvent event = validEvent();
        PipelineContext ctx = new PipelineContext(PipelineContext.Direction.EGRESS, "http");
        CloudEvent result = processor.process(event, PIPELINE_KEY, ctx);
        assertNotNull(result);
    }

    @Test
    @DisplayName("Egress: should construct with pipeline filters")
    void egress_shouldConstructWithFilters() {
        List<PipelineFilter> filters = Arrays.asList(new AuthFilter(), new SizeLimitFilter());
        EgressProcessor processor = new EgressProcessor(filters, filterEngine, transformerEngine);
        assertNotNull(processor);

        CloudEvent event = validEvent();
        PipelineContext ctx = new PipelineContext(PipelineContext.Direction.EGRESS, "http");
        CloudEvent result = processor.process(event, PIPELINE_KEY, ctx);
        assertNotNull(result);
    }

    @Test
    @DisplayName("Egress: should return null when filter drops")
    void egress_shouldReturnNullWhenFilterDrops() {
        PipelineFilter dropFilter = new PipelineFilter() {
            public String name() { return "EgressDrop"; }
            public int order() { return 0; }
            public boolean isBypassable() { return true; }
            public PipelineResult filter(CloudEvent e, PipelineContext c) {
                return PipelineResult.drop(e);
            }
        };

        EgressProcessor processor = new EgressProcessor(
            Collections.singletonList(dropFilter), filterEngine, transformerEngine);

        CloudEvent event = validEvent();
        PipelineContext ctx = new PipelineContext(PipelineContext.Direction.EGRESS, "grpc");
        CloudEvent result = processor.process(event, PIPELINE_KEY, ctx);
        assertNull(result);
    }

    @Test
    @DisplayName("Egress: backward-compatible overload should work")
    void egress_backwardCompatibleOverload() {
        EgressProcessor processor = new EgressProcessor(filterEngine, transformerEngine);
        CloudEvent event = validEvent();
        CloudEvent result = processor.process(event, PIPELINE_KEY);
        assertNotNull(result);
    }
}
