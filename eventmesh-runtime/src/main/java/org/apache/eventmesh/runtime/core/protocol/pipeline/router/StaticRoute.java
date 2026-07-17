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
 * Static router — maps source topic to fixed target topic(s).
 *
 * <p>Mapping is provided via pipeline context:
 * <pre>{@code
 *   ctx.setAttribute("StaticRoute.target", "order.processed");
 *   ctx.setAttribute("StaticRoute.broadcast", "true");  // optional
 * }</pre>
 */
@Slf4j
public class StaticRoute implements PipelineRouter {

    private static final String TARGET_ATTR = "StaticRoute.target";

    @Override
    public String name() {
        return "static-route";
    }

    @Override
    public List<String> route(CloudEvent event, PipelineContext ctx) {
        try {
            Object target = ctx.getAttribute(TARGET_ATTR);
            if (target instanceof String && !((String) target).isEmpty()) {
                log.debug("StaticRoute: {} → {}", event.getSubject(), target);
                return Collections.singletonList((String) target);
            }
        } catch (Exception e) {
            log.debug("StaticRoute: no target configured");
        }

        // No static route configured, pass-through the original subject
        if (event.getSubject() != null) {
            return Collections.singletonList(event.getSubject());
        }
        return Collections.emptyList();
    }
}
