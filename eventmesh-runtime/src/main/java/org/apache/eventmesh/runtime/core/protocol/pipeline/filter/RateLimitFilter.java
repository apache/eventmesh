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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Rate-limit filter — per-topic and per-client throttling.
 * Uses a simple sliding-window counter approach.
 *
 * <p>This filter IS bypassable — disable via configuration when not needed.
 */
@Slf4j
public class RateLimitFilter implements PipelineFilter {

    public static final String NAME = "RateLimitFilter";

    // Default limits
    private static final int DEFAULT_PER_TOPIC_LIMIT = 10_000;  // ops/sec
    private static final int DEFAULT_PER_CLIENT_LIMIT = 5_000;  // ops/sec

    private final int perTopicLimit;
    private final int perClientLimit;
    private final ConcurrentMap<String, long[]> counters;

    public RateLimitFilter() {
        this(DEFAULT_PER_TOPIC_LIMIT, DEFAULT_PER_CLIENT_LIMIT);
    }

    public RateLimitFilter(int perTopicLimit, int perClientLimit) {
        this.perTopicLimit = perTopicLimit;
        this.perClientLimit = perClientLimit;
        this.counters = new ConcurrentHashMap<>();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int order() {
        return 2;
    }

    @Override
    public boolean isBypassable() {
        return true;
    }

    @Override
    public PipelineResult filter(CloudEvent event, PipelineContext ctx) {
        String topic = event.getSubject();
        String source = event.getSource() != null ? event.getSource().toString() : "unknown";

        // Per-topic check
        if (topic != null && exceedLimit("topic:" + topic, perTopicLimit)) {
            log.debug("[RateLimitFilter] Topic {} rate limit exceeded", topic);
            return PipelineResult.drop(event);
        }

        // Per-client check
        if (exceedLimit("client:" + source, perClientLimit)) {
            log.debug("[RateLimitFilter] Client {} rate limit exceeded", source);
            return PipelineResult.drop(event);
        }

        return PipelineResult.cont(event);
    }

    /**
     * Simple in-memory sliding-window counter.
     * [0]: current second epoch, [1]: count in current second.
     */
    private boolean exceedLimit(String key, int limit) {
        long[] slot = counters.computeIfAbsent(key, k -> new long[]{0, 0});
        long now = System.currentTimeMillis() / 1000;
        synchronized (slot) {
            if (slot[0] != now) {
                slot[0] = now;
                slot[1] = 0;
            }
            slot[1]++;
            return slot[1] > limit;
        }
    }
}
