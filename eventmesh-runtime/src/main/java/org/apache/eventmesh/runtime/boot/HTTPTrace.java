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

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.runtime.trace.SpanKey;
import org.apache.eventmesh.trace.api.TracePluginFactory;
import org.apache.eventmesh.trace.api.TraceService;

import io.netty.channel.Channel;
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

public class HTTPTrace {
	
    private TextMapPropagator textMapPropagator;
    
    private Tracer tracer;
    
    private boolean useTrace = false;
    
    @Setter
    private String serviceAddress;
    
    @Setter
    private String servicePort;

    //重载初始化方法
    //TODO 初始化给本地ip和端口，待完成
    public void initTrace(Tracer tracer, TextMapPropagator textMapPropagator, boolean useTrace) {
        this.tracer = tracer;
        this.textMapPropagator = textMapPropagator;
        this.useTrace = useTrace;
    }

    public void initTrace(String traceType, boolean traceEnable, Class<?> clazz) {
        if (StringUtils.isNotEmpty(traceType) && traceEnable) {
            TraceService traceService = TracePluginFactory.getTraceService(traceType);
            traceService.init();
            tracer = traceService.getTracer(clazz.toString());
            textMapPropagator = traceService.getTextMapPropagator();
            useTrace = true;
        }
    }

    public TraceOperation getTraceOperation(HttpRequest httpRequest, Channel chnanle) {
    	if(!useTrace) {
    		return new TraceOperation(null, null, useTrace);
    	}
    	
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
        //使用IP和PORT
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

    @AllArgsConstructor
    public static class TraceOperation {

        private final Context context;
        private final Span span;
        private final boolean useTrace;

        public void endTrace() {
            if (!useTrace) {
            	return;
            }
            // TODO 此处context.makeCurrent()是否有必要，待确认，如无必要，可以不需要context
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
