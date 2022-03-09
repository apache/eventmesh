package org.apache.eventmesh.trace.api;


import io.cloudevents.CloudEvent;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;
import org.apache.eventmesh.trace.api.exception.TraceException;
import org.apache.eventmesh.trace.api.propagation.EventMeshContextCarrier;


@EventMeshSPI(isSingleton = true, eventMeshExtensionType = EventMeshExtensionType.TRACE)
public interface EventMeshTraceService {
    void init() throws TraceException;

//    EventMeshTracer getTracer(String serviceName) throws TraceException;

    EventMeshContextCarrier extractFrom(CloudEvent cloudEvent) throws TraceException;

    EventMeshContextCarrier extractFrom(ProtocolTransportObject pkg) throws TraceException;

    //TODO spanName怎么定义？ 由runtime指定 or 插件？
    EventMeshSpan createSpan(String spanName) throws TraceException;

    EventMeshSpan createSpan(String spanName, EventMeshContextCarrier carrier) throws TraceException;

    EventMeshSpan addTraceInfoInSpan(EventMeshSpan span, EventMeshContextCarrier carrier) throws TraceException;

    void shutdown() throws TraceException;
}
