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

package org.apache.eventmesh.trace.zipkin;

import static org.hamcrest.CoreMatchers.is;

import org.apache.eventmesh.trace.api.EventMeshTraceService;
import org.apache.eventmesh.trace.api.TracePluginFactory;

import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;

public class TracePluginFactoryTest {

    @Test
    public void testFailedGetTraceService() {
        NullPointerException nullPointerException1 = Assert.assertThrows(NullPointerException.class,
            () -> TracePluginFactory.getEventMeshTraceService(null));
        MatcherAssert.assertThat(nullPointerException1.getMessage(), is("traceServiceType cannot be null"));

        String traceServiceType = "non-Existing";
        NullPointerException nullPointerException2 =
            Assert.assertThrows(NullPointerException.class, () -> TracePluginFactory.getEventMeshTraceService(traceServiceType));
        MatcherAssert.assertThat(nullPointerException2.getMessage(), is("traceServiceType: " + traceServiceType + " is not supported"));
    }

    @Test
    public void testSuccessfulGetTraceService() {
        EventMeshTraceService zipkinTraceService = TracePluginFactory.getEventMeshTraceService("zipkin");
        Assert.assertNotNull(zipkinTraceService);
        Assert.assertTrue(zipkinTraceService instanceof ZipkinTraceService);
    }
}
