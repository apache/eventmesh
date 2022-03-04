package org.apache.eventmesh.runtime.trace;

import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.trace.api.EventMeshTraceService;
import org.apache.eventmesh.trace.api.EventMeshTracer;
import org.apache.eventmesh.trace.api.TracePluginFactory;
import org.apache.eventmesh.trace.api.propagation.EventMeshContextCarrier;

public class Trace {
    private EventMeshTraceService eventMeshTraceService;

    public void init(String tracePluginType) throws Exception{
        eventMeshTraceService = TracePluginFactory.getEventMeshTraceService(tracePluginType);
        eventMeshTraceService.init();
    }

    public EventMeshTracer getTracker(String serviceName) throws Exception{
        return eventMeshTraceService.getTracer(serviceName);
    }

    public EventMeshContextCarrier extractFrom(Package pkg) throws Exception{
        return eventMeshTraceService.extractFrom(pkg);
    }

    public EventMeshContextCarrier extractFrom(HttpCommand httpCommand) throws Exception{
        return eventMeshTraceService.extractFrom(httpCommand);
    }

    public void shutdown() throws Exception{
        eventMeshTraceService.shutdown();
    }
}
