package org.apache.eventmesh.runtime.admin.handler;

import com.sun.net.httpserver.HttpHandler;

import lombok.Data;

/**
 * @author : wh
 * @date : 2022/12/1 16:26
 * @description:
 */
@Data
public abstract class AbstractHttpHandler<T> implements HttpHandler {
    
    private T config;
}
