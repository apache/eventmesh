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

import io.cloudevents.CloudEvent;

/**
 * Pipeline transformer — mutates the event in place or produces a new one.
 * Transformers run after all filters have passed.
 */
public interface PipelineTransformer {

    /** @return transformer name for metrics/logging */
    String name();

    /** @return execution order (lower = earlier) */
    int order();

    /**
     * Transform the event.
     * @param event  the event to transform
     * @param ctx    pipeline context
     * @return transformed event (may be the same instance if no-op)
     */
    CloudEvent transform(CloudEvent event, PipelineContext ctx);
}
