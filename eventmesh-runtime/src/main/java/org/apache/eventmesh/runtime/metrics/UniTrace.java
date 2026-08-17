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

package org.apache.eventmesh.runtime.metrics;

import io.cloudevents.CloudEvent;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

import lombok.extern.slf4j.Slf4j;

/**
 * OTel trace span helpers for the runtime (§13.5.2). Wraps key data-path operations in spans:
 * publish → dispatch → push → ack, plus retry/dlq branches. Uses the CloudEvents {@code id} as a
 * span attribute so a single event's full lifecycle can be queried in the tracing backend.
 */
@Slf4j
public final class UniTrace {

    private static final Tracer TRACER = GlobalOpenTelemetry.getTracer("eventmesh-runtime");

    private UniTrace() {
    }

    /** Start a span for a publish operation. */
    public static Span startPublish(String topic, CloudEvent event) {
        return start("publish", topic, event);
    }

    /** Start a span for a publish operation (EventMeshFrame overload — reads id/type from attributes). */
    public static Span startPublish(String topic, org.apache.eventmesh.common.wire.EventMeshFrame event) {
        return start("publish", topic, event);
    }

    /** Start a span for a dispatch (pull + route) operation. */
    public static Span startDispatch(String topic, CloudEvent event) {
        return start("dispatch", topic, event);
    }

    /** Start a span for a dispatch operation (EventMeshFrame overload). */
    public static Span startDispatch(String topic, org.apache.eventmesh.common.wire.EventMeshFrame event) {
        return start("dispatch", topic, event);
    }

    /** Start a span for an ACK operation. */
    public static Span startAck(String deliveryId) {
        return TRACER.spanBuilder("ack")
            .setAttribute("deliveryId", deliveryId)
            .startSpan();
    }

    /** Start a span for a retry redelivery. */
    public static Span startRetry(String deliveryId, int attempt) {
        return TRACER.spanBuilder("retry")
            .setAttribute("deliveryId", deliveryId)
            .setAttribute("attempt", attempt)
            .startSpan();
    }

    /** Start a span for a DLQ routing. */
    public static Span startDlq(String topic, String reason) {
        return TRACER.spanBuilder("dlq")
            .setAttribute("topic", topic)
            .setAttribute("reason", reason)
            .startSpan();
    }

    /** Mark a span as error and record the exception. */
    public static void error(Span span, Throwable e) {
        if (span != null) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
        }
    }

    /** End a span (null-safe). */
    public static void end(Span span) {
        if (span != null) {
            span.end();
        }
    }

    private static Span start(String operation, String topic, CloudEvent event) {
        io.opentelemetry.api.trace.SpanBuilder b = TRACER.spanBuilder(operation).setAttribute("topic", topic);
        if (event != null && event.getId() != null) {
            b.setAttribute("cloudEventId", event.getId());
        }
        if (event != null && event.getType() != null) {
            b.setAttribute("cloudEventType", event.getType());
        }
        return b.startSpan();
    }

    private static Span start(String operation, String topic, org.apache.eventmesh.common.wire.EventMeshFrame event) {
        io.opentelemetry.api.trace.SpanBuilder b = TRACER.spanBuilder(operation).setAttribute("topic", topic);
        if (event != null) {
            String id = event.attributes().get("id");
            String type = event.attributes().get("type");
            if (id != null) {
                b.setAttribute("cloudEventId", id);
            }
            if (type != null) {
                b.setAttribute("cloudEventType", type);
            }
        }
        return b.startSpan();
    }
}
