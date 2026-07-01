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

import java.util.Collections;
import java.util.List;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Header router — routes based on CloudEvent extension attributes.
 *
 * <p>Configured via pipeline context:
 * <pre>{@code
 *   ctx.setAttribute("HeaderRoute.field", "routing_key");   // extension key to read
 *   ctx.setAttribute("HeaderRoute.prefix", "topic.");        // optional topic prefix
 * }</pre>
 */
@Slf4j
public class HeaderRoute implements PipelineRouter {

    private static final String FIELD_ATTR = "HeaderRoute.field";
    private static final String PREFIX_ATTR = "HeaderRoute.prefix";

    @Override
    public String name() {
        return "header-route";
    }

    @Override
    public List<String> route(CloudEvent event, PipelineContext ctx) {
        String field = null;
        String prefix = "";
        try {
            field = (String) ctx.getAttribute(FIELD_ATTR);
            String p = (String) ctx.getAttribute(PREFIX_ATTR);
            if (p != null) prefix = p;
        } catch (Exception e) {
            log.debug("HeaderRoute: no routing field configured");
            return Collections.emptyList();
        }

        if (field == null || field.isEmpty()) {
            return Collections.emptyList();
        }

        // Read routing value from CloudEvent extension
        Object routingValue = event.getExtension(field);
        if (routingValue == null) {
            // Try reading from context attributes as fallback
            routingValue = ctx.getAttribute(field);
        }

        if (routingValue != null) {
            String topic = prefix + routingValue.toString();
            log.debug("HeaderRoute: field={} value={} → topic={}", field, routingValue, topic);
            return Collections.singletonList(topic);
        }

        log.debug("HeaderRoute: field {} not found in event extensions", field);
        return Collections.emptyList();
    }
}
