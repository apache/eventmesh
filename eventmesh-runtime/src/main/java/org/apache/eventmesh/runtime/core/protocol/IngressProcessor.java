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

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * Ingress processor — unified pipeline entry point for ALL ingress traffic.
 *
 * <p>Processing order:
 * <ol>
 *   <li><b>Pipeline Filter Chain</b> — Auth → RateLimit → Protocol → Rule → ACL → SizeLimit
 *       (same for TCP/HTTP/gRPC/A2A/Connector Source)</li>
 *   <li><b>Group-Topic Filter</b> — pattern-based filtering (existing behavior)</li>
 *   <li><b>Transformer</b> — group-topic transformation</li>
 *   <li><b>Router</b> — topic routing</li>
 * </ol>
 */
@Slf4j
public class IngressProcessor {

    private final List<PipelineFilter> pipelineFilters;
    private final FilterEngine filterEngine;
    private final TransformerEngine transformerEngine;
    private final RouterEngine routerEngine;

    public IngressProcessor(FilterEngine filterEngine, TransformerEngine transformerEngine,
                            RouterEngine routerEngine) {
        this(Collections.emptyList(), filterEngine, transformerEngine, routerEngine);
    }

    public IngressProcessor(List<PipelineFilter> pipelineFilters, FilterEngine filterEngine,
                            TransformerEngine transformerEngine, RouterEngine routerEngine) {
        this.pipelineFilters = pipelineFilters;
        this.filterEngine = filterEngine;
        this.transformerEngine = transformerEngine;
        this.routerEngine = routerEngine;
    }

    /**
     * Process an event through the ingress pipeline.
     *
     * @param event       the CloudEvent to process
     * @param pipelineKey group-topic key for legacy filter/transform/router lookup
     * @param ctx         pipeline context carrying protocol/direction metadata
     * @return processed CloudEvent, or null if filtered out
     */
    public CloudEvent process(CloudEvent event, String pipelineKey, PipelineContext ctx) {
        try {
            // ---- Phase 1: Pipeline Filter Chain (all filters, all protocols) ----
            for (PipelineFilter filter : pipelineFilters) {
                if (ctx != null && isFilterSkipped(filter, ctx)) {
                    continue;
                }

                PipelineResult result = filter.filter(event, ctx);
                if (result == null || !result.passed()) {
                    log.debug("Ingress event {} filtered by {}", event.getId(), filter.name());
                    if (result != null && result.getAction() == PipelineResult.Action.DLQ) {
                        // TODO: route to DLQ topic
                        log.warn("Ingress event {} routed to DLQ by {}", event.getId(), filter.name());
                    }
                    return null;
                }
            }

            // ---- Phase 2: Legacy Group-Topic Filter ----
            org.apache.eventmesh.function.filter.pattern.Pattern filterPattern =
                filterEngine.getFilterPattern(pipelineKey);
            if (filterPattern != null && event.getData() != null) {
                String content = new String(event.getData().toBytes(), StandardCharsets.UTF_8);
                if (!filterPattern.filter(content)) {
                    return null;
                }
            }

            // ---- Phase 3: Transformer ----
            org.apache.eventmesh.function.transformer.Transformer transformer =
                transformerEngine.getTransformer(pipelineKey);
            if (transformer != null && event.getData() != null) {
                String content = new String(event.getData().toBytes(), StandardCharsets.UTF_8);
                String transformedContent = transformer.transform(content);
                event = CloudEventBuilder.from(event)
                        .withData(transformedContent.getBytes(StandardCharsets.UTF_8))
                        .build();
            }

            // ---- Phase 4: Router ----
            org.apache.eventmesh.function.api.Router router = routerEngine.getRouter(pipelineKey);
            if (router != null && event.getData() != null) {
                String content = new String(event.getData().toBytes(), StandardCharsets.UTF_8);
                String newTopic = router.route(content);
                event = CloudEventBuilder.from(event)
                        .withSubject(newTopic)
                        .build();
            }

            return event;

        } catch (Exception e) {
            log.error("Ingress pipeline exception for key: {}", pipelineKey, e);
            throw new RuntimeException("Ingress pipeline exception", e);
        }
    }

    /**
     * Backward-compatible overload without PipelineContext.
     */
    public CloudEvent process(CloudEvent event, String pipelineKey) {
        return process(event, pipelineKey,
            new PipelineContext(PipelineContext.Direction.INGRESS, "unknown"));
    }

    // ---- helpers ----

    private boolean isFilterSkipped(PipelineFilter filter, PipelineContext ctx) {
        Object disabled = ctx.getAttribute("pipeline.disabled." + filter.name());
        return disabled != null && Boolean.TRUE.equals(disabled);
    }
}
