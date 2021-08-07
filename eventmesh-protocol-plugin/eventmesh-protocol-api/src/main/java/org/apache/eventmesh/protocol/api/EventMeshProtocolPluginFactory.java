package org.apache.eventmesh.protocol.api;

import org.apache.eventmesh.spi.EventMeshExtensionFactory;

public enum EventMeshProtocolPluginFactory {
    ;

    public EventMeshProtocolServer getEventMeshProtocolServer(String protocolPluginName) {
        return EventMeshExtensionFactory.getExtension(EventMeshProtocolServer.class, protocolPluginName);
    }
}
