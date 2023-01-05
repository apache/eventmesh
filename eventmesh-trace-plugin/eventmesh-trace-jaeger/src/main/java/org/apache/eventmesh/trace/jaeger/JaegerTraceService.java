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

package org.apache.eventmesh.trace.jaeger;

import org.apache.eventmesh.trace.api.EventMeshTraceService;
import org.apache.eventmesh.trace.api.exception.TraceException;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;

public class JaegerTraceService implements EventMeshTraceService {

    @Override
    public void init() throws TraceException {

    }

    @Override
    public Context extractFrom(Context context, Map<String, Object> carrier) throws TraceException {
        return null;
    }

    @Override
    public void inject(Context context, Map<String, Object> carrier) {

    }

    @Override
    public Span createSpan(String spanName, SpanKind spanKind, long startTimestamp, TimeUnit timeUnit, Context context,
                           boolean isSpanFinishInOtherThread) throws TraceException {
        return null;
    }

    @Override
    public Span createSpan(String spanName, SpanKind spanKind, Context context, boolean isSpanFinishInOtherThread) throws TraceException {
        return null;
    }

    @Override
    public void shutdown() throws TraceException {

    }
}