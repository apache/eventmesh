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

package org.apache.eventmesh.client.trace;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;

public class TraceUtils {
    private static Logger logger = LoggerFactory.getLogger(TraceUtils.class);
    private static Trace trace = null;

    static {
        trace = Trace.getInstance();
        trace.init();
    }

    public static Span prepareClientSpan(Map map, String spanName,
                                         boolean isSpanFinishInOtherThread) {
        Span span = null;
        try {
            span = trace.createSpan(
                spanName, SpanKind.CLIENT, Context.current(), isSpanFinishInOtherThread);
            trace.inject(Context.current(), map);
        } catch (Throwable ex) {
            logger.warn("upload trace fail when prepareSpan", ex);
        }
        return span;
    }

    public static Span prepareServerSpan(Map<String, Object> map, String spanName,
                                         boolean isSpanFinishInOtherThread) {
        Span span = null;
        try {
            Context traceContext = trace.extractFrom(Context.current(), map);
            span = trace.createSpan(spanName, SpanKind.SERVER, traceContext, false);
        } catch (Throwable ex) {
            logger.warn("upload trace fail when prepareSpan", ex);
        }
        return span;
    }

    public static void finishSpan(Span span, Map<String, Object> map) {
        try {
            trace.addTraceInfoToSpan(span, map);
            trace.finishSpan(span, StatusCode.OK);
        } catch (Throwable ex) {
            logger.warn("upload trace fail when finishSpan", ex);
        }

    }

    public static void finishSpanWithException(Span span, Map<String, Object> map, String errMsg,
                                               Throwable e) {
        try {
            trace.addTraceInfoToSpan(span, map);
            trace.finishSpan(span, StatusCode.ERROR, errMsg, e);
        } catch (Throwable ex) {
            logger.warn("upload trace fail when finishSpanWithException", ex);
        }
    }
}
