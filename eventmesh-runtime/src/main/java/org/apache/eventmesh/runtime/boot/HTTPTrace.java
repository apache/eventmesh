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

import org.apache.eventmesh.runtime.trace.SpanKey;
import org.apache.eventmesh.runtime.trace.TraceUtils;
import org.apache.eventmesh.trace.api.TracePluginFactory;
import org.apache.eventmesh.trace.api.TraceService;
import org.apache.eventmesh.trace.api.common.EventMeshTraceConstants;

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import lombok.AllArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

public class HTTPTrace {

    private TextMapPropagator textMapPropagator;

    private Tracer tracer;

//    private boolean useTrace = false;

    @Setter
    private String serviceAddress;

    @Setter
    private String servicePort;

//    public void initTrace(Tracer tracer, TextMapPropagator textMapPropagator, boolean useTrace) {
//        this.tracer = tracer;
//        this.textMapPropagator = textMapPropagator;
//        this.useTrace = useTrace;
//    }
//
//    public void initTrace(String traceType, boolean traceEnable, Class<?> clazz) {
//        if (StringUtils.isNotEmpty(traceType) && traceEnable) {
//            TraceService traceService = TracePluginFactory.getTraceService(traceType);
//            traceService.init();
//            tracer = traceService.getTracer(clazz.toString());
//            textMapPropagator = traceService.getTextMapPropagator();
//            useTrace = true;
//        }
//    }

    public TraceOperation getTraceOperation(HttpRequest httpRequest, Channel channel) {
        if (!useTrace) {
            return new TraceOperation();
        }

        final Map<String, Object> headerMap = parseHttpHeader(httpRequest);
        Span span = TraceUtils.prepareServerSpan(headerMap, EventMeshTraceConstants.TRACE_UPSTREAM_EVENTMESH_SERVER_SPAN,
            false);

        Context context = textMapPropagator.extract(Context.current(), httpRequest, new TextMapGetter<HttpRequest>() {
            @Override
            public Iterable<String> keys(HttpRequest carrier) {
                return carrier.headers().names();
            }

            @Override
            public String get(HttpRequest carrier, String key) {
                return carrier.headers().get(key);
            }
        });

        Span span = tracer.spanBuilder("HTTP " + httpRequest.uri() + " " + httpRequest.method().toString())
                .setParent(context)
                .setSpanKind(SpanKind.SERVER)
                .startSpan();
        context = context.with(SpanKey.SERVER_KEY, span);

        span.setAttribute(SemanticAttributes.HTTP_METHOD, httpRequest.method().name());
        span.setAttribute(SemanticAttributes.HTTP_FLAVOR, httpRequest.protocolVersion().protocolName());
        span.setAttribute(String.valueOf(SemanticAttributes.HTTP_URL), httpRequest.uri());
        return new TraceOperation(context, span, useTrace);
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
    public static class TraceOperation {

        private final Context context;
        private final Span span;

        public void endTrace() {
            if (!useTrace) {
                return;
            }

            try (Scope ignored = context.makeCurrent()) {
                span.end();
            }
        }

        public void exceptionTrace(@Nullable Throwable ex) {
            if (!useTrace) {
                return;
            }
            try (Scope ignored = context.makeCurrent()) {
                ex = ex != null ? ex : new RuntimeException("exception for trace");
                span.setAttribute(SemanticAttributes.EXCEPTION_MESSAGE, ex.getMessage());
                span.setStatus(StatusCode.ERROR, ex.getMessage());
                span.recordException(ex);
                span.end();
            }
        }
    }
}
