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

package org.apache.eventmesh.common.protocol.pipeline;

import java.util.HashMap;
import java.util.Map;

/**
 * Context passed through every pipeline stage.
 * Carries metadata, trace info, and stage-specific configuration.
 */
public class PipelineContext {

    /** Pipeline direction */
    public enum Direction { INGRESS, EGRESS }

    private final Direction direction;
    private final String entryProtocol;  // tcp / http / grpc / a2a / connector
    private final Map<String, Object> attributes;
    private String traceId;
    private long startTimeMs;

    public PipelineContext(Direction direction, String entryProtocol) {
        this.direction = direction;
        this.entryProtocol = entryProtocol;
        this.attributes = new HashMap<>();
        this.startTimeMs = System.currentTimeMillis();
    }

    // ---- Accessors ----

    public Direction getDirection() { return direction; }
    public String getEntryProtocol() { return entryProtocol; }
    public String getTraceId() { return traceId; }

    public void setTraceId(String traceId) { this.traceId = traceId; }

    public long getStartTimeMs() { return startTimeMs; }
    public long getElapsedMs() { return System.currentTimeMillis() - startTimeMs; }

    // ---- Attribute helpers ----

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        Object v = attributes.get(key);
        if (v == null) return null;
        if (type.isInstance(v)) return (T) v;
        return null;
    }

    public Map<String, Object> getAttributes() {
        return new HashMap<>(attributes);
    }

    @Override
    public String toString() {
        return "PipelineContext{direction=" + direction
            + ", protocol=" + entryProtocol
            + ", traceId=" + traceId
            + ", elapsed=" + getElapsedMs() + "ms}";
    }
}
