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

package org.apache.eventmesh.runtime.core.protocol.pipeline.filter;

import org.apache.eventmesh.common.protocol.pipeline.PipelineContext;
import org.apache.eventmesh.common.protocol.pipeline.PipelineResult;
import org.apache.eventmesh.runtime.core.protocol.pipeline.PipelineFilter;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Message body size limit filter — rejects events exceeding configured max size.
 *
 * <p>Bypassable — disable if no size limit is needed.
 */
@Slf4j
public class SizeLimitFilter implements PipelineFilter {

    public static final String NAME = "SizeLimitFilter";
    private static final int DEFAULT_MAX_BYTES = 4 * 1024 * 1024; // 4 MB

    private final int maxBytes;

    public SizeLimitFilter() {
        this(DEFAULT_MAX_BYTES);
    }

    public SizeLimitFilter(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int order() {
        return 6;
    }

    @Override
    public boolean isBypassable() {
        return true;
    }

    @Override
    public PipelineResult filter(CloudEvent event, PipelineContext ctx) {
        if (event.getData() == null) {
            return PipelineResult.cont(event);
        }

        try {
            int dataSize = event.getData().toBytes().length;
            if (dataSize > maxBytes) {
                log.warn("[SizeLimitFilter] Event {} exceeds size limit: {} > {} bytes",
                    event.getId(), dataSize, maxBytes);
                return PipelineResult.drop(event);
            }
        } catch (Exception e) {
            log.warn("[SizeLimitFilter] Failed to read event data size for {}", event.getId(), e);
            // Cannot determine size — let it through to avoid false positives
        }

        return PipelineResult.cont(event);
    }

    public int getMaxBytes() {
        return maxBytes;
    }
}
