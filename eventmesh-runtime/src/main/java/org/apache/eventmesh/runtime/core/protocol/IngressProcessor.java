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
import org.apache.eventmesh.runtime.core.protocol.pipeline.PipelineRouter;
import org.apache.eventmesh.runtime.core.protocol.pipeline.PipelineTransformer;

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
 *   <li><b>Pipeline Filter Chain</b> — Auth → RateLimit → Protocol → Rule → ACL → SizeLimit</li>
 *   <li><b>Group-Topic Filter</b> — legacy pattern-based filtering</li>
 *   <li><b>Pipeline Transformer Chain</b> — Protocol → FieldMapping → Enrichment → Encryption → Compression</li>
 *   <li><b>Legacy Transformer</b> — group-topic transformation (fallback)</li>
 *   <li><b>Pipeline Router Chain</b> — topic routing</li>
 * </ol>
 */
@Slf4j
public class IngressProcessor {

    private final List<PipelineFilter> pipelineFilters;
    private final List<PipelineTransformer> pipelineTransformers;
    private final List<PipelineRouter> pipelineRouters;
    private final FilterEngine filterEngine;
    private final TransformerEngine transformerEngine;
    private final RouterEngine routerEngine;

    public IngressProcessor(FilterEngine filterEngine, TransformerEngine transformerEngine,
                            RouterEngine routerEngine) {
        this(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
             filterEngine, transformerEngine, routerEngine);
    }

    public IngressProcessor(List<PipelineFilter> pipelineFilters,
                            List<PipelineTransformer> pipelineTransformers,
                            List<PipelineRouter> pipelineRouters,
                            FilterEngine filterEngine,
                            TransformerEngine transformerEngine,
                            RouterEngine routerEngine) {
        this.pipelineFilters = (pipelineFilters != null) ? pipelineFilters : Collections.emptyList();
        this.pipelineTransformers = (pipelineTransformers != null) ? pipelineTransformers : Collections.emptyList();
        this.pipelineRouters = (pipelineRouters != null) ? pipelineRouters : Collections.emptyList();
        this.filterEngine = filterEngine;
        this.transformerEngine = transformerEngine;
        this.routerEngine = routerEngine;
    }

    /** Backward-compatible constructor (no PipelineRouter) */
    public IngressProcessor(List<PipelineFilter> pipelineFilters,
                            List<PipelineTransformer> pipelineTransformers,
                            FilterEngine filterEngine,
                            TransformerEngine transformerEngine,
                            RouterEngine routerEngine) {
        this(pipelineFilters, pipelineTransformers, Collections.emptyList(),
             filterEngine, transformerEngine, routerEngine);
    }

    /** Legacy constructor (no transformers/routers) */
    public IngressProcessor(List<PipelineFilter> pipelineFilters,
                            FilterEngine filterEngine,
                            TransformerEngine transformerEngine,
                            RouterEngine routerEngine) {
        this(pipelineFilters, Collections.emptyList(), Collections.emptyList(),
             filterEngine, transformerEngine, routerEngine);
    }

