package org.apache.eventmesh.trace.api;


import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;
import org.apache.eventmesh.trace.api.exception.TraceException;
import org.apache.eventmesh.trace.api.propagation.EventMeshContextCarrier;


@EventMeshSPI(isSingleton = true, eventMeshExtensionType = EventMeshExtensionType.TRACE)
public interface EventMeshTraceService {
    void init() throws TraceException;

    EventMeshTracer getTracer(String serviceName) throws TraceException;

    EventMeshContextCarrier extractFrom(ProtocolTransportObject pkg) throws TraceException;

    void shutdown() throws TraceException;
}
