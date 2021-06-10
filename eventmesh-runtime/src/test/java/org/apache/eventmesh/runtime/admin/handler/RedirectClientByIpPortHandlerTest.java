package org.apache.eventmesh.runtime.admin.handler;

import com.sun.net.httpserver.HttpExchange;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.powermock.api.mockito.PowerMockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

public class RedirectClientByIpPortHandlerTest {

    private RedirectClientByIpPortHandler redirectClientByIpPortHandler;

    @Before
    public void init() {
        EventMeshTCPServer mockServer = PowerMockito.mock(EventMeshTCPServer.class);
        redirectClientByIpPortHandler = new RedirectClientByIpPortHandler(mockServer);
    }

    @Test
    public void testHandleParamIllegal() throws IOException {
        OutputStream outputStream = new ByteArrayOutputStream();
        URI uri = URI.create("ip=127.0.0.1&port=1234&desteventMeshIp=127.0.0.1&desteventMeshPort=");

        HttpExchange mockExchange = PowerMockito.mock(HttpExchange.class);
        PowerMockito.when(mockExchange.getResponseBody()).thenReturn(outputStream);
        PowerMockito.when(mockExchange.getRequestURI()).thenReturn(uri);

        redirectClientByIpPortHandler.handle(mockExchange);

        String response = outputStream.toString();
        Assert.assertEquals("params illegal!", response);

    }
}