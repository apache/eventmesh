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
import org.apache.eventmesh.runtime.boot.TransformerEngine;
import org.apache.eventmesh.runtime.core.protocol.pipeline.PipelineFilter;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * Egress processor — unified pipeline exit point for ALL egress traffic.
 *
 * <p>Processing order:
 * <ol>
 *   <li><b>Pipeline Filter Chain</b> — ACL → SizeLimit (egress-relevant filters)</li>
 *   <li><b>Legacy Filter</b> — group-topic pattern-based filtering</li>
 *   <li><b>Transformer</b> — group-topic transformation</li>
 * </ol>
 */
@Slf4j
public class EgressProcessor {

    private final List<PipelineFilter> pipelineFilters;
    private final FilterEngine filterEngine;
    private final TransformerEngine transformerEngine;

    public EgressProcessor(FilterEngine filterEngine, TransformerEngine transformerEngine) {
        this(Collections.emptyList(), filterEngine, transformerEngine);
    }

    public EgressProcessor(List<PipelineFilter> pipelineFilters, FilterEngine filterEngine,
                           TransformerEngine transformerEngine) {
        this.pipelineFilters = pipelineFilters;
        this.filterEngine = filterEngine;
        this.transformerEngine = transformerEngine;
    }

    /**
     * Process an event through the egress pipeline.
     */
    public CloudEvent process(CloudEvent event, String pipelineKey, PipelineContext ctx) {
        try {
            // ---- Phase 1: Pipeline Filter Chain ----
            for (PipelineFilter filter : pipelineFilters) {
                if (ctx != null && isFilterSkipped(filter, ctx)) {
                    continue;
                }

                PipelineResult result = filter.filter(event, ctx);
                if (result == null || !result.passed()) {
                    log.debug("Egress event {} filtered by {}", event.getId(), filter.name());
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

            return event;

        } catch (Exception e) {
            log.error("Egress pipeline exception for key: {}", pipelineKey, e);
            throw new RuntimeException("Egress pipeline exception", e);
        }
    }

    /** Backward-compatible overload. */
    public CloudEvent process(CloudEvent event, String pipelineKey) {
        return process(event, pipelineKey,
            new PipelineContext(PipelineContext.Direction.EGRESS, "unknown"));
    }

    private boolean isFilterSkipped(PipelineFilter filter, PipelineContext ctx) {
        Object disabled = ctx.getAttribute("pipeline.disabled." + filter.name());
        return disabled != null && Boolean.TRUE.equals(disabled);
    }
}
