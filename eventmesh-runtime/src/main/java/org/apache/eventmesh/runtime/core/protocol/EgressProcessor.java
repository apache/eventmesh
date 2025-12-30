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

import org.apache.eventmesh.runtime.boot.FilterEngine;
import org.apache.eventmesh.runtime.boot.TransformerEngine;

import java.nio.charset.StandardCharsets;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EgressProcessor {

    private final FilterEngine filterEngine;
    private final TransformerEngine transformerEngine;

    public EgressProcessor(FilterEngine filterEngine, TransformerEngine transformerEngine) {
        this.filterEngine = filterEngine;
        this.transformerEngine = transformerEngine;
    }

    public CloudEvent process(CloudEvent event, String pipelineKey) {
        try {
            // 1. Filter
            org.apache.eventmesh.function.filter.pattern.Pattern filterPattern = filterEngine.getFilterPattern(pipelineKey);
            if (filterPattern != null && event.getData() != null) {
                String content = new String(event.getData().toBytes(), StandardCharsets.UTF_8);
                if (!filterPattern.filter(content)) {
                    // Filtered out
                    return null;
                }
            }

            // 2. Transformer
            org.apache.eventmesh.function.transformer.Transformer transformer = transformerEngine.getTransformer(pipelineKey);
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
}
