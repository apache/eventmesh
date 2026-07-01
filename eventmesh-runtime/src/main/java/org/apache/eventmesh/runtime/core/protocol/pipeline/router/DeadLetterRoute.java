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
import org.apache.eventmesh.common.protocol.pipeline.PipelineResult;
import org.apache.eventmesh.runtime.core.protocol.pipeline.PipelineRouter;

import java.util.Collections;
import java.util.List;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Dead Letter router — routes DLQ-tagged events to the dead-letter topic.
 *
 * <p>This router checks if the event carries a {@code eventmesh_dlq_source_filter}
 * extension (set by IngressProcessor when a filter returns DLQ action).
 * If present, routes to the configured DLQ topic.
 *
 * <p>Configuration:
 * <pre>{@code
 *   ctx.setAttribute("DeadLetterRoute.topic", "eventmesh-dlq");  // default
 * }</pre>
 */
@Slf4j
public class DeadLetterRoute implements PipelineRouter {

    private static final String DLQ_TOPIC_ATTR = "DeadLetterRoute.topic";
    private static final String DEFAULT_DLQ_TOPIC = "eventmesh-dlq";
    static final String DLQ_FILTER_EXT = "dlqfilter";

    @Override
    public String name() {
        return "dead-letter-route";
    }

    @Override
    public List<String> route(CloudEvent event, PipelineContext ctx) {
        // Only route if event is tagged as DLQ
        Object dlqSource = event.getExtension(DLQ_FILTER_EXT);
        if (dlqSource == null) {
            // Not a DLQ event — pass with original subject
            return (event.getSubject() != null)
                ? Collections.singletonList(event.getSubject())
                : Collections.emptyList();
        }

        String dlqTopic = DEFAULT_DLQ_TOPIC;
        try {
            String configured = (String) ctx.getAttribute(DLQ_TOPIC_ATTR);
            if (configured != null && !configured.isEmpty()) {
                dlqTopic = configured;
            }
        } catch (Exception e) {
            log.debug("DeadLetterRoute: using default DLQ topic {}", dlqTopic);
        }

        log.warn("DeadLetterRoute: event {} routed to DLQ topic {} (source filter: {})",
            event.getId(), dlqTopic, dlqSource);
        return Collections.singletonList(dlqTopic);
    }
}
