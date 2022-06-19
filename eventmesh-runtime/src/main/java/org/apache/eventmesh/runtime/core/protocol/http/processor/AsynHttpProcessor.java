package org.apache.eventmesh.runtime.core.protocol.http.processor;

import org.apache.eventmesh.runtime.core.protocol.http.processor.HandlerService.HandlerSpecific;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

public interface AsynHttpProcessor extends HttpProcessor {

	public default HttpResponse handler(HttpRequest httpRequest) {
		return null;
	}
	
	public  void  handler(HandlerSpecific handlerSpecific , HttpRequest httpRequest);
}
