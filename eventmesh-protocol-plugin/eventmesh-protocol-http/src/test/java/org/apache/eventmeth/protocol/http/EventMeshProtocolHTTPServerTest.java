package org.apache.eventmeth.protocol.http;

import org.apache.eventmesh.protocol.api.EventMeshProtocolServer;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;
import org.junit.Assert;
import org.junit.Test;

public class EventMeshProtocolHTTPServerTest {

    @Test
    public void testLoadHttpProtocolPlugin() {
        EventMeshProtocolServer meshProtocolServer = EventMeshExtensionFactory.getExtension(EventMeshProtocolServer.class, "http");
        Assert.assertNotNull(meshProtocolServer);
    }

}