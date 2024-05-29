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

package com.apache.eventmesh.admin.server.web.handler;

import org.apache.eventmesh.common.remote.request.BaseRemoteRequest;
import org.apache.eventmesh.common.remote.response.BaseRemoteResponse;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RequestHandlerFactory implements ApplicationListener<ContextRefreshedEvent> {

    private final Map<String, BaseRequestHandler<BaseRemoteRequest, BaseRemoteResponse>> handlers =
        new ConcurrentHashMap<>();

    public BaseRequestHandler<BaseRemoteRequest, BaseRemoteResponse> getHandler(String type) {
        return handlers.get(type);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void onApplicationEvent(ContextRefreshedEvent event) {
        Map<String, BaseRequestHandler> beans =
            event.getApplicationContext().getBeansOfType(BaseRequestHandler.class);

        for (BaseRequestHandler<BaseRemoteRequest, BaseRemoteResponse> requestHandler : beans.values()) {
            Class<?> clazz = requestHandler.getClass();
            boolean skip = false;
            while (!clazz.getSuperclass().equals(BaseRequestHandler.class)) {
                if (clazz.getSuperclass().equals(Object.class)) {
                    skip = true;
                    break;
                }
                clazz = clazz.getSuperclass();
            }
            if (skip) {
                continue;
            }

            Class tClass = (Class) ((ParameterizedType) clazz.getGenericSuperclass()).getActualTypeArguments()[0];
            handlers.putIfAbsent(tClass.getSimpleName(), requestHandler);
        }
    }
}
