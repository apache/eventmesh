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

package org.apache.eventmesh.runtime.boot;

import org.apache.eventmesh.runtime.trace.TraceUtils;
import org.apache.eventmesh.trace.api.common.EventMeshTraceConstants;

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

public class HTTPTrace {

    private TextMapPropagator textMapPropagator;

    private Tracer tracer;

    @Setter
    private String serviceAddress;

    @Setter
    private String servicePort;

    public TraceOperation getTraceOperation(HttpRequest httpRequest, Channel channel) {

        final Map<String, Object> headerMap = parseHttpHeader(httpRequest);
        Span span = TraceUtils.prepareServerSpan(headerMap, EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_SERVER_SPAN,
            false);
        return new TraceOperation(span);
    }

    private Map<String, Object> parseHttpHeader(HttpRequest fullReq) {
        Map<String, Object> headerParam = new HashMap<>();
        for (String key : fullReq.headers().names()) {
            if (StringUtils.equalsIgnoreCase(HttpHeaderNames.CONTENT_TYPE.toString(), key)
                || StringUtils.equalsIgnoreCase(HttpHeaderNames.ACCEPT_ENCODING.toString(), key)
                || StringUtils.equalsIgnoreCase(HttpHeaderNames.CONTENT_LENGTH.toString(), key)) {
                continue;
            }
            headerParam.put(key, fullReq.headers().get(key));
        }
        return headerParam;
    }

    @AllArgsConstructor
    @Getter
    public static class TraceOperation {

        private final Span span;

        public void endTrace() {

            try (Scope ignored = span.makeCurrent()) {
                TraceUtils.finishSpan(span, null);
            }
        }

        public void exceptionTrace(@Nullable Throwable ex) {
            try (Scope ignored = span.makeCurrent()) {
                TraceUtils.finishSpanWithException(span, null, ex.getMessage(), ex);
            }
        }
    }
}
