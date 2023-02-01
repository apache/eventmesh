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

import static org.junit.Assert.assertThrows;

import org.apache.eventmesh.trace.api.TracePluginFactory;

import java.lang.reflect.Field;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import io.opentelemetry.sdk.trace.SdkTracerProvider;

public class JaegerTraceServiceTest {

    @Test
    public void testInit() {
        JaegerTraceService jaegerTraceService =
                (JaegerTraceService) TracePluginFactory.getEventMeshTraceService("jaeger");
        jaegerTraceService.init();

        Assert.assertNotNull(jaegerTraceService.getSdkTracerProvider());
        Assert.assertNotNull(jaegerTraceService.getShutdownHook());

        IllegalArgumentException illegalArgumentException =
                assertThrows(IllegalArgumentException.class, () ->
                        Runtime.getRuntime().addShutdownHook(jaegerTraceService.getShutdownHook()));
        Assert.assertEquals(illegalArgumentException.getMessage(), "Hook previously registered");
    }

    @Test
    public void testShutdown() throws NoSuchFieldException, IllegalAccessException {
        SdkTracerProvider mockSdkTracerProvider = Mockito.mock(SdkTracerProvider.class);
        JaegerTraceService jaegerTraceService =
                (JaegerTraceService) TracePluginFactory.getEventMeshTraceService("jaeger");
        jaegerTraceService.init();
        Field sdkTracerProviderField = JaegerTraceService.class.getDeclaredField("sdkTracerProvider");
        sdkTracerProviderField.setAccessible(true);
        sdkTracerProviderField.set(jaegerTraceService, mockSdkTracerProvider);

        jaegerTraceService.shutdown();
        Mockito.verify(mockSdkTracerProvider, Mockito.times(1)).close();
    }
}
