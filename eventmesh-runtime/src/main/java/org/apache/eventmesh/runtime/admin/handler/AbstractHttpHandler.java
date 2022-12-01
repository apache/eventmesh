package org.apache.eventmesh.runtime.admin.handler;

import org.apache.eventmesh.runtime.admin.controller.HttpHandlerManager;

import com.sun.net.httpserver.HttpHandler;

import lombok.Data;

/**
 * AbstractHttpHandler
 */
@Data
public abstract class AbstractHttpHandler implements HttpHandler {

    private final HttpHandlerManager httpHandlerManager;

    public AbstractHttpHandler(HttpHandlerManager httpHandlerManager) {
        this.httpHandlerManager = httpHandlerManager;
        this.httpHandlerManager.register(this);
    }
}
