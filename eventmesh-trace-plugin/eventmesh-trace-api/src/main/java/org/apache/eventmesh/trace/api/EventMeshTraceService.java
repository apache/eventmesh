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

package org.apache.eventmesh.trace.api;

import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;
import org.apache.eventmesh.trace.api.exception.TraceException;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;

/**
 * EventMeshTraceService
 */
@EventMeshSPI(isSingleton = true, eventMeshExtensionType = EventMeshExtensionType.TRACE)
public interface EventMeshTraceService {
    void init() throws TraceException;

    //extract attr from carrier to context
    Context extractFrom(Context context, Map<String, Object> carrier) throws TraceException;

    //inject attr from context to carrier
    void inject(Context context, Map<String, Object> carrier);

    Span createSpan(String spanName, SpanKind spanKind, long startTimestamp, TimeUnit timeUnit,
                    Context context, boolean isSpanFinishInOtherThread) throws TraceException;

    Span createSpan(String spanName, SpanKind spanKind, Context context,
                    boolean isSpanFinishInOtherThread) throws TraceException;

    void shutdown() throws TraceException;
}
