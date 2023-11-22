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

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.eventmesh.common.utils.ReflectUtils;
import org.apache.eventmesh.trace.api.TracePluginFactory;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.opentelemetry.sdk.trace.SdkTracerProvider;

public class ZipkinTraceServiceTest {

    @Test
    public void testInit() {
        ZipkinTraceService zipkinTraceService =
            (ZipkinTraceService) TracePluginFactory.getEventMeshTraceService("zipkin");
        zipkinTraceService.init();

        Assertions.assertNotNull(zipkinTraceService.getSdkTracerProvider());
        Assertions.assertNotNull(zipkinTraceService.getShutdownHook());

        IllegalArgumentException illegalArgumentException =
            assertThrows(IllegalArgumentException.class,
                () -> Runtime.getRuntime().addShutdownHook(zipkinTraceService.getShutdownHook()));
        Assertions.assertEquals(illegalArgumentException.getMessage(), "Hook previously registered");
    }

    @Test
    public void testShutdown() throws Exception {
        ZipkinTraceService zipkinTraceService =
            (ZipkinTraceService) TracePluginFactory.getEventMeshTraceService("zipkin");
        zipkinTraceService.init();
        Field sdkTracerProviderField = null;
        try {
            sdkTracerProviderField = ZipkinTraceService.class.getDeclaredField("sdkTracerProvider");
        } catch (NoSuchFieldException e) {
            sdkTracerProviderField = ReflectUtils.lookUpFieldByParentClass(ZipkinTraceService.class, "sdkTracerProvider");
            if (sdkTracerProviderField == null) {
                throw e;
            }
        }
        sdkTracerProviderField.setAccessible(true);
        SdkTracerProvider mockSdkTracerProvider = Mockito.mock(SdkTracerProvider.class);
        sdkTracerProviderField.set(zipkinTraceService, mockSdkTracerProvider);

        zipkinTraceService.shutdown();
        Mockito.verify(mockSdkTracerProvider, Mockito.times(1)).close();
    }
}
