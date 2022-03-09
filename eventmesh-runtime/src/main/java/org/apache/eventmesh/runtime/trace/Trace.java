package org.apache.eventmesh.runtime.trace;

import io.cloudevents.CloudEvent;
import io.netty.channel.ChannelHandlerContext;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.trace.api.EventMeshSpan;
import org.apache.eventmesh.trace.api.EventMeshTraceContext;
import org.apache.eventmesh.trace.api.EventMeshTraceService;
import org.apache.eventmesh.trace.api.TracePluginFactory;
import org.apache.eventmesh.trace.api.propagation.EventMeshContextCarrier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Trace {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private boolean useTrace = false;
    private EventMeshTraceService eventMeshTraceService;

    public Trace(boolean useTrace){
        this.useTrace = useTrace;
    }

    public void init(String tracePluginType) throws Exception{
        if(useTrace) {
            eventMeshTraceService = TracePluginFactory.getEventMeshTraceService(tracePluginType);
            eventMeshTraceService.init();
        }
    }

//    public EventMeshTracer getTracer() throws Exception{
//        return eventMeshTraceService.getTracer();
//    }

    public EventMeshContextCarrier extractFrom(ProtocolTransportObject pkg) throws Exception{
        return eventMeshTraceService.extractFrom(pkg);
    }

    public EventMeshSpan createSpan(String spanName){
        if(!useTrace) return null;
        return eventMeshTraceService.createSpan(spanName);
    }

    public EventMeshSpan createSpan(String spanName, ProtocolTransportObject pkg){
        if(!useTrace) return null;
        if(pkg == null){
            return eventMeshTraceService.createSpan(spanName);
        }else {
            EventMeshContextCarrier contextCarrier = eventMeshTraceService.extractFrom(pkg);
            return eventMeshTraceService.createSpan(spanName, contextCarrier);
        }
    }

    public EventMeshSpan createSpan(String spanName, CloudEvent cloudEvent){
        if(!useTrace) return null;
        if(cloudEvent == null){
            return eventMeshTraceService.createSpan(spanName);
        }else {
            EventMeshContextCarrier contextCarrier = eventMeshTraceService.extractFrom(cloudEvent);
            return eventMeshTraceService.createSpan(spanName, contextCarrier);
        }
    }

    public EventMeshSpan getSpanFromContext(ChannelHandlerContext ctx){
        try{
            if (useTrace) {
                EventMeshTraceContext
                    context = ctx.channel().attr(AttributeKeys.EVENTMESH_SERVER_CONTEXT).get();
                return context!=null ? context.getSpan() : null;
            }
        }catch (Exception exception){
            logger.warn("get span from ChannelHandlerContext fail", exception);
        }
        return null;
    }

    public void recordExceptionInSpan(EventMeshSpan span, Throwable e){
        try{
            if (useTrace) {
                if(span == null){
                    logger.warn("span is null when recordExceptionInSpan");
                    return;
                }
                span.addError(e);
            }
        }catch (Exception ex){
            logger.warn("finishSpan occur exception,", ex);
        }

    }

    public void addTraceInfoInSpan(EventMeshSpan span, CloudEvent cloudEvent){
        try{
            if (useTrace) {
                if(span == null){
                    logger.warn("span is null when finishSpan");
                    return;
                }
                EventMeshContextCarrier contextCarrier = eventMeshTraceService.extractFrom(cloudEvent);

            }
        }catch (Exception e){
            logger.warn("finishSpan occur exception,", e);
        }
    }

    public void finishSpan(EventMeshSpan span, CloudEvent cloudEvent){
        try{
            if (useTrace) {
                if(span == null){
                    logger.warn("span is null when finishSpan");
                    return;
                }
                if(cloudEvent != null){
                    EventMeshContextCarrier contextCarrier = eventMeshTraceService.extractFrom(cloudEvent);
                    eventMeshTraceService.addTraceInfoInSpan(span, contextCarrier);
                }
                span.finish();
            }
        }catch (Exception e){
            logger.warn("finishSpan occur exception,", e);
        }
    }

    public void shutdown() throws Exception{
        if(useTrace){
            eventMeshTraceService.shutdown();
        }
    }
}
