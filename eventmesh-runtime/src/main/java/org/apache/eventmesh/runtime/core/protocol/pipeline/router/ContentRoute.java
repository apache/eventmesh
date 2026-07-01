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

package org.apache.eventmesh.runtime.core.protocol.pipeline.router;

import org.apache.eventmesh.common.protocol.pipeline.PipelineContext;
import org.apache.eventmesh.runtime.core.protocol.pipeline.PipelineRouter;
import org.apache.eventmesh.runtime.core.protocol.pipeline.transformer.FieldMappingTransformer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Content router — extracts routing key from event body using JSONPath-like expressions.
 *
 * <p>Configured via pipeline context:
 * <pre>{@code
 *   // Simple: extract value at JSON path → use as topic
 *   ctx.setAttribute("ContentRoute.path", "data.orderType");
 *
 *   // Map-based: JSON path → topic mapping rules (comma-separated)
 *   ctx.setAttribute("ContentRoute.path", "orderType");
 *   ctx.setAttribute("ContentRoute.map", "BUY:order.buy,SELL:order.sell");
 * }</pre>
 */
@Slf4j
public class ContentRoute implements PipelineRouter {

    private static final String PATH_ATTR = "ContentRoute.path";
    private static final String MAP_ATTR = "ContentRoute.map";

    @Override
    public String name() {
        return "content-route";
    }

    @Override
    public List<String> route(CloudEvent event, PipelineContext ctx) {
        String path = (String) ctx.getAttribute(PATH_ATTR);
        if (path == null || path.isEmpty() || event.getData() == null) {
            return Collections.emptyList();
        }

        try {
            String content = new String(event.getData().toBytes(), StandardCharsets.UTF_8);
            Map<String, Object> dataMap = FieldMappingTransformer.parseJson(content);

            Object pathValue = FieldMappingTransformer.resolvePath(dataMap, path);
            if (pathValue == null) {
                log.debug("ContentRoute: path {} not found in data", path);
                return Collections.emptyList();
            }

            String routingKey = pathValue.toString();

            // Check for mapping rules
            String mappingRules = (String) ctx.getAttribute(MAP_ATTR);
            if (mappingRules != null && !mappingRules.isEmpty()) {
                Map<String, String> topicMap = parseMap(mappingRules);
                String topic = topicMap.get(routingKey);
                if (topic != null) {
                    log.debug("ContentRoute: {}={} → topic={}", path, routingKey, topic);
                    return Collections.singletonList(topic);
                }
                log.debug("ContentRoute: no mapping for key {}", routingKey);
                return Collections.emptyList();
            }

            // Use routing key directly as topic
            log.debug("ContentRoute: {}={} → topic={}", path, routingKey, routingKey);
            return Collections.singletonList(routingKey);
        } catch (Exception e) {
            log.warn("ContentRoute: failed to parse content", e);
            return Collections.emptyList();
        }
    }

    static Map<String, String> parseMap(String rules) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String rule : rules.split(",")) {
            int colon = rule.indexOf(':');
            if (colon > 0) {
                map.put(rule.substring(0, colon).trim(), rule.substring(colon + 1).trim());
            }
        }
        return map;
    }
}
