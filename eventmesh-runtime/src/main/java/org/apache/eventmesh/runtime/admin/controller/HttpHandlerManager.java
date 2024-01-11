/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.runtime.admin.controller;

import org.apache.eventmesh.runtime.common.EventHttpHandler;

import java.util.ArrayList;
import java.util.List;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * This class manages the registration of {@linkplain com.sun.net.httpserver.HttpHandler HttpHandler} for an {@linkplain
 * com.sun.net.httpserver.HttpServer HttpServer}.
 */

public class HttpHandlerManager {

    private final List<HttpHandler> httpHandlers = new ArrayList<>();

    /**
     * Registers an HTTP handler.
     *
     * @param httpHandler The {@link HttpHandler} to be registered. A handler which is invoked to process HTTP exchanges. Each HTTP exchange is
     *                    handled by one of these handlers.
     */
    public void register(HttpHandler httpHandler) {
        this.httpHandlers.add(httpHandler);
    }

    /**
     * Registers multiple HTTP handlers to a given HttpServer.
     * <p>
     * Each HTTP handler is annotated with the {@link EventHttpHandler} annotation, which specifies the path where the handler should be registered.
     *
     * @param server A HttpServer object that is bound to an IP address and port number and listens for incoming TCP connections from clients on this
     *               address. The registered HTTP handlers will be associated with this server.
     */
    public void registerHttpHandler(HttpServer server) {
        httpHandlers.forEach(httpHandler -> {
            EventHttpHandler eventHttpHandler = httpHandler.getClass().getAnnotation(EventHttpHandler.class);
            server.createContext(eventHttpHandler.path(), httpHandler);
        });

    }

    public void registerHttpWrapper(HttpHandlerManagerAdapter adapter) {
        httpHandlers.forEach(adapter::register);
    }
}
