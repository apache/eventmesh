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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * This class manages the registration of {@linkplain com.sun.net.httpserver.HttpHandler HttpHandler} for an {@linkplain
 * com.sun.net.httpserver.HttpServer HttpServer}.
 */

@Slf4j
public class HttpHandlerManager {

    private final List<HttpHandler> httpHandlers = new ArrayList<>();

    private final Map<String, HttpHandler> httpHandlerMap = new ConcurrentHashMap<>();

    /**
     * Registers an HTTP handler.
     *
     * @param httpHandler The {@link HttpHandler} to be registered. A handler which is invoked to process HTTP exchanges. Each HTTP exchange is
     *                    handled by one of these handlers.
     */
    public void register(HttpHandler httpHandler) {
        this.httpHandlers.add(httpHandler);
    }

    public void register() {
        httpHandlers.forEach(e -> {
            EventHttpHandler eventHttpHandler = e.getClass().getAnnotation(EventHttpHandler.class);
            httpHandlerMap.putIfAbsent(eventHttpHandler.path(), e);
        });
    }

    public Optional<HttpHandler> getHttpHandler(String path) {
        return Optional.ofNullable(httpHandlerMap.get(path));
    }


}
