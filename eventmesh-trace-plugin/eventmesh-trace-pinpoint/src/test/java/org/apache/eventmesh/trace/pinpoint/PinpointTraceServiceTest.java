/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.apache.eventmesh.trace.pinpoint;

import static org.junit.Assert.assertThrows;

import java.lang.reflect.Field;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import io.opentelemetry.sdk.trace.SdkTracerProvider;

public class PinpointTraceServiceTest {

    @Test
    public void testInit() {
        PinpointTraceService pinpointTraceService = new PinpointTraceService();
        pinpointTraceService.init();

        IllegalArgumentException illegalArgumentException =
            assertThrows(IllegalArgumentException.class, () ->
                Runtime.getRuntime().addShutdownHook(pinpointTraceService.shutdownHook));
        Assert.assertEquals(illegalArgumentException.getMessage(), "Hook previously registered");
    }

    @Test
    public void testShutdown() throws Exception {
        SdkTracerProvider mockSdkTracerProvider = Mockito.mock(SdkTracerProvider.class);
        PinpointTraceService pinpointTraceService = new PinpointTraceService();
        pinpointTraceService.init();
        Field sdkTracerProviderField = PinpointTraceService.class.getDeclaredField("sdkTracerProvider");
        sdkTracerProviderField.setAccessible(true);
        sdkTracerProviderField.set(pinpointTraceService, mockSdkTracerProvider);

        pinpointTraceService.shutdown();
        Mockito.verify(mockSdkTracerProvider, Mockito.times(1)).close();
    }
}