    public CloudEvent process(CloudEvent event, String pipelineKey, PipelineContext ctx) {
        try {
            // ---- Phase 1: Pipeline Filter Chain ----
            for (PipelineFilter filter : pipelineFilters) {
                if (ctx != null && isFilterSkipped(filter, ctx)) {
                    continue;
                }
                PipelineResult result = filter.filter(event, ctx);
                if (result == null || !result.passed()) {
                    log.debug("Ingress event {} filtered by {}", event.getId(), filter.name());
                    handleNonPassResult(result, event, filter);
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

            // ---- Phase 3: Pipeline Transformer Chain ----
            for (PipelineTransformer transformer : pipelineTransformers) {
                try {
                    event = transformer.transform(event, ctx);
                } catch (Exception e) {
                    log.warn("PipelineTransformer {} failed, pass-through: {}", transformer.name(), e.getMessage());
                }
            }

            // ---- Phase 4: Legacy Transformer (fallback) ----
            org.apache.eventmesh.function.transformer.Transformer transformer =
                transformerEngine.getTransformer(pipelineKey);
            if (transformer != null && event.getData() != null) {
                String content = new String(event.getData().toBytes(), StandardCharsets.UTF_8);
                String transformedContent = transformer.transform(content);
                event = CloudEventBuilder.from(event)
                        .withData(transformedContent.getBytes(StandardCharsets.UTF_8))
                        .build();
            }

            // ---- Phase 5: Pipeline Router Chain ----
            for (PipelineRouter router : pipelineRouters) {
                List<String> topics = router.route(event, ctx);
                if (topics != null && !topics.isEmpty()) {
                    // Route to target topic(s); use first for storage, rest for fanout
                    event = CloudEventBuilder.from(event)
                            .withSubject(topics.get(0))
                            .build();
                }
            }

            // ---- Phase 6: Legacy Router (fallback) ----
            if (pipelineRouters.isEmpty()) {
                org.apache.eventmesh.function.api.Router router = routerEngine.getRouter(pipelineKey);
                if (router != null && event.getData() != null) {
                    String content = new String(event.getData().toBytes(), StandardCharsets.UTF_8);
                    String newTopic = router.route(content);
                    event = CloudEventBuilder.from(event)
                            .withSubject(newTopic)
                            .build();
                }
            }

            return event;
        } catch (Exception e) {
            log.error("Ingress pipeline exception for key: {}", pipelineKey, e);
            throw new RuntimeException("Ingress pipeline exception", e);
        }
    }

    public CloudEvent process(CloudEvent event, String pipelineKey) {
        return process(event, pipelineKey,
            new PipelineContext(PipelineContext.Direction.INGRESS, "unknown"));
    }

    /** Handle non-pass filter result — DLQ / RETRY / FAIL */
    private void handleNonPassResult(PipelineResult result, CloudEvent event, PipelineFilter filter) {
        if (result == null) return;
        switch (result.getAction()) {
            case DLQ:
                routeToDLQ(event, filter.name(), result.getCause());
                break;
            case RETRY:
                log.info("Ingress event {} requires RETRY from filter {}", event.getId(), filter.name());
                break;
            case FAIL:
                log.error("Ingress event {} FAILED at filter {}: {}",
                    event.getId(), filter.name(),
                    result.getCause() != null ? result.getCause().getMessage() : "unknown");
                break;
            default:
                break;
        }
    }

    /** Route event to dead-letter-queue */
    void routeToDLQ(CloudEvent event, String filterName, Throwable cause) {
        try {
            CloudEvent dlqEvent = CloudEventBuilder.from(event)
                    .withSubject("eventmesh-dlq")
                    .withExtension("dlqfilter", filterName.length() > 20
                        ? filterName.substring(0, 20) : filterName)
                    .withExtension("dlqtime", String.valueOf(System.currentTimeMillis()))
                    .withExtension("dlqreason",
                        cause != null && cause.getMessage() != null
                            ? cause.getMessage().substring(0, Math.min(cause.getMessage().length(), 100))
                            : "filtered")
                    .build();
            log.warn("Ingress event {} routed to DLQ by filter {}: {}",
                event.getId(), filterName, cause != null ? cause.getMessage() : "unknown");
        } catch (Exception e) {
            log.warn("Failed to route to DLQ: {}", e.getMessage());
        }
    }

    private boolean isFilterSkipped(PipelineFilter filter, PipelineContext ctx) {
        Object disabled = ctx.getAttribute("pipeline.disabled." + filter.name());
        return disabled != null && Boolean.TRUE.equals(disabled);
    }

    // -- accessors for unit tests --

    public List<PipelineFilter> getPipelineFilters() {
        return pipelineFilters;
    }

    public List<PipelineTransformer> getPipelineTransformers() {
        return pipelineTransformers;
    }

    public List<PipelineRouter> getPipelineRouters() {
        return pipelineRouters;
    }
}
