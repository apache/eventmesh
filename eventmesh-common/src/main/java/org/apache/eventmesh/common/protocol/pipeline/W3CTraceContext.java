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

import java.util.UUID;

import io.cloudevents.CloudEvent;

/**
 * W3C Trace Context — extracts and propagates trace information from CloudEvents.
 *
 * <p>Compliant with <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>.
 * Extracts from:
 * <ul>
 *   <li>CloudEvent extension {@code traceparent} — W3C trace context header</li>
 *   <li>CloudEvent extension {@code tracestate} — vendor-specific trace data</li>
 *   <li>CloudEvent extension {@code eventmesh_trace_id} — EventMesh internal trace ID</li>
 * </ul>
 *
 * <p>If no trace context is found, generates a new trace ID.
 */
public final class W3CTraceContext {

    private static final String EXT_TRACEPARENT = "traceparent";
    private static final String EXT_TRACESTATE = "tracestate";
    private static final String EXT_EVENTMESH_TRACE = "eventmesh_trace_id";

    private W3CTraceContext() {}

    /**
     * Extract or generate a trace ID from a CloudEvent.
     *
     * <p>Priority:
     * <ol>
     *   <li>{@code traceparent} extension (W3C standard)</li>
     *   <li>{@code eventmesh_trace_id} extension</li>
     *   <li>Generate new UUID</li>
     * </ol>
     */
    public static String extractTraceId(CloudEvent event) {
        if (event == null) return UUID.randomUUID().toString();

        // 1. W3C traceparent: "00-{trace-id}-{span-id}-{flags}"
        Object traceparent = event.getExtension(EXT_TRACEPARENT);
        if (traceparent instanceof String) {
            String tp = (String) traceparent;
            String[] parts = tp.split("-");
            if (parts.length >= 3 && parts[1].length() == 32) {
                return parts[1]; // W3C trace-id (32 hex chars)
            }
        }

        // 2. EventMesh internal trace ID
        Object emTrace = event.getExtension(EXT_EVENTMESH_TRACE);
        if (emTrace instanceof String && !((String) emTrace).isEmpty()) {
            return (String) emTrace;
        }

        // 3. Generate new
        return UUID.randomUUID().toString();
    }

    /**
     * Extract or generate a span ID from a CloudEvent.
     */
    public static String extractSpanId(CloudEvent event) {
        if (event == null) return generateSpanId();

        Object traceparent = event.getExtension(EXT_TRACEPARENT);
        if (traceparent instanceof String) {
            String[] parts = ((String) traceparent).split("-");
            if (parts.length >= 3 && parts[2].length() == 16) {
                return parts[2];
            }
        }

        return generateSpanId();
    }

    /**
     * Extract tracestate from a CloudEvent.
     */
    public static String extractTraceState(CloudEvent event) {
        if (event == null) return null;
        Object ts = event.getExtension(EXT_TRACESTATE);
        return ts instanceof String ? (String) ts : null;
    }

    /**
     * Build a traceparent header from trace ID and span ID.
     */
    public static String buildTraceParent(String traceId, String spanId) {
        return "00-" + traceId + "-" + spanId + "-01";
    }

    /** Generate a random 16-hex-digit span ID. */
    private static String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
