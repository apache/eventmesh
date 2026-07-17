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

package org.apache.eventmesh.runtime.core.protocol.pipeline;

import org.apache.eventmesh.common.protocol.pipeline.PipelineContext;
import org.apache.eventmesh.common.protocol.pipeline.PipelineResult;

import io.cloudevents.CloudEvent;

/**
 * Pipeline filter in a Chain-of-Responsibility pattern.
 * Each filter independently decides whether the event passes.
 *
 * <p>Return {@link PipelineResult} with:
 * <ul>
 *   <li>{@code Action.CONTINUE} — pass to next filter</li>
 *   <li>{@code Action.DROP} — silently discard</li>
 *   <li>{@code Action.RETRY} — retry with context</li>
 *   <li>{@code Action.DLQ} — route to dead-letter queue</li>
 *   <li>{@code Action.FAIL} — fatal error, raise alert</li>
 * </ul>
 */
public interface PipelineFilter {

    /**
     * @return filter name, used for metrics and logging
     */
    String name();

    /**
     * @return execution order (lower = earlier). Filters are sorted by this value.
     */
    int order();

    /**
     * Whether this filter can be disabled by configuration.
     * @return false for security-critical filters (Auth, ACL)
     */
    boolean isBypassable();

    /**
     * Execute the filter.
     * @param event  the CloudEvent to inspect
     * @param ctx    pipeline context with trace/metadata
     * @return PipelineResult with the action decision
     */
    PipelineResult filter(CloudEvent event, PipelineContext ctx);
}
