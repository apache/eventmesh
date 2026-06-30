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

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.function.api.Router;
import org.apache.eventmesh.function.filter.pattern.Pattern;
import org.apache.eventmesh.function.transformer.Transformer;
import org.apache.eventmesh.runtime.boot.FilterEngine;
import org.apache.eventmesh.runtime.boot.RouterEngine;
import org.apache.eventmesh.runtime.boot.TransformerEngine;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IngressProcessor {

    private final FilterEngine filterEngine;
    private final TransformerEngine transformerEngine;
    private final RouterEngine routerEngine;

    public IngressProcessor(FilterEngine filterEngine, TransformerEngine transformerEngine, RouterEngine routerEngine) {
        this.filterEngine = filterEngine;
        this.transformerEngine = transformerEngine;
        this.routerEngine = routerEngine;
    }

    public CloudEvent process(CloudEvent event, String pipelineKey) {
        try {
            // 1. Filter - operates on raw payload data
            Pattern filterPattern = filterEngine.getFilterPattern(pipelineKey);
            if (filterPattern != null && event.getData() != null) {
                String content = new String(event.getData().toBytes(), StandardCharsets.UTF_8);
                if (!filterPattern.filter(content)) {
                    // Filtered out
                    return null;
                }
            }

            // 2. Transformer - receives full CloudEvent JSON for field-based transformation
            Transformer transformer = transformerEngine.getTransformer(pipelineKey);
            if (transformer != null && event.getData() != null) {
                String eventJson = cloudEventToJson(event);
                String transformedContent = transformer.transform(eventJson);
                event = CloudEventBuilder.from(event)
                        .withData(transformedContent.getBytes(StandardCharsets.UTF_8))
                        .build();
            }

            // 3. Router - receives full CloudEvent JSON for content-based routing
            Router router = routerEngine.getRouter(pipelineKey);
            if (router != null && event.getData() != null) {
                String eventJson = cloudEventToJson(event);
                String newTopic = router.route(eventJson);
                if (newTopic != null && !newTopic.isEmpty()) {
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

    /**
     * Convert a CloudEvent to a JSON string representation that includes
     * both the event metadata (id, source, type, subject, etc.) and the
     * payload data, enabling field-based transformer and router operations.
     */
    static String cloudEventToJson(CloudEvent event) {
        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("id", event.getId());
        eventMap.put("source", event.getSource() != null ? event.getSource().toString() : null);
        eventMap.put("type", event.getType());
        eventMap.put("subject", event.getSubject());
        eventMap.put("dataContentType", event.getDataContentType());
        eventMap.put("specVersion", event.getSpecVersion() != null ? event.getSpecVersion().toString() : null);
        eventMap.put("time", event.getTime() != null ? event.getTime().toString() : null);

        // Include extensions
        Map<String, Object> extensions = new HashMap<>();
        for (String extName : event.getExtensionNames()) {
            Object extValue = event.getExtension(extName);
            if (extValue != null) {
                extensions.put(extName, extValue.toString());
            }
        }
        if (!extensions.isEmpty()) {
            eventMap.put("extensions", extensions);
        }

        // Include data payload
        if (event.getData() != null) {
            String dataStr = new String(event.getData().toBytes(), StandardCharsets.UTF_8);
            // Try to parse data as JSON object; if it fails, use as plain string
            try {
                Object parsed = JsonUtils.getJsonNode(dataStr);
                eventMap.put("data", parsed);
            } catch (Exception e) {
                eventMap.put("data", dataStr);
            }
        }

        return JsonUtils.toJSONString(eventMap);
    }
}
