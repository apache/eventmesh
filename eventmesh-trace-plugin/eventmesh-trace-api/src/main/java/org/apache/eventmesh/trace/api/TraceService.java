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

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.TextMapPropagator;

/**
 * The top-level interface of trace
 */
@EventMeshSPI(isSingleton = true, eventMeshExtensionType = EventMeshExtensionType.TRACE)
public interface TraceService {
    /**
     * init the trace service
     */
    void init();

    /**
     * close the trace service
     */
    void shutdown();

    /**
     * get the tracer
     *
     * @param instrumentationName
     * @return
     */
    Tracer getTracer(String instrumentationName);

    /**
     * get TextMapPropagator
     *
     * @return
     */
    TextMapPropagator getTextMapPropagator();
}
