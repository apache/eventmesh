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

package org.apache.eventmesh.runtime.util;

import org.apache.eventmesh.runtime.boot.EventMeshServer;
import org.apache.eventmesh.runtime.mock.MockCloudEvent;
import org.apache.eventmesh.runtime.trace.Trace;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.cloudevents.SpecVersion;
import io.opentelemetry.api.trace.Span;

public class TraceUtilsTest {
    @Test
    public void testShouldPrepareClientSpan() throws Exception {
        Map<String, Object> cloudEventExtensionMap = EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), new MockCloudEvent());
        try (MockedStatic<EventMeshServer> dummyStatic = Mockito.mockStatic(EventMeshServer.class)) {
            Trace trace = Trace.getInstance("zipkin", true);
            trace.init();
            dummyStatic.when(EventMeshServer::getTrace).thenReturn(trace);
            Span testClientSpan = TraceUtils.prepareClientSpan(
                cloudEventExtensionMap,
                "test client span",
                false
            );
            Assertions.assertNotNull(testClientSpan);
        }
    }

    @Test
    public void testShouldPrepareServerSpan() throws Exception {
        Map<String, Object> cloudEventExtensionMap = EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), new MockCloudEvent());
        try (MockedStatic<EventMeshServer> dummyStatic = Mockito.mockStatic(EventMeshServer.class)) {
            Trace trace = Trace.getInstance("zipkin", true);
            trace.init();
            dummyStatic.when(EventMeshServer::getTrace).thenReturn(trace);
            TraceUtils.prepareClientSpan(
                cloudEventExtensionMap,
                "test client span",
                false
            );
            Span testServerSpan = TraceUtils.prepareServerSpan(
                cloudEventExtensionMap,
                "test server span",
                false
            );
            Assertions.assertNotNull(testServerSpan);
        }
    }

    @Test
    public void testShouldFinishSpan() throws Exception {
        MockCloudEvent cloudEvent = new MockCloudEvent();
        Map<String, Object> cloudEventExtensionMap = EventMeshUtil.getCloudEventExtensionMap(SpecVersion.V1.toString(), cloudEvent);
        try (MockedStatic<EventMeshServer> dummyStatic = Mockito.mockStatic(EventMeshServer.class)) {
            Trace trace = Trace.getInstance("zipkin", true);
            trace.init();
            dummyStatic.when(EventMeshServer::getTrace).thenReturn(trace);
            Span testClientSpan = TraceUtils.prepareClientSpan(
                cloudEventExtensionMap,
                "test client span",
                false
            );

            TraceUtils.finishSpan(testClientSpan, cloudEvent);
            Assertions.assertFalse(testClientSpan.isRecording());
        }
    }
}
