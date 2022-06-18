package org.apache.eventmesh.runtime.core.protocol.http.processor;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

public interface HttpProcessor {
    String[] paths();

    HttpResponse handler(HttpRequest httpRequest);

}
