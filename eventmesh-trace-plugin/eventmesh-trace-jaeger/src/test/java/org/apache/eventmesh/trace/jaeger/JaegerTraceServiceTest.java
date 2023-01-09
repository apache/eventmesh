package org.apache.eventmesh.trace.jaeger;

import static org.junit.Assert.assertThrows;

import java.lang.reflect.Field;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import io.opentelemetry.sdk.trace.SdkTracerProvider;

public class JaegerTraceServiceTest {

    @Test
    public void testInit() {
        JaegerTraceService jaegerTraceService = new JaegerTraceService();
        jaegerTraceService.init();

        Assert.assertNotNull(jaegerTraceService.sdkTracerProvider);
        Assert.assertNotNull(jaegerTraceService.openTelemetry);
        Assert.assertNotNull(jaegerTraceService.shutdownHook);

        IllegalArgumentException illegalArgumentException =
            assertThrows(IllegalArgumentException.class, () ->
                Runtime.getRuntime().addShutdownHook(jaegerTraceService.shutdownHook));
        Assert.assertEquals(illegalArgumentException.getMessage(), "Hook previously registered");
    }

    @Test
    public void testShutdown() throws NoSuchFieldException, IllegalAccessException {
        SdkTracerProvider mockSdkTracerProvider = Mockito.mock(SdkTracerProvider.class);
        JaegerTraceService jaegerTraceService = new JaegerTraceService();
        jaegerTraceService.init();
        Field sdkTracerProviderField = JaegerTraceService.class.getDeclaredField("sdkTracerProvider");
        sdkTracerProviderField.setAccessible(true);
        sdkTracerProviderField.set(jaegerTraceService, mockSdkTracerProvider);

        jaegerTraceService.shutdown();
        Mockito.verify(mockSdkTracerProvider, Mockito.times(1)).close();
    }
}
