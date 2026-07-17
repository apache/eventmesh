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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Broadcast router — fans out to multiple topics.
 *
 * <p>Configured via pipeline context:
 * <pre>{@code
 *   ctx.setAttribute("BroadcastRoute.topics", "topic-a,topic-b,topic-c");
 * }</pre>
 */
@Slf4j
public class BroadcastRoute implements PipelineRouter {

    private static final String TOPICS_ATTR = "BroadcastRoute.topics";

    @Override
    public String name() {
        return "broadcast-route";
    }

    @Override
    public List<String> route(CloudEvent event, PipelineContext ctx) {
        try {
            Object topics = ctx.getAttribute(TOPICS_ATTR);
            if (topics instanceof String && !((String) topics).isEmpty()) {
                List<String> topicList = Arrays.asList(((String) topics).split(","));
                log.debug("BroadcastRoute: fan-out to {} topics", topicList.size());
                return topicList;
            }
        } catch (Exception e) {
            log.debug("BroadcastRoute: no broadcast topics configured");
        }

        return Collections.emptyList();
    }
}
