package org.apache.eventmesh.common.protocol.grpc.common;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;

public class EventMeshMessageWrapper implements ProtocolTransportObject {

    private EventMeshMessage eventMeshMessage;

    public EventMeshMessageWrapper(EventMeshMessage eventMeshMessage) {
        this.eventMeshMessage = eventMeshMessage;
    }

    public EventMeshMessage getMessage() {
        return eventMeshMessage;
    }
}
