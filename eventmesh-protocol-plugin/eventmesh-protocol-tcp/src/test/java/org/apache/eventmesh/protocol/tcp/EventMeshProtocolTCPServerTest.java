package org.apache.eventmesh.protocol.tcp;

import org.apache.eventmesh.protocol.api.EventMeshProtocolServer;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;
import org.junit.Assert;
import org.junit.Test;

public class EventMeshProtocolTCPServerTest {

    @Test
    public void testGetEventMeshProtocolTcpServerPlugin() {
        EventMeshProtocolServer meshProtocolServer = EventMeshExtensionFactory.getExtension(EventMeshProtocolServer.class, "tcp");
        Assert.assertNotNull(meshProtocolServer);
    }
}